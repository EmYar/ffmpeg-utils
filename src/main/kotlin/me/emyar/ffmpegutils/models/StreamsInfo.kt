@file:OptIn(ExperimentalSerializationApi::class)

package me.emyar.ffmpegutils.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class StreamsInfo(val streams: Collection<Stream> = listOf()) {
    @Serializable
    @JsonIgnoreUnknownKeys
    data class Stream(val index: Int)
}
