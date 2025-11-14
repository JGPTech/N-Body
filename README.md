 # N‑Body Gravity Simulator

 High-polish, production-ready visualization of galactic dynamics featuring three simulation engines, a live-updating Three.js cockpit, and Kotlin backends for both CPU and GPU execution. Built on [Daniil Rakhmatulin’s](https://github.com/qwertukg) Barnes–Hut work, the project now bundles a full-stack experience that you can run locally or deploy via Railway.

 ---

 ## Highlights

 - **Engine selector:** Barnes–Hut (CPU, 2D), Direct GPU (compute shader, 3D), and Particle Mesh presets, each with curated example scenes (collision/disk, sphere/shell, grand-design/warped disk).
 - **Web UI:** Responsive neumorphic layout with stats bar, compute controls, shader-powered particle renderer (doppler tinting, halos, enhanced luminosity), live MathJax panel, and dynamic scenario toolbar.
 - **Desktop clients:** Legacy Swing renderer plus LWJGL GPU viewer remain available for offline experimentation.
 - **Controls:** Pause/step/reset, orbit/follow camera, body count + speed sliders, physics knobs (G, Δt, θ, softening), keyboard shortcuts (trails, halos, doppler, autorotate).
 - **Performance:** 60 FPS for 10k+ bodies on CPU Barnes–Hut; tens of thousands of points streamed over WebSockets; GPU mode handles dense 3D systems with compute shaders.

 ---

 ## Project Layout

 ```
 src/main/
 ├─ kotlin/
 │  ├─ BarnesHutAlg.kt, BodyFactory.kt, Config.kt
 │  ├─ server/NBodySimulationServer.kt      # WebSocket + scenario orchestration
 │  ├─ gpu/GPU.kt, Body3DFactory.kt         # Direct-sum compute shader pipeline
 │  └─ kz/qwertukg/nBody/...                # Particle-mesh toolkit + desktop app
 └─ resources/static/
    ├─ index.html                           # Web client shell
    └─ app.js                               # Three.js scene + UI logic
 ```

 ---

 ## Getting Started

 ### Prerequisites
 - JDK 17+
 - Gradle wrapper included
 - Works on Windows, macOS, Linux (GPU mode requires desktop OpenGL 4.6)

 ### Launch Options

 | Command | Description |
 | --- | --- |
 | `./gradlew runServer` | Starts the Ktor/WebSocket server at `http://localhost:8080` with the production UI |
 | `./gradlew runGPU` | Native LWJGL viewer executing the compute-shader direct solver |
 | `./gradlew runSwing` | Legacy Swing-based Barnes–Hut renderer |
 | `./gradlew shadowJar` | Builds an executable fat JAR under `build/libs` |
 | `./gradlew runParticleMesh` | Launches the standalone LWJGL particle-mesh client |

 Once the server is running, open the browser UI and choose between Barnes–Hut, Direct, or Particle Mesh via the compute mode buttons. Scenario buttons in the footer auto-adjust per engine.

 ---

 ## Web UI Cheatsheet

 - **Compute Mode:** Barnes–Hut ↔ Direct ↔ Particle Mesh
 - **Scenarios:** Each mode swaps in two tailored presets (e.g., Direct → Sphere/Shell, Particle Mesh → Grand Design/Warped Disk)
 - **Physics Knobs:** G, Δt, θ, softening with rotary controls
 - **Body Count:** 1k–50k slider with live stat updates
 - **Speed Slider:** Scales integration speed server-side
 - **Camera Bar:** Reset/follow center of mass, OrbitControls interaction
 - **Keyboard Shortcuts:** `T` trails, `H` halos, `D` doppler, `R` auto-rotate
 - **Math Panel:** Engine-specific descriptions rendered via MathJax and refreshed whenever you switch scenarios

 ---

 ## Deployment Notes

 Railway configuration (`railway.toml`) already ships with:

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

 GPU mode is local-only; cloud deployments run the CPU Barnes–Hut engine (with the same UI/controls).

 ---

 ## Credits

 - **Physics foundation:** Daniil Rakhmatulin’s Barnes–Hut + GPU implementations
 - **Current release:** Modern UI, scenario selector, Three.js renderer, deployment tooling, and integration of particle-mesh presets

 Licensed under the same terms as the upstream project unless otherwise stated.
