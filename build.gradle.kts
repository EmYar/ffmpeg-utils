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
    "kotlin",
    "kotlinx",
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
                    "-march=native",
                    "--initialize-at-build-time=$initializeAtBuildTime",
                    "-H:+ReportExceptionStackTraces",
                    "--enable-http",
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

    val dockerRegistryProp = "dockerRegistry"
    val platformTagProp = "platformTag"
    val registryProvider = providers.gradleProperty(dockerRegistryProp)
    val platformTagProvider = providers.gradleProperty(platformTagProp)

    val appVersion = project.version.toString()

    val versionTagProvider = providers.provider {
        val platformTag = platformTagProvider.orNull
            ?: error("Please provide platform tag via -P$platformTagProp=rock4Bplus")
        val registry = registryProvider.orNull
        project.resolveDockerImageTag(registry, platformTag)
    }

    val latestTagProvider = providers.provider {
        val registry = registryProvider.orNull
            ?: error("Please provide docker registry via -P$dockerRegistryProp=host:port")
        val platformTag = platformTagProvider.get()
        "$registry/${project.name}:latest-$platformTag"
    }

    val buildNativeDockerImage = register<Exec>("buildNativeDockerImage") {
        val platformTag = platformTagProvider.get()
        val versionTag = versionTagProvider.get()

        println("Building Docker image: $versionTag")
        println("APP_VERSION = $appVersion")
        println("PLATFORM_TAG = $platformTag")

        commandLine(
            "docker", "build",
            "--build-arg", "APP_VERSION=$appVersion",
            "--build-arg", "PLATFORM_TAG=$platformTag",
            "-t", versionTag,
            ".",
        )
    }

    register<Exec>("pushNativeDockerImage") {
        dependsOn(buildNativeDockerImage)

        val versionTag = versionTagProvider.get()
        val latestTag = latestTagProvider.get()

        println("Tagging Docker image:")
        println("  from: $versionTag")
        println("    to: $latestTag")
        println("Pushing Docker images:")
        println("  $versionTag")
        println("  $latestTag")

        val script = """
            set -e
            docker tag "$versionTag" "$latestTag"
            docker push "$versionTag"
            docker push "$latestTag"
        """.trimIndent()

        commandLine("sh", "-c", script)
    }

    test {
        useJUnitPlatform()
    }

    wrapper {
        distributionType = BIN
        gradleVersion = "9.2.0"
    }
}

fun Project.resolveDockerImageTag(registry: String?, platformTag: String): String {
    val appVersion = version.toString()

    val imageName = if (registry.isNullOrBlank()) {
        name
    } else {
        "$registry/$name"
    }

    return "$imageName:$appVersion-$platformTag"
}