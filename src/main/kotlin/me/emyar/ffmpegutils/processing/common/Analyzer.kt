package me.emyar.ffmpegutils.processing.common

import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.models.LoudNormData
import java.io.File

object Analyzer {

    private val jsonRegex = """(?s)\{.*?"input_i".*?}""".toRegex()

    const val EBU_R128_CONFIG = "I=-23:TP=-2:LRA=7"

    fun getFileInfo(file: File): String =
        ProcessBuilder(
            "ffprobe",
            "-hide_banner",
            file.absolutePath,
        ).redirectErrorStream(true) // ffprobe пишет результат в ERROR
            .start()
            .also(Process::waitFor)
            .inputStream.bufferedReader().readText()

    fun getLoudNormDataForTrack(file: File, trackIndex: String): LoudNormData =
        ProcessBuilder(
            "ffmpeg",
            "-hide_banner", "-nostats", "-v", "info",
            "-i", file.absolutePath,
            "-map", trackIndex,
            "-filter:a", "aformat=channel_layouts=stereo,loudnorm=$EBU_R128_CONFIG:print_format=json",
            "-f", "null", "-",
        ).redirectErrorStream(true)
            .start()
            .also(Process::waitFor)
            .inputStream.bufferedReader().readText()
            .let { jsonRegex.findAll(it).last().value }
            .let { Json.decodeFromString<LoudNormData>(it) }
}