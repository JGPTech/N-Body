package kz.qwertukg.nBodyApp.old

import javafx.animation.AnimationTimer
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.layout.StackPane
import javafx.stage.Screen
import javafx.stage.Stage
import kotlinx.coroutines.*
import kotlinx.coroutines.javafx.JavaFx
import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kotlin.math.*
import kotlin.random.Random

/**
 * Главный класс приложения
 */
class SimulationApp : Application() {
    override fun start(primaryStage: Stage) {
        val screenBounds = Screen.getPrimary().bounds

        // Размер холста — например, квадратный, чтобы не было искажений
        val canvas = Canvas(screenBounds.width, screenBounds.height)
        val gc = canvas.graphicsContext2D

        // Simulation
        val config = SimulationConfig()
        val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)
        val simulation = ParticleMeshSimulation(config)
        simulation.initSimulation(particles)

        val root = StackPane(canvas)
        val scene = Scene(root)

        primaryStage.title = "3D kz.qwertukg.nBodyPM.Particle Simulation"
        primaryStage.scene = scene
        primaryStage.isFullScreen = config.isFullScreen
        primaryStage.show()

        // Пример простого обработчика: можно добавить логику нажатия мышкой и т.д.
        scene.controllerCreateBodyGroup(simulation, canvas)

        // Создаём корутину для обновления
        val scope = CoroutineScope(Dispatchers.Default)
        var currentJob: Job? = null

        // Анимационный таймер
        object : AnimationTimer() {
            override fun handle(now: Long) {
                // Если предыдущий шаг ещё не завершён, пропускаем кадр
                if (currentJob?.isActive == true) return

                // Запуск нового шага
                currentJob = scope.launch {
                    simulation.step()
                    withContext(Dispatchers.JavaFx) {
                        simulation.draw2D(gc)
                    }
                }
            }
        }.start()
    }
}

fun generateParticlesDiskXZ(config: SimulationConfig, cx: Float, cy: Float, cz: Float): List<Particle> {
    var sumM = 0.0
    var sumMx = 0.0
    var sumMy = 0.0
    var sumMz = 0.0

    val particles = MutableList(config.count) {
        // Генерация точек в форме диска
        val minR = config.minRadius // Радиус диска
        val maxR = config.maxRadius // Радиус диска
        val r = sqrt(Random.nextDouble(minR * minR, maxR * maxR)).toFloat() // Радиальное расстояние
        val theta = Random.nextDouble(0.0, 2 * PI) // Угол в плоскости диска

        // Координаты частицы в плоскости XZ
        val x = cx + (r * cos(theta)).toFloat()
        val z = cz + (r * sin(theta)).toFloat()
        val y = cy // Все точки лежат в одной плоскости Y

        // Масса и радиус частицы
        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
        val rParticle = sqrt(m) / 100

        // Суммы для центра масс
        sumM += m
        sumMx += m * x
        sumMy += m * y
        sumMz += m * z
        val h = (maxR - minR) / 100.0
        val rndY = Random.nextDouble(y - h, y + h).toFloat() //+ config.worldHeight/3

        Particle(x, rndY, z, 0f, 0f, 0f, m, rParticle)
    }

    val totalMass = sumM.toFloat()
    val centerMassX = (sumMx / sumM).toFloat()
    val centerMassY = (sumMy / sumM).toFloat()
    val centerMassZ = (sumMz / sumM).toFloat()

    // Применяем скорости для орбитального завихрения
    val g = config.g
    val magicConst = config.magicConst

    for (i in particles.indices) {
        val p = particles[i]

        // Направление к центру масс
        val dx = p.x - centerMassX
        val dz = p.z - centerMassZ
        val dist = sqrt(dx * dx + dz * dz)

        if (dist > 0f) {
            // Скорость завихрения для диска
            val v = sqrt(g * totalMass / dist)

            // Нормализованные векторы для орбитального направления
            val ux = -dz / dist
            val uz = dx / dist

            // Рассчитываем скорости
            val vx = ux * v
            val vy = 0f // Частицы движутся только в плоскости XZ
            val vz = uz * v

            particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy, vz * magicConst, p.m, p.r)
        } else {
            particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
        }
    }
    return particles
}

