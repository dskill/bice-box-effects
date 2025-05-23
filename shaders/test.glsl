//Oscilloscope Shader
//Visualizes waveform and FFT data from audio input
//
// iChannel0 texture layout (512x2 RGBA):
// Row 0 (y=0.0): FFT magnitude data (pre-computed in SuperCollider, 512 frequency bins)
// Row 1 (y=1.0): Waveform time-domain data (512 samples)

#define SAMPLES 15.0
#define PI 3.14159265359

float sdBox( in vec2 p, in vec2 b ) {
    vec2 d = abs(p)-b;
    return length(max(d,0.0)) + min(max(d.x,d.y),0.0);
}

float sdSound(vec2 uv) {
    // Sample waveform data from iChannel0
    // Based on the SuperCollider code, waveform data is in the texture
    float waveformValue = (texture(iChannel0, vec2(uv.x, 0.75)).x - 0.5) * 2.0; // Normalize to -1 to 1
    waveformValue *= .2;
    waveformValue *= 1.0 - abs(pow(abs(uv.x - .5)*2.0, 2.5)); // Apply tapering

    float lineOffset = uv.y - waveformValue;
    lineOffset += .15; // Apply vertical offset
    //lineOffset ;
    float line = 1.0 - abs(lineOffset) * 1.0;
    line = abs(line);
    float milkyLine = pow(line, .2)*.2;
    milkyLine += pow(line, 10.0)*.3;
    milkyLine += pow(line, 2000.0)*30.0;

    return milkyLine;
}

float sdFFT(vec2 uv) {
    // Sample FFT data from iChannel0 row 0
    // FFT magnitude data has been pre-computed from complex FFT data
    float fftValue = texture(iChannel0, vec2(abs(uv.x - .5), 0.25)).x * 0.1; // Use full uv.x and scale by 0.2
    
    // FFT value is already normalized and logarithmically scaled
    // Scale and position the FFT visualization
    float lineOffset = (uv.y) - (fftValue);
    lineOffset -= .15; // Adjust offset to match oscilloscope.js
    float line = 1.0 - abs(lineOffset) * 1.0;
    line = abs(line);
    // Create a similar "milky" glow effect as the waveform
    float milkyLine = pow(line, .2)*.2;
    milkyLine += pow(line, 10.0)*.6;
    milkyLine += pow(line, 2000.0)*30.0;

    return milkyLine;
}

vec2 cube(vec2 uv) {
    return mod((uv+.5)*8., vec2(1))-.5;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    uv.y -= 0.5;
    
    vec3 col = vec3(0.0);
    
    // Add waveform effect (green)
    float wave = sdSound(uv);
    col += mix(col, vec3(0.404,0.984,0.396), wave);
    
    // Add FFT effect (magenta)
    float fft = sdFFT(uv);
    col += mix(col, vec3(0.984,0.404,0.796), fft);
    
    // Add grid pattern
    col += .1*mix(col, vec3(0.031,0.031,0.031), float(sdBox(cube(uv), vec2(.49)) <= 0.));

    // Add vignette
    vec2 puv = fragCoord.xy / iResolution.xy;
    puv *= 1.0 - puv.yx;
    col *= pow(puv.x*puv.y*30.0, 0.5);
    
    // Apply blue tint
    col *= vec3(0.0, 0.667, 1.0);
    
    fragColor = vec4(col, 1.0);
}