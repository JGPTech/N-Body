plugins {
    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "kz.qwertukg"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val lwjglVersion = "3.3.3"
val ktorVersion = "2.3.5"

dependencies {
    // Original dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    
    // LWJGL for GPU
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl:lwjgl")
    implementation("org.lwjgl:lwjgl-glfw")
    implementation("org.lwjgl:lwjgl-opengl")
    runtimeOnly("org.lwjgl:lwjgl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-windows")
    runtimeOnly("org.lwjgl:lwjgl::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-linux")
    runtimeOnly("org.lwjgl:lwjgl::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-glfw::natives-macos")
    runtimeOnly("org.lwjgl:lwjgl-opengl::natives-macos")
    
    // ADD THESE for the old App.kt to work:
    implementation("org.joml:joml:1.10.5")
    implementation("com.github.wendykierp:JTransforms:3.1")
    
    // Dependencies for web server
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-websockets:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")
    implementation("io.ktor:ktor-server-html-builder:$ktorVersion")
    implementation("io.ktor:ktor-server-host-common:$ktorVersion")
    
    // Logging
    implementation("ch.qos.logback:logback-classic:1.4.11")
    
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("server.NBodySimulationServerKt")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveClassifier.set("all")
    manifest {
        attributes["Main-Class"] = "server.NBodySimulationServerKt"
    }
}

// Additional tasks for running different components
tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run the WebSocket simulation server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("server.NBodySimulationServerKt")
}

tasks.register<JavaExec>("runGPU") {
    group = "application"
    description = "Run the GPU N-body renderer"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("gpu.GpuNBodySSBORender")
}

tasks.register<JavaExec>("runParticleMesh") {
    group = "application"
    description = "Run the particle-mesh LWJGL client"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("kz.qwertukg.nBody.AppKt")
}

tasks.register<JavaExec>("runSwing") {
    group = "application"
    description = "Run the Swing-based Barnes-Hut visualization"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("MainKt")
}

// Exclude unused files from compilation when on Railway
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    if (System.getenv("RAILWAY_ENVIRONMENT") != null) {
        exclude("**/kz/qwertukg/nBody/**")  // Exclude entire nBody folder
    }
}
