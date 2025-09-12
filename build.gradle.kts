plugins {
    kotlin("jvm") version "1.9.23"
    application
    id("org.openjfx.javafxplugin") version "0.0.14"
    kotlin("plugin.serialization") version "2.1.0"
}

group = "kz.qwertukg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val osName = System.getProperty("os.name").toLowerCase()
val osArch = System.getProperty("os.arch")

val lwjglVersion = "3.3.1"
val lwjglNatives = when {
    osName.contains("mac") -> if (osArch.contains("aarch64")) "natives-macos-arm64" else "natives-macos"
    osName.contains("win") -> "natives-windows"
    else -> "natives-linux"
}

val javafxVersion = "21.0.2"
val javafxPlatform = when {
    osName.contains("mac") && osArch.contains("aarch64") -> "mac-aarch64"
    osName.contains("mac") -> "mac"
    osName.contains("win") -> "win"
    else -> "linux"
}

dependencies {
    // https://mvnrepository.com/artifact/com.github.wendykierp/JTransforms
    implementation("com.github.wendykierp:JTransforms:3.1")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // https://mvnrepository.com/artifact/org.joml/joml
    implementation("org.joml:joml:1.10.8")

    // LWJGL библиотеки
    implementation("org.lwjgl:lwjgl:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-glfw:$lwjglVersion")
    implementation("org.lwjgl:lwjgl-opengl:$lwjglVersion")

    runtimeOnly("org.lwjgl:lwjgl:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-glfw:$lwjglVersion:$lwjglNatives")
    runtimeOnly("org.lwjgl:lwjgl-opengl:$lwjglVersion:$lwjglNatives")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-javafx:1.7.3")

    // JavaFX зависимости
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")

    // Тестирование
    testImplementation(kotlin("test"))
    implementation(kotlin("reflect"))
}

application {
    // Укажите основной класс
    mainClass.set("kz.qwertukg.nBody.AppKt")
}

tasks.withType<JavaExec> {
    // Если понадобятся модули JavaFX, добавьте:
    // jvmArgs = listOf("--add-modules", "javafx.controls,javafx.graphics")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(18)
}