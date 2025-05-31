// created by florian berger (flockaroo) - 2016
// License Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License.

// single pass CFD
// ---------------
// this is some "computational flockarooid dynamics" ;)
// the self-advection is done purely rotational on all scales. 
// therefore i dont need any divergence-free velocity field. 
// with stochastic sampling i get the proper "mean values" of rotations 
// over time for higher order scales.
//
// try changing "RotNum" for different accuracies of rotation calculation
//
// "angRnd" is the rotational randomness of the rotation-samples
// "posRnd" is an additional error to the position of the samples (not really needed)
// for higher numbers of "RotNum" "angRnd" can also be set to 0

#define RotNum 3
#define angRnd 1.0
#define posRnd 0.0

// uniform sampler2D iAudioTexture; // For FFT/waveform data -- Removed, provided by ShaderToy environment
// uniform float iRMSOutput;        // Overall loudness -- Removed, provided by ShaderToy environment

#define Res  iChannelResolution[0]
#define Res1 iChannelResolution[1]

const float ang = 2.0*3.1415926535/float(RotNum);
mat2 m = mat2(cos(ang),sin(ang),-sin(ang),cos(ang));

float hash(float seed) { return fract(sin(seed)*158.5453 ); }
vec4 getRand4(float seed) { return vec4(hash(seed),hash(seed+123.21),hash(seed+234.32),hash(seed+453.54)); }
vec4 randS(vec2 uv)
{
    //return getRand4(uv.y+uv.x*1234.567)-vec4(0.5);
    return texture(iChannel1,uv*Res.xy/Res1.xy)-vec4(0.5);
}

float getRot(vec2 uv, float sc)
{
    float ang2 = angRnd*randS(uv).x*ang;
    vec2 p = vec2(cos(ang2),sin(ang2));
    float rot=0.0;
    for(int i=0;i<RotNum;i++)
    {
        vec2 p2 = (p+posRnd*randS(uv+p*sc).xy)*sc;
        vec2 v = texture(iChannel0,fract(uv+p2)).xy-vec2(0.5);
        rot+=cross(vec3(v,0.0),vec3(p2,0.0)).z/dot(p2,p2);
        p = m*p;
    }
    rot/=float(RotNum);
    return rot;
}

void init( out vec4 fragColor, in vec2 fragCoord )
{
	vec2 uv = fragCoord.xy / Res.xy;
    fragColor = texture(iChannel2,uv);
}

#define keyTex iChannel3
#define KEY_I texture(keyTex,vec2((105.5-32.0)/256.0,(0.5+0.0)/3.0)).x

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    vec2 uv = fragCoord.xy / Res.xy;
    float waveform_strength = texture(iAudioTexture, vec2(uv.x, 0.75)).x*.5-.5;
    vec2 scr= uv*2.0;
    scr -= vec2(1.0);
    float sc=1.0/max(Res.x,Res.y);

    vec2 v=vec2(0);
    //v.x += waveform_strength*10.1;
    for(int level=0;level<20;level++)
    {
        if ( sc > 0.7 ) break;
        float ang2 = angRnd*ang*randS(uv).y;
        vec2 p = vec2(cos(ang2),sin(ang2));
        for(int i=0;i<RotNum;i++)
        {
            vec2 p2=p*sc;
            float rot=getRot(uv+p2,sc);
            //v+=cross(vec3(0,0,rot),vec3(p2,0.0)).xy;
            v+=p2.yx*rot*vec2(-1,1); //maybe faster than above
            p = m*p;
        }
      	sc*=2.0;
    }
    
    //v.y+=scr.y*0.1;
    
    //v.x+=(1.0-scr.y*scr.y)*0.8;
    
    //v/=float(RotNum)/3.0;
    
    // Modulate advection strength with iRMSOutput
    float advection_strength = (0.0 +  0.001); // Make effect more pronounced
    fragColor=texture(iChannel0,fract(uv+v*advection_strength));
    
    // Get a bass frequency from FFT (assuming FFT data is in the 0.25 y-coord of iAudioTexture)
    float fft_bass = texture(iAudioTexture, vec2(0.05, 0.25)).x; // Sample low frequency bin
    // Get a mid frequency for color modulation
    float fft_mid = texture(iAudioTexture, vec2(0.3, 0.25)).x;


    // add a little "motor" in the center, pulsed by bass
    float motor_strength = 0.05 + fft_bass * 1.025; // Modulate base strength with bass
    fragColor.xy += motor_strength *  ( scr.xy / (dot(scr,scr)/0.001+0.03));
    if (abs(uv.y - .5)<0.01) {
      // fragColor.xy += abs(waveform_strength)*0.2;
    }
    
    
    // Add subtle color modulation based on mid frequencies and RMS
    vec3 audio_color_tint = vec3(fft_mid * 0.2, fft_mid * 0.1, (0.5 + fft_bass * 0.5) * 0.15); // Example tint
    //fragColor.rgb += audio_color_tint * iRMSOutput;

    if(iFrame<=4 || KEY_I>0.5) init(fragColor,fragCoord);
}
