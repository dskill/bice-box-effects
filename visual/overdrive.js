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
    texel *= 2.0;
    //texel.y += .01;
    // Simple diffusion

    uv = (uv - .5) * .98 + .5;
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
    hsvColor.y =  min(hsvColor.y + .05, .99); // Increase saturation
    hsvColor.z *= 0.97; // Slightly decrease value for decay effect
    
    // Convert back to RGB
    vec3 remappedColor = hsv2rgb(hsvColor);

    
    gl_FragColor = vec4(remappedColor, 1.0);// * vec4(.995,.992,.99,1.0); 
}
    
    `; 

const sketch = function (p)
{
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;

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
        fps = p.createP('');
        fps.style('color', '#444444');
        fps.style('font-family', '-apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, Arial, sans-serif');
        fps.style('font-size', '10px');
        fps.style('position', 'fixed');
        fps.style('bottom', '3px');
        fps.style('left', '3px');
        fps.style('margin', '0');
        
        p.frameRate(30);
        p.angleMode(p.DEGREES); // Use degrees for angle calculations

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

        // Draw FFT as concentric circles
        drawFFTCircles(p.fft0, p.color(255, 100, 100));

        write.end();
        p.image(write, 0, 0);

        // copy the final buffer back to next, which will become previous in the next frame
        
        // Update FPS counter
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


    const drawFFTCircles = (fftData, baseColor) => {
        if (fftData && fftData.length > 0) {
            p.push();
            p.translate(p.width / 2, p.height / 2);
            p.noStroke();

            const maxRadius = Math.min(p.width, p.height) * 0.75;
            const fftSize = fftData.length / 2;
            const sampleRate = 48000;
            const nyquist = sampleRate / 2;

            const minFreq = 82.41/2.0; // Frequency of the low E string (E2) on a standard guitar
            const maxFreq = 2318.51; // Approximately the frequency of the highest E (E6) on a standard guitar
            const minLog = Math.log2(minFreq);
            const maxLog = Math.log2(maxFreq);

            for (let bin = 0; bin < fftSize; bin++) {
                const freq = (bin / fftSize) * nyquist;
                if (freq >= minFreq && freq <= maxFreq) {
                    const real = fftData[2 * bin];
                    const imag = fftData[2 * bin + 1];
                    
                    let magnitude = Math.sqrt(real * real + imag * imag);
                    
                    // Calculate color based on magnitude 
                    const intensityColor = p.lerpColor(
                        p.color(0, 0, 0,0),  // Cool color (blue) for low intensity
                        p.color(255, 255, 255,255),  // Warm color (red) for high intensity
                        p.constrain(magnitude*.05, 0, 1)  // Map magnitude to 0-1 range
                    );
                    
                    // Blend the intensity color with the base color
                    let finalColor = p.lerpColor(baseColor, intensityColor, 1.0);
                    finalColor = intensityColor;
                    p.fill(finalColor);

                    // Calculate octave and position within octave
                    const logFreq = Math.log2(freq) - minLog;
                    const octave = Math.floor(logFreq);
                    const octaveFraction = logFreq - octave;

                    // Calculate angle (0 degrees is at the top, moving clockwise)
                    const angle = 270 + (octaveFraction * 360); // This is already in degrees

                    // Calculate radius (inner octaves have smaller radius)
                    // let radius = p.map(octave, 0, totalOctaves, maxRadius * 0.2, maxRadius);
                    let radius = p.map(logFreq, 0, maxLog, maxRadius * 0.0, maxRadius );
                    // Calculate circle size based on magnitude
                    //magnitude = 1;
                    const circleSize =(magnitude*.01 + 0) * (maxRadius / 40);
                    
                    // Use p.cos and p.sin directly with the angle in degrees
                    const x = radius * p.cos(angle)- p.width/2.0;
                    const y = radius * p.sin(angle) - p.height/2.0;
                    
                    p.ellipse(x, y, circleSize);

                     // Calculate triangle points
                     /*
                    const x1 = radius * p.cos(angle);
                    const y1 = radius * p.sin(angle);
                    const x2 = (radius + circleSize) * p.cos(angle - 5);
                    const y2 = (radius + circleSize) * p.sin(angle - 5);
                    const x3 = (radius + circleSize) * p.cos(angle + 5);
                    const y3 = (radius + circleSize) * p.sin(angle + 5);
                    

                    // Draw triangle pointing out from the center
                    p.triangle(x1, y1, x2, y2, x3, y3);
                    */
                }
            }
            p.pop();
        }
    };

    
    p.windowResized = () =>
    {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
        pingPong[0] = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
        pingPong[1] = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
    };

};

module.exports = sketch;
