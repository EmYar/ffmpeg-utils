package me.emyar.ffmpegutils.audio

import io.ktor.server.plugins.di.annotations.*
import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.audionormaliz.common.EBU_R128_CONFIG
import me.emyar.ffmpegutils.audionormaliz.common.guessSubsCodecByFileExtension
import me.emyar.ffmpegutils.commands.runProcess
import me.emyar.ffmpegutils.models.AudioTrackGlobalIndex
import me.emyar.ffmpegutils.models.LoudNormData
import me.emyar.ffmpegutils.models.SubtitlesDto
import java.io.File

private val loudnormJsonRegex = """(?s)\{.*?"input_i".*?}""".toRegex()

class AudioNormalizationService(
    @Property("ktor.application.config.ebuR128Config") private val ebuR128Config: String,
) {

    suspend fun process(
        inputFile: File,
        audioIndex: AudioTrackGlobalIndex,
        subtitles: Collection<SubtitlesDto>,
        outputFile: File,
    ): String {
        val loudNormData = getLoudNormData(inputFile, audioIndex)
        val existingSubsCount = countExistingSubsStreams(inputFile)
        val args = generateLoudNormApplyArgs(
            inputFile,
            audioIndex,
            loudNormData,
            existingSubsCount,
            subtitles,
            outputFile,
        )
        val (stdOut, exitCode) = runProcess(*args)
        if (exitCode != 0) {
            throw IllegalStateException(
                "Error running audio normalization process. FFmpeg exited with code: $exitCode. Output: $stdOut"
            )
        }
        return stdOut
    }

    private suspend fun getLoudNormData(
        file: File,
        audioIndex: AudioTrackGlobalIndex,
    ): LoudNormData {
        val (stdOut, exitCode) = runProcess(
            "ffmpeg",
            "-hide_banner", "-nostats", "-v", "info",
            "-i", file.absolutePath,
            "-map", audioIndex.toString(),
            "-filter:a", "aformat=channel_layouts=stereo,loudnorm=$ebuR128Config:print_format=json",
            "-f", "null", "-",
        )
        if (exitCode != 0) {
            throw IllegalStateException("ffmpeg exited with code: $exitCode. Output:\n$stdOut")
        }
        return loudnormJsonRegex.findAll(stdOut).last().value
            .let { Json.decodeFromString<LoudNormData>(it) }
    }

    private fun generateLoudNormApplyArgs(
        inputFile: File,
        audioIndex: AudioTrackGlobalIndex,
        loudNormData: LoudNormData,
        existingSubsCount: Int,
        additionalSubtitles: Collection<SubtitlesDto>,
        outputFile: File,
    ): Array<String> {
        val audioMetadataSource = "${audioIndex.input}:s:${audioIndex.stream}"

        val loudnormFilter = arrayOf(
            EBU_R128_CONFIG,
            "measured_I=${loudNormData.inputI}",
            "measured_TP=${loudNormData.inputTp}",
            "measured_LRA=${loudNormData.inputLra}",
            "measured_thresh=${loudNormData.inputThresh}",
            "offset=${loudNormData.targetOffset}",
            "linear=true",
            "print_format=summary"
        ).joinToString(prefix = "loudnorm=", separator = ":")

        // ----------------- формируем аргументы -----------------
        val args = mutableListOf(
            "ffmpeg",
            "-hide_banner", "-v", "warning", "-stats",
            "-probesize", "10M",
            "-y",
            "-i", inputFile.absolutePath, // вход №0 — исходное видео
        )

        if (additionalSubtitles.isNotEmpty()) {
            args += "-fix_sub_duration"
        }
        // Добавляем каждый .srt как отдельный вход (№1, №2, ...)
        additionalSubtitles.forEach { (file, _, _, charset) ->
            if (charset != null) {
                args += arrayOf("-sub_charenc", charset)
            }
            args += arrayOf("-i", file.absolutePath)
        }

        // копируем видео и существующие сабы
        args += arrayOf(
            "-map", "0:v?", "-c:v", "copy",
            "-map", "0:s?", "-c:s", "copy",
        )

        // внешние сабы — добавляем после существующих
        for (index in 1..additionalSubtitles.size) {
            args += arrayOf("-map", "$index:s?")
        }

        // Аудио — только выбранный трек, с применением loudnorm и перекодированием в FLAC стерео
        args += arrayOf(
            "-map", audioIndex.toString(),
            "-ac", "2",
            "-ar", "48000",
            "-sample_fmt", "s16",
            "-af", loudnormFilter,
            "-c:a", "flac",
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

    private suspend fun countExistingSubsStreams(file: File): Int {
        val (stdOut, exitCode) = runProcess(
            "ffprobe", "-v", "error",
            "-select_streams", "s",
            "-show_entries", "stream=index",
            "-of", "csv=p=0",
            file.absolutePath,
        )
        if (exitCode != 0) {
            throw IllegalStateException("ffprobe exited with code: $exitCode. Output:\n$stdOut")
        }
        return stdOut.lineSequence().count(String::isNotBlank)
    }
}
