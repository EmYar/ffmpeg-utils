package me.emyar.ffmpegutils.processing.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.models.LoudNormData
import java.io.File

object Analyzer {

    private val jsonRegex = """(?s)\{.*?"input_i".*?}""".toRegex()

    suspend fun getFileInfo(file: File): String = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "ffprobe",
            "-hide_banner",
            "-analyzeduration", "10000000",
            "-probesize", "50000000",
            file.absolutePath,
        ).redirectErrorStream(true)
            .start()

        process.inputStream.reader().readText()
            .also {
                process.waitFor().let {
                    if (it != 0) {
                        throw IllegalStateException("ffprobe exited with code: $it")
                    }
                }
            }
    }

    suspend fun getLoudNormDataForTrack(file: File, trackIndex: String): LoudNormData = withContext(Dispatchers.IO) {
        val process = ProcessBuilder(
            "ffmpeg",
            "-hide_banner", "-nostats", "-v", "info",
            "-i", file.absolutePath,
            "-map", trackIndex,
            "-filter:a", "aformat=channel_layouts=stereo,loudnorm=$EBU_R128_CONFIG:print_format=json",
            "-f", "null", "-",
        ).redirectErrorStream(true)
            .start()

        process.inputStream.reader().readText()
            .let { jsonRegex.findAll(it).last().value }
            .let { Json.decodeFromString<LoudNormData>(it) }
            .also {
                process.waitFor().let {
                    if (it != 0) {
                        throw IllegalStateException("ffmpeg exited with code: $it")
                    }
                }
            }
    }
}