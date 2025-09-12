package kz.qwertukg.nBodyApp.nBodyParticleMesh

import javafx.scene.canvas.GraphicsContext
import javafx.scene.paint.Color
import kotlinx.coroutines.*
import kotlin.math.min

// Основной класс симуляции
class ParticleMeshSimulation(val config: SimulationConfig) {
    // Массивы координат и характеристик частиц
    lateinit var particleX: FloatArray
    lateinit var particleY: FloatArray
    lateinit var particleZ: FloatArray
    lateinit var particleVx: FloatArray
    lateinit var particleVy: FloatArray
    lateinit var particleVz: FloatArray
    lateinit var particleM: FloatArray
    lateinit var particleR: FloatArray

    // Размеры сетки
    private val gX = config.gridSizeX
    private val gY = config.gridSizeY
    private val gZ = config.gridSizeZ

    // Предварительные расчёты
    private val gxGyGz = gX * gY * gZ
    private val gYgZ = gY * gZ
    private val cellWidth = config.worldWidth / gX
    private val cellHeight = config.worldHeight / gY
    private val cellDepth = config.worldDepth / gZ

    // Одномерные массивы для масс и потенциалов
    private val massGrid = FloatArray(gxGyGz)
    private val potentialGrid = FloatArray(gxGyGz)
    private val tempPotential = FloatArray(gxGyGz)

    /**
     * Инициализация из списка частиц
     */
    fun initSimulation(particles: List<Particle>) {
        val totalCount = particles.size
        particleX = FloatArray(totalCount) { particles[it].x }
        particleY = FloatArray(totalCount) { particles[it].y }
        particleZ = FloatArray(totalCount) { particles[it].z }
        particleVx = FloatArray(totalCount) { particles[it].vx }
        particleVy = FloatArray(totalCount) { particles[it].vy }
        particleVz = FloatArray(totalCount) { particles[it].vz }
        particleM = FloatArray(totalCount) { particles[it].m }
        particleR = FloatArray(totalCount) { particles[it].r }
    }

    fun addParticleToSimulation(particle: Particle): Int {
        val i = particleX.lastIndex
        with(particle) {
            particleX[i] =  x
            particleY[i] =  y
            particleZ[i] =  z
            particleVx[i] = vx
            particleVy[i] = vy
            particleVz[i] = vz
            particleM[i] =  m
            particleR[i] =  r
        }
        return i
    }

