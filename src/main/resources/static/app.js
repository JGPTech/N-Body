// app.js - ULTIMATE VISUAL ENHANCEMENT PACKAGE

// Scenario descriptions and equations
const scenarioData = {
    galaxy_collision: {
        title: "Galaxy Collision",
        description: "Two spiral galaxies approaching on a collision course. Each galaxy contains a central supermassive black hole surrounded by thousands of stars in stable orbits. Watch as gravitational interactions reshape both systems.",
        parameters: [
            { symbol: "G", desc: "Gravitational constant (80)" },
            { symbol: "ε", desc: "Softening parameter (1.0)" },
            { symbol: "Δt", desc: "Integration timestep (0.005s)" },
            { symbol: "θ", desc: "Barnes-Hut opening angle (0.5)" }
        ]
    },
    sphere: {
        title: "Spherical Distribution",
        description: "Uniform spherical distribution of bodies orbiting a central massive object. Particles follow stable Keplerian orbits, demonstrating the balance between gravitational attraction and orbital velocity.",
        parameters: [
            { symbol: "v_circ", desc: "Circular velocity = √(GM/r)" },
            { symbol: "M_enc", desc: "Enclosed mass within radius r" },
            { symbol: "r", desc: "Distance from center" },
            { symbol: "N", desc: "Number of bodies" }
        ]
    },
    disk: {
        title: "Rotating Disk",
        description: "Thin disk of bodies with rotation profile matching observed galaxies. Demonstrates differential rotation and the formation of spiral density waves through self-gravity.",
        parameters: [
            { symbol: "Ω(r)", desc: "Angular velocity at radius r" },
            { symbol: "κ", desc: "Epicyclic frequency" },
            { symbol: "Q", desc: "Toomre stability parameter" },
            { symbol: "σ", desc: "Velocity dispersion" }
        ]
    },
    custom: {
        title: "Custom Configuration",
        description: "Multiple clusters arranged in a ring pattern with individual rotation. Demonstrates complex multi-body gravitational interactions and orbital resonances.",
        parameters: [
            { symbol: "N_c", desc: "Number of clusters" },
            { symbol: "M_c", desc: "Mass per cluster" },
            { symbol: "R", desc: "Cluster separation" },
            { symbol: "v_orb", desc: "Orbital velocity" }
        ]
    }
};

// WebSocket connection and simulation state
let ws = null;
let isConnected = false;
let reconnectAttempts = 0;
let maxReconnectAttempts = 10;
let reconnectTimeout = null;

let simulationState = {
    mode: 'cpu',
    bodyCount: 0,
    paused: true,
    is3D: false,
    simTime: 0,
    params: {
        theta: 0.5,
        g: 80,
        dt: 0.005,
        softening: 1.0
    }
};

// Three.js scene components
let scene, camera, renderer, controls;
let particles, particleGeometry, particleMaterial;
let bodyPositions = new Float32Array(0);
let bodyColors = new Float32Array(0);
let bodySizes = new Float32Array(0);
let bodyCount = 0;

// Trail system
let trails = [];
let trailGeometry, trailMaterial, trailMesh;
const maxTrailLength = 100;
const trailDecay = 0.98;

// Massive body halos
let haloParticles = [];
let haloGeometry, haloMaterial, haloMesh;

// Previous positions for velocity calculation
let previousPositions = new Map();

// Camera settings
let followCoM = false;
let centerOfMass = new THREE.Vector3(0, 0, 0);

//Timer adjustment
let localSimTime = 0;
let lastFrameTime = performance.now();
let isSimRunning = false;

// Visual settings
let visualSettings = {
    showTrails: false,
    showHalos: true,
    dopplerShift: true,
    massSizing: true,
    bloomIntensity: 1.0,
    trailLength: 50
};

// Performance tracking
let stats = {
    fps: 0,
    simRate: 0,
    frameCount: 0,
    lastTime: performance.now(),
    stepsPerSecond: 0
};

// Knob interaction class - COMPLETE WITH RESET
class Knob {
    constructor(element) {
        this.element = element;
        this.param = element.dataset.param;
        this.min = parseFloat(element.dataset.min);
        this.max = parseFloat(element.dataset.max);
        this.value = parseFloat(element.dataset.value);
        this.defaultValue = parseFloat(element.dataset.value); // Store default
        this.angle = 0;
        this.isDragging = false;
        this.updateThrottle = null;

        this.valueDisplay = document.getElementById(`value-${this.param}`);

        // Calculate initial angle from value
        const normalized = (this.value - this.min) / (this.max - this.min);
        this.angle = normalized * 270 - 135;

        // Mouse events
        this.element.addEventListener('mousedown', this.onMouseDown.bind(this));
        document.addEventListener('mousemove', this.onMouseMove.bind(this));
        document.addEventListener('mouseup', this.onMouseUp.bind(this));

        // Touch events
        this.element.addEventListener('touchstart', (e) => {
            e.preventDefault();
            const touch = e.touches[0];
            this.onMouseDown({
                clientX: touch.clientX,
                clientY: touch.clientY,
                preventDefault: () => { }
            });
        });
        document.addEventListener('touchmove', (e) => {
            if (this.isDragging) {
                e.preventDefault();
                const touch = e.touches[0];
                this.onMouseMove({
                    clientX: touch.clientX,
                    clientY: touch.clientY
                });
            }
        });
        document.addEventListener('touchend', () => {
            this.onMouseUp();
        });

        // Set initial visual state
        this.updateVisual();
    }

