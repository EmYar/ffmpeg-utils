package me.emyar.ffmpegutils.models

sealed interface InputVideo {
    val file: java.io.File

    companion object {
        fun fromFile(file: java.io.File): InputVideo? =
            Dvd.fromFile(file)
                ?: File.fromFile(file)
    }

    data class File(override val file: java.io.File) : InputVideo {
        companion object {
            fun fromFile(file: java.io.File): File? =
                file.takeIf { KnownExtensions.valuesMap.containsKey(it.extension.uppercase()) }
                    ?.let(::File)
        }

        enum class KnownExtensions {
            MKV,
            AVI,
            ;

            companion object {
                val valuesMap = entries.associateBy { it.name }
            }
        }
    }

    data class Dvd(override val file: java.io.File) : InputVideo {
        companion object {
            fun fromFile(file: java.io.File): Dvd? =
                file.takeIf { it.nameWithoutExtension.uppercase() == "VIDEO_TS" }
                    ?.let(::Dvd)
        }
    }
}
