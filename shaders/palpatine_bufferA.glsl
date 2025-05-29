// resolution: 1.0


// Function to sample waveform from iAudioTexture (row 1)
// Input uvx is 0-1. iAudioTexture is typically 1024x2.
// Waveform data in iAudioTexture is normalized (0-1). This function maps it to -1 to 1.
float getWaveform(float uvx) {
    //ivec2 audioTexSize = textureSize(iAudioTexture, .25); // e.g., (1024, 2)
    //int xCoord = int(uvx * float(audioTexSize.x));
    //xCoord = clamp(xCoord, 0, audioTexSize.x - 1); // Clamp to stay within texture bounds
    // Fetch from row 1 (y-coordinate 1 for waveform)
    float waveVal = texture2D(iAudioTexture, vec2(uvx, 1.0)).r;
    return (waveVal * 2.0) - 1.0; // Map 0-1 range to -1 to 1 range
}

// Generates the visual representation of the sound wave
// uv_sound.x is for waveform lookup and horizontal envelope
// uv_sound.y is the vertical position for drawing the line relative to center
float sdSound(vec2 uv_sound) {
    float waveformValue = getWaveform(uv_sound.x);
    waveformValue *= 0.3;

    // Apply horizontal envelope based on uv_sound.x (screen horizontal position)
    float x_pos_factor = abs(pow(abs(uv_sound.x - 0.5) * 2.0, 2.5));
    x_pos_factor = clamp(1.0 - x_pos_factor, 0.0, 1.0);
    waveformValue *= x_pos_factor;

    // uv_sound.y is centered (e.g. -0.5 to 0.5 if called with uv.y - 0.5)
    // lineOffset is distance from the waveform's current y value
    float lineOffset = uv_sound.y - waveformValue;
    float line = 1.0 - abs(lineOffset) * 2.0; // Creates a sharper line
    line = clamp(line, 0.0, 1.0); // Ensure line intensity is positive

    float milkyLine = pow(line, 10.0) * 0.06;
    milkyLine += pow(line, 200.0) * 5.0; // Adds very sharp, bright highlights
    milkyLine *= x_pos_factor;
    return milkyLine;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = fragCoord.xy / iResolution.xy; // Use iResolution.xy
    vec2 texel = 1.0 / iResolution.xy; // Use iResolution.xy
    texel *= 3.0; // Spread for diffusion sampling, from original p5 sketch

    // Sample previous frame (from iChannel0, which is this buffer's last output)
    vec4 prev = texture(iChannel0, uv);
    vec4 prevUp = texture(iChannel0, uv - vec2(0.0, texel.y));
    vec4 prevDown = texture(iChannel0, uv + vec2(0.0, texel.y));
    vec4 prevLeft = texture(iChannel0, uv - vec2(texel.x, 0.0));
    vec4 prevRight = texture(iChannel0, uv + vec2(texel.x, 0.0));

    // Diffusion effect (weighted average of neighbors and current pixel's previous state)
    // Weights from original p5: neighbors 0.2, previous self 0.18. Sum = 0.98 (slight decay)
    vec4 diffusion = (prevUp + prevDown + prevLeft + prevRight) * 0.2 + prev * 0.2;
    // diffusion *= 0.999; // Optional additional decay, was commented out in p5

    // Generate the sound wave visualization
    // Call sdSound with uv.y shifted to cen ter the wave vertically on screen
    float wave = sdSound(vec2(uv.x, uv.y - 0.5));

    vec3 waveColor = vec3(0.6, 0.2, 1.0); // Purple base color
    vec3 lightningColor = vec3(0.7, 0.4, 1.0); // Lighter purple for highlights

    // Mix colors based on wave intensity and RMS
    vec3 col = diffusion.rgb;
    col += wave * waveColor;// * (iRMSOutput); // Base wave color, boosted by RMS
    //col += wave * lightningColor * pow(iRMSOutput, 2.0); // Highlights, strongly boosted by RMS

    // Add electric crackle effect (from original p5 sketch, kept commented)
    /*
    float crackle = fract(sin(uv.x * 100.0 + iTime * 5.0) *
                         cos(uv.y * 120.0 - iTime * 3.0) * 43758.5453123);
    col += crackle * wave * 2.1 * vec3(0.8, 0.6, 1.0);
    */

    // Add vignette effect
    // puv_intermediate.x = uv.x * (1-uv.x), max 0.25 at uv.x=0.5, min 0 at edges
    vec2 puv_intermediate = 1.0-abs(uv - .5); //abs(uv-.5);// * (1.0 - uv);
    // vignette_val is ( (uv.x*(1-uv.x)) * (uv.y*(1-uv.y)) * 35.0 ) ^ (0.05 + tiny_lightning_factor)
    // This term is 1.0 at the center and fades towards edges.
    float vignette_factor = pow(puv_intermediate.x * puv_intermediate.y * 35.0, 1.0);
    vignette_factor = clamp(vignette_factor, 0.0, 1.0); // Ensure it's a multiplier between 0 and 1

    col = 0.98 * col;// * vignette_factor; // Apply vignette and slight overall dimming

    fragColor = vec4(col, 1.0);
} 