fun generateParticlesDiskXY(config: SimulationConfig, cx: Float, cy: Float, cz: Float): List<Particle> {
    var sumM = 0.0
    var sumMx = 0.0
    var sumMy = 0.0
    var sumMz = 0.0

    val particles = MutableList(config.count) {
        // Генерация точек в форме диска
        val minR = config.minRadius // Радиус диска
        val maxR = config.maxRadius // Радиус диска
        val r = sqrt(Random.nextDouble(minR * minR, maxR * maxR)).toFloat() // Радиальное расстояние
        val theta = Random.nextDouble(0.0, 2 * PI) // Угол в плоскости диска

        // Координаты частицы
        val x = cx + (r * cos(theta)).toFloat()
        val y = cy + (r * sin(theta)).toFloat()
        val z = cz // Все точки лежат в одной плоскости Z

        // Масса и радиус частицы
        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
        val rParticle = sqrt(m) / 100

        // Суммы для центра масс
        sumM += m
        sumMx += m * x
        sumMy += m * y
        sumMz += m * z
        val h = (maxR - minR) / 2
        val rndZ = Random.nextDouble(z - h, z + h).toFloat()
        Particle(x, y, rndZ, 0f, 0f, 0f, m, rParticle)
    }

    val totalMass = sumM.toFloat()
    val centerMassX = (sumMx / sumM).toFloat()
    val centerMassY = (sumMy / sumM).toFloat()
    val centerMassZ = (sumMz / sumM).toFloat()

    // Применяем скорости для орбитального завихрения
    val g = config.g
    val magicConst = config.magicConst

    for (i in particles.indices) {
        val p = particles[i]

        // Направление к центру масс
        val dx = p.x - centerMassX
        val dy = p.y - centerMassY
        val dist = sqrt(dx * dx + dy * dy)

        if (dist > 0f) {
            // Скорость завихрения для диска
            val v = sqrt(g * totalMass / dist)

            // Нормализованные векторы для орбитального направления
            val ux = -dy / dist
            val uy = dx / dist

            // Рассчитываем скорости
            val vx = ux * v
            val vy = uy * v
            val vz = 0f // Частицы движутся только в плоскости XY

            particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy * magicConst, vz, p.m, p.r)
        } else {
            particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
        }
    }
    return particles
}

fun generateParticlesCircle(config: SimulationConfig, cx: Float, cy: Float, cz: Float): List<Particle> {
    var sumM = 0.0
    var sumMx = 0.0
    var sumMy = 0.0
    var sumMz = 0.0

    val particles = MutableList(config.count) {
        val orbitR = Random.nextDouble(config.minRadius, config.maxRadius).toFloat()
        val angle1 = Random.nextDouble(0.0, 2 * PI)
        val angle2 = Random.nextDouble(0.0, PI)

        val x = cx + (orbitR * cos(angle1) * sin(angle2)).toFloat()
        val y = cy + (orbitR * sin(angle1) * sin(angle2)).toFloat()
        val z = cz + (orbitR * cos(angle2)).toFloat()

        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val r = sqrt(m) / 100

        sumM += m
        sumMx += m * x
        sumMy += m * y
        sumMz += m * z

        Particle(x, y, z, vx, vy, vz, m, r)
    }

    val totalMass = sumM.toFloat()
    val centerMassX = (sumMx / sumM).toFloat()
    val centerMassY = (sumMy / sumM).toFloat()
    val centerMassZ = (sumMz / sumM).toFloat()

    // Применяем скорости
    val g = config.g
    val magicConst = config.magicConst

    for (i in particles.indices) {
        val p = particles[i]
        val dx = p.x - centerMassX
        val dy = p.y - centerMassY
        val dz = p.z - centerMassZ
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist > 0f) {
            val v = sqrt(g * totalMass / dist)
            val ux = dx / dist
            val uy = dy / dist

            // Correct vz calculation for orbital velocity
            val vx = -uy * v
            val vy = ux * v
            val vz = -ux * v // Adjusted vz to follow the orbital velocity rules

            particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy * magicConst, vz * magicConst, p.m, p.r)
        } else {
            particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
        }
    }
    return particles
}

