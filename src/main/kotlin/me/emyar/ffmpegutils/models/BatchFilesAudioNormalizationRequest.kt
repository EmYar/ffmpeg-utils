package me.emyar.ffmpegutils.models

import kotlinx.serialization.Serializable

@Serializable
data class BatchFilesAudioNormalizationRequest(
    val inputDir: String,
    val fixVideoTimestamps: Boolean = false,
    val audioTrackGlobalIndex: String,
    val additionalSubsDirs: List<SubtitlesDirInfo> = emptyList(),
    val outputDir: String,
) {
    @Serializable
    data class SubtitlesDirInfo(
        val path: String,
        val language: String? = null,
    )
}
