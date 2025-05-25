// https://www.shadertoy.com/view/XfVyDK
// Cotterzz 

#define IP 32
#define SG 48.
void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = fragCoord/iResolution.xy;
    vec3 col = texelFetch(iChannel0, ivec2(fragCoord), 0 ).rgb;
    float xp = (mod(float(iTime*48.), SG))/SG;
    for(int i = 0; i<IP; i++){
        xp+=(1./(SG*float(IP)));
        float yp = 0.5 + (sin(xp*30.))/max(3.,(800.*(pow(abs(0.5-xp), 2.))));
        col *= vec3(0.98,0.995,0.99);
        float dist = distance(uv, vec2(xp,yp));
        col+=vec3(1./(dist*4096.));
    }
    fragColor = vec4(col,1.0);
}