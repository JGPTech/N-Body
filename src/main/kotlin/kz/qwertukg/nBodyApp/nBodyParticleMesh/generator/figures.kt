package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig
import org.joml.Vector3f
import kotlin.math.*
import kotlin.random.Random

/**
 * 1) Лента Мёбиуса (Mobius Strip)
 *    Параметры (в params):
 *      "majorRadius" - радиус «кольца» (по умолчанию берем maxRadius)
 *      "width"       - половина ширины ленты (по умолчанию minRadius)
 *      "twists"      - сколько "поворотов" делает лента (по умолчанию 1)
 */
class MobiusStripGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val majorR = params["majorRadius"] ?: config.maxRadius.toFloat()
        val halfW  = params["width"]       ?: config.minRadius.toFloat()
        val twists = params["twists"]?.toInt() ?: 1

        val particles = mutableListOf<Particle>()

        // Применим классические параметры "t" в [0..2π * twists], "s" в [-1..1].
        repeat(config.count) {
            // t ~ [0..2π * twists]
            val t = Random.nextFloat() * (2f * PI.toFloat() * twists)
            // s ~ [-1..1]
            val s = Random.nextFloat() * 2f - 1f

            // По классической формуле ленты Мёбиуса (упрощенный вариант):
            // x = (majorR + s * cos(t/2)) * cos(t)
            // y = (majorR + s * cos(t/2)) * sin(t)
            // z = s * sin(t/2)
            val halfT = t / 2f
            val cosHalfT = cos(halfT)
            val sinHalfT = sin(halfT)

            val localR = majorR + s * halfW * cosHalfT
            val x = localR * cos(t)
            val y = localR * sin(t)
            val z = s * halfW * sinHalfT

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + z.toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        // Задаём орбитальные скорости
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Для ленты Мёбиуса можно взять ось z
            Vector3f(0f, 0f, 1f)
        }

        // Добавляем звезду
        particles.add(Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass)))
        return particles
    }
}

/**
 * 2) "Рёберный" (волнообразный) цилиндр (RidgesCylinder):
 *    Высота ~ 2 * height, радиус меняется по синусоиде вдоль z.
 *    Параметры:
 *      "baseRadius" - базовый радиус (если нет, берём minRadius)
 *      "height"     - полувысота (если нет, берём maxRadius)
 *      "amplitude"  - амплитуда колебаний радиуса (по умолчанию = baseRadius/2)
 *      "freq"       - частота колебаний (в синусе) вдоль оси z (по умолчанию 2π / height)
 */
class RidgesCylinderGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val baseR     = params["baseRadius"] ?: config.minRadius.toFloat()
        val halfH     = params["height"]     ?: config.maxRadius.toFloat()
        val amplitude = params["amplitude"]  ?: (baseR / 2f)
        // Если height == 0, freq -> 1, а так берём ~ (2π / (2*halfH)) = π/halfH
        val freq      = params["freq"]       ?: (PI.toFloat() / halfH)

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // z ~ [-halfH.. +halfH]
            val zVal = Random.nextFloat() * 2f * halfH - halfH
            // радиус = baseR + amplitude * sin(freq * zVal)
            val radial = baseR + amplitude * sin(freq * zVal)

            // Случайно в диске радиуса radial
            val rho = sqrt(Random.nextFloat()) * radial
            val theta = Random.nextDouble(0.0, 2.0 * PI)
            val x = (rho * cos(theta)).toFloat()
            val y = (rho * sin(theta)).toFloat()

            val px = cx + x
            val py = cy + y
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Ось цилиндра — z
            Vector3f(0f, 0f, 1f)
        }

        particles.add(Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass)))
        return particles
    }
}

/**
 * 3) "Синусоидный тор" (SineWaveTorus):
 *    Параметры:
 *      "majorRadius" - радиус большого круга (по умолчанию maxRadius)
 *      "tubeRadius"  - средний радиус трубы (по умолчанию minRadius)
 *      "amplitude"   - амплитуда синусоиды (прибавляется к tubeRadius)
 *      "freq"        - частота синусоиды
 */
class SineWaveTorusGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val majorR   = params["majorRadius"] ?: config.maxRadius.toFloat()
        val tubeR    = params["tubeRadius"]  ?: config.minRadius.toFloat()
        val amplitude= params["amplitude"]   ?: (tubeR.toFloat() / 2f)
        val freq     = params["freq"]        ?: 3f // например, 3 колебания на 2π

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // phi ~ [0..2π] — угол по большому кругу
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            // theta ~ [0..2π] — угол по трубе
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            // Добавляем синусоиду к tubeR: rTube = tubeR + amplitude*sin(freq*phi)
            val rTube = tubeR + amplitude * sin(freq * phi)

            val x = (majorR + rTube * cos(theta)) * cos(phi)
            val y = (majorR + rTube * cos(theta)) * sin(phi)
            val z = rTube * sin(theta)

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + z.toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Ось тора — z
            Vector3f(0f, 0f, 1f)
        }

        particles.add(Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass)))
        return particles
    }
}

/**
 * 5) Сфера с "шумом" (RandomNoiseSphere):
 *    Параметры:
 *      "baseRadius"   - средний радиус сферы (по умолчанию maxRadius)
 *      "noiseAmplitude" - колебания радиуса (по умолчанию = baseRadius/10)
 *      "noiseFreq"    - частота (сколько колебаний по углу?)
 *
 *    Простейший "шум" сделаем синусоиду от (theta + phi),
 *    либо воспользуемся random при каждом угле (тоже вариант).
 */
class RandomNoiseSphereGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val baseR   = params["baseRadius"]     ?: config.maxRadius.toFloat()
        val amp     = params["noiseAmplitude"] ?: (baseR / 10f)
        val freq    = params["noiseFreq"] ?: (Random.nextFloat() * 10)  // кол-во колебаний

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // Берём угол phi ~ [0..2π], cosTheta ~ [-1..1]
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(-1.0, 1.0)
            val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

            // Небольшой "шум": R = baseR + amp*sin(freq*(phi + arccos(cosTheta)))
            // arccos(cosTheta) = θ
            val theta = acos(cosTheta)
            val noiseVal = sin(freq * (phi + theta))

            val R = baseR + amp * noiseVal

            val px = cx + (R * sinTheta * cos(phi)).toFloat()
            val py = cy + (R * sinTheta * sin(phi)).toFloat()
            val pz = cz + (R * cosTheta).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        // Орбитальная скорость
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Пусть вращается вокруг оси z
            Vector3f(0f, 0f, 1f)
        }

        particles.add(Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass)))
        return particles
    }
}

/** 1) Генератор пирамиды (основание квадратное, вершина в центре). */
class PyramidGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // baseSize — половина стороны основания
        val baseSize = params["baseSize"] ?: config.minRadius.toFloat()
        // height — высота пирамиды
        val height = params["height"] ?: config.maxRadius.toFloat()

        val particles = mutableListOf<Particle>()
        repeat(config.count) {
            // zFrac [0..1], 0 = вершина, 1 = основание
            val zFrac = Random.nextFloat()
            val zVal = -height * zFrac  // вершина в z=0, основание в z=-height

            // "Усечённая" область квадрата: side(t) = 2*(1-t)*baseSize
            val halfSide = (1f - zFrac) * baseSize
            val rx = Random.nextFloat() * 2f * halfSide - halfSide
            val ry = Random.nextFloat() * 2f * halfSide - halfSide

            val px = cx + rx
            val py = cy + ry
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Пусть пирамида вращается вокруг оси Z
            Vector3f(0f, 0f, 1f)
        }

        // Добавляем "звезду"
        particles.add(Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass)))
        return particles
    }
}

/** 3) Генератор прямоугольного параллелепипеда (кубоида),
 *    параметры: "width", "height", "depth" (если что-то не указано, используем min/maxRadius).
 */
class CuboidGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()

    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val width  = params["width"]  ?: (config.maxRadius - config.minRadius).toFloat()
        val height = params["height"] ?: (config.maxRadius - config.minRadius).toFloat()
        val depth  = params["depth"]  ?: (config.maxRadius - config.minRadius).toFloat()

        val halfW = width / 2f
        val halfH = height / 2f
        val halfD = depth / 2f

        val particles = mutableListOf<Particle>()
        repeat(config.count) {
            val rx = Random.nextFloat() * width  - halfW
            val ry = Random.nextFloat() * height - halfH
            val rz = Random.nextFloat() * depth  - halfD

            val px = cx + rx
            val py = cy + ry
            val pz = cz + rz

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Вращение вокруг оси Z
            Vector3f(0f, 0f, 1f)
        }

        particles.add(Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass)))
        return particles
    }
}