    /**
     * Один шаг симуляции
     */
    suspend fun step() = coroutineScope {
        // 1) Обнуляем massGrid
        massGrid.fill(0f)

        val totalCount = particleX.size
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSize = (totalCount / availableProcessors).coerceAtLeast(1)

        // Предрасчёты для индексации
        val ww = config.worldWidth
        val wh = config.worldHeight
        val wd = config.worldDepth
        val dx = 1f / cellWidth
        val dy = 1f / cellHeight
        val dz = 1f / cellDepth

        // 2) Распределяем массу по сетке (параллельно)
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    val px = particleX[i]
                    val py = particleY[i]
                    val pz = particleZ[i]

                    // Индексация клетки
                    var cx = (px * dx).toInt()
                    var cy = (py * dy).toInt()
                    var cz = (pz * dz).toInt()
                    if (cx < 0) cx = 0 else if (cx >= gX) cx = gX - 1
                    if (cy < 0) cy = 0 else if (cy >= gY) cy = gY - 1
                    if (cz < 0) cz = 0 else if (cz >= gZ) cz = gZ - 1

                    val index = cx * gYgZ + cy * gZ + cz
                    massGrid[index] += particleM[i]
                }
            }
        }.awaitAll()

        // 3) Формируем tempPotential (пока без сглаживания)
        val g = config.g
        for (i in massGrid.indices) {
            tempPotential[i] = -g * massGrid[i]
        }

        // 4) Сглаживание потенциала (можно распараллелить по итерациям)
        repeat(config.potentialSmoothingIterations) {
            parallelSmoothPotential()
        }

        // 5) Обновление скоростей и позиций частиц (параллельно)
        (0 until totalCount step chunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + chunkSize, totalCount)
                for (i in startIndex until endIndex) {
                    var px = particleX[i]
                    var py = particleY[i]
                    var pz = particleZ[i]

                    // Индексы в potentialGrid
                    var cx = (px * dx).toInt()
                    var cy = (py * dy).toInt()
                    var cz = (pz * dz).toInt()
                    if (cx < 1) cx = 1 else if (cx > gX - 2) cx = gX - 2
                    if (cy < 1) cy = 1 else if (cy > gY - 2) cy = gY - 2
                    if (cz < 1) cz = 1 else if (cz > gZ - 2) cz = gZ - 2

                    val base = cx * gYgZ + cy * gZ + cz

                    // Градиент потенциала
                    val dVdx = (potentialGrid[base + gYgZ] - potentialGrid[base - gYgZ]) / (2 * cellWidth)
                    val dVdy = (potentialGrid[base + gZ] - potentialGrid[base - gZ]) / (2 * cellHeight)
                    val dVdz = (potentialGrid[base + 1] - potentialGrid[base - 1]) / (2 * cellDepth)

                    // Обновление скоростей
                    var vx = particleVx[i] - dVdx
                    var vy = particleVy[i] - dVdy
                    var vz = particleVz[i] - dVdz

                    // Обновление позиций
                    px += vx
                    py += vy
                    pz += vz

                    // Зажимаем внутри мира
                    if (config.isDropOutOfBounds) {
                        if (px < 0f) {
                            px = 0f; vx = 0f
                        } else if (px > ww) {
                            px = ww; vx = 0f
                        }
                        if (py < 0f) {
                            py = 0f; vy = 0f
                        } else if (py > wh) {
                            py = wh; vy = 0f
                        }
                        if (pz < 0f) {
                            pz = 0f; vz = 0f
                        } else if (pz > wd) {
                            pz = wd; vz = 0f
                        }
                    }

                    particleX[i] = px
                    particleY[i] = py
                    particleZ[i] = pz
                    particleVx[i] = vx
                    particleVy[i] = vy
                    particleVz[i] = vz
                }
            }
        }.awaitAll()
    }

    /**
     * Параллельное сглаживание потенциала
     * Для каждой итерации мы берём из tempPotential, пишем в potentialGrid,
     * а потом копируем potentialGrid обратно в tempPotential.
     */
    private suspend fun parallelSmoothPotential() = coroutineScope {
        val availableProcessors = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val chunkSizeX = (gX / availableProcessors).coerceAtLeast(1)

        // Разбиваем проход по оси X на несколько корутин
        val jobs = mutableListOf<Deferred<Unit>>()
        for (startX in 1 until gX - 1 step chunkSizeX) {
            val endX = min(startX + chunkSizeX, gX - 1)
            jobs += async(Dispatchers.Default) {
                for (x in startX until endX) {
                    val xBase = x * gYgZ
                    for (y in 1 until gY - 1) {
                        val yBase = y * gZ
                        val xyBase = xBase + yBase
                        for (z in 1 until gZ - 1) {
                            val idx = xyBase + z
                            val p = (
                                    tempPotential[idx] +
                                            tempPotential[idx + gYgZ] +
                                            tempPotential[idx - gYgZ] +
                                            tempPotential[idx + gZ] +
                                            tempPotential[idx - gZ] +
                                            tempPotential[idx + 1] +
                                            tempPotential[idx - 1]
                                    ) / 7f
                            potentialGrid[idx] = p
                        }
                    }
                }
            }
        }
        jobs.awaitAll()

        // Копируем back potentialGrid -> tempPotential
        val copyChunkSize = (gxGyGz / availableProcessors).coerceAtLeast(1)
        (0 until gxGyGz step copyChunkSize).map { startIndex ->
            async(Dispatchers.Default) {
                val endIndex = min(startIndex + copyChunkSize, gxGyGz)
                for (i in startIndex until endIndex) {
                    tempPotential[i] = potentialGrid[i]
                }
            }
        }.awaitAll()
    }

    /**
     * Рисуем частицы в 2D
     */
    fun draw2D(gc: GraphicsContext) {
        gc.fill = Color.BLACK
        gc.fillRect(0.0, 0.0, gc.canvas.width, gc.canvas.height)

        val scale = min(gc.canvas.width / config.worldWidth, gc.canvas.height / config.worldHeight)
        val fov = config.fov
        val halfW = config.worldWidth * 0.5
        val halfH = config.worldHeight * 0.5
        val depth = config.worldDepth

        /*val r = Random.nextInt(200, 255)
        val g = Random.nextInt(200, 255)
        val b = 255
        val color = Color.rgb(r, g, b)*/

        gc.fill = Color.WHITE
        for (i in particleX.indices) {
            val zFactor = 1.0 / (1.0 + (particleZ[i] / depth) * fov)
            val screenX = ((particleX[i] - halfW) * zFactor + halfW) * scale
            val screenY = ((particleY[i] - halfH) * zFactor + halfH) * scale
            val size = particleR[i] * zFactor
            gc.fillOval(screenX, screenY, size, size)
        }
    }

}


