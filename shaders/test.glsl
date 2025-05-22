//https://iquilezles.org/articles/palettes/
vec3 palette( float t ) {
    vec3 a = vec3(.5);
    vec3 b = vec3(.5);
    vec3 c = vec3(1.);
    vec3 d = vec3(0.263,0.416,0.557);

    return a + b*cos(6.28318*(c*t+d));
}

// Hash function for pseudo-random values
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float circle(vec2 uv, vec2 center, float size) {
    float d = length(uv - center);
    return exp(-pow(d / size, 2.) * 6.); // soft edges
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = (fragCoord * 2. - iResolution.xy) / iResolution.y;
    vec2 uv0 = uv;
    vec3 finalColor = vec3(0.);
        // Sample microphone input
    float audio = texture(iChannel0, vec2(.01, .25)).r*.5;
    float radius = 0.15 + audio * 2.2;
    
    float d = length(uv) - radius - sin(iTime*3.1416)*.02;
    
    uv = vec2(uv.x - .5, uv.y + .5);

    vec3 col = palette(length(uv) * .3 + iTime * .4);

    d = pow(.01 / d, 1.00000006);

    finalColor += col * d * 3.;
    
    for (int i = 0; i < 10; i++) {
        float fi = float(i);
        vec2 seed = vec2(fi, fi * 3.);
        vec2 pos = vec2(hash(seed), hash(seed + 1.)) * 2. - 1.;

        float speed = hash(seed + 2.) * 1.5 + .5;
        pos += vec2(sin(iTime * speed + fi), cos(iTime * speed + fi * 1.23)) * .2;

        float fadeCircle = circle(uv0, pos, .2);
        vec3 circleColor = palette(fi * .1 + iTime * .1);
        finalColor += circleColor * fadeCircle * .3;
    }
    
    fragColor = vec4(finalColor, 1.);
}