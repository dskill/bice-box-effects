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
    uniform vec2 point;
    uniform float radius;
    uniform sampler2D waveformTex;

    void main () {
        // Mirror the UV coordinates
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);
        
        float waveform = texture2D(waveformTex, vec2(mirrorUv.x, 0.5)).x * 2.0 - 1.0;
        float distance_from_center = abs(pow(1.0-mirrorUv.y, 30.0));
        vec2 splatForce;
        splatForce.y = 50.0 * abs(waveform) * distance_from_center;
        splatForce.x = 10.0 * waveform * distance_from_center;

        // Add mirrored wind tunnel effect
        splatForce.y += smoothstep(0.5, 1.0, mirrorUv.y) * 0.1 * abs(sin(mirrorUv.x * 3.14158 + 3.14158));

        vec2 baseVel = texture2D(uTarget, mirrorUv).xy * 2.0 - 1.0;
        baseVel += splatForce;
        baseVel = clamp(baseVel, -1.0, 1.0);
        gl_FragColor = vec4(0.5 + 0.5 * baseVel, 0.0, 1.0);
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
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);
        
        vec2 coord = mirrorUv - dt * (texture2D(uVelocity, mirrorUv).xy * 2.0 - 1.0) * texelSize;
        vec2 result = texture2D(uSource, coord).xy * 2.0 - 1.0;
        gl_FragColor = vec4(0.5 + 0.5 * (dissipation * result), 0.0, 1.0);
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
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);
        
        float L = texture2D(uVelocity, vec2(abs(vL.x * 2.0 - 1.0), vL.y)).x * 2.0 - 1.0;
        float R = texture2D(uVelocity, vec2(abs(vR.x * 2.0 - 1.0), vR.y)).x * 2.0 - 1.0;
        float T = texture2D(uVelocity, vec2(abs(vT.x * 2.0 - 1.0), vT.y)).y * 2.0 - 1.0;
        float B = texture2D(uVelocity, vec2(abs(vB.x * 2.0 - 1.0), vB.y)).y * 2.0 - 1.0;
        float div = 0.5 * (R - L + T - B);
        gl_FragColor = vec4(0.5 + div, 0.5, 0.5, 1.0);
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
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);

        if (mirrorUv.y > 0.99) {
            gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
            return;
        }

        float L = texture2D(uPressure, vec2(abs(vL.x * 2.0 - 1.0), vL.y)).x * 2.0 - 1.0;
        float R = texture2D(uPressure, vec2(abs(vR.x * 2.0 - 1.0), vR.y)).x * 2.0 - 1.0;
        float T = texture2D(uPressure, vec2(abs(vT.x * 2.0 - 1.0), vT.y)).x * 2.0 - 1.0;
        float B = texture2D(uPressure, vec2(abs(vB.x * 2.0 - 1.0), vB.y)).x * 2.0 - 1.0;
        float C = texture2D(uPressure, mirrorUv).x * 2.0 - 1.0;
        float divergence = texture2D(uDivergence, mirrorUv).x * 2.0 - 1.0;
        float pressure = (L + R + T + B - divergence) * 0.25;
        gl_FragColor = vec4(0.5 + pressure * 0.5, 0.5, 0.5, 1.0);
    }
`;

const displayShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    uniform sampler2D uTexture;
    uniform float u_rms;
    uniform sampler2D waveformTex;
    uniform float mirror;

    void main () {
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);
        
        float waveform = texture2D(waveformTex, mirrorUv).x * 2.0 - 1.0;
        vec3 color = texture2D(uTexture, mirrorUv).rgb;
        
        // Convert to pink-based color scheme
        float intensity = max(color.r, max(color.g, color.b));
        vec3 pink = vec3(1.0, 0.4, 0.7); // Hot pink base
        color = mix(color, pink * intensity, 0.8);
        
        // Add some shimmer based on waveform
        color += vec3(0.1, 0.05, 0.08) * abs(waveform);
        
        // Enhance the mirror effect with a subtle gradient
        color *= 1.0 + 0.2 * smoothstep(0.4, 0.6, abs(vUv.x - 0.5));
        
        color = pow(color, vec3(0.7));
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
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);
        
        float L = texture2D(uPressure, vec2(abs(vL.x * 2.0 - 1.0), vL.y)).x * 2.0 - 1.0;
        float R = texture2D(uPressure, vec2(abs(vR.x * 2.0 - 1.0), vR.y)).x * 2.0 - 1.0;
        float T = texture2D(uPressure, vec2(abs(vT.x * 2.0 - 1.0), vT.y)).x * 2.0 - 1.0;
        float B = texture2D(uPressure, vec2(abs(vB.x * 2.0 - 1.0), vB.y)).x * 2.0 - 1.0;
        vec2 velocity = texture2D(uVelocity, mirrorUv).xy * 2.0 - 1.0;
        velocity.xy -= vec2(R - L, T - B);
        gl_FragColor = vec4(0.5 + velocity * 0.5, 0.0, 1.0);
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
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);
        
        vec2 coord = mirrorUv - dt * (texture2D(uVelocity, mirrorUv).xy * 2.0 - 1.0) * texelSize;
        vec3 result = dissipation * texture2D(uSource, coord).rgb;
        gl_FragColor = vec4(result, 1.0);
    }
`;

