/** 
    License: Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
    
    I posting this as an unofficial challenge for anyone 
    to make a shader using the computer time as an input.
    
    I'm going to give myself a week to come up with 
    something cool. 
    
    05/25/2025 @byt3_m3chanic
    
    * BufferA  - has time functions
    * Common   - sdf shapes for numbers
*/

#define R iResolution

void mainImage( out vec4 O, in vec2 F )
{
    vec2 uv = (F.xy - R.xy*.5)/R.y;
    vec3 C = texture(iChannel0,(F.xy/R.xy)).rgb;
    O = vec4(pow(C, vec3(.4545)),1);
}
