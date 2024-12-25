const vertexShader = `
    attribute vec3 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        vec4 positionVec4 = vec4(aPosition, 1.0);
        positionVec4.xy = positionVec4.xy * 2.0 - 1.0;
        vTexCoord = positionVec4.xy * .5 + .5;
        gl_Position = positionVec4;
    }
`;

const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

#define PI 3.141592654
#define TWO_PI 6.283185307

uniform sampler2D u_waveform;
uniform sampler2D u_previous;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_amplitudeTime;
uniform float u_rms;
uniform float u_sparkle;
uniform float u_shimmer;
uniform float u_rotation;
uniform float u_delay;
varying vec2 vTexCoord;

// Function to create a kaleidoscope effect
vec2 kaleidoscope(vec2 uv, float segments, float rotation) {
    float angle = atan(uv.y, uv.x);
    float radius = length(uv);
    
    // Divide into segments
    angle = mod(angle + rotation * radius * .1, TWO_PI / segments) - (PI / segments);
    
    // Mirror
    angle = abs(angle);
    
    return vec2(cos(angle), sin(angle)) * radius;
}

// Function to generate sparkles
float sparkle(vec2 uv, float time, float intensity) {
    float sparklePattern = fract(sin(dot(uv, vec2(12.9898, 78.233))) * .4);
    float t = time * 2.0;
    float flicker = sin(t) * sin(t * 1.234) * sin(t * 2.345);
    return pow(sparklePattern, 10.0) * intensity * (0.8 + 0.2 * flicker);
}

void main() {
    vec2 uv = vTexCoord;
    uv = (uv - 0.5) * 2.0;
    uv.x *= u_resolution.x / u_resolution.y;
    
    // Get parameters from uniforms and modulate with RMS
    float time = u_time;
    float amplitudeTime = u_amplitudeTime;
    float rotationSpeed = u_rotation * 0.5;
    float segments =  max(1.0,floor(10.0 * (u_sparkle)));
    
    // Apply kaleidoscope effect with RMS-modulated rotation
    vec2 kUV = kaleidoscope(uv, segments, u_time * rotationSpeed * 2.0);
    
    // Sample waveform with proper scaling
    float waveVal = texture2D(u_waveform, vec2(abs(kUV.x*.5), 0.0)).r * 2.0 - 1.0;
    
    // Create base color using polar coordinates
    kUV.x *=1.0* u_delay + 1.0;
    float angle = atan(kUV.y + waveVal*1.1, kUV.x);
    float radius = length(kUV);
    
    // Create rainbow pattern modulated by RMS
    vec3 color = 0.5 + 0.5 * cos(vec3(0.0, 2.0, 4.0) + sin(angle * 10.0)+ time + radius * 3.0);
    
    // Add sparkles modulated by parameter and RMS
    float sparkleIntensity = sparkle(kUV, time, u_sparkle * (1.0 * 2.0));
    
    // Add wave distortion
    float wave = sin(radius * 10.0 + 1000.0 * u_shimmer + u_time * 10.0);
    color += wave * .1;
    
    // Add shimmer effect modulated by parameter
    float shimmerEffect = sin(radius * amplitudeTime * 20.0) * 0.5 + 0.5;
    color += shimmerEffect * vec3(0.2, 0.1, 0.3) * u_shimmer;
    
    // Add sparkles with color variation
    vec3 sparkleColor = vec3(1.0, 0.8, 0.9);
    color += sparkleColor * sparkleIntensity;
    
    // Fade edges
    float fade = 1.0 - smoothstep(0.8, 1.0, radius);
    //color *= fade;
    
    // Output
    gl_FragColor = vec4(color, 1.0);
}
`;

const sketch = function(p) {
    let shader;
    let waveformTex;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    let pingPong = [];
    let amplitudeTime = 0;
    
    p.waveform1 = [];
    p.fft1 = [];
    p.rmsOutput = 0;

    p.preload = () => {
        shader = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.imageMode(p.CENTER);

        // Create ping-pong buffers for feedback effects
        pingPong = [
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false }),
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false })
        ];
        
        // Initialize framebuffers
        pingPong[0].begin();
        p.background(0);
        pingPong[0].end();
        
        pingPong[1].begin();
        p.background(0);
        pingPong[1].end();

        // Create waveform texture
        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        waveformTex.noSmooth();

        // FPS counter setup
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
        if (!p.params) return;

        // Update amplitude-driven time
        amplitudeTime += p.rmsOutput;

        // Create dummy waveform if none exists
        if (p.waveform1.length === 0) {
            p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i * 0.1) * 0.5);
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

        // Ping-pong buffer rendering
        let read = pingPong[p.frameCount % 2];
        let write = pingPong[(p.frameCount + 1) % 2];

        write.begin();
        shader.setUniform('u_previous', read);
        shader.setUniform('u_waveform', waveformTex);
        shader.setUniform('u_resolution', [p.width, p.height]);
        shader.setUniform('u_amplitudeTime', amplitudeTime);
        shader.setUniform('u_time', p.millis()/1000.0);
        shader.setUniform('u_rms', p.rmsOutput);

        // Pass effect parameters to shader
        shader.setUniform('u_sparkle', p.params.sparkle || 0.5);
        shader.setUniform('u_shimmer', p.params.shimmer || 0.4);
        shader.setUniform('u_rotation', p.params.rotation || 0.5);
        shader.setUniform('u_delay', p.params.delayTime || 1.0);

        p.shader(shader);
        p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
        write.end();

        // Draw the result to the screen
        p.image(write, 0, 0);

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
        // Recreate framebuffers on resize
        pingPong = [
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false }),
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false })
        ];
    };
};

module.exports = sketch; 