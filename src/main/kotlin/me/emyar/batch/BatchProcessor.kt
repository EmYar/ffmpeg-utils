package me.emyar.batch

import me.emyar.common.AudioTrackAnalyzer
import me.emyar.common.SecondStep
import me.emyar.common.TrackChooser
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object BatchProcessor {

    fun readDirPath(): Path {
        println("Enter dir path:")
        return Path.of(readln())
    }

    fun readSubsPaths(inputDirPath: Path): Collection<Path> {
        println("Enter subs paths. ',' is a delimeter:")
        return readln().splitToSequence(',')
            .map { Paths.get(it.trim()) }
            .map { if (it.isAbsolute) it else inputDirPath.resolve(it) }
            .toList()
    }

    fun process(dir: Path, subs: Collection<Path>) {
        val files = dir.toFile()
            .listFiles { it.extension == "mkv" }!!
            .sortedBy { it.nameWithoutExtension }

        val subsByNameWithoutExt = subs.asSequence()
            .mapNotNull { it.toFile().takeIf(File::exists) }
            .flatMap { it.listFiles()?.asList() ?: listOf() }
            .groupBy(File::nameWithoutExtension)

        val audioTrack = TrackChooser.chooseTrack(files.first())

        println("Enter output path:")
        val outputDir = Path.of(readln()).toFile()
        outputDir.mkdirs()

        for (input in files) {
            println("""Analyzing "$input"...""")
            val output = outputDir.resolve(input.name)
            val data = AudioTrackAnalyzer.analyze(files.first(), audioTrack)
            val subs = subsByNameWithoutExt[input.nameWithoutExtension] ?: listOf()
            println("""Processing "$input" to "$output"...""")
            SecondStep.applyFilter(input, audioTrack, data, subs, output)
        }
    }
}