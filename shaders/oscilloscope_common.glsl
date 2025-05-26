/** 
    License: Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported License
    
    I posting this as an unofficial challenge for anyone 
    to make a shader using the computer time as an input.
    
    I'm going to give myself a week to come up with 
    something cool. 
    
    05/25/2025 @byt3_m3chanic
*/

float box( in vec2 p, in vec2 b ){
    vec2 d = abs(p)-b;
    return length(max(d,0.))+min(max(d.x,d.y),0.);
}

float rbox( in vec2 p, in vec2 b, in vec4 r ) {
    r.xy = (p.x>0.)?r.xy : r.zw;
    r.x  = (p.y>0.)?r.x  : r.y;
    vec2 q = abs(p)-b+r.x;
    return min(max(q.x,q.y),0.)+length(max(q,0.))-r.x;
}

const vec4 bz = vec4(.075,.055,.035,.0);
const vec4 bo = vec4(.175,.15,.075,.025);
const vec2 oa = vec2(.15,.1);

// special
float getdp(vec2 p) {
    float bt = length(p)-.075;
    return bt;
}
// number
float get0(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),bz.xxxx),
        -rbox(p,vec2(.1,.125),bz.zzzz)
    );
    return bt;
}
float get1(vec2 p) {
    float bt = box(p,vec2(.025,.175));
    return bt;
}
float get2(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.xxww),
        -rbox(p-vec2(-.075,.075),vec2(.175,.05),bz.zzww)
    );
    bt = max(bt,-box(p-vec2(-.085,.045),vec2(.08,.08)));
    float bb = max(
        rbox(p+vec2(.0,.075),oa,bz.wwxx),
        -rbox(p+vec2(-.075,.075),vec2(.175,.05),bz.wwzz)
    );
    bb = max(bb,-box(p+vec2(-.085,.045),vec2(.08,.08)));
    return min(bt,bb);
}
float get3(vec2 p) {
    vec2 of = vec2(0,.075);
    float bt = max(
    rbox(p-of,oa,bz.xxww),
    -rbox(p-of+vec2(.05,0),vec2(.15,.05),bz.zzww)
    );
    float bb = max(
    rbox(p+of,oa,bz.xxww),
    -rbox(p+of+vec2(.05,0),vec2(.15,.05),bz.zzww)
    );
    return min(bt,bb);
}
float get4(vec2 p) {
    float bt = min(box(vec2(p.x-.125,p.y),vec2(.025,.175)),
        rbox(p-vec2(.0,.075),oa,bz.wwwx)
    );
    bt = max(
        bt,
        -rbox(p-vec2(0,.115),vec2(.1,.095),bz.wwwz)
    );

    return bt;
}
float get5(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.wwwx),
        -rbox(p-vec2(.075,.075),vec2(.175,.05),bz.wwwz)
    );
    bt = max(bt,-box(p-vec2(.085,.045),vec2(.08,.08)));
    float bb = max(
        rbox(p+vec2(.0,.075),oa,bz.xxww),
        -rbox(p+vec2(.075,.075),vec2(.175,.05),bz.zzww)
    );
    bb = max(bb,-box(p+vec2(.085,.045),vec2(.08,.08)));
    return min(bt,bb);
}
float get6(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.wwxw),
        -rbox(p-vec2(.075,.075),vec2(.175,.05),bz.wwzw)
    );
    bt = max(bt,-box(p-vec2(.085,.045),vec2(.08,.08)));
    float bb = max(
        rbox(p+vec2(.0,.075),oa,bz.xxwx),
        -rbox(p+vec2(.0,.075),vec2(.1,.05),bz.zzwz)
    );
    return min(bt,bb);
}
float get7(vec2 p) {
    float bt = max(
        rbox(p+vec2(0,.0),vec2(.15,.175),bz.xwww),
        -rbox(p+vec2(.05,.045),vec2(.15,.175),bz.zwww)
    );

    return bt;
}
float get8(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.xxxx),
        -rbox(p-vec2(.0,.075),vec2(.1,.05),bz.zzzz)
    );
    float bb = max(
        rbox(p+vec2(.0,.075),oa,bz.xxxx),
        -rbox(p+vec2(.0,.075),vec2(.1,.05),bz.zzzz)
    );
    return min(bt,bb);
}
float get9(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.xwxx),
        -rbox(p-vec2(.0,.075),vec2(.1,.05),bz.zwzz)
    );
    float bb = max(
        rbox(p+vec2(.0,.075),oa,bz.wxxw),
        -rbox(p+vec2(.075,-.02),vec2(.175,.15),bz.zzwz)
    );
    return min(bt,bb);
}

