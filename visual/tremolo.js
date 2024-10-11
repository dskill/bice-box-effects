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

void main() {
    vec2 uv = vTexCoord;
    /*
    vec2 texel = 1.0 / u_resolution;
    vec4 center = texture2D(u_previous, uv);
    texel *= 13.0;
    
    // Simple diffusion
    vec4 left = texture2D(u_previous, uv - vec2(texel.x, 0.0));
    vec4 right = texture2D(u_previous, uv + vec2(texel.x, 0.0));
    vec4 up = texture2D(u_previous, uv - vec2(0.0, texel.y));
    vec4 down = texture2D(u_previous, uv + vec2(0.0, texel.y));
    
    vec4 diffusion = (left + right + up + down) * 0.25;// - center;
    */
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
    // Add some variation based on frame count
   // float variation = sin(uv.x * 10.0 + float(u_framecount) * 0.1) * 0.5 + 0.5;
    //vec4 test = texture2D(u_previous, uv - .1 * vec2(texel.x, 0.0));
    gl_FragColor = texture2D(u_previous, uv + vec2(0,.005)) * vec4(.99,.96,.94,1.0) + texture2D(u_next, uv); //vec4(1,0,0,1);// diffusion;//vec4(a, b, 0, 1.0); 
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


    p.preload = () =>
    {
        feedback = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () =>
    {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        previous = p.createFramebuffer(p.width, p.height); //, { format: p.FLOAT });
        next = p.createFramebuffer(p.width, p.height); //, { format: p.FLOAT });
        
        p.imageMode(p.CENTER);

        previous.begin();
        p.background(0,20,0,255); 
        p.ellipse(0,0,20,20); 
        previous.end();

        next.begin();
        p.background(0,0,0,255); 
        p.fill(10,0,255,255); 
        p.ellipse(0,0,41,41); 
        next.end();
 

        // FPS counter setup
        p.fps = p.createP('');
        p.fps.position(10, 10);
        p.fps.style('color', 'white');
    };

    p.draw = () =>
    {
       p.background(0,0,0,255);
        
        [previous, next] = [next, previous];

        next.begin();
        p.clear();
    
        // Draw waveform0 in white at the top with RMS
       // drawWaveform(p.waveform0, p.color(255, 100, 0), -p.height / 4, 1, p.rmsInput);
        // Draw waveform1 in blue in the middle with RMS
        drawWaveform(p.waveform1, p.color(0, 100, 255), p.height / 4, 1, p.rmsOutput);
        next.end();
        //p.image(previous, 1000, 0);

       

        next.begin();
        feedback.setUniform('u_previous', previous);
        feedback.setUniform('u_next', next);

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
        next.end();
        p.image(next, 0, 0);


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
            //p.strokeWeight(1.0 + Math.max(rms, 0.002) * 10.0); // Adjust stroke weight based on RMS
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
        previous = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
        next = p.createFramebuffer(p.width, p.height, { format: p.FLOAT });
    };

};

module.exports = sketch;