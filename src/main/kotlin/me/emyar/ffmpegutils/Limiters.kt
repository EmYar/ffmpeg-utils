package me.emyar.ffmpegutils

import io.ktor.server.plugins.di.annotations.*
import kotlinx.coroutines.sync.Semaphore

fun processingParallelismLimiter(@Property("ktor.application.config.limits.processingParallelism") limit: Int) =
    Semaphore(limit)
