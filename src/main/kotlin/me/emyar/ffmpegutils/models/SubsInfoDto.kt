package me.emyar.ffmpegutils.models

import java.io.File

data class SubsInfoDto(
    val file: File,
    val name: String,
    val lang: String? = null,
    val charset: String? = null,
)