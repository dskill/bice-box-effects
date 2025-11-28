// http://www.pouet.net/prod.php?which=57245
// If you intend to reuse this shader, please add credits to 'Danilo Guanabara'
// Audio-reactive version for grunge_distortion effect

#define t iTime
#define r iResolution.xy

void mainImage( out vec4 fragColor, in vec2 fragCoord ){
	vec3 c;
	float l,z=t;

	// Audio influence
	float rms = iRMSOutput;
	float bass = texture(iAudioTexture, vec2(0.05, 0.25)).x;

	for(int i=0;i<3;i++) {
		vec2 uv,p=fragCoord.xy/r;
		uv=p;
		p-=.5;
		p.x*=r.x/r.y;
		z+=.07;
		l=length(p);

		// Add audio-driven distortion to the feedback loop
		float distortion = (sin(z)+1.)*abs(sin(l*9.-z-z));
		distortion *= (1.0 + rms * 10.5); // Amplify with RMS

		uv+=p/l*distortion;
		c[i]=.01/length(mod(uv,1.)-.5);
	}

	// Boost brightness with audio
	c = c/l * (0.8 + rms * 0.4);

	fragColor=vec4(c,t);
}
