let feedback;
let previous, next;

let waveformTex; // New: our 1D waveform texture
let pingPong = [];

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


// Updated fragment shader to include waveform visualization
// We'll draw a horizontal line waveform across the center of the screen.
// We'll use smoothstep() on the absolute difference between the vertical uv and the waveform amplitude line.
const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

uniform sampler2D u_previous;
uniform sampler2D u_next;
uniform sampler2D u_waveform;
uniform float u_rms;
uniform float u_pingPongData[2];
uniform vec2 u_resolution;
uniform float u_framecount;

varying vec2 vTexCoord;

// Convert RGB to HSV
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0/3.0, 2.0/3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z+(q.w-q.y)/(6.0*d+e)), d/(q.x+e), q.x);
}

// Convert HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0,2.0/3.0,1.0/3.0,3.0);
    vec3 p = abs(fract(c.xxx+K.xyz)*6.0-K.www);
    return c.z*mix(K.xxx, clamp(p-K.xxx,0.0,1.0), c.y);
}

void main() {
    vec2 uv = vTexCoord;

    // Gaussian Background
    vec2 texel = 1.0 / u_resolution;
    //uv = (uv - 0.5);// * 0.98 + 0.5;
    uv.y -= .005 * u_pingPongData[0];
    uv.y = abs(uv.y - .5) + .5;
    vec4 left = texture2D(u_previous, uv - 2.0*vec2(texel.x, 0.0));
    vec4 right = texture2D(u_previous, uv + 2.0*vec2(texel.x, 0.0));
    vec4 up = texture2D(u_previous, uv - vec2(0.0, texel.y));
    vec4 down = texture2D(u_previous, uv + vec2(0.0, texel.y));
    vec4 diffusion = (left + right + up + down) * 0.25;

    
    //vec4 diffusion = texture2D(u_previous, uv - vec2(0, .01));

    // Convert diffusion to HSV and modify
    vec3 hsvColor = rgb2hsv(diffusion.rgb);
    hsvColor.x = mod(hsvColor.x - u_rms*.1, 1.0);
    hsvColor.y = min(hsvColor.y + u_rms*0.1, 0.99);
    hsvColor.z *= 0.9 + .1*(u_pingPongData[1] + .1);
    vec3 remappedColor = hsv2rgb(hsvColor);

    // Waveform visualization overlay
    float waveformValue = texture2D(u_waveform, vec2(vTexCoord.x, 0.0)).r;
    float linePos = .5 + (waveformValue * .5 - .25);
    float thickness = 0.05;
    float lineMask =.1* smoothstep(0.0, 1.0, 1.0 - abs(vTexCoord.y - linePos) / thickness);
    lineMask += smoothstep(0.9, 1.0, 1.0 - abs(vTexCoord.y - linePos) / thickness);
    //lineMask = lineMask * lineMask;
    //lineMask *= smoothstep(0.1, 0.11, u_rms + .1);

    // Combine with background
    vec3 finalColor = remappedColor + lineMask * vec3(1.0, 0.4, 0.7);

    // Add vignette
    vec2 puv = vTexCoord;
    puv *= 1.0 - puv.yx;
    finalColor *= pow(puv.x*puv.y*15.0, 0.1);

  //  finalColor.r += u_pingPongData[1];

    gl_FragColor = vec4(finalColor, 1.0);
}
`;

const sketch = function (p)
{
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;

    p.waveform1 = []; // Waveform data array (populated externally)
    p.rmsOutput = 0;

    p.preload = () =>
    {
        feedback = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () =>
    {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.imageMode(p.CENTER);

        // Create ping-pong framebuffers
        pingPong = [
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false }),
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false }),
        ];
        
        pingPong[0].begin();
        p.background(0);
        pingPong[0].end();

        pingPong[1].begin();
        p.background(0);
        pingPong[1].end();

        // Create waveform texture
        // We'll create a p5.Graphics as a buffer for the waveform.
        // This will be a single row of pixels.
        waveformTex = p.createGraphics(512, 1, p.WEBGL);
        waveformTex.pixelDensity(1);
        waveformTex.noSmooth();

        // FPS display
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
    };

    p.draw = () =>
    {
        p.background(0);

        // Ensure waveform1 has some data
        // (You need to populate waveform1 from your audio input or analysis each frame)
        if (p.waveform1.length === 0) {
            // Dummy data if no real data yet
            p.waveform1 = new Array(1024).fill(0).map((_, i) => Math.sin(i*0.1)*0.5);
        }

        // Update waveform texture
        waveformTex.loadPixels();
        for (let i = 0; i < p.waveform1.length; i++) {
            // Multiply by RMS and then clamp
            let clampedValue = Math.max(-1, Math.min(1, p.waveform1[i]));
            let val = p.map(clampedValue, -1, 1, 0, 255);
            waveformTex.pixels[i * 4] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        let read = pingPong[p.frameCount % 2]; 
        let write = pingPong[(p.frameCount + 1) % 2];

        // Reaction-Diffusion update pass
        write.begin();
        feedback.setUniform('u_previous', read);
        feedback.setUniform('u_next', write);
        feedback.setUniform('u_resolution', [p.width * p.pixelDensity(), p.height * p.pixelDensity()]);
        feedback.setUniform('u_framecount', p.frameCount);
        feedback.setUniform('u_rms', p.rmsOutput);
        if (p.customMessage && p.customMessage.values.length == 2) {
            feedback.setUniform('u_pingPongData', p.customMessage.values);
        }
        feedback.setUniform('u_waveform', waveformTex);

        p.shader(feedback);
        p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
        write.end();

        // Draw to screen
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

    p.windowResized = () =>
    {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
        pingPong[0] = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
        pingPong[1] = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
    };

};

module.exports = sketch;
