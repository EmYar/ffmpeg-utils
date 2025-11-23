package me.emyar.ffmpegutils.commands

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun runProcess(vararg command: String): ProcessResult = withContext(Dispatchers.IO) {
    val process = ProcessBuilder(*command)
        .redirectErrorStream(true)
        .start()
    val stdout = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()

    ProcessResult(stdout, exitCode)
}

data class ProcessResult(
    val stdout: String,
    val exitCode: Int,
)
