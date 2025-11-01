package me.emyar.batch

import me.emyar.common.AudioTrackAnalyzer
import me.emyar.common.SecondStep
import me.emyar.common.TrackChooser
import java.nio.file.Path

object BatchProcessor {

    fun readDirPath(): Path {
        println("Enter dir path:")
        return Path.of(readln())
    }

    fun process(dir: Path) {
        val files = dir.toFile().listFiles { it.extension == "mkv" }!!
            .sortedBy { it.nameWithoutExtension }

        val audioTrack = TrackChooser.chooseTrack(files.first())

        println("Enter output path:")
        val outputDir = Path.of(readln()).toFile()
        outputDir.mkdirs()

        for (input in files) {
            println("""Analyzing "$input"...""")
            val output = outputDir.resolve(input.name)
            val data = AudioTrackAnalyzer.analyze(files.first(), audioTrack)
            println("""Processing "$input" to "$output"...""")
            SecondStep.applyFilter(input, audioTrack, data, output)
        }
    }
}