// Inspired by https://www.shadertoy.com/view/tXlXDX
// Created by Xor, adapted for p5.js by Drew, 2024-06-10

const simWidth = 256;
const simHeight = 256;

const vertexShader = `
    attribute vec3 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;

    void main() {
        gl_Position = vec4(aPosition, 1.0);
        vTexCoord = aTexCoord;
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
uniform float u_accumulatedRms;
uniform vec2 u_mouse;
varying vec2 vTexCoord;

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
    vec2 uv_norm = vTexCoord;
    vec4 o = vec4(0.0);
    float d = 0.0; // Represents D from Shadertoy (accumulated distance)

    // Raymarch (Outer loop)
    // User snippet implies 100 iterations. Keeping 30 steps for mobile performance.
    for (int j = 0; j < 30; ++j) {
        // Original ray direction: vec3(2.0*u - u_resolution.xy, -u_resolution.x)
        // Which is vec3(2.0*u.x - u_resolution.x, 2.0*u.y - u_resolution.y, -u_resolution.x)
        vec3 p_direction = vec3((2.0*uv_norm - 1.0) * u_resolution.xy, -u_resolution.x);
        vec3 p = d * normalize(p_direction);

        // Inner loop for ripples
        // Original Shadertoy: for (s = .1; s < 1.; s += s); -> s = 0.1, 0.2, 0.4, 0.8 (4 iterations)
        float s_inner = 0.1;
        for (int k = 0; k < 4; ++k) {
            // Original: p -= dot(sin(p * s * 16.), vec3(.01)) / s
            p -= dot(sin(p * s_inner * 16.0), vec3(0.01)) / s_inner;

            // Original: p.xz *= mat2(cos(.3*iTime+vec4(0,33,11,0)))
            // iTime -> u_time
            // mat2 constructor is column-major: mat2(col0.x, col0.y, col1.x, col1.y)
            // So, mat2(cos(t+e0), cos(t+e1), cos(t+e2), cos(t+e3)) becomes:
            // [ cos(t+e0)  cos(t+e2) ]
            // [ cos(t+e1)  cos(t+e3) ]
            float t_angle = 0.3 * u_time;
            mat2 rot_matrix = mat2(
                cos(t_angle),                    // cos(t_angle + 0.0)
                cos(t_angle + 33.0),
                cos(t_angle + 11.0),
                cos(t_angle)                     // cos(t_angle + 0.0)
            );
            p.xz = rot_matrix * p.xz; // Equivalent to p.xz *= rot_matrix in Shadertoy if v *= M means v = M * v

            s_inner += s_inner; // s_inner = s_inner * 2.0
        }

        // Original: d += s = .01 + abs(p.y); (here s is the distance step)
        float s_dist = 0.01 + abs(p.y);
        d += s_dist;

        // Original: o += (1.+cos(d+vec4(4,2,1,0))) / s; (here s is s_dist)
        o += (1.0 + cos(d + vec4(4.0, 2.0, 1.0, 0.0))) / s_dist;
    }

    // Original: o = tanh(o / 6e3);
    o = tanh_v4(o / 6000.0);
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
        bufferShader.setUniform('u_accumulatedRms', scrollValue); // u_rms is not directly used in this shader, but kept for consistency
        bufferShader.setUniform('u_rms', p.rmsOutput); // u_rms is not directly used in this shader, but kept for consistency
        
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