/** 5) Генератор нескольких "скучкованных" регионов (RandomClusters):
 *    Параметры:
 *      - "clusters" (кол-во кластеров),
 *      - "clusterRadius" (радиус рассеяния внутри каждого кластера),
 *      - "spread" (как далеко кластеры разлетаются от центра).
 */
class RandomClustersGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()

    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val clusters = params["clusters"] ?: 100f
        val clusterRadius = params["clusterRadius"] ?: config.minRadius.toFloat()
        val spread = params["spread"] ?: config.maxRadius.toFloat()

        // Превращаем "clusters" в Int (округлим)
        val clusterCount = clusters.toInt().coerceAtLeast(1)

        // Для начала сгенерируем "центры" кластеров
        val clusterCenters = mutableListOf<Vector3f>()
        repeat(clusterCount) {
            // Случайное направление + расстояние
            val dist = Random.nextFloat() * spread
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(-1.0, 1.0)
            val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

            val px = dist * sinTheta * cos(phi)
            val py = dist * sinTheta * sin(phi)
            val pz = dist * cosTheta

            clusterCenters.add(Vector3f(px.toFloat(), py.toFloat(), pz.toFloat()))
        }

        val particles = mutableListOf<Particle>()
        // Разделим общее число частиц по кластерам (пример: поровну)
        val perCluster = config.count / clusterCount

        for (k in 0 until clusterCount) {
            val center = clusterCenters[k]
            repeat(perCluster) {
                // Генерируем точку внутри сферического "clusterRadius"
                val alpha = Random.nextFloat() // [0..1]
                val R = clusterRadius * cbrt(alpha)
                val phi = Random.nextDouble(0.0, 2.0 * PI)
                val cosTheta = Random.nextDouble(-1.0, 1.0)
                val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

                val dx = R * sinTheta * cos(phi)
                val dy = R * sinTheta * sin(phi)
                val dz = R * cosTheta

                val px = cx + center.x + dx
                val py = cy + center.y + dy
                val pz = cz + center.z + dz

                val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
                particles.add(Particle(px.toFloat(), py.toFloat(), pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
            }
        }

        // Если в распределении "нехватает" оставшихся частиц (из-за деления), добавим их в последний кластер
        val remainder = config.count % clusterCount
        if (remainder > 0) {
            val center = clusterCenters.last()
            repeat(remainder) {
                val alpha = Random.nextFloat()
                val R = clusterRadius * cbrt(alpha)
                val phi = Random.nextDouble(0.0, 2.0 * PI)
                val cosTheta = Random.nextDouble(-1.0, 1.0)
                val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

                val dx = R * sinTheta * cos(phi)
                val dy = R * sinTheta * sin(phi)
                val dz = R * cosTheta

                val px = cx + center.x + dx
                val py = cy + center.y + dy
                val pz = cz + center.z + dz

                val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
                particles.add(Particle(px.toFloat(), py.toFloat(), pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
            }
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Вращение вокруг оси Z
            Vector3f(0f, 0f, 1f)
        }

        particles.add(Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass)))
        return particles
    }
}

/** 1) Генерация конуса (ось вдоль Z, вершина в центре, основание в плоскости z = -height). */
class ConeGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Пусть minRadius = радиус основания, maxRadius = высота конуса
        val baseRadius = config.minRadius
        val height = config.maxRadius

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // z идёт от 0 (вершина) до -height (основание)
            val zFrac = Random.nextFloat() // [0..1]
            val zVal = -height * zFrac     // от 0 до -height
            // Радиус на этом "сечении" ~ (1 - zFrac)*baseRadius
            val rLocal = baseRadius * (1f - zFrac)
            // Случайно в круге радиуса rLocal
            val rho = sqrt(Random.nextFloat()) * rLocal
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            val x = (rho * cos(theta)).toFloat()
            val y = (rho * sin(theta)).toFloat()

            val px = cx + x
            val py = cy + y
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Для конуса возьмём ось z
            Vector3f(0f, 0f, 1f)
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)
        return particles
    }
}

