// from https://www.shadertoy.com/view/wctGz8
// Created by Xor in 2025-05-09

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

// "Simplex" by @XorDev
// Adapted for p5.js GLSL ES 1.0

uniform vec2 u_resolution;
uniform float u_time;
uniform float u_rms;
uniform vec2 u_mouse;

void main() {
    vec2 I = gl_FragCoord.xy;
    vec4 O = vec4(0.0);
    float i = 0.0;
    float z = 0.0;
    float d = 0.0;
    // Center on mouse
    vec2 center = u_mouse;
    // Raymarch 10 steps
    for(int j = 0; j < 30; ++j) {
        i = float(j);
        // Compute raymarch point from raymarch distance and ray direction
        vec3 p = z * normalize(vec3(I - center, 0.0) - vec3(u_resolution.x/2.0, u_resolution.y/2.0, u_resolution.y/2.0));
        p.z -= u_time + u_rms * 100.0;
        

        // Temporary vector for sine waves
        vec3 v = cos(p) - sin(p).yzx;
        // Scroll forward based on accumulated RMS
        // Compute distance for sine pattern (and step forward)
        z += d = 1e-4 + 0.5 * length(max(v, v.yzx * 0.2));
        // Use position for coloring
        O.rgb += (cos(p) + 1.2) / d;
    }
    // Tonemapping
    O /= O + 1e3;
    gl_FragColor = O;
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
        // console.log('accumulatedRMS:', accumulatedRMS); // Commented out for less console noise
        let scrollValue = accumulatedRMS;
        // If RMS is not changing, use u_time for scroll
        if (p.rmsOutput === 0) {
            scrollValue = p.millis() / 1000.0;
        }

        // Calculate mouse coordinates scaled to the buffer resolution
        let mx = p.mouseX * (simWidth / p.width);
        let my = (p.height - p.mouseY) * (simHeight / p.height); // Invert Y for GLSL

        // Set uniforms for the buffer shader
        renderBuffer.background(0);
        renderBuffer.shader(bufferShader);
        bufferShader.setUniform('u_resolution', [simWidth, simHeight]);
        bufferShader.setUniform('u_time', p.millis() / 1000.0);
        bufferShader.setUniform('u_rms', scrollValue);
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