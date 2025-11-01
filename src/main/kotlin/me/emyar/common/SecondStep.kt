package me.emyar.common

import java.io.File

object SecondStep {

    fun applyFilter(
        input: File,
        audioTrack: String,
        data: LoudNormData,
        output: File,
    ) {
        val audioTrackParts = audioTrack.split(':')
        require(audioTrackParts.size == 2) { "track format must be 'inputIndex:streamIndex'" }
        val metadataSource = "${audioTrackParts[0]}:s:${audioTrackParts[1]}"

        val loudnormFilter = listOf(
            "I=-23",
            "TP=-1",
            "LRA=7",
            "measured_I=${data.inputI}",
            "measured_TP=${data.inputTp}",
            "measured_LRA=${data.inputLra}",
            "measured_thresh=${data.inputThresh}",
            "offset=${data.targetOffset}",
            "linear=true",
            "print_format=summary"
        ).joinToString(prefix = "loudnorm=", separator = ":")

        val code = ProcessBuilder(
            "ffmpeg",
            "-hide_banner", "-v", "error", "-stats",
            "-i", input.absolutePath,

            "-map", "0:v", "-c:v", "copy",
            "-map", "0:s?", "-c:s", "copy",

            "-map", audioTrack,

            "-af", loudnormFilter,
            "-c:a", "flac",
            "-ac", "2",

            "-map_metadata:s:a:0", metadataSource,

            output.absolutePath,
        ).inheritIO()
            .start()
            .waitFor()
        require(code == 0) { "ffmpeg exit=$code" }
    }
}