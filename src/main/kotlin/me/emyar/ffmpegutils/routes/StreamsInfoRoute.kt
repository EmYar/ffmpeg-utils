package me.emyar.ffmpegutils.routes

import io.ktor.http.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import me.emyar.ffmpegutils.services.StreamsInfoService
import java.nio.file.Paths

/**
 * @tag *AudioNormalization
 * @path filePath [String] file absolute or relative to HOME_DIR path
 */
fun Route.streamsInfo(): Route =
    get("/streams-info/{filePath}") {
        val path = call.parameters["filePath"]?.let(Paths::get)
            ?: throw BadRequestException("filePath not found")
        val info = application.dependencies.resolve<StreamsInfoService>()
            .getFileStreamsInfo(path)
        call.respond(HttpStatusCode.OK, info)
    }
