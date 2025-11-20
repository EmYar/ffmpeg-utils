package me.emyar.ffmpegutils.processing.common

import me.emyar.ffmpegutils.models.LoudNormData
import me.emyar.ffmpegutils.models.SubsInfoDto
import java.io.File

object SecondStep {

    fun applyFilter(
        input: File,
        audioTrackGlobalIndex: String, // TODO Data model
        data: LoudNormData,
        subtitles: Collection<SubsInfoDto>,
        output: File,
    ) {
        val parts = audioTrackGlobalIndex.split(':')
        require(parts.size == 2) { "audioTrackGlobalIndex must be 'inputIndex:streamIndex', e.g. '0:2'" }
        val inputIndex = parts[0]
        val streamIndex = parts[1]
        val audioMetadataSource = "$inputIndex:s:$streamIndex"

        val loudnormFilter = arrayOf(
            EBU_R128_CONFIG,
            "measured_I=${data.inputI}",
            "measured_TP=${data.inputTp}",
            "measured_LRA=${data.inputLra}",
            "measured_thresh=${data.inputThresh}",
            "offset=${data.targetOffset}",
            "linear=true",
            "print_format=summary"
        ).joinToString(prefix = "loudnorm=", separator = ":")

        val existingSubsCount = countExistingSubtitleStreams(input)

        // ----------------- формируем аргументы -----------------
        val args = mutableListOf(
            "ffmpeg",
            "-hide_banner", "-v", "warning", "-stats",
            "-y",
            "-i", input.absolutePath, // вход №0 — исходное видео
        )

        if (subtitles.isNotEmpty()) {
            args += "-fix_sub_duration"
        }
        // Добавляем каждый .srt как отдельный вход (№1, №2, ...)
        subtitles.forEach { (file, _, _, charset) ->
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
        for (index in 1..subtitles.size) {
            args += arrayOf("-map", "$index:s?")
        }

        // Аудио — только выбранный трек, с применением loudnorm и перекодированием в FLAC стерео
        args += arrayOf(
            "-map", audioTrackGlobalIndex,
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
        subtitles.forEachIndexed { i, (file, name, lang, _) ->
            val outSubIndex = existingSubsCount + i
            args += arrayOf("-metadata:s:s:$outSubIndex", "title=${name}")
            if (lang != null) {
                args += arrayOf("-metadata:s:s:$outSubIndex", "language=$lang")
            }
            args += arrayOf("-c:s:$outSubIndex", file.guessSubsCodecByFileExtension())
        }

        args += output.absolutePath
        // ------------------------------------------------------

        ProcessBuilder(args)
            .inheritIO()
            .start()
            .waitFor()
            .let { require(it == 0) { "ffmpeg exit=$it" } }
    }

    private fun countExistingSubtitleStreams(input: File): Int {
        val process = ProcessBuilder(
            "ffprobe", "-v", "error",
            "-select_streams", "s",
            "-show_entries", "stream=index",
            "-of", "csv=p=0",
            input.absolutePath,
        ).redirectErrorStream(true)
            .start()
        return process.inputStream
            .bufferedReader()
            .lineSequence()
            .count(String::isNotBlank)
            .also {
                process.waitFor().let {
                    if (it != 0) {
                        throw IllegalStateException("ffprobe exited with code: $it")
                    }
                }
            }
    }
}