    onMouseDown(e) {
        this.isDragging = true;
        e.preventDefault();
        document.body.style.cursor = 'pointer';

        // Immediately snap to mouse position
        this.updateFromMouse(e);
    }

    onMouseMove(e) {
        if (!this.isDragging) return;
        this.updateFromMouse(e);
    }

    updateFromMouse(e) {
        // Get knob center position
        const rect = this.element.getBoundingClientRect();
        const centerX = rect.left + rect.width / 2;
        const centerY = rect.top + rect.height / 2;

        // Calculate angle from center to mouse
        const deltaX = e.clientX - centerX;
        const deltaY = e.clientY - centerY;

        // Calculate angle in degrees
        let angle = Math.atan2(deltaY, deltaX) * (180 / Math.PI);

        // Rotate by 90 degrees so that top is -90° and adjust coordinate system
        angle = angle + 90;

        // Normalize to -180 to 180 range
        if (angle > 180) angle -= 360;
        if (angle < -180) angle += 360;

        // Clamp to valid range (-135 to 135)
        angle = Math.max(-135, Math.min(135, angle));

        this.angle = angle;

        // Calculate value from angle
        const normalized = (angle + 135) / 270;
        this.value = this.min + normalized * (this.max - this.min);

        this.updateVisual();

        // Throttle updates to server
        if (this.updateThrottle) {
            clearTimeout(this.updateThrottle);
        }
        this.updateThrottle = setTimeout(() => {
            this.sendUpdate();
        }, 50);
    }

    onMouseUp() {
        if (!this.isDragging) return;

        this.isDragging = false;
        document.body.style.cursor = 'default';

        // Send final update
        if (this.updateThrottle) {
            clearTimeout(this.updateThrottle);
        }
        this.sendUpdate();
    }

    updateVisual() {
        // Clamp angle to valid range
        const clampedAngle = Math.max(-135, Math.min(135, this.angle));
        this.element.style.transform = `rotate(${clampedAngle}deg)`;

        let displayValue;
        if (this.param === 'dt') {
            displayValue = this.value.toFixed(3);
        } else if (this.param === 'theta') {
            displayValue = this.value.toFixed(2);
        } else if (this.param === 'g') {
            displayValue = Math.round(this.value);
        } else {
            displayValue = this.value.toFixed(1);
        }

        if (this.valueDisplay) {
            this.valueDisplay.textContent = displayValue;
        }
    }

    sendUpdate() {
        const params = {};
        params[this.param] = this.value;
        console.log('Knob update:', this.param, '=', this.value);
        sendMessage({ type: 'params', ...params });
    }

    setValue(value) {
        // This is called when server sends updates - don't snap if user is dragging
        if (this.isDragging) return;

        this.value = Math.max(this.min, Math.min(this.max, value));
        const normalized = (this.value - this.min) / (this.max - this.min);
        this.angle = normalized * 270 - 135;
        this.updateVisual();
    }

    // NEW: Reset to default value
    reset() {
        this.value = this.defaultValue;
        const normalized = (this.value - this.min) / (this.max - this.min);
        this.angle = normalized * 270 - 135;
        this.updateVisual();
        this.sendUpdate();
    }
}

// Create enhanced particle texture with glow
function createParticleTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 128;
    canvas.height = 128;
    const ctx = canvas.getContext('2d');

    // Create radial gradient with bright center and glow
    const gradient = ctx.createRadialGradient(64, 64, 0, 64, 64, 64);
    gradient.addColorStop(0, 'rgba(255, 255, 255, 1)');
    gradient.addColorStop(0.1, 'rgba(255, 255, 255, 0.9)');
    gradient.addColorStop(0.2, 'rgba(200, 220, 255, 0.7)');
    gradient.addColorStop(0.4, 'rgba(100, 150, 255, 0.4)');
    gradient.addColorStop(0.7, 'rgba(50, 100, 200, 0.2)');
    gradient.addColorStop(1, 'rgba(20, 50, 150, 0)');

    ctx.fillStyle = gradient;
    ctx.fillRect(0, 0, 128, 128);

    const texture = new THREE.Texture(canvas);
    texture.needsUpdate = true;
    return texture;
}

