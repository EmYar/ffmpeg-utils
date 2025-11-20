package me.emyar.ffmpegutils.processing.common

import java.nio.file.Path
import java.nio.file.Paths

const val EBU_R128_CONFIG = "I=-23:TP=-2:LRA=7"

const val DEFAULT_PARALLELISM = 1

val BASE_IN_PATH: Path = Paths.get(System.getenv("DEFAULT_INPUT_PATH_BASE")!!)
val BASE_OUT_PATH: Path = Paths.get(System.getenv("DEFAULT_OUTPUT_PATH_BASE")!!)
