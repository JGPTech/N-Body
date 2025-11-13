package server

import PhysicsEngine
import Body
import BodyFactory
import Config
import gpu.ThreadSafeGPUSimulation
import gpu.Body3D
import gpu.Body3DFactory
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Duration
import kotlin.math.min
import kotlin.random.Random
import java.io.File

/**
 * Fully integrated simulation server with working GPU support
 */
class NBodySimulationServer {
    
    // Simulation engines - one will be active at a time
    private var cpuEngine: PhysicsEngine? = null
    private var gpuSimulation: ThreadSafeGPUSimulation? = null
    private var currentMode = "cpu" // "cpu" or "gpu"
    private var isPaused = true
    private var simulationTime = 0.0
    private var speedMultiplier = 1.0
    
    // Performance tracking
    private var lastStepTime = System.currentTimeMillis()
    private var stepsPerSecond = 0
    private var stepCounter = 0
    private var lastSecond = System.currentTimeMillis()
    
    // WebSocket sessions
    private val sessions = mutableSetOf<WebSocketSession>()
    
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }
    
    fun start() {
        // Initialize with default scenario
        initializeDefaultScenario()
        
        embeddedServer(Netty, port = 8080) {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(60)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            
            routing {
                // Serve static files from resources/static directory
                staticFiles("/", File("src/main/resources/static"))
                
                webSocket("/simulation") {
                    sessions.add(this)
                    println("Client connected. Total clients: ${sessions.size}")
                    
                    try {
                        // Send initial state
                        sendState(this)
                        sendPositions(this)
                        
                        // Start simulation loop for this client
                        val simulationJob = launch {
                            while (isActive) {
                                if (!isPaused) {
                                    stepSimulation()
                                    // Broadcast positions to all connected clients
                                    sessions.forEach { session ->
                                        if (session.isActive) {
                                            sendPositions(session)
                                        }
                                    }
                                }
                                // Target 60 FPS
                                delay((16 / speedMultiplier).toLong())
                            }
                        }
                        
                        // Handle incoming messages
                        for (frame in incoming) {
                            when (frame) {
                                is Frame.Text -> {
                                    val text = frame.readText()
                                    handleMessage(text, this)
                                }
                                is Frame.Close -> break
                                else -> {}
                            }
                        }
                        
                        simulationJob.cancel()
                    } catch (e: Exception) {
                        println("WebSocket error: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        sessions.remove(this)
                        println("Client disconnected. Remaining clients: ${sessions.size}")
                    }
                }
            }
        }.start(wait = true)
    }
    
    /**
     * Clean up resources when shutting down
     */
    fun shutdown() {
        gpuSimulation?.close()
        gpuSimulation = null
    }
    
    private fun initializeDefaultScenario() {
        currentMode = "cpu"
        
        // Create a default 2D galaxy collision for CPU mode
        val bodies1 = BodyFactory.makeGalaxyDisk(
            nTotal = 7000,
            x = Config.WIDTH_PX * 0.35,
            y = Config.HEIGHT_PX * 0.5,
            vx = 25.0,
            vy = 5.0,
            r = 250.0,
            centralMass = 30000.0,
            totalSatelliteMass = 3000.0,
            epsM2 = 0.03,
            speedJitter = 0.01
        )
        
        val bodies2 = BodyFactory.makeGalaxyDisk(
            nTotal = 3000,
            x = Config.WIDTH_PX * 0.65,
            y = Config.HEIGHT_PX * 0.5,
            vx = -25.0,
            vy = -5.0,
            r = 150.0,
            centralMass = 10000.0,
            totalSatelliteMass = 1000.0,
            epsM2 = 0.02,
            speedJitter = 0.01,
            clockwise = false
        )
        
        cpuEngine = PhysicsEngine((bodies1 + bodies2).toMutableList())
        isPaused = false
    }
    
    private suspend fun handleMessage(text: String, session: WebSocketSession) {
        try {
            val jsonObject = json.parseToJsonElement(text).jsonObject
            val type = jsonObject["type"]?.jsonPrimitive?.content ?: return
            
            println("Received message type: $type")
            
            when (type) {
                "init" -> {
                    val mode = jsonObject["mode"]?.jsonPrimitive?.content ?: "cpu"
                    val bodyCount = jsonObject["bodyCount"]?.jsonPrimitive?.int ?: 10000
                    val scenario = jsonObject["scenario"]?.jsonPrimitive?.content ?: "galaxy_collision"
                    
                    println("Initializing simulation: mode=$mode, bodyCount=$bodyCount, scenario=$scenario")
                    initializeSimulation(mode, bodyCount, scenario)
                    
                    // Send updated state to all clients
                    sessions.forEach { 
                        sendState(it)
                        sendPositions(it)
                    }
                }
                
                "control" -> {
                    val command = jsonObject["command"]?.jsonPrimitive?.content ?: return
                    
                    when (command) {
                        "play" -> {
                            isPaused = false
                            println("Simulation playing")
                        }
                        "pause" -> {
                            isPaused = true
                            println("Simulation paused")
                        }
                        "step" -> {
                            stepSimulation()
                            sessions.forEach { sendPositions(it) }
                        }
                        "reset" -> {
                            initializeDefaultScenario()
                            sessions.forEach { 
                                sendState(it)
                                sendPositions(it)
                            }
                        }
                        "speed" -> {
                            speedMultiplier = jsonObject["speed"]?.jsonPrimitive?.double ?: 1.0
                        }
                    }
                    
                    sessions.forEach { sendState(it) }
                }
                
                "params" -> {
                    // Update parameters based on current mode
                    when (currentMode) {
                        "cpu" -> {
                            jsonObject["theta"]?.jsonPrimitive?.double?.let { Config.theta = it }
                            jsonObject["g"]?.jsonPrimitive?.double?.let { Config.G = it }
                            jsonObject["dt"]?.jsonPrimitive?.double?.let { Config.DT = it }
                            jsonObject["softening"]?.jsonPrimitive?.double?.let { Config.SOFTENING = it }
                        }
                        "gpu" -> {
                            gpuSimulation?.let { gpu ->
                                jsonObject["g"]?.jsonPrimitive?.double?.let { 
                                    gpu.g = it.toFloat()
                                }
                                jsonObject["dt"]?.jsonPrimitive?.double?.let { 
                                    gpu.dt = it.toFloat()
                                }
                                jsonObject["softening"]?.jsonPrimitive?.double?.let { 
                                    gpu.softening = it.toFloat()
                                }
                            }
                        }
                    }
                    
                    sessions.forEach { sendState(it) }
                }
                
                "addBodies" -> {
                    val x = jsonObject["x"]?.jsonPrimitive?.double ?: return
                    val y = jsonObject["y"]?.jsonPrimitive?.double ?: return
                    val z = jsonObject["z"]?.jsonPrimitive?.double ?: 0.0
                    val vx = jsonObject["vx"]?.jsonPrimitive?.double ?: 0.0
                    val vy = jsonObject["vy"]?.jsonPrimitive?.double ?: 0.0
                    val vz = jsonObject["vz"]?.jsonPrimitive?.double ?: 0.0
                    val count = jsonObject["count"]?.jsonPrimitive?.int ?: 100
                    val radius = jsonObject["radius"]?.jsonPrimitive?.double ?: 50.0
                    
                    addBodiesToSimulation(x, y, z, vx, vy, vz, count, radius)
                    
                    sessions.forEach {
                        sendState(it)
                        sendPositions(it)
                    }
                }
            }
        } catch (e: Exception) {
            println("Error handling message: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun initializeSimulation(mode: String, bodyCount: Int, scenario: String) {
        try {
            // Clean up previous simulation
            if (currentMode == "gpu" && mode == "cpu") {
                println("Switching from GPU to CPU - cleaning up GPU resources")
                gpuSimulation?.close()
                gpuSimulation = null
            }
            
            currentMode = mode
            
            when (mode) {
                "cpu" -> {
                    println("Initializing CPU simulation")
                    gpuSimulation?.close()
                    gpuSimulation = null
                    
                    val bodies = when (scenario) {
                        "galaxy_collision" -> generateGalaxyCollision2D(bodyCount)
                        "sphere" -> generateSphere2D(bodyCount)
                        "disk" -> generateRotatingDisk2D(bodyCount)
                        "custom" -> generateCustomPattern2D(bodyCount)
                        else -> generateGalaxyCollision2D(bodyCount)
                    }
                    
                    cpuEngine = PhysicsEngine(bodies.toMutableList())
                    println("CPU simulation initialized with ${bodies.size} bodies")
                }
                
                "gpu" -> {
                    println("Initializing GPU simulation")
                    cpuEngine = null
                    
                    val bodies3d = when (scenario) {
                        "galaxy_collision" -> Body3DFactory.generateCollision(bodyCount)
                        "sphere" -> Body3DFactory.generateSphere(
                            bodyCount,
                            Config.WIDTH_PX.toFloat() / 2f,
                            Config.HEIGHT_PX.toFloat() / 2f,
                            500f,
                            min(Config.WIDTH_PX, Config.HEIGHT_PX).toFloat() * 0.3f
                        )
                        "disk" -> Body3DFactory.generateGalaxyDisk3D(
                            bodyCount,
                            Config.WIDTH_PX.toFloat() / 2f,
                            Config.HEIGHT_PX.toFloat() / 2f,
                            500f,
                            radius = min(Config.WIDTH_PX, Config.HEIGHT_PX).toFloat() * 0.35f
                        )
                        "custom" -> generateCustomPattern3D(bodyCount)
                        else -> Body3DFactory.generateCollision(bodyCount)
                    }
                    
                    try {
                        gpuSimulation?.close()
                        gpuSimulation = ThreadSafeGPUSimulation(bodies3d)
                        println("GPU simulation initialized successfully with ${bodies3d.size} bodies in 3D")
                    } catch (e: Exception) {
                        println("Failed to initialize GPU simulation: ${e.message}")
                        e.printStackTrace()
                        println("Falling back to CPU mode")
                        currentMode = "cpu"
                        initializeSimulation("cpu", bodyCount, scenario)
                        return
                    }
                }
            }
            
            isPaused = false
            simulationTime = 0.0
        } catch (e: Exception) {
            println("Error in initializeSimulation: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun stepSimulation() {
        try {
            when (currentMode) {
                "cpu" -> {
                    cpuEngine?.step()
                }
                "gpu" -> {
                    gpuSimulation?.step()
                }
            }
            
            simulationTime += Config.DT * speedMultiplier
            
            // Track performance
            stepCounter++
            val now = System.currentTimeMillis()
            if (now - lastSecond >= 1000) {
                stepsPerSecond = stepCounter
                stepCounter = 0
                lastSecond = now
            }
        } catch (e: Exception) {
            println("Error in stepSimulation: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private suspend fun sendPositions(session: WebSocketSession) {
        try {
            val buffer = when (currentMode) {
                "cpu" -> {
                    val bodies = cpuEngine?.getBodies() ?: return
                    val buf = ByteBuffer.allocate(4 + bodies.size * 12)
                    buf.order(ByteOrder.LITTLE_ENDIAN)
                    buf.putInt(bodies.size)
                    
                    bodies.forEach { body ->
                        buf.putFloat(body.x.toFloat())
                        buf.putFloat(body.y.toFloat())
                        buf.putFloat(0f) // Z = 0 for 2D
                    }
                    buf
                }
                
                "gpu" -> {
                    val positions = gpuSimulation?.getPositionsArray()
                    if (positions == null || positions.isEmpty()) {
                        println("GPU positions are null or empty")
                        return
                    }
                    
                    val count = positions.size / 3
                    val buf = ByteBuffer.allocate(4 + positions.size * 4)
                    buf.order(ByteOrder.LITTLE_ENDIAN)
                    buf.putInt(count)
                    
                    positions.forEach { pos ->
                        buf.putFloat(pos)
                    }
                    buf
                }
                
                else -> return
            }
            
            session.send(Frame.Binary(true, buffer.array()))
        } catch (e: Exception) {
            println("Failed to send positions: ${e.message}")
        }
    }
    
    private suspend fun sendState(session: WebSocketSession) {
        try {
            val bodyCount = when (currentMode) {
                "cpu" -> cpuEngine?.getBodies()?.size ?: 0
                "gpu" -> {
                    // For GPU, we can infer body count from the simulation object
                    val bodies = gpuSimulation?.getBodies()
                    bodies?.size ?: 0
                }
                else -> 0
            }
            
            val state = buildJsonObject {
                put("mode", currentMode)
                put("bodyCount", bodyCount)
                put("fps", 60)
                put("simTime", simulationTime)
                put("paused", isPaused)
                put("stepsPerSecond", stepsPerSecond)
                put("is3D", currentMode == "gpu")
                putJsonObject("params") {
                    when (currentMode) {
                        "cpu" -> {
                            put("theta", Config.theta)
                            put("g", Config.G)
                            put("dt", Config.DT)
                            put("softening", Config.SOFTENING)
                        }
                        "gpu" -> {
                            put("theta", 0.5) // Not used in GPU mode
                            put("g", gpuSimulation?.g ?: 80.0)
                            put("dt", gpuSimulation?.dt ?: 0.005)
                            put("softening", gpuSimulation?.softening ?: 1.0)
                        }
                    }
                }
            }
            
            session.send(Frame.Text(state.toString()))
        } catch (e: Exception) {
            println("Failed to send state: ${e.message}")
        }
    }
    
    private fun addBodiesToSimulation(x: Double, y: Double, z: Double, vx: Double, vy: Double, vz: Double, count: Int, radius: Double) {
        when (currentMode) {
            "cpu" -> {
                val newBodies = BodyFactory.makeGalaxyDisk(
                    nTotal = count,
                    x = x,
                    y = y,
                    vx = vx,
                    vy = vy,
                    r = radius,
                    centralMass = if (count > 100) 5000.0 else 100.0,
                    totalSatelliteMass = count * 0.5
                )
                
                cpuEngine?.let { engine ->
                    val combined = (engine.getBodies() + newBodies).toMutableList()
                    engine.resetBodies(combined)
                }
            }
            
            "gpu" -> {
                val newBodies = Body3DFactory.generateGalaxyDisk3D(
                    n = count,
                    cx = x.toFloat(),
                    cy = y.toFloat(),
                    cz = z.toFloat(),
                    vx = vx.toFloat(),
                    vy = vy.toFloat(),
                    vz = vz.toFloat(),
                    radius = radius.toFloat(),
                    centralMass = if (count > 100) 5000f else 100f
                )
                
                gpuSimulation?.let { gpu ->
                    val combined = gpu.getBodies() + newBodies
                    gpu.updateBodies(combined)
                }
            }
        }
    }
    
    // 2D scenario generators for CPU mode
    private fun generateGalaxyCollision2D(totalBodies: Int): List<Body> {
        val ratio = 0.7
        val bodies1 = BodyFactory.makeGalaxyDisk(
            nTotal = (totalBodies * ratio).toInt(),
            x = Config.WIDTH_PX * 0.3,
            y = Config.HEIGHT_PX * 0.5,
            vx = 30.0,
            vy = 0.0,
            r = min(Config.WIDTH_PX, Config.HEIGHT_PX) * 0.2,
            centralMass = 40000.0,
            totalSatelliteMass = 4000.0,
            epsM2 = 0.04,
            speedJitter = 0.01
        )
        
        val bodies2 = BodyFactory.makeGalaxyDisk(
            nTotal = (totalBodies * (1 - ratio)).toInt(),
            x = Config.WIDTH_PX * 0.7,
            y = Config.HEIGHT_PX * 0.5,
            vx = -30.0,
            vy = 0.0,
            r = min(Config.WIDTH_PX, Config.HEIGHT_PX) * 0.15,
            centralMass = 20000.0,
            totalSatelliteMass = 2000.0,
            epsM2 = 0.03,
            speedJitter = 0.01,
            clockwise = false
        )
        
        return bodies1 + bodies2
    }
    
    private fun generateSphere2D(count: Int): List<Body> {
        val bodies = mutableListOf<Body>()
        val cx = Config.WIDTH_PX * 0.5
        val cy = Config.HEIGHT_PX * 0.5
        val rMax = min(Config.WIDTH_PX, Config.HEIGHT_PX) * 0.3
        val rng = Random(System.currentTimeMillis())
        
        bodies.add(Body(cx, cy, 0.0, 0.0, 50000.0))
        
        repeat(count - 1) {
            val r = rMax * Math.sqrt(rng.nextDouble())
            val theta = rng.nextDouble() * 2 * Math.PI
            
            val x = cx + r * Math.cos(theta)
            val y = cy + r * Math.sin(theta)
            
            val v = Math.sqrt(Config.G * 50000.0 / r)
            val vx = -v * Math.sin(theta)
            val vy = v * Math.cos(theta)
            
            bodies.add(Body(x, y, vx, vy, 1.0))
        }
        
        return bodies
    }
    
    private fun generateRotatingDisk2D(count: Int): List<Body> {
        return BodyFactory.makeGalaxyDisk(
            nTotal = count,
            x = Config.WIDTH_PX * 0.5,
            y = Config.HEIGHT_PX * 0.5,
            r = min(Config.WIDTH_PX, Config.HEIGHT_PX) * 0.35,
            vx = 0.0,
            vy = 0.0,
            centralMass = 50000.0,
            totalSatelliteMass = count * 1.0,
            epsM2 = 0.05,
            speedJitter = 0.005
        )
    }
    
    private fun generateCustomPattern2D(count: Int): List<Body> {
        val bodies = mutableListOf<Body>()
        val clustersCount = 5
        val bodiesPerCluster = count / clustersCount
        
        repeat(clustersCount) { i ->
            val angle = i * 2 * Math.PI / clustersCount
            val clusterR = min(Config.WIDTH_PX, Config.HEIGHT_PX) * 0.25
            val cx = Config.WIDTH_PX * 0.5 + clusterR * Math.cos(angle)
            val cy = Config.HEIGHT_PX * 0.5 + clusterR * Math.sin(angle)
            
            val vx = -20.0 * Math.sin(angle)
            val vy = 20.0 * Math.cos(angle)
            
            bodies.addAll(
                BodyFactory.makeGalaxyDisk(
                    nTotal = bodiesPerCluster,
                    x = cx,
                    y = cy,
                    vx = vx,
                    vy = vy,
                    r = 80.0,
                    centralMass = 5000.0,
                    totalSatelliteMass = bodiesPerCluster * 0.5
                )
            )
        }
        
        return bodies
    }
    
    // 3D scenario generators for GPU mode
    private fun generateCustomPattern3D(count: Int): List<Body3D> {
        val bodies = mutableListOf<Body3D>()
        val clustersCount = 5
        val bodiesPerCluster = count / clustersCount
        
        repeat(clustersCount) { i ->
            val angle = i * 2 * Math.PI / clustersCount
            val clusterR = min(Config.WIDTH_PX, Config.HEIGHT_PX) * 0.25
            val cx = (Config.WIDTH_PX * 0.5 + clusterR * Math.cos(angle)).toFloat()
            val cy = (Config.HEIGHT_PX * 0.5 + clusterR * Math.sin(angle)).toFloat()
            val cz = 500f + (i - 2) * 100f
            
            val vx = (-20.0 * Math.sin(angle)).toFloat()
            val vy = (20.0 * Math.cos(angle)).toFloat()
            val vz = 0f
            
            bodies.addAll(
                Body3DFactory.generateGalaxyDisk3D(
                    n = bodiesPerCluster,
                    cx = cx,
                    cy = cy,
                    cz = cz,
                    vx = vx,
                    vy = vy,
                    vz = vz,
                    radius = 80f,
                    centralMass = 5000f,
                    thickness = 30f
                )
            )
        }
        
        return bodies
    }
}

fun main() {
    println("Starting N-Body Simulation Server on http://localhost:8080")
    println("Open your browser and navigate to http://localhost:8080")
    println("GPU mode will render in full 3D with proper physics!")
    
    val server = NBodySimulationServer()
    
    // Add shutdown hook to clean up GPU resources
    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nShutting down server...")
        server.shutdown()
    })
    
    server.start()
}