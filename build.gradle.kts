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
    // built-in terminal: JediTerm + a real pty
    implementation("org.jetbrains.jediterm:jediterm-ui:3.76")
    implementation("org.jetbrains.jediterm:jediterm-core:3.76")
    implementation("org.jetbrains.pty4j:pty4j:0.13.13")
    // built-in browser for a quick look at what the agent made: JavaFX WebView
    listOf("base", "graphics", "controls", "media", "web", "swing").forEach { implementation("org.openjfx:javafx-$it:21.0.13:linux") }
    // QR code for Remote control (the phone scans it)
    implementation("com.google.zxing:core:3.5.4")
    // PDFs open in the app's file viewer, page by page, on every system
    implementation("org.apache.pdfbox:pdfbox:3.0.5")
    // the native window id of a canvas, for mpv to draw video into
    implementation("net.java.dev.jna:jna:5.14.0")
    // the tray icon: StatusNotifierItem over D-Bus, what Quickshell, Waybar and KDE show
    implementation("com.github.hypfvieh:dbus-java-core:5.1.1")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.1.1")
}

compose.desktop {
    application {
        mainClass = "app.cl0udly.MainKt"
        jvmArgs += listOf("-Xmx512m", "-Dawt.useSystemAAFontSettings=on", "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED")
        nativeDistributions {
            targetFormats(TargetFormat.AppImage, TargetFormat.Deb)
            packageName = "cl0udly"
            packageVersion = "0.0.1"
            modules("java.net.http", "jdk.httpserver", "jdk.unsupported", "jdk.jsobject", "java.scripting", "jdk.xml.dom", "jdk.unsupported.desktop", "jdk.security.auth")
            linux { iconFile.set(project.file("src/main/resources/icon.png")) }
        }
    }
}
