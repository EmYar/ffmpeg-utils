package me.emyar.ffmpegutils.models

import java.io.File

data class SubtitlesDto(
    val file: File,
    val name: String,
    val lang: String? = null,
    val charset: String? = null,
)