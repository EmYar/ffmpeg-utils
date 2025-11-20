package me.emyar.ffmpegutils.processing.batch

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.emyar.ffmpegutils.models.SubsInfoDto
import me.emyar.ffmpegutils.processing.common.Analyzer
import me.emyar.ffmpegutils.processing.common.BASE_IN_PATH
import me.emyar.ffmpegutils.processing.common.BASE_OUT_PATH
import me.emyar.ffmpegutils.processing.common.SecondStep
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

private val log = KotlinLogging.logger {}

/**
 * @tag *AudioNormalization
 */
fun Route.filesBatchRoute(parallelismSemaphore: Semaphore): Route =
    post("/files-batch") {
        val req = call.receive<FilesBatchRequest>()
        val inputDir = Paths.get(req.inputDir.trim()).let {
            if (it.isAbsolute) it else BASE_IN_PATH.resolve(it)
        }
        val subsDirs = req.additionalSubsDirs.asSequence()
            .map(Paths::get)
            .map { if (it.exists()) it else BASE_IN_PATH.resolve(it) }
            .toList()
        val outputDir = Paths.get(req.outputDir.trim()).let {
            if (it.isAbsolute) it else BASE_OUT_PATH.resolve(it)
        }
        processBatch(
            parallelismSemaphore,
            inputDir,
            req.audioTrack,
            subsDirs,
            outputDir
        )
        call.respond(HttpStatusCode.OK)
    }

private suspend fun processBatch(
    parallelismSemaphore: Semaphore,
    inputDir: Path,
    audioTrack: String,
    subsDirs: List<Path>,
    outputDir: Path,
) {
    val files = inputDir.toFile()
        .listFiles { it.extension == "mkv" }!!
        .sortedBy { it.nameWithoutExtension }

    val subsByNameWithoutExt = subsDirs.asSequence()
        .mapNotNull { it.toFile().takeIf(File::exists) }
        .flatMap { it.listFiles()?.asSequence() ?: emptySequence() }
        .groupBy(File::nameWithoutExtension)

    for (inputFile in files) {
        parallelismSemaphore.withPermit {
            log.info { """Analyzing "$inputFile"...""" }
            val outputFile = outputDir.resolve(inputFile.name).toFile()
            val data = Analyzer.getLoudNormDataForTrack(files.first(), audioTrack)
            val subs = subsByNameWithoutExt[inputFile.nameWithoutExtension]
                ?.map { SubsInfoDto(it, it.parentFile.name) }
                ?: listOf()
            log.info { """Processing "$inputFile" to "$outputFile"...""" }
            SecondStep.applyFilter(inputFile, audioTrack, data, subs, outputFile)
        }
    }
}