package me.emyar.batch

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.emyar.common.Analyzer
import me.emyar.common.SecondStep
import me.emyar.common.SubsInfoDto
import java.io.File
import java.nio.file.Path

/**
 * @tag *AudioNormalization
 */
fun Route.filesBatchRoute(mutex: Mutex): Route =
    post("/files-batch") {
        val req = call.receive<FilesBatchRequest>()
        processBatch(
            mutex,
            req.inputDir,
            req.audioTrack,
            req.additionalSubsRelativeDirs,
            req.outputDir
        )
        call.respond(HttpStatusCode.OK)
    }

private suspend fun processBatch(
    mutex: Mutex,
    inputDir: Path,
    audioTrack: String,
    additionalSubsRelativeDirs: List<Path>,
    outputDir: Path,
) {
    val subs = additionalSubsRelativeDirs
        .map { if (it.isAbsolute) it else inputDir.resolve(it) }

    val files = inputDir.toFile()
        .listFiles { it.extension == "mkv" }!!
        .sortedBy { it.nameWithoutExtension }

    val subsByNameWithoutExt = subs.asSequence()
        .mapNotNull { it.toFile().takeIf(File::exists) }
        .flatMap { it.listFiles()?.asList() ?: listOf() }
        .groupBy(File::nameWithoutExtension)

    mutex.withLock {
        coroutineScope {
            launch(Dispatchers.IO) {
                for (inputFile in files) {
                    println("""Analyzing "$inputFile"...""")
                    val outputFile = outputDir.resolve(inputFile.name).toFile()
                    val data = Analyzer.getLoudNormDataForTrack(files.first(), audioTrack)
                    val subs = subsByNameWithoutExt[inputFile.nameWithoutExtension]
                        ?.map { SubsInfoDto(it, it.parentFile.name) }
                        ?: listOf()
                    println("""Processing "$inputFile" to "$outputFile"...""")
                    SecondStep.applyFilter(inputFile, audioTrack, data, subs, outputFile)
                }
            }
        }
    }
}