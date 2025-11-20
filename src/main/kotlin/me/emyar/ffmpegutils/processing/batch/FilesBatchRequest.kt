package me.emyar.ffmpegutils.processing.batch

import kotlinx.serialization.Serializable

@Serializable
data class FilesBatchRequest(
    val inputDir: String,
    val audioTrack: String,
    val additionalSubsDirs: List<String> = emptyList(),
    val outputDir: String,
)
