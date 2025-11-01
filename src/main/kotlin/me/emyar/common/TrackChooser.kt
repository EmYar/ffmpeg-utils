package me.emyar.common

import java.io.File

object TrackChooser {

    fun chooseTrack(file: File): String {
        val process = ProcessBuilder(
            "ffprobe",
            "-hide_banner",
            file.absolutePath,
        ).inheritIO()
            .start()

        println(process.inputStream.bufferedReader().readText())

        process.waitFor()

        println("Choose audio track")
        return readln()
    }
}