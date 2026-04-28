package me.emyar.ffmpegutils.models

import kotlinx.serialization.Serializable
import me.emyar.ffmpegutils.models.serialization.PathSerializer
import java.nio.file.Path

@Serializable
data class TmpStorageConfig(
    @Serializable(with = PathSerializer::class)
    val path: Path,
    val sizeLimitBytes: Long,
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