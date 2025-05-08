const vertexShader = `
    attribute vec3 aPosition;
    attribute vec2 aTexCoord;
    varying vec2 vTexCoord;
    void main() {
        vec4 positionVec4 = vec4(aPosition, 1.0);
        vTexCoord = aTexCoord;
        gl_Position = positionVec4;
    }
`;

const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

#define PI 3.141592654
#define POINT_COUNT 6

uniform sampler2D u_waveform;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_amplitudeTime;
uniform float u_rms;
uniform float u_speed;
uniform float u_intensity;
uniform float u_roomSize;
uniform float u_decay;

varying vec2 vTexCoord;

vec2 points[POINT_COUNT];
float radius = 0.0112;
float thickness = 0.0035;

//https://www.shadertoy.com/view/MlKcDD
//Signed distance to a quadratic bezier
float sdBezier(vec2 pos, vec2 A, vec2 B, vec2 C) {    
    vec2 a = B - A;
    vec2 b = A - 2.0*B + C;
    vec2 c = a * 2.0;
    vec2 d = A - pos;

    float kk = 1.0 / dot(b,b);
    float kx = kk * dot(a,b);
    float ky = kk * (2.0*dot(a,a)+dot(d,b)) / 3.0;
    float kz = kk * dot(d,a);      

    float res = 0.0;

    float p = ky - kx*kx;
    float p3 = p*p*p;
    float q = kx*(2.0*kx*kx - 3.0*ky) + kz;
    float h = q*q + 4.0*p3;
    
    if(h >= 0.0) { 
        h = sqrt(h);
        vec2 x = (vec2(h, -h) - q) / 2.0;
        vec2 uv = sign(x)*pow(abs(x), vec2(1.0/3.0));
        float t = uv.x + uv.y - kx;
        t = clamp(t, 0.0, 1.0);

        // 1 root
        vec2 qos = d + (c + b*t)*t;
        res = length(qos);
    } else {
        float z = sqrt(-p);
        float v = acos(q/(p*z*2.0)) / 3.0;
        float m = cos(v);
        float n = sin(v)*1.732050808;
        vec3 t = vec3(m + m, -n - m, n - m) * z - kx;
        t = clamp(t, 0.0, 1.0);

        // 3 roots
        vec2 qos = d + (c + b*t.x)*t.x;
        float dis = dot(qos,qos);
        
        res = dis;

        qos = d + (c + b*t.y)*t.y;
        dis = dot(qos,qos);
        res = min(res,dis);

        qos = d + (c + b*t.z)*t.z;
        dis = dot(qos,qos);
        res = min(res,dis);

        res = sqrt(res);
    }
    
    return res;
}

//http://mathworld.wolfram.com/HeartCurve.html
vec2 getHeartPosition(float t) {
    return vec2(16.0 * sin(t) * sin(t) * sin(t),
                -(13.0 * cos(t) - 5.0 * cos(2.0*t)
                - 2.0 * cos(3.0*t) - cos(4.0*t)));
}

//https://www.shadertoy.com/view/3s3GDn
float getGlow(float dist, float radius, float intensity) {
    return pow(radius* dist, intensity);
}

float getSegment(float t, vec2 pos, float offset) {
    float len = 0.25;
    float scale = 0.012;
    scale = u_roomSize * .01;
    len *= u_decay*5.0 + 0.5;
    
    for(int i = 0; i < POINT_COUNT; i++) {
        points[i] = getHeartPosition(offset + float(i)*len + fract(u_speed * t) * 6.28);
    }
    
    vec2 c = (points[0] + points[1]) / 2.0;
    vec2 c_prev;
    float light = 0.;
    
    for(int i = 0; i < POINT_COUNT-1; i++) {
        c_prev = c;
        c = (points[i] + points[i+1]) / 2.0;
        float d = sdBezier(pos, scale * c_prev, scale * points[i], scale * c);
        float e = i > 0 ? distance(pos, scale * c_prev) : 1000.;
        light += 1. / max(d - thickness, 0.0001);
        light -= 1. / max(e - thickness, 0.0001);
    }
    
    return max(0.0, light);
}

