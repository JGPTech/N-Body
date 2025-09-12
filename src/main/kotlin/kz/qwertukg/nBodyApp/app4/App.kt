package kz.qwertukg.nBodyApp.app4

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
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
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
    val projectionMatrix = Matrix4f().perspective(
        Math.toRadians(45.0).toFloat(),
        800f / 600f,
        0.1f,
        10.0f
    )

    val viewMatrix = Matrix4f().lookAt(
        0f, 0f, 3f,  // Позиция камеры
        0f, 0f, 0f,  // Центр сцены
        0f, 1f, 0f   // Направление "вверх"
    )

    // Вершинный шейдер
    val vertexShaderSource = """
        #version 460 core
        layout(location = 0) in vec3 aPos;
        uniform mat4 projection;
        uniform mat4 view;

        void main() {
            gl_Position = projection * view * vec4(aPos, 1.0);
        }
    """.trimIndent()

    // Фрагментный шейдер
    val fragmentShaderSource = """
        #version 460 core
        out vec4 FragColor;

        void main() {
            FragColor = vec4(1.0, 1.0, 1.0, 1.0); // Белый цвет
        }
    """.trimIndent()

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
    val lightColorLocation = glGetUniformLocation(shaderProgram, "lightColor")
    val lightPosLocation = glGetUniformLocation(shaderProgram, "lightPos")
    val viewPosLocation = glGetUniformLocation(shaderProgram, "viewPos")

    val projectionArray = FloatArray(16)
    val viewArray = FloatArray(16)
    projectionMatrix.get(projectionArray)
    viewMatrix.get(viewArray)

    while (!glfwWindowShouldClose(window)) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

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
        glUniform3f(lightColorLocation, 1.0f, 1.0f, 1.0f) // Белый свет
        glUniform3f(lightPosLocation, 0.0f, 0.0f, 3.0f)   // Позиция света
        glUniform3f(viewPosLocation, 0.0f, 0.0f, 3.0f)    // Позиция камеры

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

