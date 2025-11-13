# N-Body Gravity Simulator
**Interactive Physics Visualization | Barnes–Hut Algorithm | Kotlin + Web**

Real-time gravitational dynamics simulation featuring the Barnes–Hut algorithm, implemented in Kotlin with dual rendering modes: native desktop (Swing/LWJGL) and modern web interface (Three.js + WebSockets).

Built on [Daniil Rakhmatulin's](https://github.com/qwertukg) original Barnes–Hut implementation, this fork adds a complete web-based visualization system with real-time collaborative capabilities.

---

## ✨ Features

### 🔬 Physics Engine
- **Barnes–Hut Algorithm** - O(N log N) gravitational tree code
- **Adaptive θ** (0.2–1.6) for dynamic accuracy/performance tuning
- **Multi-threaded computation** via `kotlinx.coroutines`
- **Leapfrog integration** (symmetric kick–drift–kick)
- **Force softening** (ε = 1.0) for numerical stability
- **Realistic galaxy collisions** with proper enclosed-mass velocity profiles

### 🎨 Dual Rendering Modes
- **Native Desktop** - Fullscreen borderless Swing renderer with LWJGL
- **Web Interface** - Three.js 3D visualization with WebSocket streaming
  - 60 FPS real-time particle rendering
  - Doppler shift color coding
  - Interactive camera controls (OrbitControls)
  - Responsive neumorphic UI

### 🎮 Interactive Controls
- **Mouse-based galaxy creation** - Drag to set initial velocity
- **Real-time parameter adjustment** - G, Δt, θ, body count, radius
- **Multiple scenarios** - Collision, sphere, disk, custom patterns
- **Camera system** - Pan, zoom, orbit, follow center of mass
- **Play/pause/step** simulation controls

---

## 🏗️ Architecture

```
src/main/
├── kotlin/
│   ├── BarnesHutAlg.kt          # Core Barnes–Hut tree implementation
│   ├── BodyFactory.kt            # Galaxy & body generation
│   ├── Config.kt                 # Simulation parameters
│   ├── Main.kt                   # Native desktop entry point
│   ├── NBodyPanel.kt             # Swing renderer
│   ├── server/
│   │   └── NBodySimulationServer.kt  # WebSocket server
│   └── gpu/
│       ├── GPU.kt                # Direct GPU compute (3D exact N²)
│       ├── Body3DFactory.kt      # 3D body generation
│       └── ThreadSafeGPUSimulation.kt
│
└── resources/static/
    ├── index.html               # Web UI
    └── app.js                   # WebSocket client + Three.js
```

---

## 🚀 Quick Start

### Prerequisites
- **JDK 17+** (Temurin or compatible)
- Works on Windows, Linux, macOS

### Web Interface (Recommended)
```bash
./gradlew runServer
```
Open: **http://localhost:8080**

The web UI provides real-time visualization with interactive controls.

### Native Desktop Renderer
```bash
./gradlew runSwing
```
Launches fullscreen borderless window with direct GPU rendering.

### GPU Mode (Local Only)
```bash
./gradlew runGPU
```
Runs 3D exact N² calculation with OpenGL compute shaders.

### Build Deployment JAR
```bash
./gradlew shadowJar
```
Output: `build/libs/*-all.jar`

---

## 🎮 Controls

### Web Interface
- **Scenario Buttons** - Galaxy collision, sphere, disk, custom
- **Sliders** - Adjust G, Δt, θ, softening in real-time
- **Play/Pause/Step** - Control simulation flow
- **Body Count** - 1K to 50K bodies
- **Camera** - Click and drag to orbit, scroll to zoom

### Native Desktop
**Mouse:**
- `LMB + drag` - Create galaxy disk (velocity from drag vector)
- `RMB + drag` - Create black hole
- `Middle click` - Clear scene
- `Scroll` - Zoom (1×–10×)
- `Arrow keys` - Pan camera

**Keyboard:**
- `SPACE` - Pause/resume
- `R` - Reset to default scenario
- `ESC` - Exit
- `Z/X` - Decrease/increase θ
- `A/S` - Body count (1K–10K, step 100)
- `Q/W` - Disk radius (100–500px, step 10)
- `O/P` - Decrease/increase Δt
- `K/L` - Decrease/increase G
- `D` - Toggle debug visualization
- `C` - Create random cloud

---

## ⚙️ Configuration

Main parameters in `Config.kt`:
```kotlin
object Config {
    var WIDTH_PX = 2400      // Window width
    var HEIGHT_PX = 800       // Window height
    var G = 80.0              // Gravitational constant
    var DT = 0.005            // Time step
    var SOFTENING = 1.0       // Force softening
    var theta = 0.30          // Barnes–Hut θ parameter
    const val CENTRAL_MASS = 50_000.0
    const val TOTAL_SATELLITE_MASS = 5_000.0
}
```

---

## 🌐 Deployment

### Railway / Cloud Platforms
Includes `railway.toml` for one-click deployment:
```toml
[build]
builder = "NIXPACKS"

[build.env]
NIXPACKS_JDK_VERSION = "17"

[phases.build]
cmds = ["./gradlew shadowJar"]

[deploy]
startCommand = "java -Xmx384m -jar build/libs/*-all.jar"
```

**Note:** GPU mode requires local hardware - cloud deployments run CPU (Barnes–Hut) only.

---

## 🗺️ Roadmap

Future algorithm integration (planned):

### 🔄 **Particle Mesh** (FFT-based)
- O(N log N) cosmological simulation
- Grid-based Poisson solver
- Optimized for 100K+ bodies
- Best for large-scale structure formation

### 🎯 **Unified UI Selector**
Web interface will allow switching between all three algorithms to compare:
- Speed vs. accuracy trade-offs
- Algorithm complexity visualization  
- Educational demonstrations

---

## 📊 Performance

Typical performance on modern hardware:
- **10K bodies** - 60 FPS (Barnes–Hut, web UI)
- **50K bodies** - 30 FPS (Barnes–Hut, native)
- **100K+ bodies** - Planned (Particle Mesh)

Complexity:
- Barnes–Hut: **O(N log N)**
- Direct GPU: **O(N²)**
- Particle Mesh: **O(N log N)** (planned)

---

## 🙏 Credits

**Original Physics Implementation:**  
[Daniil Rakhmatulin](https://github.com/qwertukg)
- Barnes–Hut algorithm (CPU)
- GPU compute shader implementation  
- Kotlin/LWJGL foundation

**Web Interface & Enhancements:**  
- Real-time WebSocket architecture
- Three.js 3D visualization
- Interactive parameter controls
- Deployment infrastructure

---

## 📝 License

Follows upstream licensing from the original repository unless otherwise noted.

