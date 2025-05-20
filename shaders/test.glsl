/*
    Playing with turbulence and translucency from
    @Xor's recent shaders, e.g.
        https://www.shadertoy.com/view/wXjSRt
        https://www.shadertoy.com/view/wXSXzV
        
*/

void mainImage(out vec4 o, vec2 u) {
    float i,d,s,t=iTime*.2;
    vec3  p = iResolution; 
    u = (u-p.xy/2.)/p.y;
    
    for(o*=i;
        i++<1e2;
        d += s = .03 + abs(.4-abs(p.x))*.3,
        o += 1. /  s)
        
        for (s = .05, p = vec3(u* d,d+t);
             s < 1.;
             p.yz *= mat2(cos(.01*t+vec4(0,33,11,0))),
             p += cos(t+p.yzx*2.)*.1,
             p += abs(dot(sin(6.*t+p.z+p * s * 32.), vec3(.006))) / s,
             s += s);
             
    o *= mix(vec4(1,2,4,0), vec4(4,2,1,0),
             smoothstep(.3, -.3, u.x));
         
    o = tanh(o*o / 1e7);
}