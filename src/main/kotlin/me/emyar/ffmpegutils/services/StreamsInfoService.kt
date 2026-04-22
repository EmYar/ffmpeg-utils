package me.emyar.ffmpegutils.services

import me.emyar.ffmpegutils.models.InputVideo
import me.emyar.ffmpegutils.utils.toInputArgs
import java.nio.file.Path

class StreamsInfoService(
    private val pathsAbsoluter: PathsAbsoluterService,
    private val coroutineProcessService: CoroutineProcessService,
) {
    suspend fun getFileStreamsInfo(path: Path): String {
        val input = pathsAbsoluter.absoluteIn(path)
            .toFile().also {
                if (!it.exists()) throw IllegalArgumentException("The file or directory '$path' does not exist")
            }
            .let(InputVideo::fromFile)
            ?: throw IllegalArgumentException("Unsupported file or directory: '$path'")
        val (stdOut, exitCode) = coroutineProcessService.runProcess(
            "ffprobe",
            "-hide_banner",
            "-analyzeduration", "10000000",
            "-probesize", "50000000",
            *input.toInputArgs(),
        )
        if (exitCode != 0) {
            throw IllegalStateException("ffprobe exited with code $exitCode. Output:\n$stdOut")
        }
        return stdOut
    }
}
