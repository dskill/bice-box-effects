// Inspired by https://www.shadertoy.com/view/tXlXDX
// Created by Xor, adapted for p5.js by Drew, 2024-06-10

const simWidth = 256;
const simHeight = 256;

const vertexShader = `
    attribute vec3 aPosition;
    void main() {
        gl_Position = vec4(aPosition, 1.0);
    }
`;

const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

// "Ripples" by @XorDev, adapted for p5.js GLSL ES 1.0
// https://www.shadertoy.com/view/tXlXDX

uniform vec2 u_resolution;
uniform float u_time;
uniform float u_rms;
uniform vec2 u_mouse;

// GLSL ES 1.0 does not have tanh, so we implement it
float tanh_f(float x) {
    float e1 = exp(x);
    float e2 = exp(-x);
    return (e1 - e2) / (e1 + e2);
}
vec4 tanh_v4(vec4 v) {
    return vec4(tanh_f(v.x), tanh_f(v.y), tanh_f(v.z), tanh_f(v.w));
}

void main() {
    vec2 u = gl_FragCoord.xy;
    vec4 o = vec4(0.0);
    float i = 0.0, d = 0.0, s = 0.0;
    // Center on mouse
    vec2 center = u_mouse;
    // Raymarch 100 steps
    for (int j = 0; j < 30; ++j) {
        i = float(j) + 1.0;
        vec3 p = d * normalize(vec3(u + u - center * 2.0, 0.0) - vec3(u_resolution.x, u_resolution.y, u_resolution.x));
        // Inner loop for ripples
        s = 0.1;
        for (int k = 0; k < 5; ++k) { // Unroll s loop for GLSL ES 1.0 compatibility
            if (s < 1.0) {
                p -= dot(sin(p * s * 16.0), vec3(0.01)) / s;
                float angle = 0.3 * u_time + float(j) * 1.0; // Note: original was 0.3*iTime+vec4(0,33,11,0) - simplified for now
                mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
                p.xz = rot * p.xz;
                s += s;
            }
        }
        d += s = 0.01 + abs(p.y);
        o += (1.0 + cos(d + vec4(4.0, 2.0, 1.0, 0.0))) / s;
    }
    o = tanh_v4(o / 1000.0);
    gl_FragColor = o;
}
`;

const sketch = function (p) {
    let renderBuffer;
    let bufferShader;
    let accumulatedRMS = 0;
    p.rmsOutput = 0;

    p.preload = () => {
        // Shader creation moved to setup()
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        
        renderBuffer = p.createGraphics(simWidth, simHeight, p.WEBGL);
        bufferShader = renderBuffer.createShader(vertexShader, fragmentShader);
        
        p.frameRate(60);
        p.noStroke(); // For main canvas
        renderBuffer.noStroke(); // For buffer
    };

    p.draw = () => {
        // Simulate RMS for testing if not present
        if (!p.rmsOutput || p.rmsOutput === 0) {
            p.rmsOutput = 0;
        }
        accumulatedRMS += (p.rmsOutput != null ? p.rmsOutput : 0.0) * 0.1; // scale for visual effect
        let scrollValue = accumulatedRMS;
        // If RMS is not changing, use u_time for scroll
        if (p.rmsOutput === 0) {
            scrollValue = p.millis() / 1000.0;
        }

        // Calculate mouse coordinates scaled to the buffer resolution
        let mx = p.mouseX * (simWidth / p.width);
        let my = (p.height - p.mouseY) * (simHeight / p.height); // Invert Y for GLSL

        // Set uniforms for the buffer shader
        renderBuffer.shader(bufferShader);
        renderBuffer.background(0);
        bufferShader.setUniform('u_resolution', [simWidth, simHeight]);
        bufferShader.setUniform('u_time', p.millis() / 1000.0);
        bufferShader.setUniform('u_rms', scrollValue); // u_rms is not directly used in this shader, but kept for consistency
        bufferShader.setUniform('u_mouse', [mx, my]);
        
        // Draw into the buffer
        renderBuffer.quad(-1, -1, 1, -1, 1, 1, -1, 1);

        // Draw the buffer to the main canvas
        p.background(0); // Clear main canvas
        p.image(renderBuffer, -p.width/2, -p.height/2, p.width, p.height);
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
        // The renderBuffer size remains fixed at simWidth x simHeight
    };
};

module.exports = sketch; 