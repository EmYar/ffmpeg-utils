package me.emyar.ffmpegutils.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.universalchardet.UniversalDetector
import java.io.File
import java.io.FileInputStream

private const val SAMPLE_SIZE = 4096

class FileCharsetDetectorService {

    suspend fun detectCharset(file: File): String? {
        val sample = file.readSample()
        val detector = UniversalDetector(null)
        detector.handleData(sample, 0, sample.size)
        detector.dataEnd()
        return detector.detectedCharset
    }

    private suspend fun File.readSample(): ByteArray = withContext(Dispatchers.IO) {
        FileInputStream(this@readSample).use {
            it.readNBytes(SAMPLE_SIZE)
        }
    }
}
