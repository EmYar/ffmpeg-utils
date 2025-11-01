package me.emyar.common

import kotlinx.serialization.json.Json
import java.io.File

object AudioTrackAnalyzer {

    private val jsonRegex = """(?s)\{.*?"input_i".*?}""".toRegex()

    fun analyze(file: File, trackIndex: String): LoudNormData {
        val process = ProcessBuilder(
            "ffmpeg",
            "-hide_banner", "-nostats", "-v", "info",
            "-i", file.absolutePath,
            "-map", trackIndex,
            "-filter:a", "aformat=channel_layouts=stereo,loudnorm=I=-23:TP=-1:LRA=7:print_format=json",
            "-f", "null", "-",
        ).redirectErrorStream(false)
            .start()
        try {
            val output = process.errorStream.bufferedReader().readText()
            val jsonStr = jsonRegex.findAll(output).last().value

            return Json.decodeFromString<LoudNormData>(jsonStr)
        } finally {
            process.waitFor()
        }
    }
}