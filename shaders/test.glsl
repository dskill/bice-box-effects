// "Fantasy Escape" by nusan and jeenio
// PC 4 kilobytes intro made for Evoke 2022 (3rd place)

// Original Tools: Leviathan, 4klang, Shader Minifier, Crinkler
// https://www.pouet.net/prod.php?which=91930
// https://youtu.be/Fcug737CN1U

// if sound doesn't start or seems desynchronised:
// try clicking pause/start button in the "soundcloud" square in the bottom right
// then press rewind just under the shader picture on the left

// don't read the code, it was made in a hurry and it's bad :D

float time;

mat2 rot(float a) {return mat2(cos(a),sin(a),-sin(a),cos(a));}

float beat;
float beatid;
float mesureid;

// 26 letters + some numbers
const int Letters[31] = int[31](23535,31471,25166,31595,29391,4815,31310,23533,29847,13463,23277,29257,23423,23403,15214,5103,26474,23279,14798,9367,27501,12141,32621,23213,31213,29351,31727,448,5393,29671,31599);
// Pico 8 palette
const vec3 Pal[16] = vec3[16](vec3(0),vec3(0.125,0.2,0.48),vec3(0.494,0.145,0.325),vec3(0,0.513,0.192),
                              vec3(0.74,0.321,0.211),vec3(0.27),vec3(0.76,0.764,0.78),vec3(1,0.945,0.91),
                              vec3(1,0,0.3),vec3(1,0.639,0),vec3(1,0.925,0.153),vec3(0,0.886,0.196),
                              vec3(0.16,0.678,1),vec3(0.513,0.463,0.611),vec3(1,0.467,0.659),vec3(1,0.8,0.667));

int textcolor=7;
// print up to 6 characters encoded as an int (5 bits per character)
void String6(inout vec3 col, inout vec2 uv, int val) {
    float a = 0.;
    uv = floor(uv);
    for(int i=0; i<6; ++i) {
        int cdig = int(val)%32;
        if(cdig!=0) {
			vec2 mask = step(abs(uv-vec2(1.3,2.5)),vec2(1.5,2.5));
			a += float((Letters[cdig-1]>>int(uv.x+uv.y*3.))&1)*mask.x*mask.y;
        }
        uv.x -= 4.;
        val/=32;
    }
    
    if(a>.1) col=Pal[textcolor];
}

float rnd1(float t) {
	return fract(sin(t*725.542)*314.324);
}

// display the blinking pico 8 carret
void prompt(vec2 uv, inout vec3 col, float t) {
    float carret=7.+floor(max(0.,t))*4.;
    if(uv.x>carret && abs(uv.y)<3.) {
        col=Pal[(uv.x>carret+4. || int(time*3.)%2==0)?0:8];
    }
}

float box(vec3 p, vec3 s) {
    p=abs(p)-s;
    return max(p.x, max(p.y,p.z));
}

// SDF function for the 3D scene
float map(vec3 p) {

	vec3 bp=p;
	
	// very simple kifs
	float s=step(54.,beatid)*(1.-max(0.,1.-abs(time-38.))*2.)+step(76.,beatid)*3.;
    float d=999.;
    for(int i=0; i<5; ++i) {
		p.xz *= rot(time*.3+float(i));
		p.yz *= rot(time*.2-float(i)*.3);
        // the boxes
        d=min(d, box(p, vec3(.3)));
        // some cylinders
		if(beatid>69. && i==2) d=min(d, length(abs(p.xy)-1.)-.1);
        p=abs(p)-0.5-(sin(time*3.1415/2.+2.5+step(beatid,75.)*2.)*.5+.2)*s;
    }

	// simple box for the start
	if(beatid<48.) {
		d=box(bp, vec3(2));
		vec3 d2=abs(abs(bp)-.75)-.1;
		if(beatid>39.) d=max(d, -min(min(d2.x, d2.y),d2.z));
	}
	
    return d;
}