// Create halo texture for massive bodies
function createHaloTexture() {
    const canvas = document.createElement('canvas');
    canvas.width = 256;
    canvas.height = 256;
    const ctx = canvas.getContext('2d');

    // Multiple gradient layers for complex glow
    const gradient1 = ctx.createRadialGradient(128, 128, 0, 128, 128, 128);
    gradient1.addColorStop(0, 'rgba(255, 200, 100, 0.8)');
    gradient1.addColorStop(0.3, 'rgba(255, 150, 50, 0.4)');
    gradient1.addColorStop(0.6, 'rgba(255, 100, 0, 0.2)');
    gradient1.addColorStop(1, 'rgba(255, 0, 0, 0)');

    ctx.fillStyle = gradient1;
    ctx.fillRect(0, 0, 256, 256);

    const texture = new THREE.Texture(canvas);
    texture.needsUpdate = true;
    return texture;
}

// Initialize Three.js scene with post-processing
function initThreeJS() {
    const container = document.getElementById('canvas-container');
    const width = container.clientWidth;
    const height = container.clientHeight;

    console.log('Initializing enhanced Three.js scene:', width, 'x', height);

    scene = new THREE.Scene();
    scene.fog = new THREE.FogExp2(0x000510, 0.00025);

    camera = new THREE.PerspectiveCamera(75, width / height, .01, 20000);
    camera.position.set(0, 1000, 2500);
    camera.lookAt(0, 0, 0);

    renderer = new THREE.WebGLRenderer({
        antialias: true,
        alpha: false,
        powerPreference: "high-performance"
    });
    renderer.setSize(width, height);
    renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderer.setClearColor(0x000510, 1);
    container.appendChild(renderer.domElement);

    controls = new THREE.OrbitControls(camera, renderer.domElement);
    controls.enableDamping = true;
    controls.dampingFactor = 0.05;
    controls.screenSpacePanning = false;
    controls.minDistance = 10;
    controls.maxDistance = 10000;
    controls.maxPolarAngle = Math.PI;
    controls.autoRotate = false;
    controls.autoRotateSpeed = 0.5;

    // Enhanced lighting
    const ambientLight = new THREE.AmbientLight(0x101030, 0.4);
    scene.add(ambientLight);

    const directionalLight = new THREE.DirectionalLight(0x4060ff, 0.6);
    directionalLight.position.set(1, 1, 1);
    scene.add(directionalLight);

    const backLight = new THREE.DirectionalLight(0xff4060, 0.3);
    backLight.position.set(-1, -0.5, -1);
    scene.add(backLight);

    // Initialize particle systems
    initParticles(50000);
    initTrails();
    initHalos();

    window.addEventListener('resize', onWindowResize, false);

    console.log('Enhanced Three.js initialization complete');
}

function initParticles(maxBodies) {
    if (particles) {
        scene.remove(particles);
        particleGeometry.dispose();
        particleMaterial.dispose();
    }

    particleGeometry = new THREE.BufferGeometry();

    bodyPositions = new Float32Array(maxBodies * 3);
    particleGeometry.setAttribute('position',
        new THREE.BufferAttribute(bodyPositions, 3));

    bodyColors = new Float32Array(maxBodies * 3);
    for (let i = 0; i < maxBodies * 3; i += 3) {
        bodyColors[i] = 1.0;
        bodyColors[i + 1] = 1.0;
        bodyColors[i + 2] = 1.0;
    }
    particleGeometry.setAttribute('color',
        new THREE.BufferAttribute(bodyColors, 3));

    // Add size attribute for mass-based sizing
    bodySizes = new Float32Array(maxBodies);
    bodySizes.fill(1.0);
    particleGeometry.setAttribute('size',
        new THREE.BufferAttribute(bodySizes, 1));

    particleMaterial = new THREE.ShaderMaterial({
        uniforms: {
            pointTexture: { value: createParticleTexture() },
            baseSize: { value: simulationState.is3D ? 4.0 : 3.0 }
        },
        vertexShader: `
            attribute float size;
            attribute vec3 color;
            varying vec3 vColor;
            uniform float baseSize;
            
            void main() {
                vColor = color;
                vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
                gl_PointSize = baseSize * size * (300.0 / -mvPosition.z);
                gl_Position = projectionMatrix * mvPosition;
            }
        `,
        fragmentShader: `
            uniform sampler2D pointTexture;
            varying vec3 vColor;
            
            void main() {
                vec4 texColor = texture2D(pointTexture, gl_PointCoord);
                gl_FragColor = vec4(vColor, 1.0) * texColor;
                
                // Add bloom effect
                float brightness = dot(vColor, vec3(0.299, 0.587, 0.114));
                if (brightness > 0.8) {
                    gl_FragColor.rgb += vec3(0.2) * (brightness - 0.8) * 5.0;
                }
            }
        `,
        blending: THREE.AdditiveBlending,
        transparent: true,
        depthWrite: false
    });

    particles = new THREE.Points(particleGeometry, particleMaterial);
    scene.add(particles);

    console.log('Initialized enhanced particle system for', maxBodies, 'bodies');
}

