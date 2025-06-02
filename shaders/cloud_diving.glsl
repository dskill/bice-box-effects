
//https://www.shadertoy.com/view/lcVGzz
// alternative background of my shader from Revision24 showdown final
// original version:
// https://livecode.demozoo.org/event/2024_03_29_shader_showdown_revision_2024.html
// resolution: 0.5
float gyroid (vec3 p) { return dot(cos(p),sin(p.yzx)); }

float fbm(vec3 p)
{
    float result = 0.;
    float a = .5;
    for (float i = 0.; i < 7.; ++i)
    {
        p += result*.1;
        p.z += iTime*.1;
        result += abs(gyroid(p/a)*a);
        a /= 1.7;
    }
    return result;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = (2.*fragCoord-iResolution.xy)/iResolution.y;
    vec3 ray = normalize(vec3(uv,.3));
    
    vec3 e = vec3(0.1*vec2(iResolution.x/iResolution.y), 0.);
    
    #define T(u) fbm(ray+u)
    vec3 normal = normalize(T(0.)-vec3(T(e.xzz),T(e.zyz),1.));
    vec3 color = 0.5 + 0.5 * cos(vec3(1,2,3)*5.4 - normal.x+.5);
    color *= smoothstep(-1.,1.,-normal.z);

    fragColor = vec4(color,1.0);
}