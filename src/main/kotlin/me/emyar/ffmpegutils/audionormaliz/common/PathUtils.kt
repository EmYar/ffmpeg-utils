package me.emyar.ffmpegutils.audionormaliz.common

import java.nio.file.Path

fun Path.convertToAbsolute(defaultPathBase: Path?): Path? =
    when {
        isAbsolute -> this
        defaultPathBase != null -> defaultPathBase.resolve(this)
        else -> null
    }