fun generateParticlesTorus(config: SimulationConfig, cx: Float, cy: Float, cz: Float): List<Particle> {
    var sumM = 0.0
    var sumMx = 0.0
    var sumMy = 0.0
    var sumMz = 0.0

    val particles = MutableList(config.count) {
        // Генерация точек в форме тора
        val R = config.minRadius // Большой радиус тора
        val r = config.maxRadius - config.minRadius // Малый радиус тора

        // Углы для параметрического описания тора
        val theta = Random.nextDouble(0.0, 2 * PI) // Угол вдоль большого радиуса
        val phi = Random.nextDouble(0.0, 2 * PI)   // Угол на окружности малого радиуса

        // Координаты частицы
        val x = cx + ((R + r * cos(phi)) * cos(theta)).toFloat()
        val y = cy + ((R + r * cos(phi)) * sin(theta)).toFloat()
        val z = cz + (r * sin(phi)).toFloat()

        // Масса и радиус частицы
        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
        val rParticle = sqrt(m) / 100

        // Суммы для центра масс
        sumM += m
        sumMx += m * x
        sumMy += m * y
        sumMz += m * z

        Particle(x, y, z, 0f, 0f, 0f, m, rParticle)
    }

    val totalMass = sumM.toFloat()
    val centerMassX = (sumMx / sumM).toFloat()
    val centerMassY = (sumMy / sumM).toFloat()
    val centerMassZ = (sumMz / sumM).toFloat()

    // Применяем скорости для орбитального завихрения
    val g = config.g
    val magicConst = config.magicConst

    for (i in particles.indices) {
        val p = particles[i]

        // Направление к центру масс малого круга
        val dx = p.x - centerMassX
        val dy = p.y - centerMassY
        val dz = p.z - centerMassZ

        // Рассчитываем расстояние в плоскости малого радиуса
        val distXY = sqrt(dx * dx + dy * dy)
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        if (dist > 0f) {
            // Скорость завихрения для малого радиуса
            val v = sqrt(g * totalMass / distXY)

            // Нормализованные векторы для орбитального направления
            val ux = -dy / distXY
            val uy = dx / distXY

            // Рассчитываем скорости, создавая орбитальное вращение
            val vx = ux * v
            val vy = uy * v
            val vz = 0f // Минимальная скорость вдоль оси Z для завихрения

            particles[i] = Particle(p.x, p.y, p.z, vx * magicConst, vy * magicConst, vz * magicConst, p.m, p.r)
        } else {
            particles[i] = Particle(p.x, p.y, p.z, 0f, 0f, 0f, p.m, p.r)
        }
    }
    return particles
}



