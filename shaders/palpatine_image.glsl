// resolution: 1.0
precision highp float;

// uniform vec2 iResolution; // Provided by ShaderToyLite as vec3
// uniform sampler2D iChannel0; // Provided by ShaderToyLite
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

    float milkyLine = pow(line, 10.0) * 0.1;
    milkyLine += pow(line, 1000.0) * 30.0; // Adds very sharp, bright highlights
    milkyLine *= x_pos_factor;
    return milkyLine;
}


void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    // Use iResolution.xy for 2D coordinates
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec3 buffer = texture(iChannel0, uv).rgb;
    vec3 wave = sdSound(vec2(uv.x, uv.y - 0.5)) * vec3(0.6, 0.2, 1.0);
    fragColor = vec4(buffer * .2 + wave, 1.0);

} 