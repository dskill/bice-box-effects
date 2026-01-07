// resolution: 1.0

#define PI 3.14159265359
#define TAU 6.28318530718

float getBass() {
    return texture(iAudioTexture, vec2(0.05, 0.25)).x;
}

float getMid() {
    return texture(iAudioTexture, vec2(0.3, 0.25)).x;
}

float getHigh() {
    return texture(iAudioTexture, vec2(0.7, 0.25)).x;
}

// FFT data: x = frequency bin (0-1 maps to 512 bins, low to high freq)
float getFFT(float freqX) {
    return texture(iAudioTexture, vec2(clamp(freqX, 0.0, 1.0), 0.25)).x;
}

// Waveform data: x = time sample (0-1 maps to 512 samples)
float getWaveform(float timeX) {
    return texture(iAudioTexture, vec2(timeX, 0.75)).x;
}

// Smoothed waveform for ring displacement
float getWaveformSmooth(float x) {
    float width = 0.02;
    float sum = 0.0;
    sum += getWaveform(mod(x - width, 1.0)) * 0.25;
    sum += getWaveform(x) * 0.5;
    sum += getWaveform(mod(x + width, 1.0)) * 0.25;
    return sum;
}

mat2 rot(float a) {
    float c = cos(a), s = sin(a);
    return mat2(c, -s, s, c);
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = (fragCoord.xy - 0.5 * iResolution.xy) / iResolution.y;

    float bass = getBass();
    float mid = getMid();
    float high = getHigh();
    float rms = iRMSOutput;
    float t = iRMSTime * 0.3;

    float radius = length(p);
    float angle = atan(p.y, p.x);

    // Sample buffer with slight rotation for twist effect
    float twistAmount = 0.02 + bass * 0.03;
    vec2 twisted_p = p * rot(radius * twistAmount);
    vec2 twisted_uv = twisted_p * iResolution.y / iResolution.xy + 0.5;

    vec3 buffer = texture(iChannel0, twisted_uv).rgb;

    // Tone mapping
    buffer = 1.0 - exp(-buffer * 0.4);

    // Generate fresh waveform overlay ring
    float wavePos = (angle + PI) / TAU;
    float wave = getWaveformSmooth(wavePos);
    float waveDisp = (wave - 0.5) * 2.0; // -1 to 1

    // Outer glowing ring displaced by waveform
    float outerRing = 0.0;
    float ringRadius = 0.42 + sin(t * 0.7) * 0.02;
    float ringWidth = 0.015 + bass * 0.02;
    float perturbedRadius = ringRadius + waveDisp * 0.06;
    float ring = abs(radius - perturbedRadius);
    outerRing = smoothstep(ringWidth, 0.0, ring);
    outerRing += smoothstep(ringWidth * 3.0, 0.0, ring) * 0.3; // Soft glow

    // Color for outer ring - match buffer palette
    vec3 col1 = vec3(0.1, 0.5, 1.0);   // Electric blue
    vec3 col2 = vec3(1.0, 0.1, 0.6);   // Hot pink
    vec3 col3 = vec3(0.0, 1.0, 0.7);   // Cyan
    vec3 col4 = vec3(0.6, 0.2, 1.0);   // Purple

    float colorShift = sin(t * 0.4 + bass * 2.0) * 0.5 + 0.5;
    vec3 ringColor = mix(col1, col2, colorShift);
    ringColor = mix(ringColor, col3, abs(waveDisp));

    vec3 freshRing = outerRing * ringColor * (1.2 + rms * 0.8);

    // Chromatic aberration based on distance from center
    float aberration = radius * 0.015 * (1.0 + rms);
    vec2 redOffset = p * (1.0 + aberration) * iResolution.y / iResolution.xy + 0.5;
    vec2 blueOffset = p * (1.0 - aberration) * iResolution.y / iResolution.xy + 0.5;

    float r = texture(iChannel0, redOffset).r;
    float g = buffer.g;
    float b = texture(iChannel0, blueOffset).b;

    r = 1.0 - exp(-r * 0.4);
    b = 1.0 - exp(-b * 0.4);

    vec3 chromatic = vec3(r, g, b);

    // Vignette - subtle darkening at edges
    float vignette = 1.0 - smoothstep(0.4, 0.9, radius) * 0.4;

    // Final composition
    vec3 finalColor = chromatic * vignette;
    finalColor += freshRing;

    // Center glow pulse - breathing with bass
    float pulse = 0.5 + 0.5 * sin(t * 2.0 + bass * 4.0);
    float centerGlow = exp(-radius * 6.0) * pulse * 0.5;
    finalColor += centerGlow * mix(col3, col2, pulse);

    // Subtle outer glow
    float outerGlow = smoothstep(0.35, 0.5, radius) * smoothstep(0.6, 0.45, radius);
    outerGlow *= 0.15 * (1.0 + high);
    finalColor += outerGlow * col4;

    // Gentle bloom - brighten already bright areas
    float luminance = dot(finalColor, vec3(0.299, 0.587, 0.114));
    finalColor += finalColor * smoothstep(0.4, 0.8, luminance) * 0.3;

    // Slight saturation boost
    float gray = dot(finalColor, vec3(0.33));
    finalColor = mix(vec3(gray), finalColor, 1.15);

    // Soft tone mapping
    finalColor = finalColor / (1.0 + finalColor * 0.5);

    // Final contrast curve
    finalColor = pow(finalColor, vec3(0.92));

    fragColor = vec4(finalColor, 1.0);
}