function initTrails() {
    trails = [];

    const maxTrails = 10000;
    const positions = new Float32Array(maxTrails * 3);
    const colors = new Float32Array(maxTrails * 3);
    const alphas = new Float32Array(maxTrails);

    trailGeometry = new THREE.BufferGeometry();
    trailGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    trailGeometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    trailGeometry.setAttribute('alpha', new THREE.BufferAttribute(alphas, 1));

    trailMaterial = new THREE.ShaderMaterial({
        uniforms: {
            pointTexture: { value: createParticleTexture() }
        },
        vertexShader: `
            attribute vec3 color;
            attribute float alpha;
            varying vec3 vColor;
            varying float vAlpha;
            
            void main() {
                vColor = color;
                vAlpha = alpha;
                vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
                gl_PointSize = 2.0 * (300.0 / -mvPosition.z);
                gl_Position = projectionMatrix * mvPosition;
            }
        `,
        fragmentShader: `
            uniform sampler2D pointTexture;
            varying vec3 vColor;
            varying float vAlpha;
            
            void main() {
                vec4 texColor = texture2D(pointTexture, gl_PointCoord);
                gl_FragColor = vec4(vColor, vAlpha * texColor.a);
            }
        `,
        blending: THREE.AdditiveBlending,
        transparent: true,
        depthWrite: false
    });

    trailMesh = new THREE.Points(trailGeometry, trailMaterial);
    scene.add(trailMesh);

    console.log('Trail system initialized');
}

function initHalos() {
    const positions = new Float32Array(100 * 3);
    const colors = new Float32Array(100 * 3);
    const sizes = new Float32Array(100);

    haloGeometry = new THREE.BufferGeometry();
    haloGeometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    haloGeometry.setAttribute('color', new THREE.BufferAttribute(colors, 3));
    haloGeometry.setAttribute('size', new THREE.BufferAttribute(sizes, 1));

    haloMaterial = new THREE.ShaderMaterial({
        uniforms: {
            pointTexture: { value: createHaloTexture() }
        },
        vertexShader: `
            attribute vec3 color;
            attribute float size;
            varying vec3 vColor;
            
            void main() {
                vColor = color;
                vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
                gl_PointSize = size * (300.0 / -mvPosition.z);
                gl_Position = projectionMatrix * mvPosition;
            }
        `,
        fragmentShader: `
            uniform sampler2D pointTexture;
            varying vec3 vColor;
            
            void main() {
                vec4 texColor = texture2D(pointTexture, gl_PointCoord);
                gl_FragColor = vec4(vColor, 1.0) * texColor;
            }
        `,
        blending: THREE.AdditiveBlending,
        transparent: true,
        depthWrite: false
    });

    haloMesh = new THREE.Points(haloGeometry, haloMaterial);
    scene.add(haloMesh);

    console.log('Halo system initialized');
}

