import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.api.JavaVersion.VERSION_24
import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.BIN
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_24

plugins {
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.serialization") version libs.versions.kotlin
    id("io.ktor.plugin") version libs.versions.ktor
    id("com.gradleup.shadow") version libs.versions.shadow
    id("org.graalvm.buildtools.native") version libs.versions.graalvmBuildtoolsNative
}

group = "me.emyar"
version = "1.0"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = VERSION_24
    targetCompatibility = VERSION_24
}
kotlin.compilerOptions.jvmTarget = JVM_24

dependencies {
    val ktorVersion = libs.versions.ktor.get()
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-di:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:${libs.versions.logback.get()}")
    implementation("io.github.oshai:kotlin-logging-jvm:${libs.versions.kotlinLogging.get()}")

    implementation("com.googlecode.juniversalchardet:juniversalchardet:${libs.versions.juniversalchardet.get()}")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("me.emyar.ffmpegutils.MainKtorKt")
    applicationDefaultJvmArgs += listOf(
        "-XX:+UnlockExperimentalVMOptions",
        "-XX:+UseCompactObjectHeaders",
        "-Xmx32m",
    )
}

graalvmNative {
    binaries {
        named("main") {
            val initializeAtBuildTime = arrayOf(
                "kotlin",
                "kotlinx",
                "io.github.oshai.kotlinlogging",
                "ch.qos.logback",
                "org.slf4j",
                "org.xml.sax.helpers",
            ).joinToString(",")

            imageName.set(project.name)
            mainClass.set(application.mainClass.get())
            fallback.set(false)
            useFatJar.set(true)
            buildArgs.addAll(
                "-O3",
                "-march=native",
                "--initialize-at-build-time=$initializeAtBuildTime",
                "-H:+UnlockExperimentalVMOptions",
                "-H:+ReportExceptionStackTraces",
                "-H:+InstallExitHandlers",
                "--enable-http",
                "-R:MaxHeapSize=32m",
                "-Dfile.encoding=UTF-8",
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
        gradleVersion = "9.3.0"
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
