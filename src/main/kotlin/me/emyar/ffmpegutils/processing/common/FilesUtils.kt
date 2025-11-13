package me.emyar.ffmpegutils.processing.common

import org.mozilla.universalchardet.UniversalDetector
import java.io.File
import java.io.FileInputStream

fun File.detectCharset(): String? {
    FileInputStream(this).use { fis ->
        val buf = ByteArray(4096)
        val detector = UniversalDetector(null)
        var n: Int
        while (fis.read(buf).also { n = it } > 0 && !detector.isDone) {
            detector.handleData(buf, 0, n)
        }
        detector.dataEnd()
        val charset = detector.detectedCharset
        detector.reset()
        return charset
    }
}

fun File.guessSubsCodecByFileExtension(): String =
    when (extension.lowercase()) {
        "srt", "subrip" -> "srt"
        "ass", "ssa" -> "ass"
        "vtt" -> "webvtt"
        else -> "srt"
    }
