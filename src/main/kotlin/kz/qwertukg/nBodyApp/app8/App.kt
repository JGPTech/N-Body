package kz.qwertukg.nBodyApp.app8

import kotlinx.coroutines.runBlocking
import kz.qwertukg.nBodyApp.checkProgramLinkStatus
import kz.qwertukg.nBodyApp.checkShaderCompileStatus
import kz.qwertukg.nBodyApp.nBodyParticleMesh.ParticleMeshSimulation
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import kz.qwertukg.nBodyApp.old.init
import kz.qwertukg.nBodyApp.updatePoints
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.opengl.GL.*
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import org.joml.Matrix4f

fun createShader(type: Int, source: String): Int {
    val shader = glCreateShader(type)
    glShaderSource(shader, source)
    glCompileShader(shader)
    checkShaderCompileStatus(shader)
    return shader
}

fun createProgram(vertexShader: Int, geometryShader: Int, fragmentShader: Int): Int {
    val program = glCreateProgram()
    glAttachShader(program, vertexShader)
    glAttachShader(program, geometryShader)
    glAttachShader(program, fragmentShader)
    glLinkProgram(program)
    checkProgramLinkStatus(program)
    return program
}

fun createVBO(data: FloatArray): Int {
    val vbo = glGenBuffers()
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBufferData(GL_ARRAY_BUFFER, data.size * Float.SIZE_BYTES.toLong(), GL_DYNAMIC_DRAW)
    return vbo
}

fun createVAO(vbo: Int): Int {
    val vao = glGenVertexArrays()
    glBindVertexArray(vao)
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glVertexAttribPointer(0, 3, GL_FLOAT, false, 3 * Float.SIZE_BYTES, 0)
    glEnableVertexAttribArray(0)
    return vao
}

