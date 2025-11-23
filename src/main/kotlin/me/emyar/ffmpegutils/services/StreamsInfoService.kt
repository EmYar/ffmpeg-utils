package me.emyar.ffmpegutils.services

import java.nio.file.Path

class StreamsInfoService {
    suspend fun getFileStreamsInfo(path: Path): String {
        val file = path.toFile()
        when {
            !file.exists() -> throw IllegalArgumentException("File '$path' does not exist")
            file.isDirectory -> throw IllegalArgumentException("'$path' is a directory")
        }
        val (stdOut, exitCode) = runProcess(
            "ffprobe",
            "-hide_banner",
            "-analyzeduration", "10000000",
            "-probesize", "50000000",
            file.absolutePath,
        )
        if (exitCode != 0) {
            throw IllegalStateException("ffprobe exited with code $exitCode. Output:\n$stdOut")
        }
        return stdOut
    }
}
