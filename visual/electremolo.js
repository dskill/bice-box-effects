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
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_rms;
uniform float u_electremoloData[3];
varying vec2 vTexCoord;

float sdSegment(vec2 p, vec2 a, vec2 b) {
    vec2 ba = b-a;
    vec2 pa = p-a;
    float h = clamp(dot(pa,ba)/dot(ba,ba), 0.0, 1.0);
    return length(pa - ba*h);
}
float sdBox( in vec2 p, in vec2 b )
{
    vec2 d = abs(p)-b;
    return length(max(d,0.0)) + min(max(d.x,d.y),0.0);
}

vec2 getWaveformPoint(float t) {
    float sample = texture2D(u_waveform, vec2(t, 0.0)).r * 2.0;

    sample *=  1.0 - abs(t - .5) * 8.0 * (u_electremoloData[1] * u_electremoloData[2]) ;
    sample -= 0.65;
    return vec2(t * 2.0 - 1.0, sample);
}

float sdSound(vec2 uv) {
    float hits = 0.0;
    float prevT = 0.0;
    
    vec2 prev = getWaveformPoint(0.0);
    
    for(float i = 1.0; i < SAMPLES; i++) {
        float t = i / SAMPLES;
        vec2 curr = getWaveformPoint(t);
        
        hits += min(1.0, 1.0 / (sdSegment(uv, prev, curr) * 2500.0));

        prev = curr;
    }
    
    return ( u_rms * 20.0 + 1.0)*200.0 * hits/SAMPLES;
}

vec2 cube(vec2 uv) {
    return mod((uv+.5)*8., vec2(1))-.5;
}


void main() {
    vec2 uv = vTexCoord * 2.0 - 1.0;
    // rotate 90 cause it looks cooler
    uv.xy = vec2(uv.y, uv.x);
    
    vec3 col = vec3(0.0);
    float totalTremolo = u_electremoloData[0] * u_electremoloData[1] * u_electremoloData[2];
    // Add oscilloscope effect
    uv.y = abs(uv.y);
    uv.y += sin(uv.y * 10.0)*.1;
    uv.y = uv.y - (totalTremolo)*0.1;
    float wave = sdSound(uv*.75);
    col = mix(col, vec3(0.404,0.984,0.396), wave);
    
    //col = mix(col, vec3(0.000,0.000,0.000), 1.-length(uv));
    //col = mix(col, vec3(0.031,0.031,0.031), float(sdBox(cube(uv), vec2(.49)) <= 0.));


    // Add vignette
    vec2 puv = vTexCoord;
    puv *= 1.0 - puv.yx;
    col *= pow(puv.x*puv.y*30.0, 0.5);
    
    // Add glow
    col *= vec3(0.0, 0.667, 1.0) * .5;//+ (totalTremolo*0.1);
  
    gl_FragColor = vec4(col, 1.0);
}
`;

const sketch = function (p) {
    let shader;
    let waveformTex;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    
    // Add accumulation buffer
    let accumulatedWaveform = [];
    const decayFactor = 0.8; // Adjust this value to control decay speed (0-1)

    p.waveform1 = [];
    p.rmsOutput = 0;

    p.preload = () => {
        shader = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.imageMode(p.CENTER);

        // Create waveform texture
        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        waveformTex.noSmooth();

        fps = p.createP('');
        fps.style('color', '#444444');
        fps.style('font-family', '-apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, Arial, sans-serif');
        fps.style('font-size', '10px');
        fps.style('position', 'fixed');
        fps.style('bottom', '3px');
        fps.style('left', '3px');
        fps.style('margin', '0');
        
        p.frameRate(60);
        p.noStroke();
        
        // Initialize accumulation buffer
        accumulatedWaveform = new Array(512).fill(0);
    };

    p.draw = () => {
        p.background(0);

        if (p.waveform1.length === 0) {
            p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i*0.1)*0.5);
        }

        // Update waveform texture using accumulated values
        waveformTex.loadPixels();
        for (let i = 0; i < p.waveform1.length; i++) {
            //let clampedValue = Math.max(-1, Math.min(1, accumulatedWaveform[i]));
           //let clampedValue = Math.max(-1, Math.min(1, p.waveform1[i]));
            let val = (p.waveform1[i]*.5 +.5) * 255.0;// p.map( p.waveform1[i], -10, 10, -255, 255);
            waveformTex.pixels[i * 4] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        shader.setUniform('u_waveform', waveformTex);
        shader.setUniform('u_resolution', [p.width, p.height]);
        shader.setUniform('u_time', p.millis() / 1000.0);
        shader.setUniform('u_rms', p.rmsOutput);
        if (p.customMessage && p.customMessage.values.length == 3) {
            shader.setUniform('u_electremoloData', p.customMessage.values);
        }
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
