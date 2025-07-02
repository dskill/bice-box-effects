// https://www.shadertoy.com/view/MlsGDs
// ascii terminal
// resolution: 0.5
float time;

float noise(vec2 p)
{
  return sin(p.x*10.) * sin(p.y*(3. + sin(time/11.))) + .2; 
}

mat2 rotate(float angle)
{
  return mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
}


float fbm(vec2 p)
{
  p *= 1.1;
  float f = 0.;
  float amp = .5;
  for( int i = 0; i < 3; i++) {
    mat2 modify = rotate(time/50. * float(i*i));
    f += amp*noise(p);
    p = modify * p;
    p *= 2.;
    amp /= 2.2;
  }
  return f;
}

float pattern(vec2 p, out vec2 q, out vec2 r) {
  q = vec2( fbm(p + vec2(1.)), fbm(rotate(.1*time)*p + vec2(1.)));
  r = vec2( fbm(rotate(.1)*q + vec2(0.)), fbm(q + vec2(0.)));
  return fbm(p + 1.*r);

}

float digit(vec2 p, float currentWaveformScreenY){
    vec2 grid = vec2(3.,1.) * 15.;
    vec2 s = floor(p * grid) / grid;
    vec2 q_pattern, r_pattern;
    float intensity = pattern(s/10., q_pattern, r_pattern)*1.3 - 0.03 ;
    
    vec2 p_times_grid = p * grid;
    vec2 p_fract_cell = fract(p_times_grid);
    p_fract_cell *= vec2(1.2, 1.2);

    float x = fract(p_fract_cell.x * 5.);
    float y = fract((1. - p_fract_cell.y) * 5.);
    int i = int(floor((1. - p_fract_cell.y) * 5.));
    int j = int(floor(p_fract_cell.x * 5.));
    int n = (i-2)*(i-2)+(j-2)*(j-2);
    float f = float(n)/16.;
    
    bool asciiIsOnCondition = intensity - f > 0.1;

    float waveformDotThickness = 0.05;
    bool waveformIsOnCondition = abs(p.y - currentWaveformScreenY) < (waveformDotThickness / 2.0);
    
    float isOn = (asciiIsOnCondition || waveformIsOnCondition) ? 1.0 : 0.0;
    
    return p_fract_cell.x <= 1.0 && p_fract_cell.y <= 1.0 ? isOn * (0.2 + y*4./5.) * (0.75 + x/4.) : 0.;
}

float hash(float x){
    return fract(sin(x*234.1)* 324.19 + sin(sin(x*3214.09) * 34.132 * x) + x * 234.12);
}

float onOff(float a, float b, float c, float current_iTime)
{
	return step(c, sin(current_iTime + a*cos(current_iTime*b)));
}

float displace(vec2 look, float current_iTime)
{
    float y = (look.y-mod(current_iTime/4.,1.));
    float window = 1./(1.+50.*y*y);
	return sin(look.y*20. + current_iTime)/80.*onOff(4.,2.,.8, current_iTime)*(1.+cos(current_iTime*60.))*window;
}

vec3 getColor(vec2 p, float current_iTime, float currentWaveformScreenY){
    
    float bar = mod(p.y + time*20., 1.) < 0.2 ?  1.4  : 1.;
    p.x += displace(p, current_iTime);
    float middle = digit(p, currentWaveformScreenY);
    float off = 0.002;
    float sum = 0.;
    for (float i_sum = -1.; i_sum < 2.; i_sum+=1.){
        for (float j_sum = -1.; j_sum < 2.; j_sum+=1.){
            sum += digit(p+vec2(off*i_sum, off*j_sum), currentWaveformScreenY);
        }
    }
    return vec3(0.9)*middle + sum/10.*vec3(0.,1.,0.) * bar;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    float audioFactor = 1.0 + iRMSOutput * 2.0;
    float dynamic_iTime = iRMSTime;

    vec2 p = fragCoord / iResolution.xy;

    float waveAmp = (texture(iAudioTexture, vec2(p.x, 0.75)).x - 0.5) * 2.0;
    waveAmp *= 0.5;
    float waveformScreenY = 0.5 + waveAmp;

    vec3 col = getColor(p, dynamic_iTime, waveformScreenY);
    fragColor = vec4(col,1);
}
