// https://www.shadertoy.com/view/sdl3RM

float Rectangle(vec2 uv,vec2 p,float width,float height,float blur){
   vec2 W = vec2(width,height);
   vec2 s = smoothstep(W+blur,W-blur,abs(uv-p));
   return s.x*s.y;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
    
    // Normalized pixel coordinates (from 0 to 1)
    vec2 uv = 5.* ( fragCoord - .5*iResolution.xy ) /iResolution.y;

    uv.y *= 1.0 - iRMSOutput;
    uv.x = abs(uv.x);
    vec3 col  = vec3(Rectangle(uv,vec2( 0     ,0.075 ),0.27   ,0.22 ,0.01));
         col -= vec3(Rectangle(uv,vec2( 0     ,-0.2 ),0.13   ,0.13 ,0.01));
         col -= vec3(Rectangle(uv,vec2( 0.135   ,0.06 ),0.07   ,0.11 ,0.01));
         col += vec3(Rectangle(uv,vec2( 0.13   ,-0.1 ),0.07   ,0.04,0.01));
    vec3 col2 = vec3(Rectangle(uv,vec2( 0     ,-0.2 ),0.15   ,0.15 ,0.01));
         col2-= vec3(Rectangle(uv,vec2( 0.0325,-0.2 ),0.015  ,0.15 ,0.01));
         col2-= vec3(Rectangle(uv,vec2( 0.1   ,-0.2 ),0.015  ,0.15 ,0.01));
         col2+= vec3(Rectangle(uv,vec2( 0     ,-0.2 ),0.07  ,0.015 ,0.01));
         //col2+= vec3(Rectangle(uv,vec2(-0.04  ,-0.32),0.0075 ,0.03 ,0.01));
    col = max(col,col2);
    // Output to screen
    fragColor = vec4(col ,1.0);
}