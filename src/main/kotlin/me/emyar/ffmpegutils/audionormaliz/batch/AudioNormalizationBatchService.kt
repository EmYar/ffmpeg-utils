package me.emyar.ffmpegutils.audionormaliz.batch

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.emyar.ffmpegutils.Config
import me.emyar.ffmpegutils.audionormaliz.batch.AudioNormalizationBatchRequest.SubtitlesDirInfo
import me.emyar.ffmpegutils.audionormaliz.common.Analyzer
import me.emyar.ffmpegutils.audionormaliz.common.SecondStep
import me.emyar.ffmpegutils.audionormaliz.common.convertToAbsolute
import me.emyar.ffmpegutils.audionormaliz.common.detectCharset
import me.emyar.ffmpegutils.models.AudioTrackGlobalIndex
import me.emyar.ffmpegutils.models.SubtitlesDto
import java.io.File
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

object AudioNormalizationBatchService {

    private const val INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG =
        "audioTrackGlobalIndex must be 'inputIndex:streamIndex', e.g. '0:2'"

    suspend fun process(
        request: AudioNormalizationBatchRequest,
        parallelismLimiter: Semaphore,
    ) {
        val inputDir = request.inputDir.parseValidateConvertInput()
        val audioIndex = request.audioTrackGlobalIndex.parseValidateConvertAudioIndex()
        val subsByNameWithoutExt = request.additionalSubsDirs.parseValidateConvertSubs()
            .flatMap { (dir, lang) ->
                dir.listFiles()?.asSequence()
                    ?.filter { it.isFile }
                    ?.map { SubtitlesFileDto(it, lang) }
                    ?: emptySequence()
            }
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

        for (inputFile in files) {
            parallelismLimiter.withPermit {
                val outputFile = outputDir.resolve(inputFile.name)
                val subs = subsByNameWithoutExt[inputFile.nameWithoutExtension]
                    ?.map { SubtitlesDto(it.file, it.file.parentFile.name, it.language, it.file.detectCharset()) }
                    ?: listOf()
                log.info { """Analyzing "$inputFile"...""" }
                val data = Analyzer.getLoudNormDataForTrack(files.first(), audioIndex)
                log.info { """Processing "$inputFile" to "$outputFile"...""" }
                SecondStep.applyFilter(inputFile, audioIndex, data, subs, outputFile)
            }
        }
    }

    private fun String.parseValidateConvertInput(): File {
        val inputPath = Paths.get(trim()).convertToAbsolute(Config.DEFAULT_BASE_IN_PATH)
            ?: throw IllegalArgumentException("Relative inputDir without DEFAULT_INPUT_PATH_BASE env")
        val inputFile = inputPath.toFile()
        when {
            !inputFile.exists() -> throw IllegalArgumentException("Directory '$inputPath' does not exist")
            !inputFile.isDirectory -> throw IllegalArgumentException("'$inputPath' is not a directory")
        }
        return inputFile
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

    private fun List<SubtitlesDirInfo>.parseValidateConvertSubs(): Sequence<SubtitlesDirDto> =
        asSequence()
            .mapNotNull { (pathStr, lang) ->
                val path = Paths.get(pathStr).convertToAbsolute(Config.DEFAULT_BASE_IN_PATH)
                    ?: let {
                        log.warn { "Relative additional subs path '$it' without DEFAULT_INPUT_PATH_BASE env" }
                        return@mapNotNull null
                    }
                path.toFile()
                    .takeIf(File::exists)
                    ?.let { SubtitlesDirDto(it, lang) }
            }

    private fun String.parseValidateConvertOutput(): File {
        val outputPath = Paths.get(trim()).convertToAbsolute(Config.DEFAULT_BASE_OUT_PATH)
            ?: throw IllegalArgumentException("Relative outputDir without DEFAULT_OUTPUT_PATH_BASE env")
        return outputPath.toFile()
    }
}

private data class SubtitlesDirDto(
    val dir: File,
    val language: String?,
)

private data class SubtitlesFileDto(
    val file: File,
    val language: String?,
)
