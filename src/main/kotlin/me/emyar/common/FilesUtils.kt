package me.emyar.common

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
        return charset // например "windows-1251", "UTF-8", "KOI8-R" или null
    }
}

fun File.getSubsCodecByFileExtension(): String =
    when (extension.lowercase()) {
        "srt", "subrip" -> "srt"
        "ass", "ssa" -> "ass"
        "vtt" -> "webvtt"
        else -> "srt"
    }
