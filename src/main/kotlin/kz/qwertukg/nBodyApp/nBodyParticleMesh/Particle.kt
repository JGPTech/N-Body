package kz.qwertukg.nBodyApp.nBodyParticleMesh

// Data class для 3D-частицы
data class Particle(
    val x: Float,
    val y: Float,
    val z: Float,
    val vx: Float,
    val vy: Float,
    val vz: Float,
    val m: Float,
    val r: Float // = sqrt(m) / 100
)