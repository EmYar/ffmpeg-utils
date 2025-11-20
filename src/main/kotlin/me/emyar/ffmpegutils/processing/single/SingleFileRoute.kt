package me.emyar.ffmpegutils.processing.single

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.emyar.ffmpegutils.Config
import me.emyar.ffmpegutils.models.SubsInfoDto
import me.emyar.ffmpegutils.processing.common.Analyzer
import me.emyar.ffmpegutils.processing.common.SecondStep
import me.emyar.ffmpegutils.processing.common.detectCharset
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.measureTime

private val log = KotlinLogging.logger {}

/**
 * Normalize audio for in one file
 *
 * @tag *AudioNormalization
 * @summary Обработка одного файла
 * @response 200 text/plain OK
 */
fun Route.singleFileRoute(parallelismSemaphore: Semaphore): Route =
    post("/single-file") {
        val req = call.receive<SingleFileRequest>()
        val inputPath = Paths.get(req.inputFilePath.trim()).let {
            if (it.isAbsolute) it else Config.BASE_IN_PATH.resolve(it)
        }
        val outputPath = Paths.get(req.outputFilePath.trim()).let {
            if (it.isAbsolute) it else Config.BASE_OUT_PATH.resolve(it)
        }
        val duration = measureTime {
            processFile(
                parallelismSemaphore,
                inputPath,
                req.audioTrack.trim(),
                req.additionalSubs,
                outputPath,
            )
        }
        call.respond(HttpStatusCode.OK, "Audio for '${req.inputFilePath}' normalized successfully. $duration")
    }

private suspend fun processFile(
    parallelismSemaphore: Semaphore,
    inputPath: Path,
    audioTrack: String,
    additionalSubs: List<SubsInfo>,
    outputPath: Path,
) {
    val subs = additionalSubs.mapNotNull { (path, name, lang, charset) ->
        val file = Paths.get(path).toFile().takeIf(File::exists)
            ?: return@mapNotNull null
        SubsInfoDto(file, name ?: file.nameWithoutExtension, lang, charset ?: file.detectCharset())
    }

    val inputFile = inputPath.toFile()
    if (!inputFile.exists()) {
        throw IllegalArgumentException("File '$inputPath' does not exist")
    }
    if (inputFile.isDirectory) {
        throw IllegalArgumentException("File '$inputPath' is a directory")
    }

    outputPath.parent.toFile().let {
        if (!it.exists() && !it.mkdirs()) {
            throw IllegalStateException("Failed to create directory '$it'")
        }
    }
    val outputFile = outputPath.toFile()

    parallelismSemaphore.withPermit {
        log.info { """Analyzing "$inputFile"...""" }
        val data = Analyzer.getLoudNormDataForTrack(inputFile, audioTrack)
        log.info { """Processing "$inputFile" to "$outputFile"...""" }
        SecondStep.applyFilter(inputFile, audioTrack, data, subs, outputFile)
    }
}
