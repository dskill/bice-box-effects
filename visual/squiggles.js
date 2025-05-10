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
uniform vec2 u_mouse; // Added for mouse input

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
    
    // change me! (and uncomment for loop stuff below)
    float A = 1.;    // -1. // 0.
    float r = 0.3;   // 0.6
    float th = 0.02; // 0.12
    
    vec2 dir = uv - ms;
    float a = atan(dir.x, dir.y);
    float s = 0.;
    
    // n is higher than it needs to be but works fine
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
    // waveformTex is no longer needed for this shader
    // p.waveform1 = []; // No longer needed
    // p.rmsOutput = 0; // No longer needed

    p.preload = () => {
        // Shader creation moved to setup()
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        
        shader = p.createShader(vertexShader, fragmentShader);

        console.log('[DEBUG] Shader object immediately after p.createShader:', shader);

        if (shader && shader._renderer && shader._renderer.GL) {
            const gl = shader._renderer.GL;
            let shadersCompiled = true;

            // Check Vertex Shader
            if (shader._vertShader && shader._vertShader !== -1 && typeof shader._vertShader === 'object') {
                console.log('[DEBUG] Vertex shader object appears valid:', shader._vertShader);
                if (!gl.getShaderParameter(shader._vertShader, gl.COMPILE_STATUS)) {
                    shadersCompiled = false;
                    console.error('Vertex Shader Compilation Error:', gl.getShaderInfoLog(shader._vertShader));
                } else {
                    console.log('[DEBUG] Vertex shader compiled successfully.');
                    let vsLog = gl.getShaderInfoLog(shader._vertShader);
                    if (vsLog) console.warn('Vertex Shader Log (may contain warnings/info):', vsLog);
                }
            } else {
                shadersCompiled = false;
                console.error('[DEBUG] Vertex shader object (shader._vertShader) is invalid or -1. Value:', shader._vertShader);
            }

            // Check Fragment Shader
            if (shader._fragShader && shader._fragShader !== -1 && typeof shader._fragShader === 'object') {
                console.log('[DEBUG] Fragment shader object appears valid:', shader._fragShader);
                if (!gl.getShaderParameter(shader._fragShader, gl.COMPILE_STATUS)) {
                    shadersCompiled = false;
                    console.error('Fragment Shader Compilation Error:', gl.getShaderInfoLog(shader._fragShader));
                } else {
                    console.log('[DEBUG] Fragment shader compiled successfully.');
                    let fsLog = gl.getShaderInfoLog(shader._fragShader);
                    if (fsLog) console.warn('Fragment Shader Log (may contain warnings/info):', fsLog);
                }
            } else {
                shadersCompiled = false;
                console.error('[DEBUG] Fragment shader object (shader._fragShader) is invalid or -1. Value:', shader._fragShader);
            }

            // Check Program Linking
            if (shadersCompiled && shader.program && shader.program !== 0 && typeof shader.program === 'object') {
                console.log('[DEBUG] Shader program object appears valid:', shader.program);
                if (!gl.getProgramParameter(shader.program, gl.LINK_STATUS)) {
                    console.error('Shader Program Linking Error:', gl.getProgramInfoLog(shader.program));
                } else {
                    console.log('[DEBUG] Shader program linked successfully.');
                    let programLog = gl.getProgramInfoLog(shader.program);
                    if (programLog) {
                        console.warn('Program Link Log (may contain warnings/info):', programLog);
                    }
                }
            } else if (shadersCompiled) { // Shaders might have compiled but program object is bad
                 console.error('[DEBUG] Shader program object (shader.program) is invalid (0, null, or not an object) despite individual shaders potentially compiling. Value:', shader.program);
            } else { // Shaders did not compile
                console.error('[DEBUG] Shader program linking not attempted or failed due to compilation errors in vertex or fragment shaders.');
            }

        } else {
            console.error('[DEBUG] Shader object, its renderer, or GL context is not available. Cannot check compilation/linking. Shader object:', shader);
        }
        
        p.frameRate(60);
        p.noStroke();
    };

    p.draw = () => {
        p.background(0);

        // Waveform generation and texture update logic is no longer needed
        // if (p.waveform1.length === 0) {
        //    for (let j = 0; j < 512; j++) { 
        //        p.waveform1[j] = Math.sin(p.frameCount * 0.01 + j * 0.1) * 0.5;
        //    }
        // }
        // waveformTex.loadPixels();
        // for (let j = 0; j < p.waveform1.length && j < 512; j++) { 
        //     let val = (p.waveform1[j] * 0.5 + 0.5) * 255.0;
        //     val = Math.max(0, Math.min(255, val));
        //     waveformTex.pixels[j * 4 + 0] = val;
        //     waveformTex.pixels[j * 4 + 1] = val;
        //     waveformTex.pixels[j * 4 + 2] = val;
        //     waveformTex.pixels[j * 4 + 3] = 255;
        // }
        // waveformTex.updatePixels();

        p.shader(shader);
        shader.setUniform('u_resolution', [p.width, p.height]);
        shader.setUniform('u_time', p.millis() / 1000.0);
        shader.setUniform('u_mouse', [p.mouseX, p.height - p.mouseY]); // Pass mouse coords, flipping Y
        // shader.setUniform('u_waveform', waveformTex); // No longer used
        // shader.setUniform('u_rms', p.rmsOutput); // No longer used
        
        p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch; 