#define IP 24
#define SG 48.

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = fragCoord/iResolution.xy;
    vec3 col = texelFetch(iChannel0, ivec2(fragCoord), 0 ).rgb;
    float xp = (mod(float(iTime*48.), SG))/SG;

    // Get texture dimensions for precise sampling if needed, though direct indexing is fine for iAudioTexture
    // ivec2 audioTexSize = textureSize(iAudioTexture, 0);
    float waveValRaw = texture(iAudioTexture, vec2(xp, 1.0)).r;
    float waveValSigned = (waveValRaw * 2.0) - 1.0; // Map from [0,1] back to [-1,1]
    float yp = 0.5 + waveValSigned * 0.25;
    col += vec3(1./(distance(uv, vec2(xp,yp))*4096.));


    for(int i = 0; i<IP; i++){
        xp+=(1./(SG*float(IP)));

        // Fetch waveform data from iAudioTexture (row 1)
        // Assuming xp (0 to 1) can be used as the time coordinate for the waveform
        float waveValRaw = texture2D(iAudioTexture, vec2(xp, 1.0)).r;
        float waveValSigned = (waveValRaw * 2.0) - 1.0; // Map from [0,1] back to [-1,1]

        // Adjust yp based on the signed waveform value
        // Centering at 0.5, and scaling. You might need to adjust the scaling factor (e.g., 0.25)
        float yp = 0.5 + waveValSigned * 0.25;

        col *= vec3(0.98,0.995,0.99);
        float dist = distance(uv, vec2(xp,yp));
        col+=vec3(1./(dist*4096.));
    }
    
    fragColor = vec4(col,1.0);
}