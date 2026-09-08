import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.foundation)
    implementation(compose.ui)
    implementation(compose.components.resources)
    implementation(compose.materialIconsExtended)

    if (providers.gradleProperty("useMavenLocalInnerTubeX").isPresent) {
        implementation("com.github.MetrolistGroup:innertubex:${libs.versions.innertubex.get()}")
    } else {
        implementation(libs.innertubex)
    }

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)

    implementation(libs.coil)
    implementation(libs.coil.network.okhttp)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.1")

    // OpenJFX Media for cross-platform audio streaming
    val osName = System.getProperty("os.name").lowercase()
    val jfxClassifier = when {
        osName.contains("win") -> "win"
        osName.contains("mac") -> if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
        else -> "linux"
    }
    implementation("org.openjfx:javafx-base:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-graphics:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-controls:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-media:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-web:21.0.2:$jfxClassifier")
    implementation("org.openjfx:javafx-swing:21.0.2:$jfxClassifier")
}

compose.desktop {
    application {
        mainClass = "com.metrolist.music.desktop.MainKt"
        buildTypes.release.proguard {
            isEnabled = false
        }
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "AuraMusic"
            packageVersion = "13.8.4"
            description = "AuraMusic for Desktop"
            copyright = "© 2026 AuraMusic"
            vendor = "AuraMusic"
            appResourcesRootDir.set(project.layout.projectDirectory.dir("src/main/resources"))
            modules(
                "java.base",
                "java.desktop",
                "java.logging",
                "java.net.http",
                "java.scripting",
                "java.sql",
                "java.xml",
                "java.management",
                "java.naming",
                "jdk.unsupported",
                "jdk.unsupported.desktop"
            )
            windows {
                menuGroup = "AuraMusic"
                upgradeUuid = "187e1a3b-24b2-4d05-b049-5de156c7ac56"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = false
            }
        }
    }
}

