/** 
    License: Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
    
    I posting this as an unofficial challenge for anyone 
    to make a shader using the computer time as an input.
    
    I'm going to give myself a week to come up with 
    something cool. 
    
    05/25/2025 @byt3_m3chanic
*/

#define R iResolution
#define T iTime
#define M iMouse

#define PI  3.14159265359
#define PI2 6.28318530718

mat2 rot(float a) { return mat2(cos(a),sin(a),-sin(a),cos(a)); }
float hash21(vec2 a) { return fract(sin(dot(a, vec2(27.69, 57.53)))*458.53); }

float lsp(float begin, float end, float t) { return clamp((t - begin) / (end - begin), 0.0, 1.0); }
float eoc(float t) { return (t = t - 1.0) * t * t + 1.0; }


vec3 hue(float t){ return .45 + .45*cos(PI2*t*(vec3(.95,.97,.98)+vec3(1,.65,.2))); }

float getDigits(vec2 nv,int dec) {
    float d = 1e5;
    
        if(dec == 0) d = get0(nv);
        if(dec == 1) d = get1(nv);
        if(dec == 2) d = get2(nv);
        if(dec == 3) d = get3(nv);
        if(dec == 4) d = get4(nv);
        if(dec == 5) d = get5(nv);
        if(dec == 6) d = get6(nv);
        if(dec == 7) d = get7(nv);
        if(dec == 8) d = get8(nv);
        if(dec == 9) d = get9(nv);

    return d;
}


void mainImage( out vec4 O, in vec2 F ) {

    vec2 uv = (F.xy - R.xy*.5)/R.y;
    uv *= 2.;
    uv *=rot(.36*sin(T*.78));
    uv += vec2(cos(T*.5),sin(T*.5))*.3;

    float px = fwidth(uv.x);


    //@Smoothie https://www.shadertoy.com/view/clfyRX
    int sec = int(mod(iDate.w,60.));
    int minute = int(mod(iDate.w/60.,60.));
    int hour = int(mod(iDate.w/3600.,12.));
    //end


    vec2 xv =  ((F.xy/R.xy)*.999 )+.0009;
    float ff = T*.3;
    xv += vec2(cos(ff),sin(ff))*.003;
    vec3 C = mix(vec3(.0001),texture(iChannel0,xv).rgb,.96);
    vec3 clr = hue(uv.x*.2+T*.2);
    float num = float(hour);
    if(num == 0.) num = 12.;
    float h1 = mod(num / pow(10.0,1.),10.0);
    float h2 = mod(num / pow(10.0,0.),10.0);
    float d = getDigits(uv+vec2(1.,0),int(h1));
    d = min(d,getDigits(uv+vec2(.67,0),int(h2)));

    num = float(minute);
    float m1 = mod(num / pow(10.0,1.),10.0);
    float m2 = mod(num / pow(10.0,0.),10.0);
    d = min(d,getDigits(uv+vec2(.165,0),int(m1)));
    d = min(d,getDigits(uv-vec2(.165,0),int(m2)));

    num = float(sec);
    float s1 = mod(num / pow(10.0,1.),10.0);
    float s2 = mod(num / pow(10.0,0.),10.0);
    d = min(d,getDigits(uv-vec2(.67,0),int(s1)));
    d = min(d,getDigits(uv-vec2(1.,0),int(s2)));
    
    // int ampm = int(mod(iDate.w/3600.,24.));
    C = mix(C,clr,smoothstep(px,-px,abs(d)-.0033));
   
    /**    solid text

    C = mix(C,clr,smoothstep(px,-px,d));
    C = mix(C,C*.65,smoothstep(px,-px,abs(d)-.0033));
    */
    
    O = vec4(C,1.0);
}
