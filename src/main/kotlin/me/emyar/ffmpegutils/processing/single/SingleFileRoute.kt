package me.emyar.ffmpegutils.processing.single

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.emyar.ffmpegutils.models.SubsInfoDto
import me.emyar.ffmpegutils.processing.common.Analyzer
import me.emyar.ffmpegutils.processing.common.SecondStep
import me.emyar.ffmpegutils.processing.common.detectCharset
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.measureTime

/**
 * Normalize audio for in one file
 *
 * @tag *AudioNormalization
 * @summary Обработка одного файла
 * @response 200 text/plain OK
 */
fun Route.singleFileRoute(mutex: Mutex): Route =
    post("/single-file") {
        val req = call.receive<SingleFileRequest>()
        val duration = measureTime {
            processFile(
                mutex,
                Paths.get(req.inputFilePath.trim()),
                req.audioTrack.trim(),
                req.additionalSubs,
                Paths.get(req.outputFilePath.trim()),
            )
        }
        call.respond(HttpStatusCode.OK, "Audio for '${req.inputFilePath}' normalized successfully. $duration")
    }

private suspend fun processFile(
    mutex: Mutex,
    inputPath: Path,
    audioTrack: String,
    additionalSubs: List<SubsInfo>,
    outputPath: Path,
) {
    val subs = additionalSubs.asSequence()
        .mapNotNull { (path, lang, charset) ->
            val file = Paths.get(path).toFile().takeIf(File::exists)
                ?: return@mapNotNull null
            SubsInfoDto(file, file.nameWithoutExtension, lang, charset ?: file.detectCharset())
        }
        .toList()

    val inputFile = inputPath.toFile()
    if (!inputFile.exists()) {
        throw IllegalStateException("File $inputPath does not exist")
    }

    mutex.withLock {
        coroutineScope {
            launch(Dispatchers.IO) {
                println("""Analyzing "$inputFile"...""")
                val outputFile = outputPath.toFile()
                val data = Analyzer.getLoudNormDataForTrack(inputFile, audioTrack)
                println("""Processing "$inputFile" to "$outputFile"...""")
                SecondStep.applyFilter(inputFile, audioTrack, data, subs, outputFile)
            }
        }
    }
}

