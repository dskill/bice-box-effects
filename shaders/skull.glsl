// https://www.shadertoy.com/view/sdl3RM

float Rectangle(vec2 uv,vec2 p,float width,float height,float blur){
   vec2 W = vec2(width,height);
   vec2 s = smoothstep(W+blur,W-blur,abs(uv-p));
   return s.x*s.y;
}

float sdWaveform(vec2 uv) {
    // Sample waveform data from iAudioTexture
    float waveformValue = (texture(iAudioTexture, vec2(uv.x * 0.1 + 0.5, 0.75)).x - 0.5) * 2.0; // Normalize to -1 to 1
    waveformValue *= 0.3; // Scale the amplitude
    waveformValue *= 1.0 - abs(pow(abs(uv.x)*0.5, 1.5)); // Apply tapering
    
    float lineOffset = uv.y - waveformValue;
    float line = 1.0 - abs(lineOffset) * .25;
    line = max(line, 0.0);
    
    // Create heavy metal glow effect
    float metalGlow = pow(line, 1.0) * 0.3;
    metalGlow += pow(line, 8.0) * 0.6;
    metalGlow += pow(line, 400.0) * 10.0;
    
    return metalGlow;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    
    // Normalized pixel coordinates (from 0 to 1)
    vec2 uv = 5.* ( fragCoord - .5*iResolution.xy ) /iResolution.y;

    uv.y *= 1.0 - iRMSOutput * 2.0;
    uv.x = abs(uv.x);
    
    // Sample waveform at center (x = 0.5) for skull movement
    float centerWaveform = (texture(iAudioTexture, vec2(uv.x, 0.75)).x - 0.5) * 2.0;
    centerWaveform *= 0.15; // Scale the skull movement
    
    // Apply skull movement offset to all rectangles
    vec3 col  = vec3(Rectangle(uv,vec2( 0     ,0.075 + centerWaveform ),0.27   ,0.22 ,0.01));
         col -= vec3(Rectangle(uv,vec2( 0     ,-0.2 + centerWaveform ),0.13   ,0.13 ,0.01));
         col -= vec3(Rectangle(uv,vec2( 0.135   ,0.06 + centerWaveform ),0.07   ,0.11 ,0.01));
         col += vec3(Rectangle(uv,vec2( 0.13   ,-0.1 + centerWaveform ),0.07   ,0.04,0.01));
    vec3 col2 = vec3(Rectangle(uv,vec2( 0     ,-0.2 + centerWaveform ),0.15   ,0.15 ,0.01));
         col2-= vec3(Rectangle(uv,vec2( 0.0325,-0.2 + centerWaveform ),0.015  ,0.15 ,0.01));
         col2-= vec3(Rectangle(uv,vec2( 0.1   ,-0.2 + centerWaveform ),0.015  ,0.15 ,0.01));
         col2+= vec3(Rectangle(uv,vec2( 0     ,-0.2 + centerWaveform ),0.07  ,0.015 ,0.01));
         //col2+= vec3(Rectangle(uv,vec2(-0.04  ,-0.32),0.0075 ,0.03 ,0.01));
    col = max(col,col2);
    
    // Add white waveform through the middle
    vec2 waveUV = uv;
    waveUV.x = (fragCoord.x - 0.5*iResolution.x) / iResolution.y * 5.0; // Use original x coordinate without abs()
    waveUV.y += .5;
    float wave = sdWaveform(waveUV);
    float rmsMultiplier = iRMSOutput + 0.3;
    
    // White color palette
    vec3 whiteColor = vec3(1.0, 1.0, 1.0) * 1.2; // Bright white
    vec3 whiteColor2 = vec3(0.8, 0.8, 0.8) * 1.0; // Dimmer white
    vec3 waveColor = mix(whiteColor2, whiteColor, wave);
    
    col += waveColor * wave * rmsMultiplier;
    
    // Output to screen
    fragColor = vec4(col ,1.0);
}