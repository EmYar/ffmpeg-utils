package me.emyar.ffmpegutils.services.audio

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.annotations.*
import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.models.*
import me.emyar.ffmpegutils.services.CoroutineProcessService
import me.emyar.ffmpegutils.utils.guessSubsCodecByFileExtension
import me.emyar.ffmpegutils.utils.toInputArgs
import java.io.File

private val log = KotlinLogging.logger {}

private val loudNormJsonRegex = """(?s)\{.*?"input_i".*?}""".toRegex()
private val jsonRegex = """\{[\s\S]*}""".toRegex()

class AudioNormalizationService(
    @Property("ktor.application.config.ebuR128Config") private val ebuR128Config: String,
    private val coroutineProcessService: CoroutineProcessService,
    @Property("ktor.application.config.audioFormatOutConfig") private val audioOutConfig: String,
) {

    suspend fun process(
        input: InputVideo,
        fixVideoTimestamps: Boolean,
        audioIndex: AudioTrackGlobalIndex,
        subtitles: Collection<SubtitlesDto>,
        outputFile: File,
    ): String {
        log.debug { "Measuring audio in '$input'" }
        val loudNormData = getLoudNormData(input, audioIndex)
        log.debug { "LoudNormData for $input: '$loudNormData'" }
        log.debug { "Counting subtitles in '$input'" }
        val existingSubsCount = countExistingSubsStreams(input)
        log.debug { "Subtitles count: $existingSubsCount" }
        val args = generateLoudNormApplyArgs(
            input,
            fixVideoTimestamps,
            audioIndex,
            loudNormData,
            existingSubsCount,
            subtitles,
            outputFile,
        )
        log.debug { "Normalizing audio from '$input' to '$outputFile'. Command: '${args.joinToString(" ")}'" }
        val (stdOut, exitCode) = coroutineProcessService.runProcess(*args)
        if (exitCode != 0) {
            throw IllegalStateException(
                "Error running audio normalization process. FFmpeg exited with code: $exitCode. Output: $stdOut."
            )
        }
        return stdOut
    }

    private suspend fun getLoudNormData(
        input: InputVideo,
        audioIndex: AudioTrackGlobalIndex,
    ): LoudNormData {
        val (stdOut, exitCode) = coroutineProcessService.runProcess(
            "ffmpeg",
            "-hide_banner", "-nostats", "-v", "info",
            *input.toInputArgs(),
            "-map", audioIndex.toString(),
            "-filter:a", "aformat=channel_layouts=stereo,loudnorm=$ebuR128Config:print_format=json",
            "-f", "null", "-",
        )
        if (exitCode != 0) {
            throw IllegalStateException("ffmpeg exited with code: $exitCode. Output:\n$stdOut")
        }
        return loudNormJsonRegex.findAll(stdOut).last().value
            .let { Json.decodeFromString<LoudNormData>(it) }
    }

    private fun generateLoudNormApplyArgs(
        input: InputVideo,
        fixVideoTimestamps: Boolean,
        audioIndex: AudioTrackGlobalIndex,
        loudNormData: LoudNormData,
        existingSubsCount: Int,
        additionalSubtitles: Collection<SubtitlesDto>,
        outputFile: File,
    ): Array<String> {
        val audioMetadataSource = "${audioIndex.input}:s:${audioIndex.stream}"

        val loudnormFilter = arrayOf(
            ebuR128Config,
            "measured_I=${loudNormData.inputI}",
            "measured_TP=${loudNormData.inputTp}",
            "measured_LRA=${loudNormData.inputLra}",
            "measured_thresh=${loudNormData.inputThresh}",
            "offset=${loudNormData.targetOffset}",
            "linear=true",
            "print_format=summary"
        ).joinToString(prefix = "loudnorm=", separator = ":")

        // ----------------- формируем аргументы -----------------
        val args = mutableListOf<String>().also {
            it.addAll(
                arrayOf(
                    "ffmpeg",
                    "-hide_banner", "-v", "warning", "-stats",
                    "-probesize", "10M",
                    "-y",
                )
            )
            if (fixVideoTimestamps) {
                it.add("-fflags"); it.add("+genpts")
            }
            it.addAll(input.toInputArgs())
        }

        // Добавляем каждый файл как отдельный вход (№1, №2, ...)
        additionalSubtitles.forEach { (file, _, _, charset) ->
            if (charset != null) {
                args += arrayOf("-sub_charenc", charset)
            }
            args += arrayOf("-i", file.absolutePath)
        }

        // копируем видео и существующие сабы
        args += arrayOf("-map", "0:v?", "-c:v", "copy")
        if (existingSubsCount > 0) {
            args += arrayOf("-map", "0:s?", "-c:s", "copy")
        }

        // внешние сабы — добавляем после существующих
        for (index in 1..additionalSubtitles.size) {
            args += arrayOf("-map", "$index:s?")
        }

        // Аудио — только выбранный трек, с применением loudnorm и перекодированием в FLAC стерео
        args += arrayOf(
            "-map", audioIndex.toString(),
            "-c:a", *audioOutConfig.split(' ').toTypedArray(),
            "-filter:a", loudnormFilter,
        )

        args += arrayOf(
            "-map_metadata", "0",
            "-map_metadata:s:a:0", audioMetadataSource,
        )
        for (i in 0 until existingSubsCount) {
            args += arrayOf("-map_metadata:s:s:$i", "0:s:s:$i")
        }
        additionalSubtitles.forEachIndexed { i, (file, name, lang, _) ->
            val outSubIndex = existingSubsCount + i
            args += arrayOf("-metadata:s:s:$outSubIndex", "title=${name}")
            if (lang != null) {
                args += arrayOf("-metadata:s:s:$outSubIndex", "language=$lang")
            }
            args += arrayOf("-c:s:$outSubIndex", file.guessSubsCodecByFileExtension())
        }

        args += outputFile.absolutePath

        return args.toTypedArray()
    }

    // TODO перенести в StreamsInfoService
    private suspend fun countExistingSubsStreams(input: InputVideo): Int {
        val (stdOut, exitCode) = coroutineProcessService.runProcess(
            "ffprobe", "-v", "error",
            *input.toInputArgs(),
            "-select_streams", "s",
            "-show_entries", "stream=index",
            "-of", "json",
        )
        if (exitCode != 0) {
            throw IllegalStateException("ffprobe exited with code: $exitCode. Output:\n$stdOut")
        }
        log.debug { stdOut }
        val matchResult = jsonRegex.find(stdOut)
            ?: throw IllegalStateException("Invalid subtitle stream count result: '$stdOut'")
        return Json.decodeFromString<StreamsInfo>(matchResult.value)
            .streams.size
    }
}
