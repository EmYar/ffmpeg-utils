package me.emyar.ffmpegutils.services.audio

import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.emyar.ffmpegutils.models.AudioTrackGlobalIndex
import me.emyar.ffmpegutils.models.SingleFileAudioNormalizationRequest
import me.emyar.ffmpegutils.models.SingleFileAudioNormalizationRequest.SubsInfo
import me.emyar.ffmpegutils.models.SubtitlesDto
import me.emyar.ffmpegutils.services.FileCharsetDetectorService
import me.emyar.ffmpegutils.services.PathsAbsoluterService
import java.io.File
import java.nio.file.Paths

private const val INVALID_AUDIO_TRACK_GLOBAL_INDEX_MSG =
    "audioTrackGlobalIndex must be 'inputIndex:streamIndex', e.g. '0:2'"

class SingleFileAudioNormalizationService(
    private val limiter: Semaphore,
    private val pathsAbsoluter: PathsAbsoluterService,
    private val charsetDetector: FileCharsetDetectorService,
    private val audioNormalizer: AudioNormalizationService,
) {
    suspend fun process(request: SingleFileAudioNormalizationRequest): String {
        val inputFile = request.inputFilePath.parseValidateConvertInput()
        val fixVideoTimestamps = request.fixVideoTimestamps
        val audioIndex = request.audioTrackGlobalIndex.parseValidateConvertAudioIndex()
        val additionalSubs = request.additionalSubs.parseValidateConvertSubs()
        val outputFile = request.outputFilePath.parseValidateConvertOutput()
        outputFile.parentFile.let {
            if (!it.exists() && !it.mkdirs()) {
                throw IllegalStateException("Failed to create output directory: $it")
            }
        }

        return limiter.withPermit {
            audioNormalizer.process(
                inputFile,
                fixVideoTimestamps,
                audioIndex,
                additionalSubs,
                outputFile,
            )
        }
    }

    private fun String.parseValidateConvertInput(): File =
        pathsAbsoluter.absoluteIn(Paths.get(trim())).toFile()
            .also {
                when {
                    !it.exists() -> throw IllegalArgumentException("File '$it' does not exist")
                    it.isDirectory -> throw IllegalArgumentException("File '$it' is a directory")
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

    private suspend fun List<SubsInfo>.parseValidateConvertSubs(): List<SubtitlesDto> =
        mapNotNull { (pathStr, desiredName, language, charset) ->
            val file = pathsAbsoluter.absoluteIn(Paths.get(pathStr))
                .toFile()
                .takeIf(File::exists)
                ?: return@mapNotNull null
            val name = desiredName ?: file.nameWithoutExtension
            SubtitlesDto(file, name, language, charset ?: charsetDetector.detectCharset(file))
        }

    private fun String.parseValidateConvertOutput(): File =
        pathsAbsoluter.absoluteOut(Paths.get(trim()))
            .toFile()
            .also {
                if (it.exists()) {
                    throw IllegalArgumentException("File '$it' is already exist")
                }
            }
}
