// visual/Singularity.js
// "Singularity" by @XorDev, adapted for p5.js by AI, 2024
// Original: https://www.shadertoy.com/view/XsfXWX (example, actual source from user)

const simWidth = 256;
const simHeight = 256;

const vertexShader = `
    attribute vec3 aPosition;
    attribute vec2 aTexCoord; // Added attribute for texture coordinates
    varying vec2 vTexCoord;   // Added varying to pass tex coords to fragment shader

    void main() {
        gl_Position = vec4(aPosition, 1.0);
        vTexCoord = aTexCoord; // Pass texture coordinates
    }
`;

const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

uniform vec2 u_resolution; // Corresponds to iResolution
uniform float u_time;     // Corresponds to iTime
varying vec2 vTexCoord;   // Added varying to receive tex coords

// mainImage(out vec4 O, vec2 F)
void main() {
    vec4 O;                   // Output color

    //Iterator and attenuation (distance-squared)
    float i = 0.2; // Original 'i', used for initial calcs, then loop, then reassigned
    float a;       // Attenuation

    //Resolution for scaling and centering
    vec2 r = u_resolution.xy;

    //Centered ratio-corrected coordinates
    vec2 uv_centered = vTexCoord * 2.0 - 1.0;
    vec2 p;
    p.x = uv_centered.x * (r.x / r.y); // r.x / r.y is aspect ratio
    p.y = uv_centered.y;
    p /= 0.7; // Original scaling factor

    //Diagonal vector for skewing
    vec2 d_diag = vec2(-1.0, 1.0); // Renamed 'd' to avoid conflict if any
    //Blackhole center
    vec2 b = p - i * d_diag; // Uses initial i = 0.2

    //Rotate and apply perspective
    // c = p * mat2(1, 1, d_diag/(.1 + i/dot(b,b)))
    float K_persp = 0.1 + i / dot(b,b); // Uses initial i = 0.2
    vec2 d_div_K = d_diag / K_persp;
    mat2 m1_persp = mat2(1.0, d_div_K.x,  // Column 0: (1.0, d_div_K.x) -> Shadertoy mat2(a,b,c,d) is [a,c;b,d]
                         1.0, d_div_K.y); // Column 1: (1.0, d_div_K.y) -> for p5.js mat2(c0x,c0y,c1x,c1y) = [c0x c1x; c0y c1y]
                                         // Original: mat2(1,1, d_val.x, d_val.y) -> [1, d_val.x; 1, d_val.y]
                                         // So, M = [1.0, d_val.x; 1.0, d_val.y] for col-major constructor
                                         // This should be: mat2(1.0, 1.0, d_div_K.x, d_div_K.y)
                                         // Which is col0=(1,1), col1=(d_div_K.x, d_div_K.y) => [1, d_div_K.x; 1, d_div_K.y]
    m1_persp = mat2(1.0, 1.0, d_div_K.x, d_div_K.y);
    vec2 c = p * m1_persp; // vec * mat in GLSL means row-vector times matrix

    //Rotate into spiraling coordinates
    // v = c * mat2(cos(.5*log(a=dot(c,c)) + iTime*i + vec4(0,33,11,0)))/i
    a = dot(c,c); // a_attenuation is assigned here
    float log_val = 0.5 * log(a);
    float time_effect = u_time * i; // Uses initial i = 0.2
    
    vec4 T_plus_V4_angles = vec4(
        log_val + time_effect + 0.0,
        log_val + time_effect + 33.0,
        log_val + time_effect + 11.0,
        log_val + time_effect + 0.0
    );
    vec4 cos_angles = cos(T_plus_V4_angles);
    // mat2(v.x,v.y,v.z,v.w) -> [v.x, v.z; v.y, v.w]
    mat2 rot_matrix = mat2(cos_angles.x, cos_angles.y, cos_angles.z, cos_angles.w);
    vec2 v = (c * rot_matrix) / i; // Uses initial i = 0.2

    //Waves cumulative total for coloring
    vec2 w = vec2(0.0); // Initialized as vec2
    
    //Loop through waves
    // Original: for(; i++<9.; w += 1.+sin(v) ) v += .7* sin(v.yx*i+iTime) / i + .5;
    // 'i' starts at 0.2. In the loop, 'i' for body is 1.2, 2.2, ..., 9.2.
    // The loop runs 9 times. 'i' becomes 9.2 after the loop.
    for(int k_loop = 0; k_loop < 9; ++k_loop) {
        i += 1.0; // Increment 'i' (0.2 -> 1.2 on 1st iter; 8.2 -> 9.2 on last)
        w += 1.0 + sin(v); // sin(vec2) is component-wise. 1.0 + vec2 is (1+vx, 1+vy)
        v += 0.7 * sin(v.yx * i + u_time) / i + 0.5;
    }

    //Acretion disk radius - 'i' is reassigned here
    i = length( sin(v/0.3)*0.4 + vec2(c.x*(3.0+d_diag.x), c.y*(3.0+d_diag.y)) ); // c*(3.+d_diag) -> c * vec2(2,4) component wise

    //Red/blue gradient
    vec4 num_exp_term = exp( c.x * vec4(0.6, -0.4, -1.0, 0.0) );
    vec4 denom1_waves = w.xyyx; // vec4(w.x, w.y, w.y, w.x)
    
    // Denominators can be zero or very small, check original if issues arise
    denom1_waves = max(denom1_waves, vec4(0.00001)); // Avoid division by zero if w is zero

    float denom2_brightness_scalar = ( 2.0 + i*i/4.0 - i );
    float denom3_center_dark_scalar = ( 0.5 + 1.0 / max(a, 0.00001) ); // a = dot(c,c), ensure not zero
    float denom4_rim_highlight_scalar = ( 0.03 + abs( length(p)-0.7 ) );

    vec4 final_term = num_exp_term;
    final_term /= denom1_waves; 
    final_term /= max(denom2_brightness_scalar, 0.00001);
    final_term /= max(denom3_center_dark_scalar, 0.00001);
    final_term /= max(denom4_rim_highlight_scalar, 0.00001);
    
    O = 1.0 - exp(-final_term);
    gl_FragColor = O;
}
`;

const sketch = function (p) {
    let renderBuffer;
    let bufferShader;

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
        
        renderBuffer = p.createGraphics(simWidth, simHeight, p.WEBGL);
        bufferShader = renderBuffer.createShader(vertexShader, fragmentShader);
        
        p.frameRate(60);
        renderBuffer.noStroke(); 
    };

    p.draw = () => {
        renderBuffer.shader(bufferShader);
        renderBuffer.background(0); // Not strictly needed as shader writes all pixels

        bufferShader.setUniform('u_resolution', [simWidth, simHeight]);
        bufferShader.setUniform('u_time', p.millis() / 1000.0);
        
        renderBuffer.quad(-1, -1, 1, -1, 1, 1, -1, 1);

        p.background(0); 
        p.imageMode(p.CORNER); // Ensure image is drawn from -w/2, -h/2 correctly in WEBGL mode
        p.image(renderBuffer, -p.width/2, -p.height/2, p.width, p.height);
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch; 