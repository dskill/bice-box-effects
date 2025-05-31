

/////////// spicy noise
float fbm (vec3 seed)
{
    // thebookofshaders.com/13
    float result = 0.;
    float a = .5;
    for (int i = 0; i < 4; ++i)
    {
        // distort
        seed += result / 2.;
        
        // animate
        seed.y -= .1*iTime/a;
        
        // accumulate
        result += gyroid(seed/a)*a;
        
        // granule
        a /= 3.;
    }
    return result;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{

    /////////// coordinates
    vec2 uv = fragCoord/iResolution.xy;
    vec2 p = (2.*fragCoord-R)/R.y;
    
    // salt
    float rng = hash13(vec3(fragCoord, iFrame));
    
    // noise
    vec3 seed = vec3(p, length(p) + iTime) * 2.;
    float noise = fbm(seed);
    float a = noise * 3.14;
    
    // normal
    vec3 unit = vec3(vec2(rng*.005), 0.);
    vec3 normal = normalize(vec3(T(uv-unit.xz)-T(uv+unit.xz),
                                 T(uv-unit.zy)-T(uv+unit.zy),
                                 unit.y));
                                 
    // mask
    vec2 mask = vec2(1.-abs(uv.x-.5)*2., uv.y);
    
    // mouse
    vec2 mouse = iMouse.xy/R;
    float clic = step(0., iMouse.z);
    
    
    ////////// shape
    float shape = 1.;
    
    // bottom line
    shape *= ss(.01,.0,abs(uv.y));
    
    // horizontal center
    shape *= mask.x;
    
    // salt
    shape *= pow(rng, 4.);
    
    
    
    ////////// forces field
    vec2 offset = vec2(0);
            
    // turbulence                     
    offset -= vec2(cos(a),sin(a)) * fbm(seed+.195) * (1.-mask.y);

    // slope
    offset -= normal.xy * mask.y;
    
    // mouse
    vec2 velocity = vec2(0);
    p -= (2.*iMouse.xy-R)/R.y;
    float mouseArea = ss(.3,.0,length(p)-.1);
    offset -= clic * normalize(p) * mouseArea * 0.2;
    velocity += (texture(iChannel0, vec2(0)).yz - mouse);
    if (length(velocity) > .001) velocity = clic * normalize(velocity) * mouseArea;
    
    // inertia
    velocity = clamp(texture(iChannel0, uv+velocity*.05).yz * .99 + velocity * .5,-1.,1.);
    
    // gravity
    offset -= vec2(0,1) * (1.-mask.y);
    
    // inertia
    offset += velocity;
    
    // apply
    uv += .015 * offset * mask.x;
    
    
    
    ////////// frame buffer
    vec4 frame = texture(iChannel0, uv);
    
    // fade out
    float fade = iTimeDelta*.2;
    shape = max(shape, frame.r - fade);
    
    // result
    shape = clamp(shape, 0., 1.);
    fragColor = vec4(shape, velocity, 1);
    
    // previous mouse
    if (fragCoord.x < 1. && fragCoord.y < 1.) fragColor = vec4(0,mouse,1);
}