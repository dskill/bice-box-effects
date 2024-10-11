let feedback;
let previous, next;


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

uniform sampler2D u_previous;
uniform sampler2D u_next;
 
uniform vec2 u_resolution;
uniform float u_framecount;

uniform float u_diffusion_rate_a;
uniform float u_diffusion_rate_b;
uniform float u_reaction_speed;
uniform float u_feed_rate;
uniform float u_kill_rate;

varying vec2 vTexCoord;

// Function to convert RGB to HSV
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));

    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}

// Function to convert HSV to RGB
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}

void main() {
    vec2 uv = vTexCoord;
    
    vec2 texel = 1.0 / u_resolution;
    vec4 center = texture2D(u_previous, uv);
    texel *= 6.0;
    //texel.y += .01;
    // Simple diffusion
    vec4 left = texture2D(u_previous, uv - vec2(texel.x, 0.0));
    vec4 right = texture2D(u_previous, uv + vec2(texel.x, 0.0));
    vec4 up = texture2D(u_previous, uv - vec2(0.0, texel.y));
    vec4 down = texture2D(u_previous, uv + vec2(0.0, texel.y));
    
    vec4 diffusion = (left + right + up + down) * 0.25;// - center;
    
    /*
    // Reaction-diffusion 
    float a = center.r;
    float b = center.g;
    float reaction = a * b * b;
    
    float da = u_diffusion_rate_a * diffusion.r - reaction + u_feed_rate * (1.0 - a);
    float db = u_diffusion_rate_b * diffusion.g + reaction - (u_kill_rate + u_feed_rate) * b;
    
    a += da * u_reaction_speed;
    b += db * u_reaction_speed;
    */


    // Convert diffusion to HSV
    vec3 hsvColor = rgb2hsv(diffusion.rgb);
    
    // Modify HSV values
    hsvColor.x = mod(hsvColor.x + 0.1, 1.0); // Shift hue
    //hsvColor.y = min(hsvColor.y * 1.05, 1.0); // Increase saturation
    hsvColor.z *= 0.99; // Slightly decrease value for decay effect
    
    // Convert back to RGB
    vec3 remappedColor = hsv2rgb(hsvColor);

    
    gl_FragColor = vec4(remappedColor, 1.0);// * vec4(.995,.992,.99,1.0); 
}
    
    `; 

const sketch = function (p)
{
    p.waveform0 = []; // Initialize waveform data for channel 0
    p.waveform1 = []; // Initialize waveform data for channel 1
    let decayingWaveform = []; // Local array for decaying waveform
    const decayFactor = 0.95; // Decay factor for exponential decay
    // Add RMS properties
    p.rmsInput = 0;
    p.rmsOutput = 0;
    p.fft0 = [];
    p.fft1 = [];

    let pingPong = [];

    p.preload = () =>
    {
        feedback = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () =>
    {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        pingPong = [
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false }),
            p.createFramebuffer({ width: p.width, height: p.height, depth: false, antialias: false }),
        ];
        
        p.imageMode(p.CENTER);

        // Initialize the first buffer
        pingPong[0].begin();
        p.background(0, 0, 0, 255);
        pingPong[0].end();

        // Initialize the second buffer
        pingPong[1].begin();
        p.background(0, 0, 0, 255);
        pingPong[1].end();

        // FPS counter setup
        p.fps = p.createP('');
        p.fps.position(10, 10);
        p.fps.style('color', 'white');
        p.frameRate(30);

    };

    p.draw = () =>
    {
       p.background(0,0,0,255);
        
        let read = pingPong[p.frameCount % 2]; 
        let write = pingPong[(p.frameCount + 1) % 2];

        

        write.begin();
        feedback.setUniform('u_previous', read);
        feedback.setUniform('u_next', write);

        feedback.setUniform('u_resolution', [p.width * p.pixelDensity(), p.height * p.pixelDensity()]);
        feedback.setUniform('u_framecount', p.frameCount);

        // You can adjust these values or make them interactive
        feedback.setUniform('u_diffusion_rate_a', 0.95);
        feedback.setUniform('u_diffusion_rate_b', 0.2);
        feedback.setUniform('u_reaction_speed', 1.11);
        feedback.setUniform('u_feed_rate', 0.031);
        feedback.setUniform('u_kill_rate', 0.056);

        p.shader(feedback);
        p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
        write.end();

        write.begin();
        // Draw waveform1 in blue in the middle with RMS
        drawWaveform(p.waveform1, p.color(200, 100, 255), p.height / 2, 3, p.rmsOutput);
        write.end();
        p.image(write, 0, 0);

        // copy the final buffer back to next, which will become previous in the next frame
        
        // Update FPS counter
        updateFPS();
    };

    const updateFPS = () =>
    {
        p.fps.html('FPS: ' + p.frameRate().toFixed(2));
    };


    const drawWaveform = (waveform, color, yOffset, yMult, rms) => {
        p.push();
        if (waveform && waveform.length > 0) {
            //color.setRed(rms * 1000);
            p.stroke(color);
            p.strokeWeight(3.0);//+ Math.max(rms, 0.002) * 10.0); // Adjust stroke weight based on RMS
            p.noFill();
            p.beginShape();

            for (let i = 0; i < waveform.length; i++) {
                let x = p.map(i, 0, waveform.length, -p.width/2, p.width/2);
                let y = p.height / 2 - yOffset + waveform[i] * p.height / 8 * yMult;
                p.vertex(x, y);
            }

            p.endShape();
        }
        p.pop();
    };

    
    p.windowResized = () =>
    {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
        pingPong[0] = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
        pingPong[1] = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
    };

};

module.exports = sketch;
