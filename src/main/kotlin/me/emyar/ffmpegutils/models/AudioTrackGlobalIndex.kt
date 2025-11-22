package me.emyar.ffmpegutils.models

data class AudioTrackGlobalIndex(
    val input: UShort,
    val stream: UShort,
) {
    override fun toString() = "$input:$stream"
}