//letters
float getA(vec2 p) {
    vec2 of = vec2(0,.075);
    float bt = max(
    rbox(p,vec2(.15,.175),bz.xwxw),
    -rbox(p+vec2(.0,.05),vec2(.1,.175),bz.zwzw)
    );
    float bb = box(p,vec2(.15,.025));
    return min(bt,bb);
}
/**
float getB(vec2 p) {
    vec2 of = vec2(0,.075);
    float bt = max(
        rbox(p-of,oa,bz.xxww),
        -rbox(p-of,vec2(.1,.05),bz.zzww)
    );
    float bb = max(
        rbox(p+of,oa,bz.xxww),
        -rbox(p+of,vec2(.1,.05),bz.zzww)
    );
    return min(bt,bb);
}
float getC(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),bz.wwxx),
        -rbox(p-vec2(.05,0),vec2(.15,.125),bz.wwzz)
    );
    return bt;
}
float getD(vec2 p) {
    float bt = max(rbox(p,vec2(.15,.175),bz.xxww),
    -rbox(p,vec2(.1,.125),vec4(.035,.035,0,0)));
    return bt;
}
float getE(vec2 p) {
    vec2 of = vec2(0,.075);
    float bt = max(
        rbox(p-of,oa,bz.wwxx),
        -rbox(p-of-vec2(.05,0),vec2(.15,.05),bz.wwzz)
    );
    float bb = max(
        rbox(p+of,oa,bz.wwxx),
        -rbox(p+of-vec2(.05,0),vec2(.15,.05),bz.wwzz)
    );
    return min(bt,bb);
}
float getF(vec2 p) {
    float bt = min(
        box(vec2(p.x+.125,p.y),vec2(.025,.175)),
        box(vec2(p.x,abs(p.y-.075)-.075),vec2(.15,.025))
    );
    return bt;
}
float getG(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),bz.wwxx),
        -rbox(p-vec2(.05,0),vec2(.15,.125),bz.wwzz)
    );
    float bf = min(
        box(vec2(p.x-.125,p.y+.075),vec2(.025,.1)),
        box(vec2(p.x-.05,p.y),vec2(.1,.025))
    );
    return min(bt,bf);
}
float getH(vec2 p) {
    float bt = min(
        box(vec2(abs(p.x)-.125,p.y),vec2(.025,.175)),
        box(p,vec2(.15,.025))
    );
    return bt;
}
float getI(vec2 p) {
    float bt = min(
        box(p,vec2(.025,.175)),
        box(vec2(p.x,abs(p.y)-.15),vec2(.15,.025))
    );
    return bt;
}
float getJ(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),vec4(.0,.1,0,0)),
        -rbox(p+vec2(.05,-.05),vec2(.15,.175),vec4(.0,.055,0,0))
    );
    return bt;
}
float getK(vec2 p) {
    float bt = max(
        rbox(p-vec2(0,.075),oa,vec4(.0,.1,0,0)),
        -rbox(p-vec2(0,.12),vec2(.1,.095),vec4(.0,.055,0,0))
    );
    float bb = max(
        rbox(p+vec2(0,.075),oa,vec4(.1,.0,0,0)),
        -rbox(p+vec2(0,.12),vec2(.1,.095),vec4(.055,.0,0,0))
    );
    return min(bt,bb);
}
float getL(vec2 p) {
    float bt = min(
        box(p+vec2(.125,0),vec2(.025,.175)),
        box(p+vec2(0,.15),vec2(.15,.025))
    );
    return bt;
}
*/
float getM(vec2 p) {
    vec2 of = vec2(.065,0);
    float bt = max(
        rbox(p+of,vec2(.085,.175),bz.xwxw),
        -rbox(p+of+vec2(.0,.05),vec2(.0375,.175),bz.zwzw)
    );
    float bb = max(
        rbox(p-of,vec2(.085,.175),bz.xwxw),
        -rbox(p-of+vec2(.0,.05),vec2(.0375,.175),bz.zwzw)
    );
    return min(bt,bb);
}
/**
float getN(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),bz.xwxw),
        -rbox(p+vec2(.0,.045),vec2(.1,.175),bz.zwzw)
    );
    return bt;
}
float getO(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),bz.xxxx),
        -rbox(p,vec2(.1,.125),bz.zzzz)
    );
    return bt;
}
*/
float getP(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.xxww),
        -rbox(p-vec2(.0,.075),vec2(.1,.05),bz.zzww)
    );
    float bb = box(p+vec2(.125,0),vec2(.025,.175));
    return min(bt,bb);
}
/**
float getQ(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),vec4(.075,.11,.075,.075)),
        -rbox(p,vec2(.1,.125),vec4(.035,.065,.035,.035))
    );
    vec2 q = (p+vec2(-.0925,.115))*rot(.8);
    float bb = box(q,vec2(.025,.065));
    return min(bt,bb);
}
float getR(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.xxww),
        -rbox(p-vec2(.0,.075),vec2(.1,.05),bz.zzww)
    );
    float bb = max(
        rbox(p+vec2(.0,.075),oa,bz.xwww),
        -rbox(p+vec2(.0,.12),vec2(.1,.095),bz.zwww)
    );
    return min(bt,bb);
}
float getS(vec2 p) {
    float bt = max(
        rbox(p-vec2(.0,.075),oa,bz.wwxx),
        -rbox(p-vec2(.075,.075),vec2(.175,.05),bz.wwzz)
    );
    bt = max(bt,-box(p-vec2(.085,.045),vec2(.08,.08)));
    float bb = max(
        rbox(p+vec2(.0,.075),oa,bz.xxww),
        -rbox(p+vec2(.075,.075),vec2(.175,.05),bz.zzww)
    );
    bb = max(bb,-box(p+vec2(.085,.045),vec2(.08,.08)));
    return min(bt,bb);
}
float getT(vec2 p) {
    vec2 of = vec2(0,.075);
    return min(box(p-of-vec2(0,.075),vec2(.15,.025)),box(p,vec2(.025,.175)));
}
float getU(vec2 p) {
    float bt = max(
        rbox(p,vec2(.15,.175),bz.wxwx),
        -rbox(p-vec2(0,.045),vec2(.1,.175),bz.wzwz)
    );
    return bt;
}
float getV(vec2 p) {
    vec2 q = p;
    p.x=abs(p.x);
    p*=rot(-.24);
    float bt = max(
        rbox(p-vec2(0,.03),vec2(.1,.2),bz.wxwx),
        -rbox(p-vec2(-.0475,.05),vec2(.1,.175),bz.wzwz)
    );
    return max(box(q,vec2(.2,.175)),bt);
}
float getW(vec2 p) {
    vec2 of = vec2(.065,0);
    float bt = max(
        rbox(p+of,vec2(.085,.175),bz.wxwx),
        -rbox(p+of-vec2(.0,.05),vec2(.0375,.175),bz.wzwz)
    );
    float bb = max(
    rbox(p-of,vec2(.085,.175),vec4(0,.075,0,.075)),
    -rbox(p-of-vec2(.0,.05),vec2(.0375,.175),bz.wzwz)
    );
    return min(bt,bb);
}
float getX(vec2 p) {
    vec2 r = p;
    vec2 q = p*rot(.5707);
    p*=rot(-.5707);
    float bt = min(box(p,vec2(.025,.225)),box(q,vec2(.025,.225)));
    return max(box(r,vec2(.2,.175)),bt);
}
float getY(vec2 p) {
    vec2 of = vec2(0,.075);
    float bt = max(
        rbox(p-of,oa,bz.wxwx),
        -rbox(p-of-vec2(0,.05),vec2(.1),bz.wzwz)
    );
    return min(bt,box(p+of,vec2(.025,.1)));
}

float getZ(vec2 p) {
    vec2 q = p*rot(.86);
    float bt = min(
        box(p-vec2(-.0125,.15),vec2(.1375,.025)),
        box(p+vec2(-.0125,.15),vec2(.1375,.025))
    );
    bt = min(
        min(length(vec2(abs(q.x)-.198,q.y))-.025,bt),
        box(q,vec2(.195,.025))
    );
    return bt;
}
*/