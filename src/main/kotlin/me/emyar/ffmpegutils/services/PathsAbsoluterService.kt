package me.emyar.ffmpegutils.services

import io.ktor.server.plugins.di.annotations.*
import java.nio.file.Path
import java.nio.file.Paths

private const val defaultInPathKey = "ktor.application.config.defaultInPath"
private const val defaultOutPathKey = "ktor.application.config.defaultOutPath"

class PathsAbsoluterService(
    @Property(defaultInPathKey) defaultInStr: String?,
    @Property(defaultOutPathKey) defaultOutStr: String?,
) {
    private val defaultInPath: Path? = defaultInStr?.let(Paths::get)
    private val defaultOutPath: Path? = defaultOutStr?.let(Paths::get)

    fun absoluteIn(path: Path): Path =
        when {
            path.isAbsolute -> path
            defaultInPath != null -> defaultInPath.resolve(path)
            else -> throw IllegalArgumentException("Relative path '$path' without '$defaultInPathKey' property")
        }

    fun absoluteOut(path: Path): Path =
        when {
            path.isAbsolute -> path
            defaultOutPath != null -> defaultOutPath.resolve(path)
            else -> throw IllegalArgumentException("Relative path '$path' without '$defaultOutPathKey' property")
        }
}