fun Scene.controllerCreateBodyGroup(simulation: ParticleMeshSimulation, canvas: Canvas) {
    val config = simulation.config

    // Преобразование экранных координат в мировые координаты при заданном Z
    fun screenToWorldXY(screenX: Double, screenY: Double, currentZ: Float): Pair<Float, Float> {
        val scale = min(canvas.width / config.worldWidth, canvas.height / config.worldHeight)
        val halfW = config.worldWidth * 0.5f
        val halfH = config.worldHeight * 0.5f
        val zFactor = 1.0 / (1.0 + (currentZ / config.worldDepth) * config.fov)
        val invZFactor = 1.0 / zFactor
        val worldX = (((screenX / scale) - halfW) * invZFactor + halfW).toFloat()
        val worldY = (((screenY / scale) - halfH) * invZFactor + halfH).toFloat()
        return Pair(worldX, worldY)
    }

    setOnMousePressed { e ->
        if (e.isPrimaryButtonDown) {
            val (dx, dy) = if (!e.isControlDown) screenToWorldXY(e.sceneX, e.sceneY, config.centerZ)
            else config.worldWidth / 2 to config.worldWidth / 2
            generateBlackHole(dx, dy, simulation)
        }

        if (e.isSecondaryButtonDown) {
            val (dx, dy) = screenToWorldXY(e.sceneX, e.sceneY, config.centerZ)
            val particles = generateParticlesCircle(config, dx, dy, config.centerZ)
            simulation.initSimulation(particles)
            simulation.config.blackHoleIndex = null
        }
    }

    setOnMouseDragged { event ->
        if (event.isPrimaryButtonDown) {
            val (dx, dy) = screenToWorldXY(event.sceneX, event.sceneY, config.centerZ)
            generateBlackHole(dx, dy, simulation)
        }
    }

    setOnMouseReleased { event ->
            dropBlackHole(simulation)
    }
}

suspend fun main() {
    // Simulation
    /*val config = kz.qwertukg.nBodyPM.SimulationConfig()
    val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)
    val simulation = kz.qwertukg.nBodyPM.ParticleMeshSimulation(config)
    simulation.initSimulation(particles)
    val iterations = 10000
    val dt = measureTimeMillis {
        repeat(iterations) {
            simulation.step()
        }
    }
    val tpi = 1000 / (dt / iterations)
    println("Duration time for ${config.count} bodies: $dt millis. Time per iteration: $tpi")*/

    System.setProperty("javafx.animation.fullspeed", "true")
    Application.launch(SimulationApp::class.java)
}


//==========================

fun generateParticlesBox(config: SimulationConfig): List<Particle> {
    val particles = MutableList(config.count) {
        val x = Random.nextFloat() * config.worldWidth
        val y = Random.nextFloat() * config.worldHeight
        val z = Random.nextFloat() * config.worldDepth

        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat() // TODO
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val particleR = sqrt(m) // TODO

        Particle(x, y, z, vx, vy, vz, m, particleR)
    }

    return particles
}

fun generateParticlesLine(config: SimulationConfig): List<Particle> {
    val w = config.worldWidth * 100
    val h = config.worldHeight * 100
    val d = config.worldDepth * 100
    val count = config.count
    val particles = mutableListOf<Particle>()
    repeat(count) {
        val dx = sqrt(w * w) / count
        val dy = sqrt(h * h) / count
        val dz = sqrt(d * d) / count

        val i = it - count / 2
        val x = i * dx
        val y = i * dy
        val z = i * dz

        val iii = -it + count / 2
        val xxx = iii * dx

        val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat() // TODO
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val r = sqrt(m) // TODO


        particles.addAll(listOf(
            Particle(x, y, z, vx, vy, vz, m, r),
            Particle(config.worldWidth/2, y, z, vx, vy, vz, m, r),
            Particle(xxx, y, z, vx, vy, vz, m, r),
        ))


    }


    return particles
}

fun generateBlackHole(x: Float, y: Float, simulation: ParticleMeshSimulation) {
    val m = 1_000_000f
    val currentI = simulation.config.blackHoleIndex
    if (currentI == null) {
        val z = simulation.config.worldDepth / 2
        val vx = 0f
        val vy = 0f
        val vz = 0f
        val r = 0.01f
        val particle = Particle(x, y, z, vx, vy, vz, m, r)
        val i = simulation.addParticleToSimulation(particle)
        simulation.config.blackHoleIndex = i
    } else {
        simulation.particleX[currentI] = x
        simulation.particleY[currentI] = y
        simulation.particleM[currentI] = m
        println(simulation.particleM[currentI])
    }
}

fun dropBlackHole(simulation: ParticleMeshSimulation) {
    val currentI = simulation.config.blackHoleIndex
    if (currentI != null) {
        simulation.particleM[currentI] = 0f

    }
}