function updateParticles(buffer) {
    const view = new DataView(buffer);
    const count = view.getInt32(0, true);

    if (count !== bodyCount || count * 3 > bodyPositions.length) {
        if (count * 3 > bodyPositions.length) {
            console.log('Resizing particle buffer to', count);
            initParticles(Math.ceil(count * 1.5));
        }
        bodyCount = count;
    }

    let offset = 4;
    let comX = 0, comY = 0, comZ = 0;
    let massiveBodyCount = 0;

    // Update halo positions
    const haloPositions = haloGeometry.attributes.position.array;
    const haloColors = haloGeometry.attributes.color.array;
    const haloSizes = haloGeometry.attributes.size.array;

    if (simulationState.is3D) {
        for (let i = 0; i < count * 3; i += 3) {
            if (offset + 12 > buffer.byteLength) break;

            const x = view.getFloat32(offset, true);
            const y = view.getFloat32(offset + 4, true);
            const z = view.getFloat32(offset + 8, true);

            const idx = i / 3;

            // Transform coordinates
            bodyPositions[i] = x - 1720;
            bodyPositions[i + 1] = z - 500;
            bodyPositions[i + 2] = y - 720;

            comX += x;
            comY += y;
            comZ += z;

            // Calculate velocity for Doppler shift
            const currentPos = new THREE.Vector3(bodyPositions[i], bodyPositions[i + 1], bodyPositions[i + 2]);
            const prevPos = previousPositions.get(idx);

            let velocityTowardCamera = 0;
            if (prevPos && visualSettings.dopplerShift) {
                const velocity = currentPos.clone().sub(prevPos);
                const toCamera = camera.position.clone().sub(currentPos).normalize();
                velocityTowardCamera = velocity.dot(toCamera);
            }

            previousPositions.set(idx, currentPos.clone());

            // Enhanced color based on velocity (Doppler effect)
            if (visualSettings.dopplerShift) {
                const shift = velocityTowardCamera * 0.5;

                if (shift < 0) {
                    // Blue shift (approaching)
                    bodyColors[i] = 0.3;
                    bodyColors[i + 1] = 0.5 + Math.abs(shift) * 0.5;
                    bodyColors[i + 2] = 1.0;
                } else {
                    // Red shift (receding)
                    bodyColors[i] = 1.0;
                    bodyColors[i + 1] = 0.4 - shift * 0.3;
                    bodyColors[i + 2] = 0.3 - shift * 0.2;
                }

                // Add velocity-based intensity
                const speed = Math.abs(velocityTowardCamera);
                const intensity = Math.min(1.0, 0.5 + speed * 2.0);
                bodyColors[i] *= intensity;
                bodyColors[i + 1] *= intensity;
                bodyColors[i + 2] *= intensity;
            } else {
                // Z-based depth coloring
                const zNorm = (z - 300) / 400;
                bodyColors[i] = 0.3 + zNorm * 0.4;
                bodyColors[i + 1] = 0.5 + (1 - Math.abs(zNorm - 0.5)) * 0.3;
                bodyColors[i + 2] = 0.8 + (1 - zNorm) * 0.2;
            }

            // Mass-based sizing (assuming larger mass = ~1000+)
            if (visualSettings.massSizing) {
                const estimatedMass = 1.0; // Would need mass data from server
                bodySizes[idx] = 1.0 + estimatedMass * 0.5;

                // Detect massive bodies for halos
                if (estimatedMass > 100 && massiveBodyCount < 100) {
                    haloPositions[massiveBodyCount * 3] = bodyPositions[i];
                    haloPositions[massiveBodyCount * 3 + 1] = bodyPositions[i + 1];
                    haloPositions[massiveBodyCount * 3 + 2] = bodyPositions[i + 2];

                    haloColors[massiveBodyCount * 3] = 1.0;
                    haloColors[massiveBodyCount * 3 + 1] = 0.6;
                    haloColors[massiveBodyCount * 3 + 2] = 0.2;

                    haloSizes[massiveBodyCount] = 50.0 + estimatedMass * 2.0;
                    massiveBodyCount++;
                }
            }

            offset += 12;
        }

        particleGeometry.attributes.color.needsUpdate = true;
        particleGeometry.attributes.size.needsUpdate = true;

        if (visualSettings.showHalos) {
            haloGeometry.setDrawRange(0, massiveBodyCount);
            haloGeometry.attributes.position.needsUpdate = true;
            haloGeometry.attributes.color.needsUpdate = true;
            haloGeometry.attributes.size.needsUpdate = true;
            haloMesh.visible = true;
        } else {
            haloMesh.visible = false;
        }

    } else {
        // 2D mode - simpler coloring
        for (let i = 0; i < count * 3; i += 3) {
            if (offset + 12 > buffer.byteLength) break;

            const x = view.getFloat32(offset, true);
            const y = view.getFloat32(offset + 4, true);

            bodyPositions[i] = x - 1720;
            bodyPositions[i + 1] = 0;
            bodyPositions[i + 2] = y - 720;

            comX += x;
            comY += y;

            // Simple gradient for 2D
            const dist = Math.sqrt(Math.pow(x - 1720, 2) + Math.pow(y - 720, 2));
            const t = Math.min(1.0, dist / 1000);

            bodyColors[i] = 0.4 + t * 0.4;
            bodyColors[i + 1] = 0.6 + (1 - t) * 0.3;
            bodyColors[i + 2] = 1.0 - t * 0.3;

            offset += 12;
        }

        haloMesh.visible = false;
    }

    // Update trails
    if (visualSettings.showTrails && simulationState.is3D) {
        updateTrails();
    } else {
        trailMesh.visible = false;
    }

    if (count > 0) {
        if (simulationState.is3D) {
            centerOfMass.set(
                comX / count - 1720,
                comZ / count - 500,
                comY / count - 720
            );
        } else {
            centerOfMass.set(
                comX / count - 1720,
                0,
                comY / count - 720
            );
        }
    }

    particleGeometry.attributes.position.needsUpdate = true;
    particleGeometry.setDrawRange(0, count);

    if (particleMaterial.uniforms) {
        particleMaterial.uniforms.baseSize.value = simulationState.is3D ? 4.0 : 3.0;
    }

    updateStatDisplay('stat-bodies', count.toLocaleString());
    updateInteractionCount(count);
}

function updateTrails() {
    // Sample a subset of particles for trails
    const sampleRate = Math.max(1, Math.floor(bodyCount / 200));
    const trailPositions = trailGeometry.attributes.position.array;
    const trailColors = trailGeometry.attributes.color.array;
    const trailAlphas = trailGeometry.attributes.alpha.array;

    let trailIndex = 0;

    for (let i = 0; i < bodyCount * 3; i += 3 * sampleRate) {
        if (trailIndex >= trailPositions.length / 3) break;

        const idx = i / 3;
        const history = trails[idx] || [];

        // Add current position to history
        history.push({
            x: bodyPositions[i],
            y: bodyPositions[i + 1],
            z: bodyPositions[i + 2],
            r: bodyColors[i],
            g: bodyColors[i + 1],
            b: bodyColors[i + 2]
        });

        // Keep only recent history
        if (history.length > visualSettings.trailLength) {
            history.shift();
        }

        trails[idx] = history;

        // Render trail
        for (let j = 0; j < history.length && trailIndex < trailPositions.length / 3; j++) {
            const point = history[j];
            const alpha = (j / history.length) * 0.6;

            trailPositions[trailIndex * 3] = point.x;
            trailPositions[trailIndex * 3 + 1] = point.y;
            trailPositions[trailIndex * 3 + 2] = point.z;

            trailColors[trailIndex * 3] = point.r;
            trailColors[trailIndex * 3 + 1] = point.g;
            trailColors[trailIndex * 3 + 2] = point.b;

            trailAlphas[trailIndex] = alpha;

            trailIndex++;
        }
    }

    trailGeometry.setDrawRange(0, trailIndex);
    trailGeometry.attributes.position.needsUpdate = true;
    trailGeometry.attributes.color.needsUpdate = true;
    trailGeometry.attributes.alpha.needsUpdate = true;
    trailMesh.visible = true;
}

