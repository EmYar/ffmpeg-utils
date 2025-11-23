package me.emyar.ffmpegutils.utils

import java.io.File

fun File.guessSubsCodecByFileExtension(): String =
    when (extension.lowercase()) {
        "srt", "subrip" -> "srt"
        "ass", "ssa" -> "ass"
        "vtt" -> "webvtt"
        else -> "srt"
    }
