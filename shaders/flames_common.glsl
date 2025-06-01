
#define R iResolution.xy
#define ss(a,b,t) smoothstep(a,b,t)
#define N normalize
#define T(uv) texture(iChannel0, uv).r

// Dave Hoskins https://www.shadertoy.com/view/4djSRW
float hash11(float p)
{
    p = fract(p * .1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}
float hash13(vec3 p3)
{
	p3  = fract(p3 * .1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}
vec2 hash23(vec3 p3)
{
	p3 = fract(p3 * vec3(.1031, .1030, .0973));
    p3 += dot(p3, p3.yzx+33.33);
    return fract((p3.xx+p3.yz)*p3.zy);
}

// Martijn Steinrucken youtube.com/watch?v=b0AayhCO7s8
float gyroid (vec3 seed)
{
    return dot(sin(seed),cos(seed.yzx));
}