import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN

plugins {
    val kotlinVersion = "2.2.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("io.ktor.plugin") version "3.3.2"

    id("com.gradleup.shadow") version "9.2.2"

    id("org.graalvm.buildtools.native") version "0.11.3"
}

group = "me.emyar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

val ktorVersion = "3.3.2"
dependencies {
    implementation("io.ktor:ktor-server-cio:$ktorVersion")

    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:${ktorVersion}")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")

    implementation("io.ktor:ktor-server-openapi:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:1.5.21")

    implementation("com.googlecode.juniversalchardet:juniversalchardet:1.0.3")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("me.emyar.ffmpegutils.MainKtorKt")
}

val initializeAtBuildTime = arrayOf(
    "me.emyar.ffmpegutils",
    "io.ktor",
    "kotlin",
    "kotlinx",
    "org.slf4j",
    "ch.qos.logback",
).joinToString(",")

graalvmNative {
    binaries {
        named("main") {
            imageName.set(project.name)
            mainClass.set(application.mainClass.get())
            fallback.set(false)
            useFatJar.set(true)
            buildArgs.addAll(
                listOf(
                    "-O3",
                    "--initialize-at-build-time=$initializeAtBuildTime",
                    "-H:+ReportExceptionStackTraces",
                    "-R:MaxHeapSize=32m",
                )
            )
        }
    }
}

tasks {
    processResources {
        dependsOn("buildOpenApi")
    }

    named<ShadowJar>("shadowJar") {
        archiveFileName.set("${project.name}-all.jar")
    }

    val buildNativeDockerImage = register<Exec>("buildNativeDockerImage") {
        doFirst {
            val platformTag = project.findProperty(platformTag) as String?
                ?: error("Please provide docker registry: -P$dockerRegistryEnv=host:port")
            val appVersion = project.version.toString()
            val imageTag = project.resolveDockerImageTag(requireRegistry = false)

            println("Building Docker image: $imageTag")
            println("APP_VERSION = $appVersion")
            println("PLATFORM_TAG = $platformTag")

            commandLine(
                "docker", "build",
                "--build-arg", "APP_VERSION=$appVersion",
                "--build-arg", "PLATFORM_TAG=$platformTag",
                "-t", imageTag,
                ".",
            )
        }
    }

    register<Exec>("pushNativeDockerImage") {
        dependsOn(buildNativeDockerImage)

        doFirst {
            val registry = project.findProperty(dockerRegistryEnv) as String?
                ?: error("Please provide docker registry: -P$dockerRegistryEnv=host:port")

            val imageTag = "$registry/${project.name}:latest"

            commandLine("docker", "push", imageTag)
        }
    }

    test {
        useJUnitPlatform()
    }

    wrapper {
        distributionType = BIN
        gradleVersion = "9.2.0"
    }
}

val dockerRegistryEnv = "dockerRegistry"
val platformTag = "platformTag"

fun Project.resolveDockerImageTag(requireRegistry: Boolean): String {
    val registry = findProperty(dockerRegistryEnv) as String?
    val platformTag = findProperty(platformTag) as String
    val appVersion = version.toString()

    if (requireRegistry && registry.isNullOrBlank()) {
        error("Please provide docker registry via -P$dockerRegistryEnv=host:port")
    }

    val imageName = if (registry.isNullOrBlank()) {
        project.name
    } else {
        "$registry/${project.name}"
    }

    return "$imageName:$appVersion-$platformTag"
}