const colorSplatShader = `
    precision highp float;
    precision highp sampler2D;

    varying vec2 vUv;
    uniform sampler2D uTarget;
    uniform float aspectRatio;
    uniform float radius;
    uniform float u_rms;
    uniform sampler2D waveformTex;

    vec3 hsv2rgb(vec3 c) {
        vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
        vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
        return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
    }

    void main () {
        vec2 mirrorUv = vUv;
        mirrorUv.x = abs(mirrorUv.x * 2.0 - 1.0);
        
        float waveform = texture2D(waveformTex, mirrorUv).x * 2.0 - 1.0;
        float dist = abs(mirrorUv.y - 0.15);
        dist *= 15.0;

        vec3 base = texture2D(uTarget, mirrorUv).rgb;
        
        float intensity = waveform;
        vec3 hsv = vec3(
            0.9 + abs(intensity) * 0.1, // Hue - pink range
            0.8 - abs(intensity) * 0.3,  // Saturation
            abs(intensity) * 2.0 + 0.025  // Value
        );
        vec3 color = hsv2rgb(hsv);
        
        vec3 splat = exp(-dist / radius) * color;
        gl_FragColor = vec4(base + splat, 1.0);
    }
`;

const sketch = (p) => {
    let simWidth, simHeight;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    let dt = 5.0;
    let radius = 0.15;
    let dye_dissipation = 0.998;
    let advection_dissipation = 0.97;

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

    let dye;
    let dyeProgram, colorSplatProgram;

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.pixelDensity(1);

        simWidth = 256;
        simHeight = 256;

        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);

        // Initialize shaders
        advectionProgram = p.createShader(baseVertexShader, advectionShader);
        divergenceProgram = p.createShader(baseVertexShader, divergenceShader);
        pressureProgram = p.createShader(baseVertexShader, pressureShader);
        displayProgram = p.createShader(baseVertexShader, displayShader);
        splatProgram = p.createShader(baseVertexShader, splatShader);
        gradientSubtractProgram = p.createShader(baseVertexShader, gradientSubtractShader);

        velocity = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        pressure = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        divergence = p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB });

        fps = p.createP('');
        fps.style('color', '#444444');
        fps.style('font-family', 'monospace');
        fps.style('position', 'fixed');
        fps.style('bottom', '10px');
        fps.style('left', '10px');

        dyeProgram = p.createShader(baseVertexShader, dyeShader);
        colorSplatProgram = p.createShader(baseVertexShader, colorSplatShader);

        dye = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
    };

    p.draw = () => {
        dt = 5.0;
        dye_dissipation = 0.999 * (p.params.gain * 0.02 + 0.98);
        advection_dissipation = 0.997 * (p.params.mirror * 0.05 + 0.95);

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
        advectionProgram.setUniform('dt', dt);
        advectionProgram.setUniform('dissipation', advection_dissipation);
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

        // Pressure step
        for (let i = 0; i < 4; i++) {
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

        // Dye advection step
        p.shader(dyeProgram);
        dyeProgram.setUniform('uVelocity', velocity[0]);
        dyeProgram.setUniform('uSource', dye[0]);
        dyeProgram.setUniform('texelSize', [1.0/simWidth, 1.0/simHeight]);
        dyeProgram.setUniform('dt', dt);
        dyeProgram.setUniform('dissipation', dye_dissipation);
        dye[1].begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        dye[1].end();
        [dye[0], dye[1]] = [dye[1], dye[0]];

        // Display
        p.shader(displayProgram);
        displayProgram.setUniform('uTexture', dye[0]);
        displayProgram.setUniform('u_rms', p.rmsOutput || 0.5);
        displayProgram.setUniform('waveformTex', waveformTex);
        displayProgram.setUniform('mirror', p.params.mirror || 0.5);
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);

        updateFPS();
        addForce();
        addColor();
    };

    const addForce = () => {
        p.shader(splatProgram);
        splatProgram.setUniform('uTarget', velocity[0]);
        splatProgram.setUniform('aspectRatio', simWidth/simHeight);
        splatProgram.setUniform('radius', radius);    
        splatProgram.setUniform('waveformTex', waveformTex);
        
        velocity[1].begin();
        p.quad(-1, -1, 1, -1, 1, 1, -1, 1);
        velocity[1].end();
        [velocity[0], velocity[1]] = [velocity[1], velocity[0]];
    };

    const addColor = () => {
        p.shader(colorSplatProgram);
        colorSplatProgram.setUniform('uTarget', dye[0]);
        colorSplatProgram.setUniform('aspectRatio', simWidth/simHeight);
        colorSplatProgram.setUniform('radius', radius);
        colorSplatProgram.setUniform('waveformTex', waveformTex);
        colorSplatProgram.setUniform('u_rms', p.rmsOutput);
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

        velocity = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        pressure = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
        divergence = p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB });

        dye = [
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB }),
            p.createFramebuffer({ width: simWidth, height: simHeight, format: p.RGB })
        ];
    };
};

module.exports = sketch; 