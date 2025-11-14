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

tasks.processResources {
    dependsOn("buildOpenApi")
}

tasks.test {
    useJUnitPlatform()
}

val fatJarName = "${project.name}-${project.version}-all.jar"
tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set(fatJarName)
}

val initializeAtBuildTime = arrayOf(
    "me.emyar.ffmpegutils",
    "io.ktor",
    "kotlin",
    "kotlinx",
    "org.slf4j",
    "ch.qos.logback",
).joinToString(",")

val defaultHeapSize = "32m"

tasks.register<Exec>("nativeArm64Glibc") {
    dependsOn("shadowJar")
    commandLine(
        "docker", "run", "--rm",
        "--platform", "linux/arm64",
        "-v", project.projectDir.absolutePath + ":/work",
        "-w", "/work",
        "ghcr.io/graalvm/native-image-community:latest",

        "--no-fallback",
        "-O3",
        "--initialize-at-build-time=$initializeAtBuildTime",
        "-H:+UnlockExperimentalVMOptions",
        "-H:Name=${project.name}-${project.version}",
        "-H:+ReportExceptionStackTraces",
        "-R:MaxHeapSize=$defaultHeapSize",
        "-jar", "build/libs/$fatJarName",
    )
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("${project.name}-${project.version}")
            mainClass.set(application.mainClass.get())
            fallback.set(false)
            useFatJar.set(true)
            buildArgs.addAll(
                listOf(
                    "-O3",
                    "--initialize-at-build-time=$initializeAtBuildTime",
                    "-H:+ReportExceptionStackTraces",
                    "-R:MaxHeapSize=$defaultHeapSize",
                )
            )
        }
    }
}

tasks.wrapper {
    distributionType = BIN
    gradleVersion = "9.2.0"
}