/** 2) Генерация тора (ось вдоль Z, центр тора в (cx,cy,cz), радиус "трубы" = minRadius, радиус "кольца" = maxRadius). */
class TorusGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Внутренний радиус "трубы" (rTube) и внешний радиус "бублика" (rMajor)
        val rTube = config.minRadius
        val rMajor = config.maxRadius

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // Угол вокруг большой окружности
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            // Угол вокруг малой окружности (трубы)
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            // Координаты относительно центра (0,0,0), ось тора - ось Z
            val x = (rMajor + rTube * cos(theta)) * cos(phi)
            val y = (rMajor + rTube * cos(theta)) * sin(phi)
            val z = rTube * sin(theta)

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + z.toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Для тора тоже удобно взять ось z
            Vector3f(0f, 0f, 1f)
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)
        return particles
    }
}

/** 3) Генерация полушара (ось вдоль Z, "нижнее" основание в плоскости z=0). */
class HemisphereGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val rMax = config.maxRadius

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // Радиус с учётом равномерности по объёму полушара
            val alpha = Random.nextFloat() // [0..1]
            val R = rMax * cbrt(alpha)     // радиус от 0 до rMax
            // Случайное направление, но ограничиваем z >= 0
            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(0.0, 1.0) // т.к. z>=0 => theta в [0..pi/2]
            val sinTheta = sqrt(1.0 - cosTheta*cosTheta)

            val xDir = sinTheta * cos(phi)
            val yDir = sinTheta * sin(phi)
            val zDir = cosTheta

            val px = cx + (R * xDir).toFloat()
            val py = cy + (R * yDir).toFloat()
            val pz = cz + (R * zDir).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Для полушара — ось z
            Vector3f(0f, 0f, 1f)
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)
        return particles
    }
}

/** 4) Генерация "двойного конуса": конус вверх и конус вниз (ось вдоль Z, вершины соединены в центре). */
class DoubleConeGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val baseRadius = config.minRadius
        val height = config.maxRadius

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // Случайно выбираем, в какой конус попасть (верхний / нижний)
            val isUpper = Random.nextBoolean()
            // zFrac ~ [0..1], zVal ~ [-height.. height]
            val zFrac = Random.nextFloat()
            val zVal = (if (isUpper) height else -height) * zFrac

            // Радиус на этом уровне ~ zFrac * baseRadius
            val rLocal = baseRadius * zFrac
            val rho = sqrt(Random.nextFloat()) * rLocal
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            val x = rho * cos(theta)
            val y = rho * sin(theta)

            val px = cx + x.toFloat()
            val py = cy + y.toFloat()
            val pz = cz + zVal

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Ось вращения — z
            Vector3f(0f, 0f, 1f)
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)
        return particles
    }
}

/** 5) Генерация "спиральной ленты" (spiral band) вокруг оси Z, растягиваемой от minRadius до maxRadius по высоте. */
class SpiralBandGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val rBase = config.minRadius
        val rTop = config.maxRadius
        val bandTurns = 3  // число витков

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // "доля" вдоль высоты [0..1]
            val t = Random.nextFloat()
            // радиус изменяется линейно: R(t) = rBase + (rTop - rBase)*t
            val R = rBase + (rTop - rBase) * t

            // угол спирали (например, bandTurns витков при проходе t=[0..1])
            val angle = bandTurns * 2.0 * PI * t + Random.nextDouble(-0.05, 0.05)
            // небольшой случайный разброс для "ленты"

            val x = (R * cos(angle)).toFloat()
            val y = (R * sin(angle)).toFloat()
            val z = (rTop - rBase) * (t - 0.5f) // смещаем по z в диапазоне ~[-..., +...]

            val px = cx + x
            val py = cy + y
            val pz = cz + z

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz.toFloat(), 0f, 0f, 0f, m, sqrt(m)))
        }

        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles, cx, cy, cz, starMass, config.g, config.magicConst
        ) { _ ->
            // Вращение вокруг оси Z
            Vector3f(0f, 0f, 1f)
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)
        return particles
    }
}

class CubeGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Предположим, что minRadius < maxRadius
        val side = config.maxRadius - config.minRadius
        val halfSide = side / 2f

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // Случайная точка внутри куба: [ -halfSide, +halfSide ]
            val rx = Random.nextFloat() * side - halfSide  // от -halfSide до +halfSide
            val ry = Random.nextFloat() * side - halfSide
            val rz = Random.nextFloat() * side - halfSide

            val px = cx + rx
            val py = cy + ry
            val pz = cz + rz

            // Масса
            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val rP = sqrt(m)

            particles.add(Particle(px.toFloat(), py.toFloat(), pz.toFloat(), 0f, 0f, 0f, m, rP))
        }

        // Задаём орбитальные скорости (перенесённый общий код)
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles  = particles,
            centerX    = cx,
            centerY    = cy,
            centerZ    = cz,
            starMass   = starMass,
            g          = config.g,
            magicConst = config.magicConst
        ) { _ ->
            // Для куба можно взять в качестве базового cross-вектора, скажем, ось Z
            Vector3f(0f, 0f, 1f)
        }

        // Добавляем "звезду"
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

class CylinderGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // Радиус основания (minRadius) и "полувысота" (maxRadius)
        val radius = config.minRadius
        val halfHeight = config.maxRadius

        val particles = mutableListOf<Particle>()

        repeat(config.count) {
            // 1) Равномерно внутри круга:
            //    rho ~ [0, radius], но по площади => rho = sqrt(U(0,1)) * radius
            val rho = sqrt(Random.nextFloat()) * radius
            val theta = Random.nextDouble(0.0, 2.0 * PI)

            val x = cx + (rho * cos(theta)).toFloat()
            val y = cy + (rho * sin(theta)).toFloat()

            // 2) Случайно по высоте
            val z = Random.nextFloat() * (2f * halfHeight) - halfHeight  // от -h до +h
            val pz = cz + z

            // Масса
            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            val rP = sqrt(m)

            particles.add(Particle(x, y, pz.toFloat(), 0f, 0f, 0f, m, rP))
        }

        // Задаём орбитальные скорости
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles  = particles,
            centerX    = cx,
            centerY    = cy,
            centerZ    = cz,
            starMass   = starMass,
            g          = config.g,
            magicConst = config.magicConst
        ) { _ ->
            // Для цилиндра, ось которого вдоль z, часто удобно взять ось z
            Vector3f(0f, 0f, 1f)
        }

        // Добавляем "звезду"
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

class RandomOrbitsGenerator : FigureGenerator {

    override val params = mutableMapOf<String, Float>(
        "outerRadius" to 100f,
        "innerRadius" to 0f
    )

    override fun generate(config: SimulationConfig): List<Particle> {
        // Координаты центра
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        val particles = mutableListOf<Particle>()

        // Генерация случайных координат в объёме между rIn и rOut
        repeat(config.count) {
            // Масса частицы
            val m = 1f

            // Создаём частицу (скорости пока 0)
            // displaySize для наглядности сделаем sqrt(m)
            particles.add(
                Particle(
                    x = cx + it/10,
                    y = cy,
                    z = cz,
                    vx = 0f,
                    vy = -1000f,
                    vz = 0f,
                    m = m,
                    r = sqrt(m)
                )
            )
        }

        // Задаём орбитальные скорости
        val starMass = 1_000_000_000f

        // Добавляем "звезду"
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

// --- Генератор «диска» (disk) ---
class DiskGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ
        val particles = mutableListOf<Particle>()

        // 1) Генерация координат
        repeat(config.count) {
            val r = Random.nextDouble(config.minRadius.toDouble(), config.maxRadius.toDouble()).toFloat()
            val theta = Random.nextDouble(0.0, 2 * PI)
            val thetaZ = Random.nextDouble(PI / 2.2, PI / 1.9)

            val x = cx + (r * cos(theta) * sin(thetaZ)).toFloat()
            val y = cy + (r * sin(theta) * sin(thetaZ)).toFloat()
            val z = cz + (r * cos(thetaZ)).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(x, y, z, 0f, 0f, 0f, m, sqrt(m)))
        }

        // 2) Задаём орбитальные скорости (весь дублирующийся код - в едином методе!)
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles = particles,
            centerX = cx,
            centerY = cy,
            centerZ = cz,
            starMass = starMass,
            g = config.g,
            magicConst = config.magicConst
        ) { _ ->  // базовый вектор для cross
            // Для диска берём ось Z
            Vector3f(0f, 0f, 1f)
        }

        // 3) Добавляем звезду
        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

