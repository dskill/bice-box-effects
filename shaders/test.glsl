        // Assuming iChannel0 is a 1D texture with waveform data
        // UV.x goes from 0.0 to 1.0 across the waveform
        float waveformSample(float uv_x) {
            // texelFetch is good for precise sample access, texture for interpolated access
            // Using texture() for simplicity here, might need texelFetch for raw samples
            // The .r component is assumed to hold the waveform value.
            return texture(iChannel0, vec2(uv_x, 0.5)).r*.5-0.5; 
        }
/* This animation is the material of my first youtube tutorial about creative 
   coding, which is a video in which I try to introduce programmers to GLSL 
   and to the wonderful world of shaders, while also trying to share my recent 
   passion for this community.
                                       Video URL: https://youtu.be/f4s1h2YETNY
*/

//https://iquilezles.org/articles/palettes/
vec3 palette( float t ) {
    vec3 a = vec3(0.5, 0.5, 0.5);
    vec3 b = vec3(0.5, 0.5, 0.5);
    vec3 c = vec3(1.0, 1.0, 1.0);
    vec3 d = vec3(0.263,0.416,0.557);

    return a + b*cos( 6.28318*(c*t+d) );
}

//https://www.shadertoy.com/view/mtyGWy
void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = (fragCoord * 2.0 - iResolution.xy) / iResolution.y;
    vec2 uv0 = uv;
    vec3 finalColor = vec3(0.0);
    
    float waveform = waveformSample( fragCoord.x/iResolution.x);

    for (float i = 0.0; i < 4.0; i++) {
        uv.y += waveform * .4;

        uv = fract(uv * 1.5) - 0.5;

        float d = length(uv) * exp(-length(uv0));
        vec3 col = palette(length(uv0) + i*.4 + iTime*.4);
    

        d = sin(d*8. + iTime)/8.;
        d = abs(d);

        d = pow(0.01 / d, 1.2);

        finalColor += col * d;
        uv.y += sin(2.0*waveform) * 0.3;
    }
        
    fragColor = vec4(finalColor, 1.0);
}