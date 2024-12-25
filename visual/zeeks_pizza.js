const vertexShader = `
    attribute vec3 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        vec4 positionVec4 = vec4(aPosition, 1.0);
        vTexCoord = aTexCoord;
        gl_Position = positionVec4;
    }
`;

const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

#define PI 3.141592654

uniform sampler2D u_waveform;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_rms;

varying vec2 vTexCoord;

float getColor(vec2 fragCoord) {
    // Normalized pixel coordinates (from 0 to 1)
    vec2 uv = (fragCoord - 0.5 * u_resolution.xy)/u_resolution.y;
    
    // Incorporate waveform
    float waveVal = texture2D(u_waveform, vec2(vTexCoord.x, 0.0)).r * 2.0 - 1.0;
    //t += waveVal * 5.0;
    uv.y += waveVal * 0.1;

    uv *= 10.;
    uv += 0.5;
    uv *= mat2(sin(PI/4.), cos(PI/4.), cos(PI/4.), -sin(PI/4.));

    vec2 gv = fract(uv) - 0.5;
    vec2 id = floor(uv);
    
    float sharpFactor = 0.;
    float t = -u_time * 1. + 13.;
    
    
    
    for (float x = -1.; x <= 1.; x++) {
        for (float y = -1.; y <= 1.; y++) {
            vec2 delta = vec2(x, y);
            float d = length(gv-delta);
            float r = mix(0.3, 1.5, sin(t + length(id+delta)*0.3)*0.5+0.5);
            sharpFactor += 1.-step(r, d);
        }
    }
    
    return mod(sharpFactor, 2.);
}

void main() {
    vec2 fragCoord = vTexCoord * u_resolution;
    vec3 col = vec3(getColor(fragCoord));

    // Add vignette
    vec2 puv = vTexCoord;
    puv *= 1.0 - puv.yx;
    col *= pow(puv.x*puv.y*30.0, 0.5);
    
    col *= vec3(1.0, 0.667, .5);

    // Output to screen
    gl_FragColor = vec4(col.r, col.g, col.b, 1.0);
}
`;

const sketch = function (p) {
    let shaderProgram;
    let waveformTex;
    let fftTex;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    let amplitudeTime = 0;
    
    p.waveform1 = [];
    p.fft0 = [];
    p.rmsOutput = 0;

    p.preload = () => {
        // Create our new shader using the updated fragmentShader:
        shaderProgram = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.imageMode(p.CENTER);

        // Create textures for waveform & FFT:
        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        fftTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        fftTex.pixelDensity(1);
        waveformTex.noSmooth();
        fftTex.noSmooth();

        // Simple fps output for debugging
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

        // If no audio data is provided externally yet, create some dummy waveform data:
        if (p.waveform1.length === 0) {
            p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i * 0.1) * 0.5);
        }
        // If no FFT data is provided externally yet, create some dummy data:
        if (p.fft0.length === 0) {
            p.fft0 = new Array(512).fill(0);
        }

        amplitudeTime += p.rmsOutput;

        // Populate the waveform texture:
        waveformTex.loadPixels();
        for (let i = 0; i < p.waveform1.length; i++) {
            let val = (p.waveform1[i] * 0.5 + 0.5) * 255.0;
            waveformTex.pixels[i * 4 + 0] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        // Populate the FFT texture (if you wish to use it further in the shader or expansions):
        fftTex.loadPixels();
        const fftSize = p.fft0.length / 2; // Each FFT bin has real & imaginary parts
        for (let i = 0; i < fftSize; i++) {
            const real = p.fft0[2 * i];
            const imag = p.fft0[2 * i + 1];
            let magnitude = Math.sqrt(real * real + imag * imag);
            magnitude = Math.log(magnitude + 1.0) / Math.log(10.0); 
            let val = magnitude * 100.0;
            fftTex.pixels[i * 4 + 0] = val;
            fftTex.pixels[i * 4 + 1] = val;
            fftTex.pixels[i * 4 + 2] = val;
            fftTex.pixels[i * 4 + 3] = 255;
        }
        fftTex.updatePixels();

        // Send uniforms to our new shader:
        shaderProgram.setUniform('u_waveform', waveformTex);
        shaderProgram.setUniform('u_resolution', [p.width, p.height]);
        shaderProgram.setUniform('u_time', amplitudeTime); //p.millis() / 1000.0);
        shaderProgram.setUniform('u_rms', p.rmsOutput);

        p.shader(shaderProgram);

        // Render full-screen quad:
        p.quad(-1, 1, 1, 1, 1, -1, -1, -1);

        updateFPS();
    };

    // Simple rolling-average FPS counter:
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