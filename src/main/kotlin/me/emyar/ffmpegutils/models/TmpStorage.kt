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

data class TmpStorageDump(
    val path: Path,
    val limitBytes: Long,
    val usedBytes: Long = 0,
)

fun TmpStorageState.toDump() = TmpStorageDump(
    path = path,
    limitBytes = limitBytes,
    usedBytes = usedBytes,
)