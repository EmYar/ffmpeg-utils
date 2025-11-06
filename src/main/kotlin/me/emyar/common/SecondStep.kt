package me.emyar.common

import java.io.File

object SecondStep {

    fun applyFilter(
        input: File,
        audioTrack: String,
        data: LoudNormData,
        subtitles: Collection<File>,
        output: File,
    ) {
        val audioTrackParts = audioTrack.split(':')
        require(audioTrackParts.size == 2) { "track format must be 'inputIndex:streamIndex'" }
        val metadataSource = "${audioTrackParts[0]}:s:${audioTrackParts[1]}"

        val loudnormFilter = listOf(
            "I=-23",
            "TP=-1",
            "LRA=7",
            "measured_I=${data.inputI}",
            "measured_TP=${data.inputTp}",
            "measured_LRA=${data.inputLra}",
            "measured_thresh=${data.inputThresh}",
            "offset=${data.targetOffset}",
            "linear=true",
            "print_format=summary"
        ).joinToString(prefix = "loudnorm=", separator = ":")

        // ----------------- формируем аргументы -----------------
        val args = mutableListOf(
            "ffmpeg",
            "-hide_banner", "-v", "error", "-stats",
            "-y",
            "-i", input.absolutePath, // вход №0 — исходное видео
        )

        // Добавляем каждый .srt как отдельный вход (№1, №2, ...)
        subtitles.forEach {
            args += listOf("-i", it.absolutePath)
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

        // задаём названия сабов по имени папки
        // индексы выходных субтитров начинаются после встроенных, но FFmpeg сам их пронумерует
        subtitles.forEachIndexed { i, srt ->
            val title = srt.parentFile?.name
            args += listOf("-metadata:s:s:$i", "title=$title")
        }

        // Выходной файл
        args += output.absolutePath
        // ------------------------------------------------------

        val resultCode = ProcessBuilder(args).inheritIO().start().waitFor()
        require(resultCode == 0) { "ffmpeg exit=$resultCode" }
    }
}