// Image: Final output using BufferA
void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    
    // Get BufferA output
    vec4 bufferA = texture(iChannel0, uv);
    
    // Get audio FFT data
    float audioFFT = texture(iAudioTexture, vec2(uv.x, 0.75)).r;
    
    // Simple post-processing: add some color based on FFT
    vec3 color = bufferA.rgb;
    color += vec3(audioFFT * 0.5, 0.0, audioFFT * 0.3);
    
    // Add a simple vignette
    float vignette = 1.0 - length(uv - 0.5) * 0.8;
    color *= vignette;
    
    fragColor = vec4(color, 1.0);
}