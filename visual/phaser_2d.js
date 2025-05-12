// from https://www.shadertoy.com/view/7sBfDD
// Created by SnoopethDuckDuck in 2022-03-0
const vertexShader = `
    attribute vec3 aPosition;
    attribute vec2 aTexCoord;
    // varying vec2 vTexCoord; // Commented out
    void main() {
        vec4 positionVec4 = vec4(aPosition, 1.0);
        // vTexCoord = aTexCoord; // Commented out
        gl_Position = positionVec4;
    }
`;

const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

#define pi 3.14159

// varying vec2 vTexCoord; // Commented out
uniform vec2 u_resolution;
uniform float u_time;
uniform vec2 u_mouse;
uniform sampler2D u_waveform;
uniform float u_rms;

mat2 Rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

vec3 pal(in float t, in vec3 a, in vec3 b, in vec3 c, in vec3 d) {
    return a + b*cos( 6.28318*(c*t+d) );
}

// mainImage becomes main, fragColor becomes gl_FragColor
void main() {
    vec2 uv = (gl_FragCoord.xy - 0.5 * u_resolution.xy) / u_resolution.y;
    vec2 ms = (u_mouse - 0.5 * u_resolution.xy) / u_resolution.y;
    float waveformVal = texture2D(u_waveform, vec2(clamp(uv.x + 0.5, 0.0, 1.0), 0.5)).x * 2.0 - 1.0;
    ms.x += waveformVal * 0.3;
    
    float A = mix(-1.0, 1.0, u_rms);    // -1. // 0.
    float r = mix(0.2, 1.5, u_rms);   // 0.6
    float th = mix(0.01, 0.1, u_rms); // 0.12
    
    vec2 dir = uv - ms;
    float a = atan(dir.x, dir.y);
    float s = 0.;
    
    const float n_val_for_calc = 20.0; // For calculations where 'n' was used
    const int num_loop_iterations = 20; // For the loop itself

    float k = 6./u_resolution.y;
    
    for (int iter = 0; iter < num_loop_iterations; ++iter) {
        float i_loop_val = n_val_for_calc - float(iter); // This makes i_loop_val go 20, 19, ..., 1

        float io = A * 2. * pi * i_loop_val / n_val_for_calc;
        float sc = -4. - 0.5 * i_loop_val + 0.9 * cos(io - 9. * length(dir) + u_time);
        vec2 fpos = fract(sc * uv + 0.5 * i_loop_val * ms) - 0.5;
        //fpos = abs(fpos) - 0.25;
        fpos *= Rot(a); // a + io // 5. * a // a + 3. * atan(fpos.x, fpos.y)
        float d = abs(fpos.x);
        s *= 0.865;
        s += step(0., s) * smoothstep(-k, k, -abs(d - r) + th); 
    }
    
    float val = s * 0.1 + 0.72 + 0. * u_time - 0.23 * pow(dot(dir,dir), 0.25);
    val = clamp(val, 0.4, 1.);
    vec3 e = vec3(1);
    vec3 col = 0.5 * pal(val, e, e, e, 0.24 * vec3(0,1,2)/3.);   
    col = smoothstep(0., 1., col);
    
    gl_FragColor = vec4(col,1.0);
}
`;

const sketch = function (p) {
    let shader;
    let waveformTex;
    p.waveform1 = [];
    p.rmsOutput = 0;

    p.preload = () => {
        // Shader creation moved to setup()
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        
        shader = p.createShader(vertexShader, fragmentShader);
        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        // waveformTex.noSmooth();
        p.frameRate(60);
        p.noStroke();
    };

    p.draw = () => {
        p.background(0);

        // Fill waveformTex from p.waveform1
        if (p.waveform1.length === 0) {
            p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i*0.1)*0.5);
        }
        waveformTex.loadPixels();
        for (let i = 0; i < p.waveform1.length; i++) {
            let val = (p.waveform1[i]*.5 +.5) * 255.0;
            waveformTex.pixels[i * 4] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        p.shader(shader);
        shader.setUniform('u_resolution', [p.width, p.height]);
        shader.setUniform('u_time', p.millis() / 1000.0);
        shader.setUniform('u_mouse', [p.mouseX, p.height - p.mouseY]);
        shader.setUniform('u_waveform', waveformTex);
        shader.setUniform('u_rms', p.rmsOutput != null ? p.rmsOutput : 0.0);
        
        p.quad(-1, 1, 1, 1, 1, -1, -1, -1);

        // Send OSC message with normalized mouse coordinates
        if (p.sendOscToSc) {
            let x_val = p.mouseX / p.width;
            let y_val = (p.height - p.mouseY) / p.height; // Invert Y as p5's Y is top-to-bottom and normalize
            // Clamp values to ensure they are strictly within 0-1, 
            // as phaser_2d.sc uses linlin and linexp which might behave unexpectedly at exact boundaries
            // or if values go slightly out of bounds due to floating point inaccuracies.
            x_val = Math.min(Math.max(x_val, 0.0001), 0.9999);
            y_val = Math.min(Math.max(y_val, 0.0001), 0.9999);
            p.sendOscToSc('/params', x_val, y_val);
        }
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch; 