package me.emyar.ffmpegutils.utils

import me.emyar.ffmpegutils.models.InputVideo

fun InputVideo.toInputArgs() =
    when (this) {
        is InputVideo.Dvd -> arrayOf("-f", "dvdvideo", "-i", file.absolutePath)
        is InputVideo.File -> arrayOf("-i", file.absolutePath)
    }
