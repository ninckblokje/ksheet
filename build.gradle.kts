import com.palantir.gradle.gitversion.VersionDetails

fun getVersionDetails(): VersionDetails = (extra["versionDetails"] as groovy.lang.Closure<*>)() as VersionDetails

plugins {
    kotlin("multiplatform") version "1.4.0"
    id("com.codingfeline.buildkonfig") version "0.7.0"
    id("com.palantir.git-version") version "0.12.3"
}
group = "ninckblokje.ksheet"
version = "1.0"

repositories {
    mavenCentral()
    maven("https://kotlin.bintray.com/kotlinx")
}

kotlin {
    val hostOs = System.getProperty("os.name")
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosX64("native")
        hostOs == "Linux" -> linuxX64("native")
        isMingwX64 -> mingwX64("native")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "ninckblokje.ksheet.main"
            }
        }
    }

    sourceSets {
        val nativeMain by getting
        val nativeTest by getting

        commonMain {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3")
            }
        }
    }
}

buildkonfig {
    packageName = "ninckblokje.ksheet"
    defaultConfigs {
        buildConfigNullableField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "ksheetRevision", getVersionDetails().gitHash
        )
        buildConfigField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING, "ksheetVersion", rootProject.version.toString()
        )
    }
}