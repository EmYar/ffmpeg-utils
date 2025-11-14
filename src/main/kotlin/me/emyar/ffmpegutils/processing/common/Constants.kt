package me.emyar.ffmpegutils.processing.common

val FFTOOLS_PREFIX = System.getenv("FFTOOLS_PREFIX") ?: ""

val FFMPEG_CMD = FFTOOLS_PREFIX + "ffmpeg"

val FFPROBE_CMD = FFTOOLS_PREFIX + "ffprobe"

const val EBU_R128_CONFIG = "I=-23:TP=-2:LRA=7"