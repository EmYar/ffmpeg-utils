package me.emyar.ffmpegutils.services

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.annotations.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.models.TmpStorageConfig
import me.emyar.ffmpegutils.models.TmpStorageState
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger { }

@OptIn(ExperimentalUuidApi::class)
class TmpStorageService(
    @Property("ktor.application.config.tmpStoragesJson") config: String?,
) {
    private val storages: List<TmpStorageState> =
        config?.takeIf { it.isNotBlank() }
            ?.let { Json.decodeFromString<List<TmpStorageConfig>>(it) }
            ?.map {
                if (!it.path.isAbsolute) {
                    throw IllegalStateException("Path '${it.path}' must be absolute")
                }
                TmpStorageState(
                    path = it.path,
                    limitBytes = it.sizeLimitMb.toLong() * 1024 * 1024,
                )
            }
            ?: emptyList()

    private val mutex = Mutex()

    private val paths = mutableListOf<Pair<String, String>>()

    suspend fun getPath(file: File): Path? {
        if (storages.isEmpty()) return null

        return mutex.withLock {
            withContext(Dispatchers.IO) {
                if (!file.exists()) {
                    throw IllegalArgumentException("File does not exist: '$file'")
                }
                val fileSize = Files.size(file.toPath())
                val storage = storages.find { (it.limitBytes - it.usedBytes) > fileSize }
                    ?: run {
                        log.debug { "No storage found for file '${file.absolutePath}'. Storages: '$storages'" }
                        return@withContext null
                    }

                storage.usedBytes += fileSize

                storage.path.resolve(Uuid.generateV4().toString())
                    .also {
                        log.debug { "Using TMP storage for file '${file.path}': '$it'" }
                        paths += file.absolutePath to it.absolutePathString()
                    }
            }
        }
    }

    suspend fun removeFile(filePath: Path) {
        if (storages.isEmpty()) {
            throw IllegalStateException("Attempt to remove file from tmp storage without storages")
        }

        mutex.withLock {
            val fileExists: Boolean
            val absoluteFilePathString: String
            val fileSize: Long

            withContext(Dispatchers.IO) {
                fileExists = Files.exists(filePath)
                if (fileExists) {
                    absoluteFilePathString = filePath.absolutePathString()
                    fileSize = Files.size(filePath)
                } else {
                    log.warn { "File does not exist: '$filePath'" }
                    absoluteFilePathString = ""
                    fileSize = 0
                }
            }

            if (!fileExists) return

            val storage = storages.find { absoluteFilePathString.startsWith(it.path.toString()) }
                ?: throw IllegalStateException("Storage for file '$filePath' not found'")

            paths.removeIf { (_, tmpFilePath) -> tmpFilePath == absoluteFilePathString }

            storage.usedBytes -= fileSize
        }
    }
}
