import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25

plugins {
    kotlin("jvm") version libs.versions.kotlin
    kotlin("plugin.serialization") version libs.versions.kotlin
    id("io.ktor.plugin") version libs.versions.ktor
    id("com.gradleup.shadow") version libs.versions.shadow
    id("org.graalvm.buildtools.native") version libs.versions.graalvmBuildtoolsNative
}

group = "me.emyar"
version = "2.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
kotlin.compilerOptions.jvmTarget = JVM_25

ktor {
    openApi {
        enabled = true
    }
}

dependencies {
    implementation(enforcedPlatform("org.jetbrains.kotlin:kotlin-bom:${libs.versions.kotlin.get()}"))

    val ktorVersion = libs.versions.ktor.get()
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    implementation("io.ktor:ktor-server-di:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
//    implementation("io.ktor:ktor-server-request-validation:$ktorVersion")
    implementation("io.ktor:ktor-server-routing-openapi:$ktorVersion")
    implementation("io.ktor:ktor-server-swagger:$ktorVersion")

    implementation("ch.qos.logback:logback-classic:${libs.versions.logback.get()}")
    implementation("io.github.oshai:kotlin-logging-jvm:${libs.versions.kotlinLogging.get()}")

    implementation("com.googlecode.juniversalchardet:juniversalchardet:${libs.versions.juniversalchardet.get()}")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-assertions-core:${libs.versions.kotest.get()}")
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
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(25))
                    vendor.set(JvmVendorSpec.GRAAL_VM)
                }
            )

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
    named<ShadowJar>("shadowJar") {
        archiveFileName.set("${project.name}-all.jar")
    }

    test {
        useJUnitPlatform {
            excludeTags("native")
        }
    }

    register<Test>("nativeSmokeTest") {
        description = "Runs slow end-to-end smoke tests against the compiled native image."
        group = "verification"
        dependsOn("nativeCompile")
        testClassesDirs = sourceSets.test.get().output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath
        useJUnitPlatform {
            includeTags("native")
        }
        systemProperty(
            "nativeExecutablePath",
            layout.buildDirectory.file("native/nativeCompile/${project.name}").get().asFile.absolutePath,
        )
        outputs.upToDateWhen { false }
    }

    wrapper {
        gradleVersion = "9.7.1"
    }
}
