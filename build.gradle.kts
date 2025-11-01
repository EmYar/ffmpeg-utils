import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    val kotlinVersion = "2.2.20"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    application
    id("com.gradleup.shadow") version "9.2.2"

    id("org.graalvm.buildtools.native") version "0.11.1"
}

group = "me.emyar"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation(kotlin("test"))
}

application {
    mainClass.set("me.emyar.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

val fatJarName = "${project.name}-${project.version}-all.jar"
tasks.named<ShadowJar>("shadowJar") {
    archiveFileName.set(fatJarName)
}

tasks.register<Exec>("nativeArm64Glibc") {
    dependsOn("shadowJar")
    commandLine(
        "docker", "run", "--rm",
        "--platform", "linux/arm64",
        "-v", project.projectDir.absolutePath + ":/work",
        "-w", "/work",
        "ghcr.io/graalvm/native-image-community:latest",
        "sh", "-lc",
        """
        native-image \
          --no-fallback -O3 \
          --initialize-at-build-time=kotlin,org.jetbrains,kotlinx.serialization \
          -H:Name=ffnorm \
          -H:+ReportExceptionStackTraces \
          -jar build/libs/$fatJarName
        """.trimIndent()
    )
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("${project.name}-${project.version}")
            mainClass.set(application.mainClass.get())
            fallback.set(false)
            buildArgs.addAll(
                listOf(
                    "-O3",
                    "--initialize-at-build-time=kotlin,org.jetbrains,kotlinx.serialization",
                    "-H:+ReportExceptionStackTraces"
                )
            )
            useFatJar.set(true)
        }
    }
}
