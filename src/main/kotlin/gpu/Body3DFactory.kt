package gpu

import kotlin.math.*
import kotlin.random.Random

// Re-export Body from gpu package for compatibility
typealias Body3D = Body

/**
 * Factory for creating 3D body distributions using existing GPU functions
 */
object Body3DFactory {
    
    /**
     * Generate a spherical distribution of bodies
     */
    fun generateSphere(n: Int, cx: Float, cy: Float, cz: Float, radius: Float): List<Body3D> {
        fun cross(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float): Triple<Float, Float, Float> =
            Triple(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)

        fun norm(x: Float, y: Float, z: Float): Triple<Float, Float, Float> {
            val len = sqrt(x * x + y * y + z * z).coerceAtLeast(1e-8f)
            return Triple(x / len, y / len, z / len)
        }

        val rnd = Random(System.currentTimeMillis())
        val out = ArrayList<Body3D>(n)
        
        // Central massive body
        out += Body3D(cx, cy, cz, 0f, 0f, 0f, 50000f)

        // Satellites
        for (i in 1 until n) {
            val r = radius * cbrt(rnd.nextFloat().toDouble()).toFloat()
            val z = rnd.nextFloat() * 2f - 1f
            val phi = rnd.nextFloat() * (2f * Math.PI).toFloat()
            val s = sqrt(max(0f, 1f - z * z))
            val rx = s * cos(phi)
            val ry = s * sin(phi)
            val rz = z
            
            val x = cx + r * rx
            val y = cy + r * ry
            val zPos = cz + r * rz
            
            // Calculate tangential velocity
            val speed = sqrt(80f * 50000f / r) // sqrt(G * M / r)
            val az = 0f
            val ax = if (abs(rz) > 0.99f) 1f else 0f
            val ay = if (abs(rz) > 0.99f) 0f else 1f
            val (tx0, ty0, tz0) = cross(rx, ry, rz, ax, ay, az)
            val (tx, ty, tz) = norm(tx0, ty0, tz0)
            
            val vx = tx * speed
            val vy = ty * speed
            val vz = tz * speed
            
            out += Body3D(x, y, zPos, vx, vy, vz, 1.0f)
        }
        
        return out
    }
    
    /**
     * Generate a 3D galaxy disk with thickness
     */
    fun generateGalaxyDisk3D(
        n: Int,
        cx: Float, cy: Float, cz: Float,
        vx: Float = 0f, vy: Float = 0f, vz: Float = 0f,
        radius: Float,
        centralMass: Float = 50000f,
        thickness: Float = 20f
    ): List<Body3D> {
        val bodies = mutableListOf<Body3D>()
        val rnd = Random(System.currentTimeMillis())
        
        // Central body
        bodies += Body3D(cx, cy, cz, vx, vy, vz, centralMass)
        
        // Disk bodies
        val satelliteMass = centralMass * 0.1f / n
        
        for (i in 1 until n) {
            val r = radius * sqrt(rnd.nextFloat())
            val theta = rnd.nextFloat() * 2f * PI.toFloat()
            
            // Position with thickness variation
            val x = cx + r * cos(theta)
            val y = cy + r * sin(theta)
            val z = cz + (rnd.nextFloat() - 0.5f) * thickness
            
            // Circular velocity
            val vCirc = sqrt(80f * centralMass / r)
            
            // Tangential direction in xy plane
            val bvx = -vCirc * sin(theta) + vx
            val bvy = vCirc * cos(theta) + vy
            val bvz = vz
            
            bodies += Body3D(x, y, z, bvx, bvy, bvz, satelliteMass)
        }
        
        return bodies
    }
    
    /**
     * Generate colliding galaxies in 3D
     */
    fun generateCollision(totalBodies: Int): List<Body3D> {
        val bodies1 = generateGalaxyDisk3D(
            n = (totalBodies * 0.6).toInt(),
            cx = 800f, cy = 720f, cz = 500f,
            vx = 30f, vy = 0f, vz = -5f,
            radius = 300f,
            centralMass = 40000f,
            thickness = 30f
        )
        
        val bodies2 = generateGalaxyDisk3D(
            n = (totalBodies * 0.4).toInt(),
            cx = 2600f, cy = 720f, cz = 500f,
            vx = -30f, vy = 0f, vz = 5f,
            radius = 200f,
            centralMass = 20000f,
            thickness = 20f
        )
        
        return bodies1 + bodies2
    }
}