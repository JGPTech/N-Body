package kz.qwertukg.nBodyApp

import kz.qwertukg.nBodyApp.nBodyParticleMesh.ParticleMeshSimulation
import org.lwjgl.opengl.GL46.*


// Проверка статуса компиляции шейдера
fun checkShaderCompileStatus(shader: Int) {
    val status = glGetShaderi(shader, GL_COMPILE_STATUS)
    if (status == GL_FALSE) {
        val log = glGetShaderInfoLog(shader)
        throw RuntimeException("Ошибка компиляции шейдера: $log")
    }
}

// Проверка статуса линковки программы
fun checkProgramLinkStatus(program: Int) {
    val status = glGetProgrami(program, GL_LINK_STATUS)
    if (status == GL_FALSE) {
        val log = glGetProgramInfoLog(program)
        throw RuntimeException("Ошибка линковки программы: $log")
    }
}

fun updatePoints(simulation: ParticleMeshSimulation, scale: Float): FloatArray {
    val x = simulation.particleX
    val y = simulation.particleY
    val z = simulation.particleZ

    if (x.size != y.size || y.size != z.size) {
        throw IllegalArgumentException("Все массивы должны быть одинаковой длины")
    }

    val updatedPoints = FloatArray(x.size * 3)
    for (i in x.indices) {
        updatedPoints[i * 3] = (x[i] - simulation.config.worldWidth / 2) / scale
        updatedPoints[i * 3 + 1] = (y[i] - simulation.config.worldHeight / 2) / scale
        updatedPoints[i * 3 + 2] = (z[i] - simulation.config.worldDepth / 2) / scale
    }

    return updatedPoints
}