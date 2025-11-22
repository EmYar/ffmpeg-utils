package me.emyar.ffmpegutils.audionormaliz.single

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.emyar.ffmpegutils.Config
import me.emyar.ffmpegutils.audionormaliz.common.Analyzer
import me.emyar.ffmpegutils.audionormaliz.common.SecondStep
import me.emyar.ffmpegutils.audionormaliz.common.convertToAbsolute
import me.emyar.ffmpegutils.audionormaliz.common.detectCharset
import me.emyar.ffmpegutils.audionormaliz.single.AudioNormalizationRequestSingle.SubsInfo
import me.emyar.ffmpegutils.models.AudioTrackGlobalIndex
import me.emyar.ffmpegutils.models.SubtitlesDto
import java.io.File
import java.nio.file.Paths

private val log = KotlinLogging.logger {}

object AudioNormalizationSingleService {

    private const val INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG =
        "audioTrackGlobalIndex must be 'inputIndex:streamIndex', e.g. '0:2'"

    suspend fun process(
        request: AudioNormalizationRequestSingle,
        parallelismLimiter: Semaphore,
    ) {
        val inputFile = request.inputFilePath.parseValidateConvertInput()
        val audioIndex = request.audioTrackGlobalIndex.parseValidateConvertAudioIndex()
        val additionalSubs = request.additionalSubs.parseValidateConvertSubs()
        val outputFile = request.outputFilePath.parseValidateConvertOutput()
        outputFile.parentFile.let {
            if (!it.exists() && !it.mkdirs()) {
                throw IllegalStateException("Failed to create output directory: $it")
            }
        }

        parallelismLimiter.withPermit {
            log.info { """Analyzing "$inputFile"...""" }
            val data = Analyzer.getLoudNormDataForTrack(inputFile, audioIndex)
            log.info { """Processing "$inputFile" to "$outputFile"...""" }
            SecondStep.applyFilter(inputFile, audioIndex, data, additionalSubs, outputFile)
        }
    }

    private fun String.parseValidateConvertInput(): File {
        val inputPath = Paths.get(trim()).convertToAbsolute(Config.DEFAULT_BASE_IN_PATH)
            ?: throw IllegalArgumentException("Relative inputFilePath without DEFAULT_INPUT_PATH_BASE env")
        val inputFile = inputPath.toFile()
        if (!inputFile.exists()) {
            throw IllegalArgumentException("File '$inputPath' does not exist")
        }
        if (inputFile.isDirectory) {
            throw IllegalArgumentException("File '$inputPath' is a directory")
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

    private fun List<SubsInfo>.parseValidateConvertSubs(): List<SubtitlesDto> =
        mapNotNull { (pathStr, desiredName, language, charset) ->
            val path = Paths.get(pathStr).convertToAbsolute(Config.DEFAULT_BASE_IN_PATH)
                ?: let {
                    log.warn { "Relative additionalSubs path '$it' without DEFAULT_INPUT_PATH_BASE env" }
                    return@mapNotNull null
                }
            val file = path.toFile().takeIf(File::exists)
                ?: return@mapNotNull null
            val name = desiredName ?: file.nameWithoutExtension
            SubtitlesDto(file, name, language, charset ?: file.detectCharset())
        }

    private fun String.parseValidateConvertOutput(): File {
        val outputPath = Paths.get(trim()).convertToAbsolute(Config.DEFAULT_BASE_OUT_PATH)
            ?: throw IllegalArgumentException("Relative outputFilePath without DEFAULT_OUTPUT_PATH_BASE env")
        val outputFile = outputPath.toFile()
        if (outputFile.exists()) {
            throw IllegalArgumentException("File '$outputPath' is already exist")
        }
        return outputFile
    }
}
