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

#define time iTime
#define PI 3.14159265359
#define NUM_BANDS 40

uniform sampler2D u_waveform;
uniform vec2 u_resolution;
uniform float u_time;

varying vec2 vTexCoord;

float noise3D(vec3 p) {
    return fract(sin(dot(p ,vec3(12.9898,78.233,12.7378))) * 43758.5453)*2.0-1.0;
}

vec3 mixc(vec3 col1, vec3 col2, float v) {
    v = clamp(v,0.0,1.0);
    return col1+v*(col2-col1);
}

vec3 drawBands(vec2 uv) {
    uv = 2.0*uv-1.0;
    uv.x*=u_resolution.x/u_resolution.y;
    uv = vec2(length(uv), atan(uv.y,uv.x));
    
    uv.y -= PI*0.5;
    vec2 uv2 = vec2(uv.x, uv.y*-1.0);
    uv.y = mod(uv.y,PI*2.0);
    uv2.y = mod(uv2.y,PI*2.0);
    
    vec3 col = vec3(0.0);
    vec3 col2 = vec3(0.0);
    
    float nBands = float(NUM_BANDS);
    float i = floor(uv.x*nBands);
    float f = fract(uv.x*nBands);
    float band = i/nBands;
    float s;
    
    band *= band*band; 
    
    band = band*0.1;
    band += 0.01;
    
    s = texture2D(u_waveform, vec2(band,0.0)).x;
    
    if(band<0.0||band>=1.0) {
        s = 0.0;
    }
    
    const int nColors = 6;
    vec3 colors[6];  
    colors[0] = vec3(0.522,0.502,0.502);
    colors[1] = vec3(0.000,0.173,0.580);
    colors[2] = vec3(0.192,0.180,1.000);
    colors[3] = vec3(0.361,0.573,1.000);
    colors[4] = vec3(0.361,0.573,1.000);
    colors[5] = vec3(0.361,0.573,1.000);
 
    vec3 gradCol = colors[0];
    float n = float(nColors)-0.0;
    for(int i = 1; i < nColors; i++) {
        gradCol = mixc(gradCol,colors[i],(s-float(i-1)/n)*n);
    }
    
    float h = PI*3.5;
    
    col += vec3(1.0-smoothstep(-2.0,1.5,uv.y-s*h));
    col *= gradCol;

    col2 += vec3(1.0-smoothstep(-2.0,1.5,uv2.y-s*h));
    col2 *= gradCol;
    
    col = mix(col,col2,step(0.0,uv.y-PI));

    col *= smoothstep(0.05,0.5,f);
    col *= smoothstep(1.0,0.8,f); 
    
    col = clamp(col,0.0,1.0);
    
    return col;
}

void main() {
    vec2 uv = vTexCoord;
    
    vec2 p = vec2(uv.x, uv.y+0.01);
    vec3 col = vec3(0.0);
    col += drawBands(p);
    
    vec3 ref = vec3(0.0);
    vec2 eps = vec2(0.01,-0.01);

    ref += drawBands(vec2(p.x,1.0-p.y)+eps.xx);
    /*
    ref += drawBands(vec2(p.x,1.0-p.y)+eps.xy);
    
    ref += drawBands(vec2(p.x,1.0-p.y)+eps.yy);
    ref += drawBands(vec2(p.x,1.0-p.y)+eps.yx);
    
    ref += drawBands(vec2(p.x+eps.x,1.0-p.y));
    ref += drawBands(vec2(p.x+eps.y,1.0-p.y));
    ref += drawBands(vec2(p.x,1.0-p.y+eps.x));
    ref += drawBands(vec2(p.x,1.0-p.y+eps.y));
    */

    ref /= 3.0;
     
    float colStep = length(smoothstep(1.0,0.1,col));
    
    vec3 cs1 = drawBands(vec2(3.5,0.51));
    vec3 cs2 = drawBands(vec2(0.5,0.93));
        
    vec3 plCol = mix(cs1,cs2,length(p*1.0-1.0))*0.5*smoothstep(1.75,-0.5,length(p*0.0-1.0));
    vec3 plColBg = vec3(0.02)*smoothstep(1.0,0.0,length(p*8.0-1.0));
    vec3 pl = (plCol+plColBg)*smoothstep(0.5,0.65,5.0-uv.y);
    
    col += clamp(pl*(1.0-colStep),0.0,1.0);
    
    col += ref*smoothstep(0.125,1.6125,p.y); 
    
    col = clamp(col, 0.0, 1.0);

    float dither = noise3D(vec3(uv,u_time))*9.0/2226.0;
    col += dither;
     
    gl_FragColor = vec4(col,1.0);
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
        fps.style('color', '#FFFFFF');
        fps.style('font-family', '-apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, Arial, sans-serif');
        fps.style('font-size', '14px');
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

        // Update accumulated waveform
        
        for (let i = 0; i < p.waveform1.length; i++) {
            accumulatedWaveform[i] = Math.max(
                Math.abs(p.waveform1[i]),
                accumulatedWaveform[i] * decayFactor
            );
        }
        

        // Update waveform texture using accumulated values
        waveformTex.loadPixels();
        for (let i = 0; i < accumulatedWaveform.length; i++) {
            let clampedValue = Math.max(-1, Math.min(1, accumulatedWaveform[i]));
            let val = p.map(clampedValue, 0, 1, -255, 255);
            waveformTex.pixels[i * 4] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        shader.setUniform('u_waveform', waveformTex);
        shader.setUniform('u_resolution', [p.width, p.height]);
        shader.setUniform('u_time', p.millis() / 1000.0);

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
