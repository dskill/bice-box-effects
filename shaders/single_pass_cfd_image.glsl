
// Fire Fighter Fever
// interactive fluid simulacre
//
// a bit like "Smell of Burning Plastic" but better https://www.shadertoy.com/view/7dyBRm
// resolution: 0.5
void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = fragCoord/iResolution.xy;
                                 
    // grayscale
    vec4 data = texture(iChannel0, uv);
    
    // Inigo Quilez iquilezles.org/www/articles/palettes/palettes.htm
    vec3 color = .5+.5*cos(vec3(1,2,3)*5. + data.r*5.+4.);
    color = mix(color, vec3(1), uv.y*.5);
    
    // normal
    float rng = hash13(vec3(fragCoord, iFrame));
    vec3 unit = vec3(vec2(.05*rng), 0.);
    vec3 normal = normalize(vec3(T(uv-unit.xz)-T(uv+unit.xz),
                                 T(uv-unit.zy)-T(uv+unit.zy),
                                 unit.y));
    
    // light
    float light = dot(normal, N(vec3(0,-4,1)))*.5+.5;
    color += light;
    
    // shadow
    color *= data.r;

    fragColor = vec4(color, 1);
    
    // debug art
    if (iMouse.z > 0. && iMouse.x/R.x < .2)
    {
        if (uv.x > .66) fragColor = vec4(normal*.5+.5, 1);
        else if (uv.x > .33) fragColor = vec4(vec3(sin(data.r*6.28*2.)*.5+.5), 1);
        else fragColor = vec4(data.yz*.5+.5,.5, 1);
    }
}