// WebSocket connection with better error handling
function connectWebSocket() {
    const statusEl = document.getElementById('connection-status');
    const statusText = document.getElementById('status-text');

    if (reconnectTimeout) {
        clearTimeout(reconnectTimeout);
        reconnectTimeout = null;
    }

    console.log('Attempting WebSocket connection... (attempt', reconnectAttempts + 1, ')');

    try {
        ws = new WebSocket('ws://localhost:8080/simulation');

        ws.onopen = () => {
            console.log('WebSocket connected successfully');
            isConnected = true;
            reconnectAttempts = 0;
            statusEl.className = 'status-connected';
            statusText.textContent = 'Connected';

            setTimeout(() => {
                console.log('Requesting initial simulation start');
                sendMessage({
                    type: 'control',
                    command: 'play'
                });
            }, 100);
        };

        ws.onmessage = async (event) => {
            if (event.data instanceof ArrayBuffer) {
                updateParticles(event.data);
            } else if (event.data instanceof Blob) {
                const arrayBuffer = await event.data.arrayBuffer();
                updateParticles(arrayBuffer);
            } else {
                try {
                    const data = JSON.parse(event.data);
                    handleStateUpdate(data);
                } catch (e) {
                    console.error('Failed to parse JSON:', e, event.data);
                }
            }
        };

        ws.onerror = (error) => {
            console.error('WebSocket error:', error);
            statusEl.className = 'status-disconnected';
            statusText.textContent = 'Connection Error';
        };

        ws.onclose = (event) => {
            console.log('WebSocket closed:', event.code, event.reason);
            isConnected = false;
            statusEl.className = 'status-disconnected';
            statusText.textContent = 'Disconnected';

            if (reconnectAttempts < maxReconnectAttempts) {
                reconnectAttempts++;
                const delay = Math.min(1000 * Math.pow(2, reconnectAttempts - 1), 10000);
                console.log('Reconnecting in', delay, 'ms...');
                reconnectTimeout = setTimeout(connectWebSocket, delay);
            } else {
                console.error('Max reconnection attempts reached');
                statusText.textContent = 'Failed to Connect';
            }
        };
    } catch (error) {
        console.error('Failed to create WebSocket:', error);
        statusEl.className = 'status-disconnected';
        statusText.textContent = 'Connection Failed';
    }
}

function handleStateUpdate(state) {
    console.log('State update received:', state);

    const was3D = simulationState.is3D;
    Object.assign(simulationState, state);

    // Sync local sim time with server
    if (state.simTime !== undefined) {
        localSimTime = state.simTime;
    }

    // Track if sim is running
    isSimRunning = !state.paused;

    if (was3D !== state.is3D) {
        console.log('Switching to', state.is3D ? '3D' : '2D', 'mode');
        if (state.is3D) {
            camera.position.set(0, 1500, 3000);
            controls.minDistance = 200;
            controls.maxDistance = 10000;
        } else {
            camera.position.set(0, 500, 1500);
            controls.minDistance = 100;
            controls.maxDistance = 5000;
        }
        camera.lookAt(0, 0, 0);
        controls.target.set(0, 0, 0);
        controls.update();

        // Clear trails when switching modes
        trails = [];
        previousPositions.clear();
    }

    const modeText = state.mode === 'cpu' ? 'CPU (2D)' : 'GPU (3D)';
    updateStatDisplay('stat-mode', modeText);

    document.querySelectorAll('.mode-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.mode === state.mode);
    });

    updateStatDisplay('stat-time', (state.simTime || 0).toFixed(1) + 's');

    if (state.stepsPerSecond !== undefined) {
        updateStatDisplay('stat-sim-rate', state.stepsPerSecond);
    }

    if (state.params && knobs) {
        if (knobs.theta) knobs.theta.setValue(state.params.theta || 0.5);
        if (knobs.g) knobs.g.setValue(state.params.g || 80);
        if (knobs.dt) knobs.dt.setValue(state.params.dt || 0.005);
        if (knobs.softening) knobs.softening.setValue(state.params.softening || 1.0);
    }

    const playBtn = document.getElementById('btn-play');
    const pauseBtn = document.getElementById('btn-pause');

    if (state.paused) {
        pauseBtn.classList.add('active');
        playBtn.classList.remove('active');
    } else {
        playBtn.classList.add('active');
        pauseBtn.classList.remove('active');
    }
}

