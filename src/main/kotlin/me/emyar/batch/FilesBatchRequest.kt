package me.emyar.batch

import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class FilesBatchRequest(
    val inputDir: Path,
    val audioTrack: String,
    val additionalSubsRelativeDirs: List<Path> = emptyList(),
    val outputDir: Path,
)