void cam(inout vec3 p) {
	float t=time*5.;
	p.xz *= rot(t*(.1+rnd1(mesureid)*.4));
    p.yz *= rot(t*.3);
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    float musictime=iTime;// iChannelTime[0]; doesn't work well for some reason
	time = min(musictime,106.);

	beat=fract(time*2.);
	mesureid=floor(time/4.-.5*step(58.,time*2.));
	beatid=floor(time*2.);
	
	vec2 uv = (fragCoord.xy/iResolution.xy-.5)*vec2(iResolution.x,-iResolution.y)/iResolution.y;
    vec2 fuv = floor(uv * 128.);

	bool bay=true;
	// Wave that remove the resolution/palette pico 8 constraints
	float wave=(60.+sin(time*20.+sin(fuv.x/40.+time)*10.+fuv.x/10.)*40.);
	float ll=fract(time/4.-.5);
	wave *= ll*max(0.,min(1.,2.-ll*4.));
	if(beatid>91. && abs(fuv.y)<wave+max(time-54.,0.)*(3.+cos(min(time,65.)*3.)*1.5)) {
		if(beatid<172.) fuv=uv * 128.;
		bay=false;
	}
	
	// outro wave
	if(time>78.) fuv.y+=sin(fuv.x/30.+time)*10.+sin(time)*20.-64.;
	// glitches
	if(abs(time-35.5)<3.5) fuv.x+=(rnd1(fuv.y+floor(time*15.)*.1)*20.-10.)*max(0.,sin(fuv.y/99.+3.*time));

	//////////////////////

    vec3 s=vec3(0,0,-10);
    vec3 r=normalize(vec3(fuv/128., 1));
	cam(s);
	cam(r);
    
    // basic raymarching
    vec3 p=s;
    for(int i=0; i<100; ++i) {
        float d=map(p);
        if(d<.001 || d>100.) break;
        p+=r*d;
    }
    
    fuv.x+=.5;
	// background colors
    vec3 col=vec3(fuv*.01*rot(time),sin(time*1.+fuv.x/17.)*.5+.5);
	if(time>50.) col=abs(vec3(sin(uv.x+time),uv.y,0));
	if(beatid>75.) col=vec3(1);
			
	float depth=length(p-s);
    if(depth<100.)
    col = vec3(map(p-r)); // simplest shading
    
	if(beatid>75.) {
		// palette colors in the background 
		col=pow(col,vec3(bay?2:4)) * (.7+depth/300.) * Pal[int(mesureid+(bay?9.:0.))%8+8];
	}

	if(bay) {
		// randomized bayern pattern
		int val = 0;
		int curdiv = 8;
		int scale = 1;
		for(int i=0;i<4;++i) {
			vec2 rrr=floor(fuv/float(curdiv*2));
			ivec2 pos = ((ivec2(abs(fuv))/curdiv)%2 + ivec2(fract(sin(rrr.xy*457.519+rrr.yx*542.517)*327.233)*2.))%2;
			val += int[4](0,2,3,1)[(pos.x+pos.y*2)%4] * scale;
            int pidx=(pos.x+pos.y*2)%4;
			if(pidx>0) pidx=pidx%3+1;
			//val += pidx * scale;
			curdiv /= 2;
			scale *= 4;
		}
		col+=((float(val)/256.)*.2+.1)*step(40.,beatid);
		float dist=999.;
		int best=0;
		// finding closest color from the palette
		for(int i=0;i<16; ++i) {
			float cur=length(max(col,0.)-Pal[i]);
			if(cur<dist) {
				dist=cur;
				best=i;
			}
		}
		col = Pal[best];
	}
    
			
	//////////////////////
	fuv+=64.;
    
	if(time>70.) col*=0.;

	// cute circles for intro/outro
	if(beatid<32. || beatid>171.) {
		if(beatid<32.)col=Pal[1];
		if(beatid>16.+fuv.y*.06 && beatid<32.) col=Pal[int(fuv.x/4.+(sin(fuv.y*.03+time+beatid)*10.+fuv.y/2.)*step(24.,beatid))%16];
		if(beatid>15.) {
			for(float i=0.;i<15.; ++i) {
				float ct=i*7.37+beatid*12.7;
				vec2 pos=vec2(abs(fract(ct)-.5),abs(fract(ct*.7)-.5));
				if(beatid>23.) pos.y=pos.y*0.2+.2+sin(time*.7+i*.7)*.1-step(80.,time)*.3;
				pos=fuv-pos*212.-12.;
				float size=floor(rnd1(beatid+i*.1)*12.);
				// circfill
				if(length(pos)-size<0.) col=Pal[int(i)];
				// circ
				if(abs(length(pos)-size+0.1)<.5) col=Pal[7];
			}
		}
	}
    	

	vec2 baseuv=fuv-2.;
    vec2 suv = baseuv;
	// intro
	if(time<7.5) {
		col*=0.;
		vec2 suv2 = baseuv*.7;
		
		String6(col, suv2, 935824688);
		suv.y-=16.;
    
		if(time>1.) {
			textcolor=6;
			String6(col, suv, 935824688);
			String6(col, suv, 54985920);
			String6(col, suv, 485589811);
			String6(col, suv, 176627);
			suv.x=baseuv.x;
			suv.y-=6.;
			String6(col, suv, 660013858);
			String6(col, suv, 336462273);
			String6(col, suv, 16038053);
		}
		if(time>1.2) {
			suv.x=baseuv.x;
			suv.y-=14.;
			String6(col, suv, 604160820);
			String6(col, suv, 16384469);
			String6(col, suv, 15026);
		}
		if(time>1.6) {
			suv.x=baseuv.x;
			suv.y-=14.;

			textcolor=7;
			String6(col, suv, 15386653);
			baseuv.y-=52.;
			prompt(baseuv, col, min(time-2.9,3.));
		}
		if(time<.8)
		{ col=Pal[abs(int(uv.x*.05+uv.y*floor(time*10.)*7.7))%16]; }
		if(time<0.4) col*=0.;
	}
        
	// outro
	if(time>70.) {
		if(time<78.) {
			textcolor=14;
			String6(col, suv, 446315186);
			String6(col, suv, 522785797);
			String6(col, suv, 18);
			suv.x=baseuv.x;
			suv.y-=6.;
			textcolor=6;
			String6(col, suv, 550687361);
			String6(col, suv, 168284180);
			String6(col, suv, 136376);
			suv.x=baseuv.x;
			suv.y-=6.;
			String6(col, suv, 211437600);
			String6(col, suv, 12625001);
			String6(col, suv, 658814252);
			suv.x=baseuv.x;
			suv.y-=6.;
			textcolor=13;
			String6(col, suv, 52560329);
			String6(col, suv, 182759447);
			String6(col, suv, 913248);
			suv.x=baseuv.x;
			suv.y-=6.;
		}
		textcolor=7;
		String6(col, suv, 553689117);
		String6(col, suv, 385553433);
		String6(col, suv, 1039136773);
		suv.x=baseuv.x;
		suv.y-=2.;
		if(time<78.)
		{ prompt(suv, col, min((time-70.2)*2.2,16.)); }

		if(beatid>187. && abs(wave-abs(fuv.y-4.5)+6.)<2.) col = Pal[abs(int(fuv.x/4.-time*10.))%16];
	}
	
	// final rainbow filter on text
	if(time>78. && beatid<172.) col=step(.7,col.y)*Pal[abs(int(time*4.+floor((fuv.x-1.)/4.)))%8+6];
	
	// letterboxing
	float anim=max(0.,time-38.)*99.*step(time,78.);
	if(depth>100. || time<32. || time>78.)
    { col *= step(-anim,fuv.x)*step(fuv.x,128.+anim); }
	
	//////////////////////

    fragColor = vec4(col-max(musictime-107.,0.),1);
}