// https://www.shadertoy.com/view/4lBcDG
// resolution: 0.75
void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
	vec2 uv = fragCoord.xy / iResolution.xy;
    vec3 col_add=vec3(0.0, 0.0, 0.0), col_scale=vec3(2.0,2.0,1.0);
    
	fragColor = vec4(abs(texture(iChannel0, uv).xzy)*col_scale+col_add, 1.0);
}