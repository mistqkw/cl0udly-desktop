import org.jetbrains.compose.desktop.application.dsl.TargetFormat

// cl0udly для ПК: тот же клиент OpenCode, что на телефоне, но в рамке Claude desktop.
// Облака, маскот и разбор API перенесены из opencode-mobile (Compose переносится как есть).
plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

kotlin { jvmToolchain(21) }

version = "0.0.1"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
}

compose.desktop {
    application {
        mainClass = "app.cl0udly.MainKt"
        jvmArgs += listOf("-Xmx512m", "-Dawt.useSystemAAFontSettings=on", "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
            packageName = "cl0udly"
            packageVersion = "0.0.1"
            modules("java.net.http", "jdk.unsupported")
            linux { iconFile.set(project.file("src/main/resources/icon.png")) }
        }
    }
}
