package me.emyar.ffmpegutils.audionormaliz.common

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.nio.file.Paths

/**
 * @tag *AudioNormalization
 * @path filePath [String] file absolute or relative to HOME_DIR path
 */
fun Route.fileInfo(): Route =
    get("/file-info/{filePath}") {
        val file = call.parameters["filePath"]
            ?.let { Paths.get(it) }
            ?.toFile()
        if (file == null || !file.exists()) {
            call.respond(HttpStatusCode.BadRequest, "File does not exist")
        } else {
            val info = Analyzer.getFileInfo(file)
            call.respond(HttpStatusCode.OK, info)
        }
    }
