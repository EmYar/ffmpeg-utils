package me.emyar.ffmpegutils.models

import kotlinx.serialization.Serializable

@Serializable
data class SingleFileAudioNormalizationRequest(
    val inputFilePath: String,
    val audioTrackGlobalIndex: String,
    val additionalSubs: List<SubsInfo> = emptyList(),
    val outputFilePath: String,
) {
    @Serializable
    data class SubsInfo(
        val path: String,
        val name: String? = null,
        val language: String? = null,
        val charset: String? = null,
    )
}
