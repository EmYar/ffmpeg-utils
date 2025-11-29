package me.emyar.ffmpegutils.services.audio

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import me.emyar.ffmpegutils.models.AudioTrackGlobalIndex
import me.emyar.ffmpegutils.models.BatchFilesAudioNormalizationRequest
import me.emyar.ffmpegutils.models.BatchFilesAudioNormalizationRequest.SubtitlesDirInfo
import me.emyar.ffmpegutils.models.SubtitlesDto
import me.emyar.ffmpegutils.services.FileCharsetDetectorService
import me.emyar.ffmpegutils.services.PathsAbsoluterService
import java.io.File
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

private const val INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG =
    "audioTrackGlobalIndex must be 'inputIndex:streamIndex', e.g. '0:2'"

class BatchFilesAudioNormalizationService(
    private val limiter: Semaphore,
    private val pathsAbsoluter: PathsAbsoluterService,
    private val charsetDetector: FileCharsetDetectorService,
    private val audioNormalizer: AudioNormalizationService,
) {
    suspend fun process(request: BatchFilesAudioNormalizationRequest) {
        val inputDir = request.inputDir.parseValidateConvertInput()
        val audioIndex = request.audioTrackGlobalIndex.parseValidateConvertAudioIndex()
        val subsByNameWithoutExt = request.additionalSubsDirs.parseValidateConvertSubs()
            .groupBy { (file, _) -> file.nameWithoutExtension }
        val outputDir = request.outputDir.parseValidateConvertOutput()

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Failed to create directory $outputDir")
        }

        val files = inputDir.listFiles { it.extension == "mkv" }
            ?.sortedBy { it.nameWithoutExtension }
            ?: throw IllegalStateException("Failed to get files in '$inputDir' directory")
        if (files.isEmpty()) {
            throw IllegalArgumentException("No mkv files in '$inputDir' directory")
        }

        coroutineScope {
            for (inputFile in files) {
                limiter.acquire()
                launch {
                    try {
                        val outputFile = outputDir.resolve(inputFile.name)
                        val subs = subsByNameWithoutExt[inputFile.nameWithoutExtension] ?: listOf()
                        audioNormalizer.process(inputFile, audioIndex, subs, outputFile)
                    } finally {
                        limiter.release()
                    }
                }
            }
        }
    }

    private fun String.parseValidateConvertInput(): File =
        pathsAbsoluter.absoluteIn(Paths.get(trim())).toFile()
            .also {
                when {
                    !it.exists() -> throw IllegalArgumentException("File '$it' does not exist")
                    !it.isDirectory -> throw IllegalArgumentException("File '$it' is not a directory")
                }
            }

    private fun String.parseValidateConvertAudioIndex(): AudioTrackGlobalIndex =
        split(':').let {
            require(it.size == 2) { INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG }
            val input = it[0].toUShortOrNull()
                ?: throw IllegalArgumentException(INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG)
            val stream = it[1].toUShortOrNull()
                ?: throw IllegalArgumentException(INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG)
            AudioTrackGlobalIndex(input, stream)
        }

    private suspend fun List<SubtitlesDirInfo>.parseValidateConvertSubs(): List<SubtitlesDto> =
        asSequence()
            .mapNotNull { (pathStr, lang) ->
                val dir = pathsAbsoluter.absoluteIn(Paths.get(pathStr))
                    .toFile()
                    .takeIf(File::exists)
                    ?: return@mapNotNull null
                when {
                    !dir.exists() -> {
                        log.warn { "Directory '$pathStr' does not exist" }
                        return@mapNotNull null
                    }

                    !dir.isDirectory -> {
                        log.warn { "'$pathStr' is not a directory" }
                        return@mapNotNull null
                    }
                }

                dir to lang
            }
            .flatMap { (dir, lang) ->
                dir.listFiles()?.asSequence()
                    ?.filter { it.isFile }
                    ?.map { it to lang }
                    ?: emptySequence()
            }
            .toList()
            .map { (file, lang) ->
                val charset = charsetDetector.detectCharset(file)
                log.debug { "Detected charset for '$file': $charset" }
                SubtitlesDto(file, file.parentFile.name, lang, charset)
            }

    private fun String.parseValidateConvertOutput(): File =
        pathsAbsoluter.absoluteOut(Paths.get(trim()))
            .toFile()
}
