@file:OptIn(ExperimentalUuidApi::class)

package me.emyar.ffmpegutils.services

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.models.TmpStorageConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val TEST_DIR_NAME = TmpStorageServiceTest::class.simpleName

class TmpStorageServiceTest {

    private val testDir = Paths.get(System.getProperty("java.io.tmpdir"), TEST_DIR_NAME)
    private val config = TmpStorageConfig(path = testDir, sizeLimitMb = 1)
        .let { Json.encodeToString(listOf(it)) }

    private lateinit var service: TmpStorageService

    @BeforeEach
    fun setUp() {
        Files.createDirectories(testDir)
        service = TmpStorageService(config)
    }

    @AfterEach
    fun tearDown() {
        Files.delete(testDir)
    }

    @Test
    fun getPath(): Unit = runBlocking {
        val fileToStore = File(Uuid.generateV4().toString())

        val tmpPath = service.getPath(fileToStore)
            .shouldNotBeNull()

        tmpPath.toFile().writeText("${TmpStorageServiceTest::class.simpleName}#getPath")

        service.getStateDump().first().usedBytes shouldBe Files.size(tmpPath)
//
//        service.removeFile(tmpPath)
//
//        service.getStateDump().first().usedBytes shouldBe 0
    }

    @Test
    fun removeFile() {

    }

}