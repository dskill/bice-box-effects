// resolution: 0.2

// Based on AA sphere marching of SDF https://shadertoy.com/view/3Xc3W2
// AA version of antialiasing of dodecahedron + icosaedron https://shadertoy.com/view/MfyBRR
// Original by user t333Dj on Shadertoy
// Modified for audio reactivity in BICE Box

mat2 rot(float a) { 
    return mat2(cos(a), -sin(a), sin(a), cos(a)); 
}

// Hash function for pseudo-random values
float hash(float n) { 
    return fract(sin(n) * 43758.5453123); 
}

// Smooth audio-reactive distance function
float audioSDF(vec3 p, float audioScale, float shapeType) {
    // Sample different frequency ranges
    float bass = texture(iAudioTexture, vec2(0.1, 0.25)).r;
    float mid = texture(iAudioTexture, vec2(0.5, 0.25)).r;
    float high = texture(iAudioTexture, vec2(0.9, 0.25)).r;
    
    // Create multiple pulsing shapes
    vec3 q = p;
    
    // Bass-driven folding
    for (int i = 0; i < 3; i++) {
        if (dot(q, vec3(1, 0, 0)) < 0.) q = reflect(q, vec3(1, 0, 0));
        if (dot(q, vec3(-.5, .87, 0)) < 0.) q = reflect(q, vec3(-.5, .87, 0));
        if (dot(q, vec3(-.81,-.47, .36)) < 0.) q = reflect(q, vec3(-.81,-.47, .36));
    }
    
    q.z--;
    
    // Audio-reactive shape morphing
    float edge = length(q + vec3(-.81,-.47, .36) * clamp(-dot(q, vec3(-.81,-.47, .36)), 0., .72)) 
                - (.02 + bass * 0.08 + sin(iTime * 3.0 + shapeType) * 0.01);
    
    float face = abs(q.z + .5) - (.2 + mid * 0.4 + high * 0.2);
    
    return min(edge, face) * audioScale;
}