// Event handlers (keeping existing code)
function setupEventHandlers() {
    console.log('Setting up event handlers');

    document.querySelectorAll('.mode-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            const mode = btn.dataset.mode;
            console.log('Mode button clicked:', mode);

            document.querySelectorAll('.mode-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');

            const bodyCount = parseInt(document.getElementById('body-count-slider').value);
            const scenario = document.querySelector('.model-option.active')?.dataset.scenario || 'galaxy_collision';

            sendMessage({
                type: 'init',
                mode: mode,
                bodyCount: bodyCount,
                scenario: scenario
            });
        });
    });

    document.getElementById('btn-play').addEventListener('click', () => {
        console.log('Play clicked');
        sendMessage({ type: 'control', command: 'play' });
    });

    document.getElementById('btn-pause').addEventListener('click', () => {
        console.log('Pause clicked');
        sendMessage({ type: 'control', command: 'pause' });
    });

    document.getElementById('btn-reset').addEventListener('click', () => {
        console.log('Reset clicked');
        trails = [];
        previousPositions.clear();

        // Reset all knobs to default values
        if (knobs) {
            if (knobs.g) knobs.g.reset();
            if (knobs.dt) knobs.dt.reset();
            if (knobs.theta) knobs.theta.reset();
            if (knobs.softening) knobs.softening.reset();
        }

        // Reset sliders too
        const bodyCountSlider = document.getElementById('body-count-slider');
        const speedSlider = document.getElementById('speed-slider');
        if (bodyCountSlider) {
            bodyCountSlider.value = 10000;
            document.getElementById('body-count-display').textContent = '10,000';
        }
        if (speedSlider) {
            speedSlider.value = 1.0;
            document.getElementById('speed-display').textContent = '1.0x';
        }

        sendMessage({ type: 'control', command: 'reset' });
    });

    const bodyCountSlider = document.getElementById('body-count-slider');
    const bodyCountDisplay = document.getElementById('body-count-display');
    bodyCountSlider.addEventListener('input', (e) => {
        const value = parseInt(e.target.value);
        bodyCountDisplay.textContent = value.toLocaleString();
    });

    const speedSlider = document.getElementById('speed-slider');
    const speedDisplay = document.getElementById('speed-display');
    speedSlider.addEventListener('input', (e) => {
        const value = parseFloat(e.target.value);
        speedDisplay.textContent = value.toFixed(1) + 'x';
        sendMessage({
            type: 'control',
            command: 'speed',
            speed: value
        });
    });

    document.querySelectorAll('.model-option').forEach(option => {
        option.addEventListener('click', () => {
            const scenario = option.dataset.scenario;
            console.log('Scenario selected:', scenario);

            document.querySelectorAll('.model-option').forEach(o => o.classList.remove('active'));
            option.classList.add('active');

            updateMathPanel(scenario);

            // Clear trails on scenario change
            trails = [];
            previousPositions.clear();

            const bodyCount = parseInt(document.getElementById('body-count-slider').value);
            const mode = document.querySelector('.mode-btn.active')?.dataset.mode || 'cpu';

            sendMessage({
                type: 'init',
                mode: mode,
                bodyCount: bodyCount,
                scenario: scenario
            });
        });
    });

    document.getElementById('cam-reset').addEventListener('click', () => {
        console.log('Camera reset');
        if (simulationState.is3D) {
            camera.position.set(0, 1500, 3000);
        } else {
            camera.position.set(0, 500, 1500);
        }
        camera.lookAt(0, 0, 0);
        controls.target.set(0, 0, 0);
        controls.update();
    });

    document.getElementById('cam-follow').addEventListener('click', () => {
        followCoM = !followCoM;
        console.log('Camera follow:', followCoM);
        document.getElementById('cam-follow').classList.toggle('active', followCoM);
    });

    // Keyboard shortcuts
    document.addEventListener('keydown', (e) => {
        switch (e.key.toLowerCase()) {
            case 't':
                visualSettings.showTrails = !visualSettings.showTrails;
                console.log('Trails:', visualSettings.showTrails);
                break;
            case 'h':
                visualSettings.showHalos = !visualSettings.showHalos;
                console.log('Halos:', visualSettings.showHalos);
                break;
            case 'd':
                visualSettings.dopplerShift = !visualSettings.dopplerShift;
                console.log('Doppler shift:', visualSettings.dopplerShift);
                break;
            case 'r':
                controls.autoRotate = !controls.autoRotate;
                console.log('Auto-rotate:', controls.autoRotate);
                break;
        }
    });

    console.log('Event handlers setup complete');
}

