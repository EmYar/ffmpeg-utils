@file:OptIn(ExperimentalSerializationApi::class)

package me.emyar.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@Serializable
@JsonIgnoreUnknownKeys
data class LoudNormData(
    @SerialName("input_i") val inputI: String,
    @SerialName("input_tp") val inputTp: String,
    @SerialName("input_lra") val inputLra: String,
    @SerialName("input_thresh") val inputThresh: String,
    @SerialName("target_offset") val targetOffset: String,
)