void main() {
    vec2 uv = vec2(1.0) - vTexCoord;
    float widthHeightRatio = u_resolution.x/u_resolution.y;
    vec2 centre = vec2(0.5, 0.5);
    vec2 pos = centre - uv;
    pos.y /= widthHeightRatio;
    pos.y += 0.03;
    
    // Incorporate waveform
    float waveVal = texture2D(u_waveform, vec2( length(pos.xy), 0.0)).r * 2.0 - 1.0;
    vec2 posDir = normalize(pos.xy);
    pos.xy += posDir * waveVal * 0.1;
    
    float t = u_time * .3 + u_amplitudeTime * 1.5;
    radius *= 10.0*u_rms + 0.1;
    // Get first segment
    float dist = getSegment(t, pos, 0.0);
    float glow = getGlow(dist, radius, 1.5);
    
    vec3 col = vec3(0.0);
    
    // Pink glow
    col += glow * vec3(1.0,0.05,0.3);
    
    
    // Get second segment
    dist = getSegment(t, pos, 3.4);
    glow = getGlow(dist, radius, 1.5);
    
    // Blue glow
    col += glow * vec3(0.1,0.4,1.0);
    
    
    // Add RMS influence to color
     col *= 1.0 + u_rms * 4.0;
        
    // Tone mapping
     col = 1.0 - exp(-col);
    
    // Gamma
     col = pow(col, vec3(0.4545));

    // Output to screen
    gl_FragColor = vec4(col, 1.0);
}
`;

const sketch = function (p) {
    let shaderProgram;
    let waveformTex;
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;
    let amplitudeTime = 0;
    
    p.waveform1 = [];
    p.fft1 = [];
    p.rmsOutput = 0;
    p.params = {};

    p.preload = () => {
        shaderProgram = p.createShader(vertexShader, fragmentShader);
    };

    p.setup = () => {
        let cnv = p.createCanvas(p.windowWidth/4, p.windowHeight/4, p.WEBGL);
        cnv.style('width', '100%');
        cnv.style('height', '100%');
        p.imageMode(p.CENTER);

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
            p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i * 0.1) * 0.5);
        }
        if (p.fft1.length === 0) {
            p.fft1 = new Array(512).fill(0);
        }

        amplitudeTime += p.rmsOutput;

        waveformTex.loadPixels();
        for (let i = 0; i < p.waveform1.length; i++) {
            let val = (p.waveform1[i] * 0.5 + 0.5) * 255.0;
            waveformTex.pixels[i * 4 + 0] = val;
            waveformTex.pixels[i * 4 + 1] = val;
            waveformTex.pixels[i * 4 + 2] = val;
            waveformTex.pixels[i * 4 + 3] = 255;
        }
        waveformTex.updatePixels();

        shaderProgram.setUniform('u_waveform', waveformTex);
        shaderProgram.setUniform('u_resolution', [p.width, p.height]);
        shaderProgram.setUniform('u_amplitudeTime', amplitudeTime);
        shaderProgram.setUniform('u_time', p.millis() / 1000.0);
        shaderProgram.setUniform('u_rms', p.rmsOutput);
        
        // Set uniforms from params
        shaderProgram.setUniform('u_speed', p.params.speed !== null ? p.params.speed : 0.5);
        shaderProgram.setUniform('u_intensity', p.params.intensity !== null ? p.params.intensity : 0.5);
        shaderProgram.setUniform('u_roomSize', p.params.roomSize !== null ? p.params.roomSize : 0.5);
        shaderProgram.setUniform('u_decay', p.params.decay !== null ? p.params.decay : 0.5);
        p.shader(shaderProgram);
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
        p.resizeCanvas(p.windowWidth/4, p.windowHeight/4);
    };
};

module.exports = sketch; 