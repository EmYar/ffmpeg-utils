package me.emyar.ffmpegutils.services

import java.nio.file.Path

class StreamsInfoService(
    private val coroutineProcessService: CoroutineProcessService,
) {
    suspend fun getFileStreamsInfo(path: Path): String {
        val file = path.toFile()
        when {
            !file.exists() -> throw IllegalArgumentException("File '$path' does not exist")
            file.isDirectory -> throw IllegalArgumentException("'$path' is a directory")
        }
        val (stdOut, exitCode) = coroutineProcessService.runProcess(
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
