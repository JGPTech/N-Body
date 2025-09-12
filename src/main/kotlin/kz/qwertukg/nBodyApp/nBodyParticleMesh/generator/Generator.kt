package kz.qwertukg.nBodyApp.nBodyParticleMesh.generator

import kz.qwertukg.nBodyApp.nBodyParticleMesh.Particle
import kz.qwertukg.nBodyApp.nBodyParticleMesh.SimulationConfig

// --- Основной класс Generator, использующий «стратегию» ---
class Generator(val config: SimulationConfig) {
    val figureGenerators: MutableMap<String, FigureGenerator> = mutableMapOf()

    fun registerFigure(name: String, generator: FigureGenerator) {
        figureGenerators[name.lowercase()] = generator
    }

    fun generate(figureName: String): List<Particle> {
        val gen = figureGenerators[figureName.lowercase()]
            ?: throw NotImplementedError("Нет генератора для фигуры '$figureName'")

        return gen.generate(config)
    }
}