// --- Генератор «случайной плоскости» (circle) ---
class RandomPlaneGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ

        // 1) Случайная нормаль
        val phi = Random.nextDouble(0.0, 2.0 * PI)
        val cosTheta = Random.nextDouble(-1.0, 1.0)
        val sinTheta = sqrt(1.0 - cosTheta * cosTheta)

        val nx = (sinTheta * cos(phi)).toFloat()
        val ny = (sinTheta * sin(phi)).toFloat()
        val nz = cosTheta.toFloat()
        val normal = Vector3f(nx, ny, nz).normalize()

        // 2) Строим ортонормированный базис (u, v) в этой плоскости
        val maybeZ = Vector3f(0f, 0f, 1f)
        val u = Vector3f()
        normal.cross(maybeZ, u)
        if (u.length() < 1e-8f) {
            normal.cross(Vector3f(0f, 1f, 0f), u)
        }
        u.normalize()

        val v = Vector3f()
        normal.cross(u, v)
        v.normalize()

        // 3) Генерация координат
        val particles = mutableListOf<Particle>()
        repeat(config.count) {
            val r = Random.nextDouble(config.minRadius.toDouble(), config.maxRadius.toDouble()).toFloat()
            val angle = Random.nextDouble(0.0, 2.0 * PI)

            val localX = (r * cos(angle)).toFloat()
            val localY = (r * sin(angle)).toFloat()

            val pos = Vector3f(u).mul(localX).add(Vector3f(v).mul(localY))

            val px = cx + pos.x
            val py = cy + pos.y
            val pz = cz + pos.z

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        // 4) Орбитальные скорости
        val starMass = 1_000_000_000f
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles = particles,
            centerX = cx,
            centerY = cy,
            centerZ = cz,
            starMass = starMass,
            g = config.g,
            magicConst = config.magicConst
        ) { _ ->
            // Для «случайной плоскости» берём её нормаль
            normal
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}

// --- Генератор «сферы» (ball) ---
class SphereGenerator : FigureGenerator {
    override val params: MutableMap<String, Float> = mutableMapOf()
    override fun generate(config: SimulationConfig): List<Particle> {
        val cx = config.centerX
        val cy = config.centerY
        val cz = config.centerZ
        val particles = mutableListOf<Particle>()

        val starMass = 1_000_000_000f
        val g = config.g

        // 1) Генерация координат (равномерная в объёме сферы)
        repeat(config.count) {
            val alpha = Random.nextFloat()
            val rMin3 = config.minRadius * config.minRadius * config.minRadius
            val rMax3 = config.maxRadius * config.maxRadius * config.maxRadius
            val R = ((alpha * (rMax3 - rMin3)) + rMin3).pow(1.0 / 3.0).toFloat()

            val phi = Random.nextDouble(0.0, 2.0 * PI)
            val cosTheta = Random.nextDouble(-1.0, 1.0)
            val sinTheta = sqrt(1.0 - cosTheta * cosTheta)

            val xDir = sinTheta * cos(phi)
            val yDir = sinTheta * sin(phi)
            val zDir = cosTheta

            val px = cx + (R * xDir).toFloat()
            val py = cy + (R * yDir).toFloat()
            val pz = cz + (R * zDir).toFloat()

            val m = Random.nextDouble(config.massFrom.toDouble(), config.massUntil.toDouble()).toFloat()
            particles.add(Particle(px, py, pz, 0f, 0f, 0f, m, sqrt(m)))
        }

        // 2) Орбитальные скорости (вынесены во внешнюю функцию)
        OrbitalVelocityUtils.assignOrbitalVelocities(
            particles = particles,
            centerX = cx,
            centerY = cy,
            centerZ = cz,
            starMass = starMass,
            g = g,
            magicConst = config.magicConst
        ) { _ ->
            // Для сферы в качестве базового cross-вектора чаще всего
            // берут что-то типа (0,1,0).
            Vector3f(0f, 1f, 0f)
        }

        val star = Particle(cx, cy, cz, 0f, 0f, 0f, starMass, sqrt(starMass))
        particles.add(star)

        return particles
    }
}