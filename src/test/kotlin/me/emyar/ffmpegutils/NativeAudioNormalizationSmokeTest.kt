@file:OptIn(ExperimentalPathApi::class)

package me.emyar.ffmpegutils

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.models.LoudNormData
import me.emyar.ffmpegutils.models.SingleFileAudioNormalizationRequest
import me.emyar.ffmpegutils.models.StreamsInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.io.File
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.math.abs

/**
 * End-to-end smoke test that exercises the actual compiled GraalVM native executable:
 * it starts the real binary produced by `nativeCompile`, POSTs a request to the running
 * server and verifies the produced file was genuinely loudness-normalized (not just copied).
 *
 * Slow and environment-dependent (requires a pre-built native image plus `ffmpeg`/`ffprobe`
 * on PATH), so it's tagged "native" and excluded from the regular `test` task. Run it via
 * `./gradlew nativeSmokeTest` (which depends on `nativeCompile`).
 */
@Tag("native")
class NativeAudioNormalizationSmokeTest {

    private companion object {
        const val TARGET_INTEGRATED_LOUDNESS = -23.0
        const val LOUDNORM_CONFIG = "I=-23:TP=-2:LRA=10"
        val LOUDNORM_JSON_REGEX = """(?s)\{.*?"input_i".*?}""".toRegex()
    }

    private val testDir: Path = Files.createTempDirectory("native-audio-smoke")
    private val httpClient: HttpClient = HttpClient.newHttpClient()
    private var serverProcess: Process? = null

    @AfterEach
    fun tearDown() {
        try {
            serverProcess?.let { process ->
                process.destroy()
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    process.waitFor(5, TimeUnit.SECONDS)
                }
            }
        } finally {
            testDir.deleteRecursively()
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    fun `normalizes the selected audio track through the native binary`() {
        // 1. Build a fixture with two audio streams (global indices 0 and 1) at clearly
        //    different loudness: a normal-level tone and one attenuated by 30dB.
        val fixture = testDir.resolve("fixture.mkv").toFile()
        generateTwoTrackFixture(fixture)

        val originalSelectedTrackLoudness = measureIntegratedLoudness(fixture, "0:1")
        // Sanity check on the fixture itself: it must genuinely be far off the target,
        // otherwise the final assertion could pass even on an untouched/copied file.
        abs(originalSelectedTrackLoudness - TARGET_INTEGRATED_LOUDNESS) shouldBeGreaterThan 10.0

        // 2. Launch the real native binary on a free ephemeral port.
        val port = findFreePort()
        serverProcess = startNativeServer(port)
        waitForServerUp(port)

        // 3. Ask the running server to normalize the SECOND audio track (global index 1).
        val outputFile = testDir.resolve("output.mkv").toFile()
        val requestJson = Json.encodeToString(
            SingleFileAudioNormalizationRequest(
                inputFilePath = fixture.absolutePath,
                audioTrackGlobalIndex = "0:1",
                outputFilePath = outputFile.absolutePath,
            )
        )
        val response = httpClient.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/api/v1/audio-normalization/single"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .timeout(Duration.ofSeconds(30))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        // 4. Assertions.
        response.statusCode() shouldBe 200
        outputFile.exists() shouldBe true

        val streams = Json.decodeFromString<StreamsInfo>(probeAudioStreams(outputFile))
        streams.streams.size shouldBe 1

        val normalizedLoudness = measureIntegratedLoudness(outputFile, "0:a:0")
        // Close to the configured target...
        normalizedLoudness shouldBe (TARGET_INTEGRATED_LOUDNESS plusOrMinus 1.5)
        // ...and clearly different from the original loudness we deliberately set, so this
        // can't accidentally pass on a plain stream-copy of the untouched track.
        abs(normalizedLoudness - originalSelectedTrackLoudness) shouldBeGreaterThan 10.0
    }

    private fun generateTwoTrackFixture(file: File) {
        runProcess(
            "ffmpeg", "-hide_banner", "-y",
            "-f", "lavfi", "-i", "sine=frequency=1000:duration=3:sample_rate=48000",
            "-f", "lavfi", "-i", "sine=frequency=1000:duration=3:sample_rate=48000,volume=-30dB",
            "-map", "0:a", "-map", "1:a",
            "-c:a", "pcm_s16le",
            file.absolutePath,
        )
    }

    private fun probeAudioStreams(file: File): String =
        runProcess(
            "ffprobe", "-v", "error",
            "-select_streams", "a",
            "-show_entries", "stream=index",
            "-of", "json",
            file.absolutePath,
        )

    private fun measureIntegratedLoudness(file: File, mapSpec: String): Double =
        runProcess(
            "ffmpeg", "-hide_banner", "-nostats", "-v", "info",
            "-i", file.absolutePath,
            "-map", mapSpec,
            "-filter:a", "loudnorm=$LOUDNORM_CONFIG:print_format=json",
            "-f", "null", "-",
        ).let { output -> LOUDNORM_JSON_REGEX.findAll(output).last().value }
            .let { Json.decodeFromString<LoudNormData>(it) }
            .inputI.toDouble()

    private fun runProcess(vararg command: String): String {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().decodeToString()
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            """Command '${command.joinToString(" ")}' failed with exit code $exitCode. Output:
               $output
            """.trimIndent()
        }
        return output
    }

    private fun findFreePort(): Int =
        ServerSocket(0).use { it.localPort }

    private fun startNativeServer(port: Int): Process {
        val binaryPath = System.getProperty("nativeExecutablePath")
            ?: "build/native/nativeCompile/ffmpeg-utils"
        val binary = File(binaryPath)
        check(binary.exists()) {
            "Native executable not found at '${binary.absolutePath}'. Run './gradlew nativeCompile' first."
        }
        return ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .redirectOutput(testDir.resolve("native-server.log").toFile())
            .apply { environment()["PORT"] = port.toString() }
            .start()
    }

    private fun waitForServerUp(port: Int) {
        val deadlineNanos = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        var lastError: Exception? = null
        while (System.nanoTime() < deadlineNanos) {
            try {
                httpClient.send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/"))
                        .timeout(Duration.ofSeconds(1))
                        .GET()
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
                return
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(50)
            }
        }
        error("Native server on port $port did not start within timeout. Last error: $lastError")
    }
}