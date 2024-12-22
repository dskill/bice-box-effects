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
uniform sampler2D u_waveform;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_rms;
uniform float u_lightning;
varying vec2 vTexCoord;

float sdSound(vec2 uv) {
    float waveformValue = (texture2D(u_waveform, vec2(uv.x, 0.0)).x - 0.5) * 2.0;
    waveformValue *= 0.3;
    waveformValue *= 1.0 - abs(pow(abs(uv.x - 0.5) * 2.0, 2.5));
    
    float lineOffset = uv.y - waveformValue;
    float line = 1.0 - abs(lineOffset) * 2.0;
    float milkyLine = pow(line, 0.2) * 0.2;
    milkyLine += pow(line, 10.0) * 0.6;
    milkyLine += pow(line, 2000.0) * 30.0;
    
    return milkyLine;
}

void main() {
    vec2 uv = vTexCoord;
    vec2 texel = 1.0 / u_resolution;
    
    // Sample previous frame with slight offset for lightning spread effect
    vec4 prev = texture2D(u_previous, uv);
    vec4 prevUp = texture2D(u_previous, uv - vec2(0.0, texel.y));
    vec4 prevDown = texture2D(u_previous, uv + vec2(0.0, texel.y));
    vec4 prevLeft = texture2D(u_previous, uv - vec2(texel.x, 0.0));
    vec4 prevRight = texture2D(u_previous, uv + vec2(texel.x, 0.0));
    
    // Create diffusion effect
    vec4 diffusion = (prevUp + prevDown + prevLeft + prevRight) * .3 + prev * .6;
    //diffusion *= 0.999; // Decay factor
    
    // Add new waveform
    float wave = sdSound(vec2(uv.x, uv.y - 0.5));
    vec3 waveColor = vec3(0.6, 0.2, 1.0); // Purple base
    vec3 lightningColor = vec3(0.7, 0.4, 1.0); // Lighter purple for highlights
    
    // Mix colors based on wave intensity and RMS
    vec3 col = diffusion.rgb;
    col += wave * waveColor * (1.0 + u_rms);
    col += wave * lightningColor * pow(u_rms, 2.0);
    
    // Add electric crackle effect
    float crackle = fract(sin(uv.x * 100.0 + u_time * 5.0) * 
                         cos(uv.y * 120.0 - u_time * 3.0) * 43758.5453123);
    col += crackle * wave * 2.1 * vec3(0.8, 0.6, 1.0);
    
    // Add vignette
    vec2 puv = vTexCoord;
    puv *= 1.0 - puv.yx;
    col.xyz = col.xyz * pow(puv.x * puv.y * 5.0, 1.4 - u_lightning * .008);
    
    gl_FragColor = vec4(col, 1.0);
}
`;

const sketch = function (p) {
    let shader;
    let waveformTex;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    let pingPong = [];

    p.waveform1 = [];
    p.rmsOutput = 0;

    p.preload = () => {
        shader = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        p.imageMode(p.CENTER);

        // Create ping-pong framebuffers
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

        // Ping-pong buffer rendering
        let read = pingPong[p.frameCount % 2];
        let write = pingPong[(p.frameCount + 1) % 2];

        write.begin();
        shader.setUniform('u_previous', read);
        shader.setUniform('u_next', write);
        shader.setUniform('u_waveform', waveformTex);
        shader.setUniform('u_resolution', [p.width * p.pixelDensity(), p.height * p.pixelDensity()]);
        shader.setUniform('u_time', p.millis() / 1000.0);
        shader.setUniform('u_rms', p.rmsOutput);
        shader.setUniform('u_lightning', p.params.drive * p.params.mix);

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