package me.emyar.ffmpegutils.models

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class TmpStorageConfig(
    val path: Path,
    val sizeLimitMb: Int,
)

data class TmpStorageState(
    val path: Path,
    val limitBytes: Long,
    var usedBytes: Long = 0,
)