package me.emyar.ffmpegutils.processing.single

import kotlinx.serialization.Serializable

@Serializable
data class SingleFileRequest(
    val inputFilePath: String,
    val audioTrack: String,
    val additionalSubs: List<SubsInfo> = emptyList(),
    val outputFilePath: String,
)

@Serializable
data class SubsInfo(
    val path: String,
    val name: String? = null,
    val language: String? = null,
    val charset: String? = null,
)