// resolution: 1.0
precision highp float;

// uniform vec2 iResolution; // Provided by ShaderToyLite as vec3
// uniform sampler2D iChannel0; // Provided by ShaderToyLite

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    // Use iResolution.xy for 2D coordinates
    vec2 uv = fragCoord.xy / iResolution.xy;
    fragColor = texture(iChannel0, uv);
} 