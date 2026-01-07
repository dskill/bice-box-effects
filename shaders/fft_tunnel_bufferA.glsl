// resolution: 0.5

#define PI 3.14159265359
#define TAU 6.28318530718

// Audio texture helpers
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
// Located at y = 0.25
float getFFT(float freqX) {
    return texture(iAudioTexture, vec2(clamp(freqX, 0.0, 1.0), 0.25)).x;
}

// Waveform data: x = time sample (0-1 maps to 512 samples)
// Located at y = 0.75 - this wraps naturally around a circle!
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

// Smooth noise for tunnel distortion
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash(i), hash(i + vec2(1, 0)), f.x),
        mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x),
        f.y
    );
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 p = (fragCoord.xy - 0.5 * iResolution.xy) / iResolution.y;

    // Audio values
    float bass = getBass();
    float mid = getMid();
    float high = getHigh();
    float rms = iRMSOutput;

    // Time with audio influence
    float t = iRMSTime * 0.3;

    // Convert to polar coordinates
    float angle = atan(p.y, p.x);
    float radius = length(p);

    // Tunnel zoom effect - pull toward center over time
    vec2 center = vec2(0.5);
    vec2 centered_uv = uv - center;
    float zoom = 0.985 - rms * 0.01; // Faster zoom with louder audio
    vec2 zoomed_uv = centered_uv * zoom + center;

    // Sample previous frame with zoom
    vec4 prev = texture(iChannel0, zoomed_uv);

    // Rotation based on audio
    float rotAmount = t * 0.2 + bass * 0.3;
    p *= rot(rotAmount);
    angle = atan(p.y, p.x);

    // Map angle to waveform sample position (0 to 1)
    // Angle goes from -PI to PI, we want 0 to 1
    float wavePos = (angle + PI) / TAU;

    // Get waveform value for this angle - this creates the circular waveform display
    float wave = getWaveformSmooth(wavePos);
    // Waveform is 0-1, center it around 0.5 and scale
    float waveDisplacement = (wave - 0.5) * 2.0; // Now -1 to 1

    // Multiple concentric rings displaced by waveform
    float rings = 0.0;
    float ringColorMix = 0.0;
    for (float i = 0.0; i < 6.0; i++) {
        float ringRadius = 0.06 + i * 0.07;

        // Each ring samples waveform with slight time offset for ripple effect
        float waveOffset = getWaveformSmooth(mod(wavePos + i * 0.05, 1.0));
        float waveDisp = (waveOffset - 0.5) * 2.0;

        // Perturb ring radius by waveform
        float displacement = 0.04 + i * 0.01; // Outer rings displace more
        float perturbedRadius = ringRadius + waveDisp * displacement;

        // Breathing effect - rings slightly pulse
        perturbedRadius += sin(t * 3.0 + i * 1.2) * 0.006 * (1.0 + bass);

        // Ring width based on FFT bass for thickness variation
        float ringWidth = 0.010 + bass * 0.015;

        // Ring SDF with glow
        float ring = abs(radius - perturbedRadius);
        float ringIntensity = smoothstep(ringWidth, 0.0, ring);

        // Add soft glow around each ring
        ringIntensity += smoothstep(ringWidth * 4.0, 0.0, ring) * 0.2;

        // Vary intensity per ring
        rings += ringIntensity * (0.6 + i * 0.08);
        ringColorMix += ringIntensity * i / 6.0;
    }

    // Tunnel walls - radial lines that create depth illusion
    float segments = 16.0;
    float segmentAngle = mod(angle * segments / TAU + t * 1.5, 1.0);
    float tunnelLines = smoothstep(0.42, 0.5, segmentAngle) * smoothstep(0.58, 0.5, segmentAngle);
    tunnelLines *= smoothstep(0.5, 0.08, radius); // Fade toward center
    tunnelLines *= 0.2 + high * 0.5;

    // Add secondary spinning lines going opposite direction
    float segments2 = 8.0;
    float segmentAngle2 = mod(angle * segments2 / TAU - t * 0.8, 1.0);
    float tunnelLines2 = smoothstep(0.35, 0.5, segmentAngle2) * smoothstep(0.65, 0.5, segmentAngle2);
    tunnelLines2 *= smoothstep(0.45, 0.15, radius);
    tunnelLines2 *= 0.15 + mid * 0.3;

    // Color palette - shift with audio
    vec3 col1 = vec3(0.1, 0.5, 1.0);   // Electric blue
    vec3 col2 = vec3(1.0, 0.1, 0.6);   // Hot pink
    vec3 col3 = vec3(0.0, 1.0, 0.7);   // Cyan
    vec3 col4 = vec3(0.6, 0.2, 1.0);   // Purple

    float colorShift = sin(t * 0.4 + bass * 2.0) * 0.5 + 0.5;
    vec3 ringColor = mix(col1, col2, colorShift);
    ringColor = mix(ringColor, col3, ringColorMix);
    ringColor = mix(ringColor, col4, high * 0.5);

    // Tunnel line colors - different for each layer
    vec3 lineColor1 = mix(col3, col1, sin(angle * 2.0 + t) * 0.5 + 0.5);
    vec3 lineColor2 = mix(col4, col2, cos(angle * 1.5 - t) * 0.5 + 0.5);

    // Combine new elements
    vec3 newColor = rings * ringColor * (1.2 + rms * 1.5);
    newColor += tunnelLines * lineColor1 * 0.4;
    newColor += tunnelLines2 * lineColor2 * 0.3;

    // Center glow - pulsing with bass
    float centerGlow = exp(-radius * 5.0) * (0.4 + bass * 0.6);
    vec3 glowColor = mix(col2, col3, sin(t * 2.0) * 0.5 + 0.5);
    newColor += centerGlow * glowColor;

    // Outer edge subtle glow
    float edgeGlow = smoothstep(0.3, 0.5, radius) * 0.1 * (1.0 + mid);
    newColor += edgeGlow * col4;

    // Decay previous frame
    vec3 accumulated = prev.rgb * (0.92 + rms * 0.05);

    // Combine
    vec3 finalColor = accumulated + newColor;

    fragColor = vec4(finalColor, 1.0);
}
