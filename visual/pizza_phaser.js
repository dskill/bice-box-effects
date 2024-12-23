const vertexShader = `
    attribute vec3 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        vec4 positionVec4 = vec4(aPosition, 1.0);
        positionVec4.xy = positionVec4.xy;
        vTexCoord = aTexCoord;
        gl_Position = positionVec4;
    }
`;

const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

#define PI 3.14159265359

uniform sampler2D u_waveform;
uniform sampler2D u_fft;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_rms;

varying vec2 vTexCoord;

// Function to create a spiral pattern
float spiral(vec2 st, float t) {
    vec2 pos = st - vec2(0.5);
    float r = length(pos) * 2.0;
    float a = atan(pos.y, pos.x);
    float n = fract((r + t) * 1.0 - a / PI / 2.0);
    return smoothstep(0.4, 0.5, n);
}

// Function to create a pizza slice shape
float pizzaSlice(vec2 st, float angle) {
    vec2 pos = st - vec2(0.5);
    float r = length(pos);
    float a = atan(pos.y, pos.x) + PI;
    float slice = step(0.0, cos(a * 8.0 + u_time)) * step(r, 0.5);
    return slice * (1.0 - step(0.48, r));
}

void main() {
    vec2 st = vTexCoord;
    vec3 color = vec3(0.0);
    
    // Sample waveform data
    float wave = texture2D(u_waveform, vec2(st.x, 0.0)).r;
    
    // Create spiral effect
    float spiralPattern = spiral(st, u_time * 0.5);
    
    // Create pizza slice pattern
    float pizza = pizzaSlice(st, u_time);
    
    // Create circular waveform
    vec2 pos = st - vec2(0.5);
    float angle = atan(pos.y, pos.x);
    float radius = length(pos);
    float waveCircle = texture2D(u_waveform, vec2(angle / (2.0 * PI), 0.0)).r;
    float circle = smoothstep(0.3 + waveCircle * 0.1, 0.31 + waveCircle * 0.1, radius);
    
    // Combine effects
    vec3 finalColor = vec3(0.0);
    finalColor += vec3(1.0, 0.3, 0.1) * spiralPattern; // Red spiral
    finalColor += vec3(1.0, 0.6, 0.2) * pizza; // Orange pizza
    finalColor += vec3(0.2, 0.8, 1.0) * (1.0 - circle); // Blue circle
    
    // Add some movement based on RMS
    finalColor *= 1.0 + u_rms * 0.5;
    
    // Add glow
    float glow = pow(u_rms, 2.0) * 0.5;
    finalColor += vec3(1.0, 0.8, 0.4) * glow;
    
    gl_FragColor = vec4(finalColor, 1.0);
}
`;

const sketch = function(p) {
    let shader;
    let waveformTex;
    let fftTex;
    
    p.waveform1 = [];
    p.fft0 = [];
    p.rmsOutput = 0;

    p.preload = () => {
        shader = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.imageMode(p.CENTER);

        // Create textures
        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        fftTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        fftTex.pixelDensity(1);
        waveformTex.noSmooth();
        fftTex.noSmooth();
    };

    p.draw = () => {
        p.background(0);

        if (p.waveform1.length === 0) {
            p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i * 0.1) * 0.5);
        }

        if (p.fft0.length === 0) {
            p.fft0 = new Array(512).fill(0);
        }

        // Update waveform texture
        waveformTex.loadPixels();
        for (let i = 0; i < p.waveform1.length; i++) {
            let val = (p.waveform1[i] * 0.5 + 0.5) * 255.0;
            waveformTex.pixels[i * 4] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        // Update FFT texture
        fftTex.loadPixels();
        for (let i = 0; i < p.fft0.length / 2; i++) {
            const real = p.fft0[2 * i];
            const imag = p.fft0[2 * i + 1];
            let magnitude = Math.sqrt(real * real + imag * imag);
            magnitude = Math.log(magnitude + 1) / Math.log(10);
            let val = magnitude * 100.0;
            fftTex.pixels[i * 4] = val;
            fftTex.pixels[i * 4 + 1] = val;
            fftTex.pixels[i * 4 + 2] = val;
            fftTex.pixels[i * 4 + 3] = 255;
        }
        fftTex.updatePixels();

        shader.setUniform('u_waveform', waveformTex);
        shader.setUniform('u_fft', fftTex);
        shader.setUniform('u_resolution', [p.width, p.height]);
        shader.setUniform('u_time', p.millis() / 1000.0);
        shader.setUniform('u_rms', p.rmsOutput);

        p.shader(shader);
        p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch; 