package me.emyar.ffmpegutils.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun runProcess(vararg command: String): ProcessResult = withContext(Dispatchers.IO) {
    val process = ProcessBuilder(*command)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.readBytes().decodeToString()
    val exitCode = process.waitFor()

    ProcessResult(output, exitCode)
}

data class ProcessResult(
    val output: String,
    val exitCode: Int,
)