function updateMathPanel(scenario) {
    const data = scenarioData[scenario];
    if (!data) {
        console.warn('No scenario data for:', scenario);
        return;
    }

    console.log('Updating math panel for:', scenario);

    const mathContent = document.getElementById('math-content');

    let paramsHTML = '';
    data.parameters.forEach(param => {
        paramsHTML += `
            <div class="parameter-item">
                <span class="parameter-symbol">${param.symbol}</span> = ${param.desc}
            </div>
        `;
    });

    mathContent.innerHTML = `
        <div class="section-title">${data.title}</div>
        
        <div class="description">
            ${data.description}
        </div>
        
        <div class="equation-block">
            <div class="equation-label">Gravitational Force</div>
            <div class="equation">
                \\[\\vec{F}_{ij} = -G \\frac{m_i m_j}{r_{ij}^2 + \\epsilon^2} \\hat{r}_{ij}\\]
            </div>
        </div>
        
        <div class="equation-block">
            <div class="equation-label">N-Body Acceleration</div>
            <div class="equation">
                \\[\\vec{a}_i = -G \\sum_{j \\neq i} \\frac{m_j}{r_{ij}^2 + \\epsilon^2} \\hat{r}_{ij}\\]
            </div>
        </div>
        
        <div class="parameter-list">
            ${paramsHTML}
        </div>
        
        <div class="equation-block">
            <div class="equation-label">Computational Complexity</div>
            <div class="equation">
                \\[\\text{GPU: } \\mathcal{O}(N^2)\\]
                \\[\\text{CPU: } \\mathcal{O}(N \\log N)\\]
            </div>
        </div>
        
        <div class="description" style="margin-top: 20px; font-size: 11px; opacity: 0.7;">
            <strong>Keyboard Shortcuts:</strong><br>
            T - Toggle Trails 
            D - Toggle Doppler Shift 
        </div>
    `;

    if (window.MathJax && window.MathJax.typesetPromise) {
        window.MathJax.typesetPromise([mathContent]).catch((err) => {
            console.error('MathJax typeset error:', err);
        });
    }
}

function sendMessage(msg) {
    if (ws && ws.readyState === WebSocket.OPEN) {
        const jsonStr = JSON.stringify(msg);
        console.log('Sending message:', jsonStr);
        ws.send(jsonStr);
    } else {
        console.warn('WebSocket not ready, cannot send message:', msg);
    }
}

function updateInteractionCount(count) {
    let interactions;
    if (simulationState.mode === 'cpu') {
        interactions = count > 0 ? Math.floor(count * Math.log2(Math.max(count, 2))) : 0;
    } else {
        interactions = count * count;
    }

    let formatted;
    if (interactions > 1e9) {
        formatted = (interactions / 1e9).toFixed(2) + 'B';
    } else if (interactions > 1e6) {
        formatted = (interactions / 1e6).toFixed(2) + 'M';
    } else if (interactions > 1e3) {
        formatted = (interactions / 1e3).toFixed(2) + 'K';
    } else {
        formatted = interactions.toString();
    }

    updateStatDisplay('stat-interactions', formatted);
}

function updateStatDisplay(id, value) {
    const element = document.getElementById(id);
    if (element) {
        element.textContent = value;
    }
}

// Animation loop
function animate() {
    requestAnimationFrame(animate);

    controls.update();

    if (followCoM && centerOfMass) {
        controls.target.lerp(centerOfMass, 0.05);
    }

    if (particles && !simulationState.is3D) {
        particles.rotation.y += 0.0001;
    }

    updateStats();

    renderer.render(scene, camera);
}

function updateStats() {
    const now = performance.now();
    stats.frameCount++;

    // Update local sim time if running
    if (isSimRunning && simulationState.mode) {
        const deltaTime = (now - lastFrameTime) / 1000; // seconds
        const dt = simulationState.params.dt || 0.005;
        const speed = parseFloat(document.getElementById('speed-slider')?.value || 1);

        localSimTime += dt * speed;
        updateStatDisplay('stat-time', localSimTime.toFixed(1) + 's');
    }
    lastFrameTime = now;

    if (now - stats.lastTime >= 1000) {
        stats.fps = stats.frameCount;
        stats.frameCount = 0;
        stats.lastTime = now;

        updateStatDisplay('stat-fps', stats.fps);
    }
}

function onWindowResize() {
    const container = document.getElementById('canvas-container');
    const width = container.clientWidth;
    const height = container.clientHeight;

    camera.aspect = width / height;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height);
}

let knobs = {};

function init() {
    console.log('Initializing ULTIMATE visualization...');

    initThreeJS();

    console.log('Initializing knobs...');
    knobs.g = new Knob(document.getElementById('knob-g'));
    knobs.dt = new Knob(document.getElementById('knob-dt'));
    knobs.theta = new Knob(document.getElementById('knob-theta'));
    knobs.softening = new Knob(document.getElementById('knob-soft'));

    setupEventHandlers();
    updateMathPanel('galaxy_collision');
    connectWebSocket();
    animate();

    console.log('🚀 ULTIMATE visualization initialized - prepare for AWESOME! 🌌');
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
} else {
    init();
}