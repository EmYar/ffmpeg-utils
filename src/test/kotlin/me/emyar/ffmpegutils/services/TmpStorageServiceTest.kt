@file:OptIn(ExperimentalUuidApi::class, ExperimentalPathApi::class)

package me.emyar.ffmpegutils.services

import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import me.emyar.ffmpegutils.models.TmpStorageConfig
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val TEST_DIR_NAME = TmpStorageServiceTest::class.simpleName

class TmpStorageServiceTest {

    private val testDir = Paths.get(System.getProperty("java.io.tmpdir"), TEST_DIR_NAME)
    private val config = TmpStorageConfig(path = testDir, sizeLimitBytes = 1024 * 1024)
        .let { Json.encodeToString(listOf(it)) }

    private lateinit var service: TmpStorageService

    @BeforeEach
    fun setUp() {
        Files.createDirectories(testDir)
        service = TmpStorageService(config)
    }

    @AfterEach
    fun tearDown() {
        testDir.deleteRecursively()
    }

    @Test
    fun getPathAndRemoveFile(): Unit = runBlocking {
        val fileToStore = testDir.resolve(Uuid.generateV4().toString()).toFile()
            .also {
                it.createNewFile()
                it.writeText("${TmpStorageServiceTest::class.simpleName}#getPath")
            }

        val tmpPath = service.getPath(fileToStore)
            .shouldNotBeNull()

        Files.copy(fileToStore.toPath(), tmpPath)

        service.getStateDump().first().usedBytes shouldBe Files.size(tmpPath)

        service.removeFile(tmpPath)

        service.getStateDump().first().usedBytes shouldBe 0
    }
}