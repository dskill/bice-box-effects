const baseVertexShader = `
    precision highp float;
    attribute vec2 aPosition;
    varying vec2 vUv;
    varying vec2 vL;
    varying vec2 vR;
    varying vec2 vT;
    varying vec2 vB;
    uniform vec2 texelSize;

    void main () {
        vUv = aPosition * 0.5 + 0.5;
        vL = vUv - vec2(texelSize.x, 0.0);
        vR = vUv + vec2(texelSize.x, 0.0);
        vT = vUv + vec2(0.0, texelSize.y);
        vB = vUv - vec2(0.0, texelSize.y);
        gl_Position = vec4(aPosition, 0.0, 1.0);
    }
`;

// Add splatShader for adding forces
const splatShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    uniform sampler2D uTarget;
    uniform float aspectRatio;
    //uniform vec3 color;
    uniform vec2 point;
    uniform float radius;
    uniform sampler2D waveformTex;

    void main () {
        // Only consider the vertical UV coordinate
        float waveform = texture2D(waveformTex, vec2(vUv.x,0.5)).x * 2.0 - 1.0;
        waveform *= 2.0;
        float dist =  abs(waveform - vUv.y);
        dist *= 2.0;
        vec3 base = texture2D(uTarget, vUv).xyz;
        vec3 color = vec3(0.0,waveform, 0.0);
        vec3 splat = exp(-dist / radius) * color ;
        gl_FragColor = vec4(base + splat, 1.0);
    }
`;

const advectionShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    uniform sampler2D uVelocity;
    uniform sampler2D uSource;
    uniform vec2 texelSize;
    uniform float dt;
    uniform float dissipation;

    void main () {
        vec2 coord = vUv - dt * texture2D(uVelocity, vUv).xy * texelSize;
        gl_FragColor = dissipation * texture2D(uSource, coord);
        gl_FragColor.a = 1.0;
    }
`;

const divergenceShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    varying vec2 vL;
    varying vec2 vR;
    varying vec2 vT;
    varying vec2 vB;
    uniform sampler2D uVelocity;

    void main () {
        float L = texture2D(uVelocity, vL).x;
        float R = texture2D(uVelocity, vR).x;
        float T = texture2D(uVelocity, vT).y;
        float B = texture2D(uVelocity, vB).y;
        float div = 0.5 * (R - L + T - B);
        gl_FragColor = vec4(div, 0.0, 0.0, 1.0);
    }
`;

const pressureShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    varying vec2 vL;
    varying vec2 vR;
    varying vec2 vT;
    varying vec2 vB;
    uniform sampler2D uPressure;
    uniform sampler2D uDivergence;

    void main () {
        float L = texture2D(uPressure, vL).x;
        float R = texture2D(uPressure, vR).x;
        float T = texture2D(uPressure, vT).x;
        float B = texture2D(uPressure, vB).x;
        float C = texture2D(uPressure, vUv).x;
        float divergence = texture2D(uDivergence, vUv).x;
        float pressure = (L + R + T + B - divergence) * 0.25;
        gl_FragColor = vec4(pressure, 0.0, 0.0, 1.0);
    }
`;

const displayShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    uniform sampler2D uTexture;
    uniform float u_rms;

    void main () {
        vec3 color = texture2D(uTexture, vUv).rgb;
        float brightness = max(color.r, max(color.g, color.b));
        color = mix(color, vec3(1.0), brightness);// * u_rms);
        gl_FragColor = vec4(color, 1.0);
    }
`;

const gradientSubtractShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    varying vec2 vL;
    varying vec2 vR;
    varying vec2 vT;
    varying vec2 vB;
    uniform sampler2D uPressure;
    uniform sampler2D uVelocity;

    void main () {
        float L = texture2D(uPressure, vL).x;
        float R = texture2D(uPressure, vR).x;
        float T = texture2D(uPressure, vT).x;
        float B = texture2D(uPressure, vB).x;
        vec2 velocity = texture2D(uVelocity, vUv).xy;
        velocity.xy -= vec2(R - L, T - B);
        gl_FragColor = vec4(velocity, 0.0, 1.0);
    }
`;

const dyeShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    uniform sampler2D uVelocity;
    uniform sampler2D uSource;
    uniform vec2 texelSize;
    uniform float dt;
    uniform float dissipation;

    void main () {
        vec2 coord = vUv - dt * texture2D(uVelocity, vUv).xy * texelSize;
        vec3 result = dissipation * texture2D(uSource, coord).rgb;
        gl_FragColor = vec4(result, 1.0);
    }
`;

// Modify splatShader to handle color differently
const colorSplatShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    uniform sampler2D uTarget;
    uniform float aspectRatio;
    //uniform vec3 color;
    uniform vec2 point;
    uniform float radius;
    uniform sampler2D waveformTex;

    void main () {
        // Only consider the vertical UV coordinate
        float waveform = texture2D(waveformTex, vUv).x * 2.0 - 1.0;
        float dist =  abs(vUv.y - 0.5); //abs(waveform - vUv.y + 0.5);
        vec3 base = texture2D(uTarget, vUv).rgb;
        vec3 color = vec3(abs(waveform)*1.0, 0.3, 0.3);
        vec3 splat = exp(-dist / radius) * color ;
        gl_FragColor = vec4(base + splat, 1.0);
    }
`;

const sketch = (p) => {
    let simWidth, simHeight;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    const dt = 10.0;
    const radius = 0.005;

    let waveformTex;
    p.waveform1 = [];

    // Simulation programs
    let advectionProgram;
    let divergenceProgram;
    let pressureProgram;
    let displayProgram;
    let splatProgram;
    let gradientSubtractProgram;

    // Framebuffers
    let velocity;
    let pressure;
    let divergence;

    let dye; // Add dye framebuffers
    let dyeProgram, colorSplatProgram; // Add new programs

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.pixelDensity(1);

        // Use full resolution for simulation
        simWidth = 256;//p.width / DOWNSAMPLE;
        simHeight = 256;// p.height / DOWNSAMPLE;

        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        //waveformTex.noSmooth();


        // Initialize shaders
        advectionProgram = p.createShader(baseVertexShader, advectionShader);
        divergenceProgram = p.createShader(baseVertexShader, divergenceShader);
        pressureProgram = p.createShader(baseVertexShader, pressureShader);
        displayProgram = p.createShader(baseVertexShader, displayShader);
        splatProgram = p.createShader(baseVertexShader, splatShader);
        gradientSubtractProgram = p.createShader(baseVertexShader, gradientSubtractShader);

        // Create simulation framebuffers with floating point textures
        velocity = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        pressure = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        divergence = p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB });

        // Initialize FPS counter
        fps = p.createP('');
        fps.style('color', '#444444');
        fps.style('font-family', 'monospace');
        fps.style('position', 'fixed');
        fps.style('bottom', '10px');
        fps.style('left', '10px');

        // Add new shader programs
        dyeProgram = p.createShader(baseVertexShader, dyeShader);
        colorSplatProgram = p.createShader(baseVertexShader, colorSplatShader);

        // Create dye framebuffers
        dye = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
    };

    p.draw = () => {

        // Update waveform texture
        waveformTex.loadPixels();
        for (let i = 0; i < p.waveform1.length; i++) {
            let val = (p.waveform1[i]*.5 +.5) * 255.0;
            waveformTex.pixels[i * 4] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        // Advection step
        p.shader(advectionProgram);
        advectionProgram.setUniform('uVelocity', velocity[0]);
        advectionProgram.setUniform('uSource', velocity[0]);
        advectionProgram.setUniform('texelSize', [1.0/simWidth, 1.0/simHeight]);
        advectionProgram.setUniform('dt', dt); // Slightly reduced timestep
        advectionProgram.setUniform('dissipation', 0.95); // Less dissipation
        velocity[1].begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        velocity[1].end();
        [velocity[0], velocity[1]] = [velocity[1], velocity[0]];

        // Divergence step
        p.shader(divergenceProgram);
        divergenceProgram.setUniform('uVelocity', velocity[0]);
        divergenceProgram.setUniform('texelSize', [1.0/simWidth, 1.0/simHeight]);
        divergence.begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        divergence.end();

        // Pressure step - more iterations for better accuracy
        for (let i = 0; i < 1; i++) { // Increased from 20 to 40
            p.shader(pressureProgram);
            pressureProgram.setUniform('uPressure', pressure[0]);
            pressureProgram.setUniform('uDivergence', divergence);
            pressureProgram.setUniform('texelSize', [1.0/simWidth, 1.0/simHeight]);
            pressure[1].begin();
            p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
            pressure[1].end();
            [pressure[0], pressure[1]] = [pressure[1], pressure[0]];
        }

        // Gradient subtraction step
        p.shader(gradientSubtractProgram);
        gradientSubtractProgram.setUniform('uPressure', pressure[0]);
        gradientSubtractProgram.setUniform('uVelocity', velocity[0]);
        gradientSubtractProgram.setUniform('texelSize', [1.0/simWidth, 1.0/simHeight]);
        velocity[1].begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        velocity[1].end();
        [velocity[0], velocity[1]] = [velocity[1], velocity[0]];

        // Add dye advection step
        p.shader(dyeProgram);
        dyeProgram.setUniform('uVelocity', velocity[0]);
        dyeProgram.setUniform('uSource', dye[0]);
        dyeProgram.setUniform('texelSize', [1.0/simWidth, 1.0/simHeight]);
        dyeProgram.setUniform('dt', dt);
        dyeProgram.setUniform('dissipation', 0.99); // Slightly stronger dissipation for dye
        dye[1].begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        dye[1].end();
        [dye[0], dye[1]] = [dye[1], dye[0]];

        // Display
        p.shader(displayProgram);
        displayProgram.setUniform('uTexture', dye[0]); // Changed from velocity[0] to dye[0]
        displayProgram.setUniform('u_rms', p.rmsOutput || 0.5);
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);

        updateFPS();

        // Add forces and color based on audio input
        /*
        if (p.rmsOutput) {
            const x = p.mouseX/p.width;
            const y = 1.0 - p.mouseY/p.height;
            addForce(x, y, p.rmsOutput * 0.0, p.rmsOutput * 2.0);
            addColor(x, y, [1.0, 0.5, 0.0]); // Add orange dye
        }
        */
        addForce(0.5, 0.5, 1.0, 1.0);
        addColor(0.5, 0.5, [1.0, 0.5, 0.0]); // Add orange dye

    };

    const addForce = (x, y, dx, dy) => {
        p.shader(splatProgram);
        splatProgram.setUniform('uTarget', velocity[0]);
        splatProgram.setUniform('aspectRatio', simWidth/simHeight);
        splatProgram.setUniform('color', [dx, dy, 0.0]);
        splatProgram.setUniform('point', [x, y]);
        splatProgram.setUniform('radius', radius);    
        splatProgram.setUniform('waveformTex', waveformTex);
        
        velocity[1].begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        velocity[1].end();
        [velocity[0], velocity[1]] = [velocity[1], velocity[0]];
    };

    const addColor = (x, y, color) => {
        p.shader(colorSplatProgram);
        colorSplatProgram.setUniform('uTarget', dye[0]);
        colorSplatProgram.setUniform('aspectRatio', simWidth/simHeight);
        colorSplatProgram.setUniform('color', color);
        colorSplatProgram.setUniform('point', [x, y]);
        colorSplatProgram.setUniform('radius', radius);
        colorSplatProgram.setUniform('waveformTex', waveformTex);
        
        dye[1].begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        dye[1].end();
        [dye[0], dye[1]] = [dye[1], dye[0]];
    };

    const updateFPS = () => {
        fpsArray.push(p.frameRate());
        if (fpsArray.length > fpsArraySize) fpsArray.shift();
        const averageFPS = fpsArray.reduce((sum, value) => sum + value, 0) / fpsArray.length;
        fps.html('FPS: ' + averageFPS.toFixed(2));
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
        //simWidth = 256;//p.width / DOWNSAMPLE;
        //simHeight = 512;// p.height / DOWNSAMPLE;

        // Recreate framebuffers at new size
        velocity = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        pressure = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        divergence = p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB });

        // Add dye buffer resize
        dye = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
    };
};

module.exports = sketch; 