void mainImage( out vec4 O, in vec2 u ) {
    // INTENSE audio reactivity
    float rms = iRMSOutput;
    float rmsExplosive = rms * rms * rms * 5.0; // Cube for explosive effect
    float rmsPulse = (1.0 + sin(rms * 20.0)) * rms * 3.0; // Rapid pulsing
    
    // Sample waveform for dynamic effects - reduced influence
    float waveCenter = (texture(iAudioTexture, vec2(0.5, 0.75)).r - 0.5) * 2.0;
    float waveBass = (texture(iAudioTexture, vec2(0.1, 0.75)).r - 0.5) * 3.0;
    float waveHigh = (texture(iAudioTexture, vec2(0.9, 0.75)).r - 0.5) * 2.5;
    
    // Multiple frequency bands - MASSIVELY amplified
    float bass = texture(iAudioTexture, vec2(0.05, 0.25)).r * 8.0;
    float lowMid = texture(iAudioTexture, vec2(0.2, 0.25)).r * 6.0;
    float mid = texture(iAudioTexture, vec2(0.4, 0.25)).r * 7.0;
    float highMid = texture(iAudioTexture, vec2(0.7, 0.25)).r * 5.0;
    float high = texture(iAudioTexture, vec2(0.95, 0.25)).r * 10.0;
    
    vec3 R = iResolution;
    vec2 uv = (u - .5 * R.xy) / R.y;
    
    // EXTREME audio-reactive distortion
    uv *= (1.0 + rmsExplosive * 0.8 + sin(bass * 5.0) * 0.3);
    uv += vec2(waveCenter * 0.25, waveBass * 0.2);
    
    // Camera pulled back to see more of the shapes
    vec3 P = vec3(waveCenter * 0.5, waveBass * 0.4, 6.0 + rmsExplosive * 3.0);
    vec3 D = normalize(vec3(uv, -2.2 + rmsPulse * 0.3));
    
    // EXTREME audio-reactive camera rotation
    float camRotX = iTime * 0.3 + bass * 8.0 + waveCenter * 1.5 + sin(high * 10.0) * 2.0;
    float camRotY = iTime * 0.2 + mid * 6.0 + waveBass * 2.0 + cos(lowMid * 8.0) * 1.5;
    D.yz *= rot(camRotX);
    D.xz *= rot(camRotY);
    P.yz *= rot(camRotX);
    P.xz *= rot(camRotY);
    
    O = vec4(0.);
    float totalGlow = 0.0;
    
    // Multiple overlapping shapes for complexity
    for (int shapeIdx = 0; shapeIdx < 3; shapeIdx++) {
        vec3 rayPos = P;
        float shapeOffset = float(shapeIdx) * 2.1;
        float h = 9.0, minDist = 99.0, depth = 0.0;
        
        // INSANE shape rotations based on different frequencies
        float rotSpeed1 = bass * 15.0 + lowMid * 12.0 + iTime * (0.5 + float(shapeIdx) * 0.3) + sin(high * 20.0) * 5.0;
        float rotSpeed2 = mid * 18.0 + highMid * 10.0 + iTime * (0.3 + float(shapeIdx) * 0.2) + cos(bass * 15.0) * 8.0;
        
        for (int i = 0; i < 40 && h > .001 && depth < 8.0; i++) {
            vec3 q = rayPos;
            
            // EXTREME shape-specific rotations with audio chaos
            q.yz *= rot(rotSpeed1 + shapeOffset + waveCenter * 5.0);
            q.xz *= rot(rotSpeed2 + shapeOffset * 0.7 + waveBass * 6.0);
            q.xy *= rot(high * 8.0 + waveHigh * 3.0); // Add third rotation axis
            
            // EXPLOSIVE audio-reactive scaling and morphing
            float scale = 1.0 + rmsExplosive * 3.0 + rmsPulse * 1.5 + sin(bass * 8.0 + shapeOffset) * 0.8;
            scale += cos(mid * 10.0) * 0.6 + sin(high * 12.0) * 0.4;
            h = audioSDF(q / scale, scale, shapeOffset) * 0.6;
            
            if (depth > 1.0 && h / depth < minDist) {
                minDist = h / depth;
            }
            
            rayPos += h * D;
            depth += h;
        }
        
        if (h < 0.01) {
            // Hit surface - create glow based on shape and audio
            float intensity = 1.0 - minDist * R.y * 0.5;
            intensity = max(0.0, intensity);
            intensity *= intensity; // Square for more dramatic falloff
            
            // Classic Synthwave colors with intense animation
            vec3 color;
            if (shapeIdx == 0) {
                // Hot pink/magenta core
                color = vec3(1.0, 0.1, 0.8) * bass * 3.0 + vec3(0.2, 0.05, 0.4) * high * 2.0;
                color += vec3(sin(bass * 15.0) * 0.5 + 0.8, 0.05, cos(mid * 12.0) * 0.3 + 0.6) * lowMid;
            } else if (shapeIdx == 1) {
                // Electric cyan/blue
                color = vec3(0.0, 0.8, 1.0) * mid * 3.5 + vec3(0.3, 0.1, 0.8) * bass * 2.5;
                color += vec3(0.1, cos(high * 20.0) * 0.4 + 0.6, sin(bass * 10.0) * 0.3 + 0.9) * highMid;
            } else {
                // Neon purple/violet
                color = vec3(0.6, 0.0, 1.0) * high * 4.0 + vec3(0.8, 0.2, 0.9) * bass * 2.0;
                color += vec3(sin(mid * 25.0) * 0.2 + 0.5, 0.1, cos(high * 30.0) * 0.4 + 0.8) * mid;
            }
            
            // Synthwave glow pulse
            color *= (1.2 + rmsExplosive * 6.0 + rmsPulse * 3.0);
            
            // Add synthwave color cycling
            float colorCycle = iTime * 2.0 + rms * 10.0;
            color.r += sin(colorCycle) * 0.3 + cos(waveHigh * 7.5) * 0.2;
            color.g += sin(colorCycle + 2.1) * 0.2 + sin(waveCenter * 10.0) * 0.15;
            color.b += sin(colorCycle + 4.2) * 0.4 + cos(waveBass * 12.5) * 0.25;
            
            totalGlow += intensity * 0.4;
            O.rgb += color * intensity * 0.3;
        }
    }
    
    // Synthwave grid patterns
    float gridX = sin(uv.x * 15.0 + bass * 8.0) * cos(uv.y * 8.0 + mid * 6.0);
    float gridY = cos(uv.x * 12.0 + high * 10.0) * sin(uv.y * 12.0 + lowMid * 5.0);
    float scanlines = sin(uv.y * 40.0 + iTime * 5.0 + waveCenter * 7.5);
    
    gridX *= rmsExplosive * 0.2;
    gridY *= rmsPulse * 0.15;
    scanlines *= rms * 0.1;
    
    // Classic synthwave background colors - dark purples and magentas
    O.rgb += vec3(gridX * 0.3, gridX * 0.1, gridX * 0.4); // Dark magenta grid
    O.rgb += vec3(gridY * 0.1, gridY * 0.3, gridY * 0.6); // Dark cyan grid
    O.rgb += vec3(scanlines * 0.2, scanlines * 0.05, scanlines * 0.3); // Purple scanlines
    
    // Synthwave neon glow
    O.rgb += totalGlow * vec3(1.0, 0.2, 0.8) * rmsExplosive * 1.5; // Hot pink glow
    
    // Color cycling for that classic synthwave feel
    float synthCycle = iTime * 1.5 + rms * 8.0;
    O.rgb += vec3(sin(synthCycle) * 0.1 + 0.05, 0.02, cos(synthCycle) * 0.15 + 0.1); // Subtle pink/purple cycling
    
    // Enhanced contrast for that neon look
    O.rgb = mix(O.rgb, O.rgb * O.rgb, rmsExplosive * 0.6);
    O.rgb *= (1.0 + rms * 3.0);
    
    // Final synthwave color boost
    O.rgb += vec3(waveHigh * 0.15, waveCenter * 0.05, waveBass * 0.2) * rms;
    
    O.a = 1.0;
}