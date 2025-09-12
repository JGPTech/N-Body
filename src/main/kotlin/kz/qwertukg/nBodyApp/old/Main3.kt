package kz.qwertukg.nBodyApp.old

import kotlinx.coroutines.*
import kz.qwertukg.nBodyApp.nBodyParticleMesh.generator.Generator
import kz.qwertukg.nBodyApp.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL11.*
import java.lang.Math.toRadians
import kotlin.math.*

fun init(config: SimulationConfig, type: String = "disc"): ParticleMeshSimulation {
    val particles = Generator(config).generate(type)
    return ParticleMeshSimulation(config).apply {
        initSimulation(particles)
    }
}

var prevX = 0.0
var prevY = 0.0
var mouseDx: Float = 0f
var mouseDy: Float = 0f
var isDrag = false
var isAlt = false
var isCtrl = false
var isShift = false
var zoom = 0.4f
val zoomStep = 0.1f
val zoomFar = 1000f
val zoomStepScale = 10f
val zoomStepScale10x = 100f
val zoomStepScale01x = 0.1f

// фруструм камеры
val fovY = 50.0
var aspect = 1.0
val zNear = 0.01
val zFar = 1000.0
suspend fun main() = runBlocking {
    // Simulation init
    var config = SimulationConfig()
    var simulation = init(config)

    // фруструм камеры
    aspect = config.screenW / config.screenH.toDouble()

    // Инициализация GLFW
    if (!glfwInit()) {
        throw IllegalStateException("Не удалось инициализировать GLFW")
    }
    // Создаем окно
    val window = glfwCreateWindow(config.screenW, config.screenH, "LWJGL3 Cube",
        if (config.isFullScreen) glfwGetPrimaryMonitor() else 0, 0)
    if (window == 0L) {
        throw RuntimeException("Не удалось создать окно GLFW")
    }


    // Устанавливаем контекст текущего окна
    glfwMakeContextCurrent(window)
    glfwShowWindow(window)
    createCapabilities() // Инициализация OpenGL для текущего окна

    // Настройки мультисемплинга
//    glfwWindowHint(GLFW_SAMPLES, 8) // 4-кратное сглаживание
//    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3) // Версия OpenGL 3.x
//    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
//    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
    // Включаем мультисемплинг
//    glEnable(GL_MULTISAMPLE)

    // Настройки OpenGL
    glEnable(GL_DEPTH_TEST) // Включить тест глубины
    glDepthFunc(GL_LESS)    // Отрисовывать пиксели, которые ближе к камере
    glEnable(GL_CULL_FACE) // Включить отсечение невидимых граней
    glCullFace(GL_BACK)    // Отбрасывать грани, которые "смотрят" назад
    glFrontFace(GL_CCW)    // Устанавливаем направление обхода вершин (CCW — против часовой стрелки)

    // Устанавливаем обработчик клавиш
    glfwSetScrollCallback(window) { _, xoffset, yoffset ->
        val scaledStep = if (isCtrl) zoomStep * zoomStepScale
        else if (isShift) zoomStep * zoomStepScale10x
        else if (isAlt) zoomStep * zoomStepScale01x
        else zoomStep
        // Обработка изменения прокрутки
        zoom -= yoffset.toFloat() * scaledStep
        zoom = zoom.coerceIn(0.01f, zoomFar) // Ограничиваем zoom между 0.1 и 2.0
    }

    // Устанавливаем обработчик событий мыши (нажатие и отпускание кнопок)
    glfwSetMouseButtonCallback(window) { _, button, action, _ ->
        if (button == GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW_PRESS) {
                isDrag = true
                val cursorPos = glfwGetCursorPos(window)
                prevX = cursorPos.first
                prevY = cursorPos.second
            }
            if (action == GLFW_RELEASE) {
                isDrag = false
            }
        }
    }

    // Устанавливаем обработчик перемещения мыши
    glfwSetCursorPosCallback(window) { _, xpos, ypos ->
        if (isDrag) {
            // Вычисляем разницу между текущими и предыдущими координатами
            mouseDx += (xpos - prevX).toFloat() / 10
            mouseDy += (ypos - prevY).toFloat() / 10

            // Сохраняем текущие координаты как предыдущие
            prevX = xpos
            prevY = ypos
        }
    }

    glfwSetKeyCallback(window) { _, key, scancode, action, mods ->
        if (action == GLFW_PRESS) when (key) {
            GLFW_KEY_LEFT_ALT -> isAlt = true
            GLFW_KEY_LEFT_CONTROL -> isCtrl = true
            GLFW_KEY_LEFT_SHIFT -> isShift = true
            GLFW_KEY_ESCAPE -> glfwSetWindowShouldClose(window, true) // Закрытие окна
            GLFW_KEY_SPACE -> {
                val particles = generateParticlesCircle(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_Z -> {
                val particles = generateParticlesBox(config)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_X -> {
                val particles = generateParticlesTorus(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_C -> {
                val particles = generateParticlesDiskXY(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
            GLFW_KEY_V -> {
                val particles = generateParticlesDiskXZ(config, config.centerX, config.centerY, config.centerZ)
                simulation = ParticleMeshSimulation(config)
                simulation.initSimulation(particles)
            }
        }
        if (action == GLFW_RELEASE) when (key) {
            GLFW_KEY_LEFT_ALT -> isAlt = false
            GLFW_KEY_LEFT_CONTROL -> isCtrl = false
            GLFW_KEY_LEFT_SHIFT -> isShift = false
        }
    }

    // Основной цикл программы
    while (!glfwWindowShouldClose(window)) { // Проверяем, не запросил ли пользователь закрытие окна
        // Очистка экрана и буфера глубины
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT) // Очищаем цвет и глубину

        // Устанавливаем проекционную матрицу
        glMatrixMode(GL_PROJECTION) // Переход в режим матрицы проекции
        glLoadIdentity() // Сбрасываем матрицу
        gluPerspective(fovY, aspect, zNear, zFar) // Устанавливаем перспективную проекцию

        // Устанавливаем модельно-видовую матрицу
        glMatrixMode(GL_MODELVIEW) // Переход в режим модельно-видовой матрицы
        glLoadIdentity() // Сбрасываем матрицу
        glTranslatef(0.0f, 0.0f, -zoom) // Сдвигаем "камеру" на 5 единиц назад

        glRotatef(mouseDy, 1.0f, 0.0f, 0.0f)
        glRotatef(mouseDx, 0.0f, 1.0f, 0.0f)

        simulation.step()

        glBegin(GL_POINTS)
        drawTrianglesAsync(simulation, 1000000f, Triple(0f, 0f, zoom))

        // Добавить оставшиеся грани здесь (левая, правая, верхняя, нижняя)
        glEnd() // Завершаем рисование куба

        // Меняем буферы местами (для отображения следующего кадра)
        glfwSwapBuffers(window)

        // очистить буфер глубины
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Обрабатываем события (например, нажатия клавиш или движение мыши)
        glfwPollEvents()
    }

    // Закрываем и уничтожаем окно
    glfwDestroyWindow(window)
    glfwTerminate() // Завершаем работу GLFW
}

// Функция для задания перспективной проекции
fun gluPerspective(fovY: Double, aspect: Double, zNear: Double, zFar: Double) {
    // fovY: угол обзора по вертикали в градусах
    // aspect: соотношение сторон окна (ширина / высота)
    // zNear: минимальное расстояние до видимой области (ближняя плоскость отсечения)
    // zFar: максимальное расстояние до видимой области (дальняя плоскость отсечения)

    val fH = tan(fovY / 360 * Math.PI).toFloat() * zNear // Вычисляем высоту ближней плоскости
    val fW = fH * aspect // Вычисляем ширину ближней плоскости
    glFrustum(-fW, fW, -fH, fH, zNear, zFar) // Устанавливаем параметры отсечения
}

fun glfwGetCursorPos(window: Long): Pair<Double, Double> {
    val xpos = DoubleArray(1)
    val ypos = DoubleArray(1)
    glfwGetCursorPos(window, xpos, ypos)
    return Pair(xpos[0], ypos[0])
}

suspend fun drawTrianglesAsync(simulation: ParticleMeshSimulation, scale: Float, cameraPosition: Triple<Float, Float, Float>) = coroutineScope {
    val totalIterations = simulation.particleX.size
    val availableCores = Runtime.getRuntime().availableProcessors() // Получаем количество ядер
    val chunkSize = totalIterations / availableCores // Распределяем задачи равномерно по ядрам
    val jobs = mutableListOf<Job>()

    val paddingX = simulation.config.worldWidth / 2 / scale
    val paddingY = simulation.config.worldHeight / 2 / scale
    val paddingZ = simulation.config.worldDepth / 2 / scale
    val (ox, oy, oz) = getOriginalCoordinates(cameraPosition.first, cameraPosition.second, cameraPosition.third, mouseDx, mouseDy)

    for (i in 0 until totalIterations step chunkSize) {
        jobs.add(launch {
            val end = minOf(i + chunkSize, totalIterations)
            for (j in i until end) {
                // Преобразование мировых координат частиц в пространство камеры
                val x = (simulation.particleX[j] / scale) - paddingX
                val y = (simulation.particleY[j] / scale) - paddingY
                val z = (simulation.particleZ[j] / scale) - paddingZ
                val dx = x - ox
                val dy = y - oy
                val dz = z - oz

                // Нормализуем расстояние для изменения цвета
                val distance = sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))
                val color = (1 - distance).coerceIn(0.015f, 1f)

                if (simulation.config.isFading) {
                    glColor3f(color, color, color)
                }

                // Рисуем частицу
                glVertex3f(x, y, z)
            }
        })
    }

    // Дождаться завершения всех задач
    jobs.forEach { it.join() }
}

fun getOriginalCoordinates(
    x: Float, y: Float, z: Float,
    dx: Float, dy: Float
): Triple<Float, Float, Float> {
    // Углы в радианах
    val dxRadians = toRadians(dx.toDouble()).toFloat()
    val dyRadians = toRadians(dy.toDouble()).toFloat()

    // Синусы и косинусы углов
    val sinDx = sin(-dxRadians) // Отрицательный угол для обратного преобразования
    val cosDx = cos(-dxRadians)
    val sinDy = sin(-dyRadians)
    val cosDy = cos(-dyRadians)

    // Вращение вокруг оси X (dy)
    val rotatedX1 = x
    val rotatedY1 = cosDy * y - sinDy * z
    val rotatedZ1 = sinDy * y + cosDy * z

    // Вращение вокруг оси Y (dx)
    val rotatedX2 = cosDx * rotatedX1 + sinDx * rotatedZ1
    val rotatedY2 = rotatedY1
    val rotatedZ2 = -sinDx * rotatedX1 + cosDx * rotatedZ1

    return Triple(rotatedX2, rotatedY2, rotatedZ2)
}

fun isObjectInFrustum(
    x: Float,
    y: Float,
    z: Float,
    fovY: Double,
    aspect: Double,
    zNear: Double,
    zFar: Double
): Boolean {
    if (z > -zNear || z < -zFar) {
        // Если объект находится перед ближней или за дальней плоскостью, он невидим
        return false
    }

    // Вычисляем размеры ближней плоскости
    val halfHeight = (tan(toRadians(fovY / 2)) * -z).toFloat()
    val halfWidth = (halfHeight * aspect).toFloat()

    // Проверяем, находится ли объект в пределах фруструма
    return x in -halfWidth..halfWidth && y in -halfHeight..halfHeight
}



