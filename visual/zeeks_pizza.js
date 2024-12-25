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

#define SAMPLES 15.0
#define PI 3.14159265359

uniform sampler2D u_waveform;
uniform sampler2D u_fft;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_rms;

varying vec2 vTexCoord;

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 ba = b-a;
    vec2 pa = p-a;
    float h = clamp(dot(pa,ba)/dot(ba,ba), 0.0, 1.0);
    return length(pa - ba*h);
}

float sdBox( in vec2 p, in vec2 b ) {
    vec2 d = abs(p)-b;
    return length(max(d,0.0)) + min(max(d.x,d.y),0.0);
}

float sdSound(vec2 uv) {
    float waveformValue = (texture2D(u_waveform, vec2(uv.x, 0.0) ).x - 0.5) * 2.0;
    waveformValue *= .2;
    waveformValue *= 1.0 - abs(pow(abs(uv.x - .5)*2.0, 2.5));

    float lineOffset = uv.y - waveformValue;
    lineOffset += .15;
    float line = 1.0 - abs(lineOffset) * 1.0;
    line = abs(line);
    float milkyLine = pow(line, .2)*.2;
    milkyLine += pow(line, 10.0)*.3;
    milkyLine += pow(line, 5000.0)*30.0;

    return milkyLine;
}

float sdFFT(vec2 uv) {
    // Sample FFT data - already processed with sqrt(real^2 + imag^2) and log scaling
    float fftValue = texture2D(u_fft, vec2(uv.x, 0.0)).x;
    fftValue *= .2;
    
    // Scale and position the FFT visualization
    float lineOffset = (uv.y) + (fftValue); // Adjust scaling and position
    lineOffset -= .15;
    float line = 1.0 - abs(lineOffset) * 1.0;
    line = abs(line);
    // Create a similar "milky" glow effect as the waveform
    float milkyLine = pow(line, .2)*.2;
    milkyLine += pow(line, 10.0)*.6;
    milkyLine += pow(line, 5000.0)*30.0;

    return milkyLine;
}

vec2 cube(vec2 uv) {
    return mod((uv+.5)*8., vec2(1))-.5;
}

void main() {
    vec2 uv = vTexCoord;
    uv.y -= 0.5;
    
    vec3 col = vec3(0.0);
    
    // Add waveform effect
    float wave = sdSound(uv);
    col += mix(col, vec3(0.504,0.184,0.196), wave);
    
    // Add FFT effect
    float fft = sdFFT(uv);
    col += mix(col, vec3(0.384,0.804,0.196), fft);
    
    col += .1*mix(col, vec3(0.031,0.031,0.031), float(sdBox(cube(uv), vec2(.49)) <= 0.));

    // Add vignette
    vec2 puv = vTexCoord;
    puv *= 1.0 - puv.yx;
    col *= pow(puv.x*puv.y*30.0, 0.5);
    
    col *= vec3(1.0, 0.667, 1.0);
    gl_FragColor = vec4(col, 1.0);
}
`;

const sketch = function (p) {
    let shader;
    let waveformTex;
    let fftTex;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    
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
        // actual OSC transmisison is 4096 samples, but we downsample to 512 for visualisation
        fftTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        fftTex.pixelDensity(1);
        waveformTex.noSmooth();
        fftTex.noSmooth();

        fps = p.createP('');
        fps.style('color', '#FFFFFF');
        fps.style('font-family', '-apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, Arial, sans-serif');
        fps.style('font-size', '10px');
        fps.style('position', 'fixed');
        fps.style('bottom', '3px');
        fps.style('left', '3px');
        fps.style('margin', '0');
        
        p.frameRate(60);
        p.noStroke();
    };

    p.draw = () => {
        p.background(0);

        if (p.waveform1.length === 0) {
            p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i*0.1)*0.5);
        }

        if (p.fft0.length === 0) {
            p.fft0 = new Array(512).fill(0);
        }

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

        // Update FFT texture with proper complex FFT handling
        fftTex.loadPixels();
        const fftSize = p.fft0.length / 2; // Each FFT bin has real and imaginary parts
        for (let i = 0; i < fftSize; i++) {
            const real = p.fft0[2 * i];
            const imag = p.fft0[2 * i + 1];
            let magnitude = Math.sqrt(real * real + imag * imag);
            // Apply logarithmic scaling like in waveform_with_fft_simple.js
            magnitude = Math.log(magnitude + 1) / Math.log(10);
            // Scale to 0-255 range for texture
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

        updateFPS();
    };

    const updateFPS = () => {
        fpsArray.push(p.frameRate());
        if (fpsArray.length > fpsArraySize) {
            fpsArray.shift();
        }
        const averageFPS = fpsArray.reduce((sum, value) => sum + value, 0) / fpsArray.length;
        fps.html('FPS: ' + averageFPS.toFixed(2));
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;