suspend fun main() = runBlocking {
    val config = SimulationConfig()
    val simulation = init(config)

    val windowWidth = simulation.config.screenW
    val windowHeight = simulation.config.screenH
    val scale = 2000000f
    val pointSize = 0.0025f
    val zNear = 0.1f
    val zFar = 10f
    var camZ = 3f
    val camZStep = 0.1f

    // Инициализация GLFW
    if (!glfwInit()) throw IllegalStateException("Не удалось инициализировать GLFW")

    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(
        windowWidth, windowHeight,
        "3D Точки с геометрическим шейдером",
        if (config.isFullScreen) glfwGetPrimaryMonitor() else 0,
        NULL
    ) ?: throw RuntimeException("Не удалось создать окно")

    glfwMakeContextCurrent(window)
    glfwSwapInterval(0)
    createCapabilities()

    // Создание VAO и VBO
    val initialPoints = updatePoints(simulation, scale)
    val vbo = createVBO(initialPoints)
    val vao = createVAO(vbo)

    glEnable(GL_DEPTH_TEST)

    // Матрицы проекции и вида
    val projectionMatrix = Matrix4f().perspective(
        Math.toRadians(45.0).toFloat(),
        windowWidth.toFloat() / windowHeight,
        zNear, zFar
    )
    val projectionArray = FloatArray(16)
    projectionMatrix.get(projectionArray)

    val viewArray = FloatArray(16)

    // Обработка колёсика мыши для изменения позиции камеры
    glfwSetScrollCallback(window) { _, _, yoffset ->
        camZ = (camZ - yoffset.toFloat() * camZStep).coerceIn(zNear, zFar)
    }

    // Шейдеры
    val vertexShader = createShader(GL_VERTEX_SHADER, """
        #version 410 core
        layout(location = 0) in vec3 aPos;
        uniform mat4 projection;
        uniform mat4 view;

        out float fragDistance; 
        out vec3 fragPos;
        out vec3 normal;

        void main() {
            vec4 viewPosition = view * vec4(aPos, 1.0);
            fragDistance = -viewPosition.z; 
            fragPos = aPos; 
            normal = normalize(aPos); 
            gl_Position = projection * viewPosition;
        }
    """)

    val geometryShader = createShader(GL_GEOMETRY_SHADER, """
        #version 410 core
        layout(points) in;
        layout(triangle_strip, max_vertices = 130) out;

        uniform float w, h, pointSize;
        const int edges = 16;
        in float fragDistance[];
        in vec3 fragPos[], normal[];
        out float fragDistance2;
        out vec3 fragPos2, normal2;

        void main() {
            fragPos2 = fragPos[0];
            normal2 = normal[0];
            fragDistance2 = fragDistance[0];
            vec4 center = gl_in[0].gl_Position;

            for (int i = 0; i <= edges; ++i) {
                float angle = 2.0 * 3.14159265359 * float(i) / float(edges);
                float xOffset = cos(angle) * pointSize * h / w;
                float yOffset = sin(angle) * pointSize;

                gl_Position = center;
                EmitVertex();

                gl_Position = center + vec4(xOffset, yOffset, 0.0, 0.0);
                EmitVertex();
            }
            EndPrimitive();
        }
    """)

    val fragmentShader = createShader(GL_FRAGMENT_SHADER, """
       #version 410 core

        in float fragDistance2; // Расстояние до камеры
        uniform float zNear;   // Ближняя плоскость отсечения
        uniform float zFar;    // Дальняя плоскость отсечения
        
        in vec3 fragPos2; // Позиция фрагмента в мировых координатах
        in vec3 normal2;  // Нормаль фрагмента
        
        uniform vec3 lightColor; // Цвет света
        uniform vec3 lightPos;   // Позиция источника света
        uniform vec3 viewPos;    // Позиция камеры
        
        out vec4 FragColor;
        
        void main() {
            // Нормализация расстояния до камеры
            float normalizedDistance = clamp((fragDistance2 - zNear) / (zFar - zNear), 0.0, 1.0);
            
            // Цвет материала
            vec3 objectColor = vec3(1.0, 1.0, 1.0);
        
            // Фоновое освещение
            float ambientStrength = 0.5;
            vec3 ambient = ambientStrength * lightColor;
        
            // Рассеянное освещение
            vec3 norm = normalize(normal2);
            vec3 lightDir = normalize(lightPos - fragPos2);
            float diff = max(dot(norm, lightDir), 0.0);
            vec3 diffuse = diff * lightColor;
        
            // Спекулярное освещение
            float specularStrength = 0.5;
            vec3 viewDir = normalize(viewPos - fragPos2);
            vec3 reflectDir = reflect(-lightDir, norm);
            float spec = pow(max(dot(viewDir, reflectDir), 0.0), 32);
            vec3 specular = specularStrength * spec * lightColor;
        
            // Итоговый цвет с учетом расстояния
            vec3 result = (ambient + diffuse + specular) * objectColor;
            result *= (1.0 - normalizedDistance); // Чем дальше, тем темнее
        
            FragColor = vec4(result, 1.0);
        }
    """)

    val shaderProgram = createProgram(vertexShader, geometryShader, fragmentShader)
    glDeleteShader(vertexShader)
    glDeleteShader(geometryShader)
    glDeleteShader(fragmentShader)

    // Получение uniform-локаций
    val projectionLocation = glGetUniformLocation(shaderProgram, "projection")
    val viewLocation = glGetUniformLocation(shaderProgram, "view")
    val pointSizeLocation = glGetUniformLocation(shaderProgram, "pointSize")
    val wLocation = glGetUniformLocation(shaderProgram, "w")
    val hLocation = glGetUniformLocation(shaderProgram, "h")
    val lightColorLocation = glGetUniformLocation(shaderProgram, "lightColor")
    val lightPosLocation = glGetUniformLocation(shaderProgram, "lightPos")
    val viewPosLocation = glGetUniformLocation(shaderProgram, "viewPos")
    val zNearLocation = glGetUniformLocation(shaderProgram, "zNear")
    val zFarLocation = glGetUniformLocation(shaderProgram, "zFar")

    // Основной цикл
    while (!glfwWindowShouldClose(window)) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)

        // Обновляем матрицу вида
        val viewMatrix = Matrix4f().lookAt(0f, 0f, camZ, 0f, 0f, 0f, 0f, 1f, 0f)
        viewMatrix.get(viewArray)

        // Обновляем буфер с координатами
        simulation.step()
        val updatedPoints = updatePoints(simulation, scale)

        glBindBuffer(GL_ARRAY_BUFFER, vbo)
        val mappedBuffer = glMapBufferRange(
            GL_ARRAY_BUFFER, 0,
            updatedPoints.size * Float.SIZE_BYTES.toLong(),
            GL_MAP_WRITE_BIT or GL_MAP_INVALIDATE_BUFFER_BIT
        )?.asFloatBuffer()
        mappedBuffer?.put(updatedPoints)?.flip()
        glUnmapBuffer(GL_ARRAY_BUFFER)

        glUseProgram(shaderProgram)

        // Передача uniform-переменных
        glUniformMatrix4fv(projectionLocation, false, projectionArray)
        glUniformMatrix4fv(viewLocation, false, viewArray)
        glUniform1f(pointSizeLocation, pointSize)
        glUniform1f(wLocation, windowWidth.toFloat())
        glUniform1f(hLocation, windowHeight.toFloat())
        glUniform3f(lightColorLocation, 1.0f, 1.0f, 1.0f)
        glUniform3f(lightPosLocation, 0.0f, 0.0f, camZ)
        glUniform3f(viewPosLocation, 0.0f, 0.0f, camZ)
        glUniform1f(zNearLocation, zNear)
        glUniform1f(zFarLocation, zFar)

        glBindVertexArray(vao)
        glDrawArrays(GL_POINTS, 0, initialPoints.size / 3)

        glfwSwapBuffers(window)
        glfwPollEvents()
    }

    // Очистка ресурсов
    glDeleteVertexArrays(vao)
    glDeleteBuffers(vbo)
    glDeleteProgram(shaderProgram)

    glfwDestroyWindow(window)
    glfwTerminate()
}
