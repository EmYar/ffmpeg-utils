package me.emyar.ffmpegutils.processing.common

import me.emyar.ffmpegutils.models.LoudNormData
import me.emyar.ffmpegutils.models.SubsInfoDto
import java.io.File

object SecondStep {

    fun applyFilter(
        input: File,
        audioTrack: String, // TODO Data model
        data: LoudNormData,
        subtitles: Collection<SubsInfoDto>,
        output: File,
    ) {
        val audioTrackParts = audioTrack.split(':')
        require(audioTrackParts.size == 2) { "track format must be 'inputIndex:streamIndex'" }
        val metadataSource = "${audioTrackParts[0]}:s:${audioTrackParts[1]}"

        val loudnormFilter = listOf(
            EBU_R128_CONFIG,
            "measured_I=${data.inputI}",
            "measured_TP=${data.inputTp}",
            "measured_LRA=${data.inputLra}",
            "measured_thresh=${data.inputThresh}",
            "offset=${data.targetOffset}",
            "linear=true",
            "print_format=summary"
        ).joinToString(prefix = "loudnorm=", separator = ":")

        val existingSubs = countExistingSubtitleStreams(input)

        // ----------------- формируем аргументы -----------------
        val args = mutableListOf(
            FFMPEG_CMD,
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
                args += listOf("-sub_charenc", charset)
            }
            args += listOf("-i", file.absolutePath)
        }

        // копируем видео и существующие сабы
        args += listOf("-map", "0:v?", "-c:v", "copy")
        args += listOf("-map", "0:s?", "-c:s", "copy")

        // внешние сабы — добавляем после существующих
        for (index in 1..subtitles.size) {
            args += listOf("-map", "$index:s?")
        }

        // Аудио — только выбранный трек, с применением loudnorm и перекодированием в FLAC стерео
        args += listOf(
            "-map", audioTrack,
            "-af", loudnormFilter,
            "-c:a", "flac",
            "-ac", "2",
            "-map_metadata:s:a:0", metadataSource,
        )

        subtitles.forEachIndexed { i, (file, name, lang, _) ->
            val outSubIndex = existingSubs + i
            args += listOf("-metadata:s:s:$outSubIndex", "title=${name}")
            if (lang != null) {
                args += listOf("-metadata:s:s:$outSubIndex", "language=$lang")
            }
            args += listOf("-c:s:$outSubIndex", file.guessSubsCodecByFileExtension())
        }

        // Выходной файл
        args += output.absolutePath
        // ------------------------------------------------------

        ProcessBuilder(args)
            .inheritIO()
            .start()
            .waitFor()
            .let { require(it == 0) { "ffmpeg exit=$it" } }
    }

    private fun countExistingSubtitleStreams(input: File): Int =
        ProcessBuilder(
            listOf(
                FFPROBE_CMD, "-v", "warning",
                "-select_streams", "s",
                "-show_entries", "stream=index",
                "-of", "csv=p=0",
                input.absolutePath,
            )
        ).redirectErrorStream(true)
            .start()
            .also(Process::waitFor)
            .inputStream
            .bufferedReader()
            .lineSequence()
            .count(String::isNotBlank)
}