package me.emyar.ffmpegutils.services.audio

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import me.emyar.ffmpegutils.models.AudioTrackGlobalIndex
import me.emyar.ffmpegutils.models.BatchFilesAudioNormalizationRequest
import me.emyar.ffmpegutils.models.BatchFilesAudioNormalizationRequest.SubtitlesDirInfo
import me.emyar.ffmpegutils.models.InputVideo
import me.emyar.ffmpegutils.models.SubtitlesDto
import me.emyar.ffmpegutils.services.FileCharsetDetectorService
import me.emyar.ffmpegutils.services.PathsAbsoluterService
import java.io.File
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

class BatchFilesAudioNormalizationService(
    private val limiter: Semaphore,
    private val pathsAbsoluter: PathsAbsoluterService,
    private val charsetDetector: FileCharsetDetectorService,
    private val audioNormalizer: AudioNormalizationService,
) {
    companion object {
        private const val INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG =
            "audioTrackGlobalIndex must be 'inputIndex:streamIndex', e.g. '0:2'"
    }

    suspend fun process(request: BatchFilesAudioNormalizationRequest) {
        val inputDir = request.inputDir.parseValidateConvertInput()
        val fixVideoTimestamps = request.fixVideoTimestamps
        val audioIndex = request.audioTrackGlobalIndex.parseValidateConvertAudioIndex()
        val subsByNameWithoutExt = request.additionalSubsDirs.parseValidateConvertSubs()
            .groupBy { (file, _) -> file.nameWithoutExtension }
        val outputDir = request.outputDir.parseValidateConvertOutput()

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw IllegalStateException("Failed to create directory $outputDir")
        }

        val inputVideoFiles =
            inputDir.listFiles { InputVideo.File.KnownExtensions.valuesMap.containsKey(it.extension.uppercase()) }
            ?.sortedBy { it.nameWithoutExtension }
                ?.map { InputVideo.File(it) }
            ?: throw IllegalStateException("Failed to get files in '$inputDir' directory")
        if (inputVideoFiles.isEmpty()) {
            throw IllegalArgumentException("There are no supported video files in the directory '$inputDir'")
        }

        coroutineScope {
            for (input in inputVideoFiles) {
                limiter.acquire()
                launch {
                    try {
                        val outputFile = outputDir.resolve("${input.file.nameWithoutExtension}.mkv")
                        val subs = subsByNameWithoutExt[input.file.nameWithoutExtension] ?: listOf()
                        audioNormalizer.process(input, fixVideoTimestamps, audioIndex, subs, outputFile)
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
