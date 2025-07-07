// resolution: 0.75
// Fractal visual effect inspired by Shadertoy
// Original shader: https://www.shadertoy.com/view/mtyGWy
// Adapted for audio-reactive visuals in BICE Box

#define PI 3.141592654

// Palette function from Inigo Quilez
vec3 palette(float t) {
    vec3 a = vec3(0.5, 0.5, 0.5);
    vec3 b = vec3(0.5, 0.5, 0.5);
    vec3 c = vec3(1.0, 1.0, 1.0);
    vec3 d = vec3(0.263, 0.416, 0.557);
    
    return a + b * cos(6.28318 * (c * t + d));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;
    vec2 uv0 = uv;
    vec3 finalColor = vec3(0.0);
    
    // Audio reactivity
    float audioScale = 1.0 + iRMSOutput * 2.0;
    float waveVal = texture(iAudioTexture, vec2(fragCoord.x / iResolution.x, 0.75)).x * 2.0 - 1.0;
    float timeOffset = iRMSTime * 0.05 + iTime * 0.4;
    
    for (float i = 0.0; i < 4.0; i++) {
        uv = fract(uv * 1.5 * audioScale) - 0.5;
        
        float d = length(uv) * exp(-length(uv0));
        
        vec3 col = palette(length(uv0) + i * 0.4 + timeOffset + waveVal * 0.03);
        
        d = sin(d * 8.0 + timeOffset + waveVal * 0.5) / 8.0;
        d = abs(d);
        
        d = pow(0.01 / d, 1.2);
        
        finalColor += col * d;
    }
    
    // Add vignette
    vec2 puv = fragCoord / iResolution.xy;
    puv *= 1.0 - puv.yx;
    finalColor *= pow(puv.x * puv.y * 30.0, 0.3);
    
    fragColor = vec4(finalColor, 1.0);
}