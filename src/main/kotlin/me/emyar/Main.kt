package me.emyar

import me.emyar.batch.BatchProcessor

fun main(args: Array<String>) {
    println("Choose mode: f — single file, d — all .mkv in folder")
    when (readln().uppercase()) {
        "F", "FILE" -> TODO("Not yet implemented")
        "D", "DIRECTORY", "DIR" -> {
            val inputDirPath = BatchProcessor.readDirPath()
            val subsPaths = BatchProcessor.readSubsPaths(inputDirPath)
            BatchProcessor.process(inputDirPath, subsPaths)
        }
        else -> throw UnsupportedOperationException("Unsupported value") // TODO цикл чтения
    }
}