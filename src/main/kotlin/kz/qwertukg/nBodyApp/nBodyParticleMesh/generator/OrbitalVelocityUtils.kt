package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import org.joml.Vector3f
import kotlin.math.sqrt

/**
 * Утилита для общего кода расчёта орбитальных скоростей.
 *
 * crossBaseProducer(rVec):
 *  - возвращает "базовый" вектор, с которым нужно сделать cross(rVec, base),
 *    чтобы получить направление скорости.
 *  - Например, для диска это всегда zAxis (0,0,1).
 *  - Для «случайной плоскости» — это normal плоскости.
 *  - Для сферы — обычно yAxis (0,1,0) или что-то ещё.
 */
object OrbitalVelocityUtils {

    fun assignOrbitalVelocities(
        particles: MutableList<Particle>,
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        starMass: Float,
        g: Float,
        magicConst: Float,
        crossBaseProducer: (Vector3f) -> Vector3f
    ) {
        for (i in particles.indices) {
            val p = particles[i]

            // Радиус-вектор от центра (звезды) до частицы
            val rVec = Vector3f(
                p.x - centerX,
                p.y - centerY,
                p.z - centerZ
            )
            val dist = rVec.length()

            if (dist > 1e-8f) {
                // Модуль скорости для круговой орбиты
                val v = sqrt(g * starMass / dist)

                // Берём "базовый" вектор для cross (например, zAxis или normal)
                // и пробуем получить направление.
                val base = crossBaseProducer(rVec)
                val crossCandidate = Vector3f()
                rVec.cross(base, crossCandidate)

                // Если вектор получился слишком маленьким (rVec ~|| base),
                // то используем запасной вариант (x-ось)
                if (crossCandidate.length() < 1e-8f) {
                    rVec.cross(Vector3f(1f, 0f, 0f), crossCandidate)
                }

                crossCandidate.normalize()

                val vx = crossCandidate.x * v * magicConst
                val vy = crossCandidate.y * v * magicConst
                val vz = crossCandidate.z * v * magicConst

                particles[i] = p.copy(vx = vx, vy = vy, vz = vz)
            } else {
                // Если частица в самом центре
                particles[i] = p.copy(vx = 0f, vy = 0f, vz = 0f)
            }
        }
    }
}