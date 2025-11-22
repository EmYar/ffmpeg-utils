package me.emyar.ffmpegutils.audionormaliz.batch

import kotlinx.serialization.Serializable

@Serializable
data class AudioNormalizationBatchRequest(
    val inputDir: String,
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
