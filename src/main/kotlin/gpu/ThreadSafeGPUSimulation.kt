package gpu

import org.lwjgl.glfw.GLFW.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL46.*
import org.lwjgl.system.MemoryUtil.*
import kotlin.math.*
import kotlin.random.Random
import java.util.concurrent.Executors
import java.util.concurrent.CompletableFuture

/**
 * Thread-safe GPU N-body simulation that runs all OpenGL operations on a dedicated thread
 */
class ThreadSafeGPUSimulation(initialBodies: List<Body3D>) : AutoCloseable {
    
    companion object {
        const val G = 80.0f
        const val DT = 0.005f
        const val SOFTENING = 1.0f
        const val WORK_GROUP_SIZE = 256
    }
    
    // Single thread executor for all GPU operations
    private val gpuExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "GPU-Thread").apply { 
            isDaemon = false
            priority = Thread.MAX_PRIORITY
        }
    }
    
    private var window: Long = 0
    private var count: Int = initialBodies.size
    private var capacityBytes = 0L
    
    // OpenGL resources
    private var ssbo: Int = 0
    private var computeProg: Int = 0
    
    // Uniform locations
    private var uDt: Int = 0
    private var uSoft: Int = 0
    private var uG: Int = 0
    private var uCountC: Int = 0
    
    // Current parameters
    var g = G
    var dt = DT
    var softening = SOFTENING
    
    init {
        // Initialize everything on the GPU thread
        gpuExecutor.submit {
            initializeGL()
            uploadBodies(initialBodies)
        }.get() // Wait for initialization to complete
    }
    
    private fun initializeGL() {
        // Initialize GLFW and create hidden context
        GLFWErrorCallback.createPrint(System.err).set()
        if (!glfwInit()) {
            throw RuntimeException("Failed to initialize GLFW")
        }
        
        glfwDefaultWindowHints()
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE) // Hidden window
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 4)
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 6)
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
        
        window = glfwCreateWindow(1, 1, "GPU Context", 0, 0)
        if (window == 0L) {
            glfwTerminate()
            throw RuntimeException("Failed to create GLFW window")
        }
        
        // CRITICAL: Make context current and create capabilities
        glfwMakeContextCurrent(window)
        GL.createCapabilities() // <-- This is the ONLY place this should be called
        
        // Initialize GPU resources
        ssbo = glGenBuffers()
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
        glBufferData(GL_SHADER_STORAGE_BUFFER, 0L, GL_DYNAMIC_DRAW)
        glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0)
        
        computeProg = buildComputeProgram()
        
        uDt = glGetUniformLocation(computeProg, "uDt")
        uSoft = glGetUniformLocation(computeProg, "uSoftening")
        uG = glGetUniformLocation(computeProg, "uG")
        uCountC = glGetUniformLocation(computeProg, "uCount")
        
        ensureCapacity(count)
    }
    
    private fun buildComputeProgram(): Int {
        val src = """
    #version 460 core
    layout(local_size_x = ${WORK_GROUP_SIZE}) in;

    struct Body { vec4 posMass; vec4 velPad; };

    layout(std430, binding = 0) buffer BodyBuffer { Body bodies[]; };

    uniform float uDt;
    uniform float uSoftening;
    uniform float uG;
    uniform uint  uCount;

    shared vec4 tilePosMass[${WORK_GROUP_SIZE}];

    void main() {
        uint id = gl_GlobalInvocationID.x;
        uint localIndex = gl_LocalInvocationID.x;
        
        // Load own body data (or zero if out of bounds)
        vec3 position;
        float mass; 
        vec3 velocity;
        vec3 acc = vec3(0.0);
        
        if (id < uCount) {
            Body self = bodies[id];
            position = self.posMass.xyz;
            mass = self.posMass.w;
            velocity = self.velPad.xyz;
        } else {
            // Threads beyond body count still need to participate in barriers
            position = vec3(0.0);
            mass = 0.0;
            velocity = vec3(0.0);
        }

        for (uint tile = 0u; tile < uCount; tile += ${WORK_GROUP_SIZE}u) {
            uint idx = tile + localIndex;
            
            // ALL threads must execute this, even if out of bounds
            tilePosMass[int(localIndex)] = (idx < uCount) ? bodies[idx].posMass : vec4(0.0);
            barrier();  // ALL threads hit this barrier

            if (id < uCount) {  // Only valid bodies calculate forces
                uint tileSize = min(uCount - tile, uint(${WORK_GROUP_SIZE}));
                for (uint j = 0u; j < tileSize; ++j) {
                    uint otherIndex = tile + j;
                    if (otherIndex == id) continue;
                    vec4 other = tilePosMass[int(j)];
                    vec3 d = other.xyz - position;
                    float dist2 = dot(d,d) + uSoftening;
                    float invR = inversesqrt(dist2);
                    float invR3 = invR * invR * invR;
                    acc += (uG * other.w) * d * invR3;
                }
            }
            barrier();  // ALL threads hit this barrier too
        }

        // Only update valid bodies
        if (id < uCount) {
            velocity += acc * uDt;
            position += velocity * uDt;
            bodies[id].posMass = vec4(position, mass);
            bodies[id].velPad = vec4(velocity, 0.0);
        }
    }
    """.trimIndent()

        val shader = glCreateShader(GL_COMPUTE_SHADER)
        glShaderSource(shader, src)
        glCompileShader(shader)
        if (glGetShaderi(shader, GL_COMPILE_STATUS) != GL_TRUE) {
            val log = glGetShaderInfoLog(shader)
            glDeleteShader(shader)
            error("Compute shader compile error:\n$log")
        }
        val prog = glCreateProgram()
        glAttachShader(prog, shader)
        glLinkProgram(prog)
        if (glGetProgrami(prog, GL_LINK_STATUS) != GL_TRUE) {
            val log = glGetProgramInfoLog(prog)
            glDeleteProgram(prog)
            glDeleteShader(shader)
            error("Compute program link error:\n$log")
        }
        glDetachShader(prog, shader)
        glDeleteShader(shader)
        return prog
    }
    
    private fun ensureCapacity(n: Int) {
        val floatsPerBody = 8
        val bytes = n.toLong() * floatsPerBody * java.lang.Float.BYTES
        if (bytes > capacityBytes) {
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
            glBufferData(GL_SHADER_STORAGE_BUFFER, bytes, GL_DYNAMIC_DRAW)
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0)
            capacityBytes = bytes
        }
    }
    
    private fun uploadBodies(bodies: List<Body3D>) {
        val n = bodies.size
        if (n == 0) return
        val floatsPerBody = 8
        val buf = memAllocFloat(n * floatsPerBody)
        try {
            for (b in bodies) {
                buf.put(b.x).put(b.y).put(b.z).put(b.m)
                buf.put(b.vx).put(b.vy).put(b.vz).put(0f)
            }
            buf.flip()
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
            glBufferSubData(GL_SHADER_STORAGE_BUFFER, 0, buf)
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0)
        } finally { 
            memFree(buf) 
        }
    }
    
    /**
     * Run one simulation step (async)
     */
    fun stepAsync(): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            if (count <= 0) return@runAsync
            
            glfwMakeContextCurrent(window)
            
            glUseProgram(computeProg)
            glUniform1f(uDt, dt)
            glUniform1f(uSoft, softening * softening)
            glUniform1f(uG, g)
            glUniform1ui(uCountC, count)
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, ssbo)
            
            val groups = (count + WORK_GROUP_SIZE - 1) / WORK_GROUP_SIZE
            glDispatchCompute(groups, 1, 1)
            
            // [NEW FIX] Removed glFinish(). The barrier is sufficient and safer.
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT or GL_BUFFER_UPDATE_BARRIER_BIT)
            // glFinish() // <--- REMOVED. This was likely the problem.
            
            glUseProgram(0)
        }, gpuExecutor)
    }
    
    /**
     * Run one simulation step (blocking)
     */
    fun step() {
        stepAsync().get()
    }
    
    /**
     * Update bodies (async)
     */
    fun updateBodiesAsync(newBodies: List<Body3D>): CompletableFuture<Void> {
        return CompletableFuture.runAsync({
            // Ensure context is current
            glfwMakeContextCurrent(window)
            
            count = newBodies.size
            ensureCapacity(count)
            uploadBodies(newBodies)
        }, gpuExecutor)
    }
    
    /**
     * Update bodies (blocking)
     */
    fun updateBodies(newBodies: List<Body3D>) {
        updateBodiesAsync(newBodies).get()
    }
    
    /**
     * Get current body positions (async)
     */
    fun getBodiesAsync(): CompletableFuture<List<Body3D>> {
        return CompletableFuture.supplyAsync({
            if (count == 0) return@supplyAsync emptyList()
            
            // Ensure context is current
            glfwMakeContextCurrent(window)
            
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)
            
            val floatsPerBody = 8
            val totalFloats = count * floatsPerBody
            val buf = memAllocFloat(totalFloats)
            
            try {
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
                glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, buf)
                
                val bodies = mutableListOf<Body3D>()
                for (i in 0 until count) {
                    val base = i * floatsPerBody
                    bodies.add(Body3D(
                        x = buf.get(base + 0),
                        y = buf.get(base + 1),
                        z = buf.get(base + 2),
                        m = buf.get(base + 3),
                        vx = buf.get(base + 4),
                        vy = buf.get(base + 5),
                        vz = buf.get(base + 6)
                    ))
                }
                bodies
            } finally {
                memFree(buf)
            }
        }, gpuExecutor)
    }
    
    /**
     * Get current body positions (blocking)
     */
    fun getBodies(): List<Body3D> {
        return getBodiesAsync().get()
    }
    
    /**
     * Get position array (async)
     */
    fun getPositionsArrayAsync(): CompletableFuture<FloatArray> {
        return CompletableFuture.supplyAsync({
            if (count == 0) return@supplyAsync floatArrayOf()
            
            // Ensure context is current
            glfwMakeContextCurrent(window)
            
            // Wait for compute shader to finish writing
            glMemoryBarrier(GL_SHADER_STORAGE_BARRIER_BIT)
            
            val floatsPerBody = 8
            val totalFloats = count * floatsPerBody
            val buf = memAllocFloat(totalFloats)
            
            try {
                glBindBuffer(GL_SHADER_STORAGE_BUFFER, ssbo)
                var error = glGetError()
                if (error != GL_NO_ERROR) {
                    println("OpenGL error when binding buffer: $error")
                    return@supplyAsync floatArrayOf()
                }
                
                glGetBufferSubData(GL_SHADER_STORAGE_BUFFER, 0L, buf)
                
                // [NEW FIX] Check for errors AFTER the read, which is where it fails
                error = glGetError()
                if (error != GL_NO_ERROR) {
                    println("OpenGL error during glGetBufferSubData: $error")
                    return@supplyAsync floatArrayOf()
                }
                
                val positions = FloatArray(count * 3)
                for (i in 0 until count) {
                    val base = i * floatsPerBody
                    positions[i * 3] = buf.get(base + 0)
                    positions[i * 3 + 1] = buf.get(base + 1)
                    positions[i * 3 + 2] = buf.get(base + 2)
                }
                positions
            } finally {
                memFree(buf)
            }
        }, gpuExecutor)
    }
    
    /**
     * Get position array (blocking)
     */
    fun getPositionsArray(): FloatArray {
        return getPositionsArrayAsync().get()
    }
    
    /**
     * Get center of mass
     */
    fun getCenterOfMass(): FloatArray {
        if (count == 0) return floatArrayOf(0f, 0f, 0f)
        
        val bodies = getBodies()
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        var sm = 0.0
        
        for (b in bodies) {
            val m = b.m.toDouble()
            sx += b.x * m
            sy += b.y * m
            sz += b.z * m
            sm += m
        }
        
        if (sm == 0.0) return floatArrayOf(0f, 0f, 0f)
        return floatArrayOf((sx/sm).toFloat(), (sy/sm).toFloat(), (sz/sm).toFloat())
    }
    
    override fun close() {
        gpuExecutor.submit {
            if (window != 0L) {
                // Make this window's context current before deleting!
                glfwMakeContextCurrent(window) 
                
                // Now it's safe to delete these objects
                glDeleteProgram(computeProg)
                glDeleteBuffers(ssbo)
                glfwDestroyWindow(window)
            }
        }.get()
        gpuExecutor.shutdown()
    }
}