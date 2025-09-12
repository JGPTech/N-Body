package kz.qwertukg.nBodyApp.nBodyParticleMesh

import kotlin.math.E
import kotlin.math.PI

// Конфигурация симуляции
data class SimulationConfig(
    val count: Int = 1000000,
    val screenW: Int = 3024,
    val screenH: Int = 1964,
    val gridSize: Int = 64,
    val gridSizeX: Int = gridSize * screenW/screenH,
    val gridSizeY: Int = gridSize,
    val gridSizeZ: Int = gridSize,
    val worldSize: Float = 1000_000f,
    val worldWidth: Float = worldSize * screenW/screenH,
    val worldHeight: Float = worldSize,
    val worldDepth: Float = worldSize,
    val potentialSmoothingIterations: Int = 60, //60 ok //80 OK //100 Endless dance OK! //110 Alien eyes OK! //120 is MAX!!! //140 SUPERMAX!!! // 300 is too long wait
    var g: Float = 10f,
    var centerX: Float = worldWidth * 0.5f,
    var centerY: Float = worldHeight * 0.5f,
    var centerZ: Float = worldDepth * 0.5f,
    var minRadius: Double = worldSize * 0.2, // 0.2 ok
    var maxRadius: Double = worldSize * 0.25, // 0.25 ok
    val massFrom: Float =   1.0f,
    val massUntil: Float =  25_000.01f,
    val isDropOutOfBounds: Boolean = false,
    val fov: Float = 1f,
    val magicConst: Float = (PI).toFloat(), // 3f is ok
    val isFullScreen: Boolean = true,
    var blackHoleIndex: Int? = null,
    val isFading: Boolean = false,
)