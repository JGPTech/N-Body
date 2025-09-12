package kz.qwertukg.nBodyApp.app6

import kz.qwertukg.nBodyApp.old.init
import kotlinx.coroutines.runBlocking
import kz.qwertukg.nBodyApp.checkProgramLinkStatus
import kz.qwertukg.nBodyApp.checkShaderCompileStatus
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.updatePoints
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f

suspend fun main() = runBlocking {
    if (!glfwInit()) {
        throw IllegalStateException("Не удалось инициализировать GLFW")
    }

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(800, 600, "Источники света", NULL, NULL)
    if (window == NULL) {
        throw RuntimeException("Не удалось создать окно")
    }

    glfwMakeContextCurrent(window)
    glfwSwapInterval(0)
    createCapabilities()

    val config = SimulationConfig()
    val simulation = init(config)

    val scale = 1000000f
    val points = updatePoints(simulation, scale)

    // Создание VAO и VBO
    val vao = glGenVertexArrays()
    val vbo = glGenBuffers()

    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)

    glBufferData(GL_ARRAY_BUFFER, points.size * java.lang.Float.BYTES.toLong(), GL_DYNAMIC_DRAW)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * java.lang.Float.BYTES, 0)
    glEnableVertexAttribArray(0)
    glBindVertexArray(0)

    // Включение теста глубины
    glEnable(GL_DEPTH_TEST)

    // Матрицы
    val zNear = 0.1f
    val zFar = 10.0f
    val projectionMatrix = Matrix4f().perspective(
        Math.toRadians(45.0).toFloat(),
        800f / 600f,
        zNear,
        zFar
    )

    // Шейдеры
    val vertexShaderSource = """
        #version 410 core
        
        layout(location = 0) in vec3 aPos;
        uniform mat4 projection;
        uniform mat4 view;
        
        out float fragDistance; // Передаем расстояние до камеры во фрагментный шейдер
        
        void main() {
            // Преобразуем позицию в пространство камеры
            vec4 viewPosition = view * vec4(aPos, 1.0);
            fragDistance = -viewPosition.z; // Расстояние до камеры (z отрицательный)
        
            gl_Position = projection * viewPosition;
        }

    """.trimIndent()

    val fragmentShaderSource = """
        #version 410 core

        in float fragDistance; // Расстояние до камеры, переданное из вершинного шейдера
        uniform float zNear;   // Ближняя плоскость отсечения
        uniform float zFar;    // Дальняя плоскость отсечения
        
        out vec4 FragColor;
        
        void main() {
            // Нормализация расстояния в диапазон [0, 1]
            float normalizedDistance = clamp((fragDistance - zNear) / (zFar - zNear), 0.0, 1.0);
        
            // Яркость обратно пропорциональна расстоянию
            float brightness = 1.0 - normalizedDistance;
        
            // Результирующий цвет (чем ближе, тем ярче, дальше — темнее)
            FragColor = vec4(vec3(brightness), 1.0); // Оттенки серого
        }
    """.trimIndent()

    // Обработка колёсика мыши для изменения позиции камеры
    var camZ = 3f
    val camZStep = 0.1f
    glfwSetScrollCallback(window) { _, _, yoffset ->
        camZ = (camZ - yoffset.toFloat() * camZStep).coerceIn(zNear, zFar)
    }

    val vertexShader = glCreateShader(GL_VERTEX_SHADER)
    glShaderSource(vertexShader, vertexShaderSource)
    glCompileShader(vertexShader)
    checkShaderCompileStatus(vertexShader)

    val fragmentShader = glCreateShader(GL_FRAGMENT_SHADER)
    glShaderSource(fragmentShader, fragmentShaderSource)
    glCompileShader(fragmentShader)
    checkShaderCompileStatus(fragmentShader)

    val shaderProgram = glCreateProgram()
    glAttachShader(shaderProgram, vertexShader)
    glAttachShader(shaderProgram, fragmentShader)
    glLinkProgram(shaderProgram)
    checkProgramLinkStatus(shaderProgram)

    glDeleteShader(vertexShader)
    glDeleteShader(fragmentShader)

    // Локации uniform-переменных
    val projectionLocation = glGetUniformLocation(shaderProgram, "projection")
    val viewLocation = glGetUniformLocation(shaderProgram, "view")
    val zNearLocation = glGetUniformLocation(shaderProgram, "zNear")
    val zFarLocation = glGetUniformLocation(shaderProgram, "zFar")

    val projectionArray = FloatArray(16)
    val viewArray = FloatArray(16)
    projectionMatrix.get(projectionArray)

    while (!glfwWindowShouldClose(window)) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Обновляем матрицу вида
        val viewMatrix = Matrix4f().lookAt(0f, 0f, camZ, 0f, 0f, 0f, 0f, 1f, 0f)
        viewMatrix.get(viewArray)

        simulation.step()
        val updatedPoints = updatePoints(simulation, scale)

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val mappedBuffer = glMapBufferRange(
            GL_ARRAY_BUFFER,
            0,
            updatedPoints.size * java.lang.Float.BYTES.toLong(),
            GL_MAP_WRITE_BIT or GL_MAP_INVALIDATE_BUFFER_BIT
        )?.asFloatBuffer()
        mappedBuffer?.put(updatedPoints)?.flip()
        glUnmapBuffer(GL_ARRAY_BUFFER)

        glUseProgram(shaderProgram)

        glUniformMatrix4fv(projectionLocation, false, projectionArray)
        glUniformMatrix4fv(viewLocation, false, viewArray)
        glUniform1f(zNearLocation, zNear) // Ближняя плоскость
        glUniform1f(zFarLocation, zFar) // Дальняя плоскость

        glBindVertexArray(vao)
        glDrawArrays(GL_POINTS, 0, points.size / 3)

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteProgram(shaderProgram)

    glfwDestroyWindow(window)
    glfwTerminate()
}
