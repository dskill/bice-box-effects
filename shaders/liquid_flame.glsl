// resolution: 0.25
// Flame effect inspired by Shadertoy
// Original shader: https://www.shadertoy.com/view/wX33zX
// CC0: Another flaming experiment by original author
// Adapted for audio-reactive visuals in BICE Box

void mainImage(out vec4 O, vec2 C) {
  vec3 
      r=iResolution    // Screen resolution
    , o                // Output color accumulator
    , p                // Current ray position in 3D space
    , P                // Stored/reference position for calculations
    ;
  
  // Audio reactivity variables
  float bassFreq = texture(iAudioTexture, vec2(0.1, 0.25)).x;
  float midFreq = texture(iAudioTexture, vec2(0.5, 0.25)).x;
  float highFreq = texture(iAudioTexture, vec2(0.8, 0.25)).x;
  float waveform = texture(iAudioTexture, vec2(C.x / r.x, 0.75)).x * 2.0 - 1.0;
  
  // Audio-reactive scaling and timing
  float audioScale = 1.0 + iRMSOutput * 1.5;
  float audioTime = iTime + iRMSTime * 0.1;
  
  // RAYMARCHING LOOP: Cast a ray from camera through each pixel
  for (
  float 
      i                // Ray step counter
    , j                // Temporary variable for turbulence and distance calculations
    , d                // Distance to nearest surface (SDF result)
    , z                // Current depth along the ray
    , t=audioTime      // Audio-reactive time
    ; ++i<48.          // Reduced from 88 to 48 steps
    ; z+=d/6.          // Step forward by distance/6 (slightly more aggressive stepping)
    ) {
    
    // RAY SETUP: Calculate ray direction from camera through current pixel
    p=z*normalize(vec3(C-.5*r.xy, r.y));  // Convert screen coords to 3D ray direction
    p.z-=4.;         // Move camera back 4 units from origin
    P=p;             // Store original ray position for later use
    
    // SPACE TRANSFORMATION: Bend/twist the space for visual effect with audio reactivity
    float rotationAmount = P.y*P.y/4.+2.*P.y-t + bassFreq * 3.0;
    p.xz*=mat2(cos(rotationAmount+vec4(0,11,33,0)));  // Rotate XZ plane based on Y position, time, and bass
    p.x+=sin(.2*t-P.x) + waveform * 0.3;  // Add sinusoidal wave distortion with waveform influence
    
    // TURBULENCE: Add fractal noise to create flame-like distortion with audio reactivity
    for(
        d=j=9.       
      ; --j>6.       // Reduced from 5 to 6 (fewer iterations)
      ; d/=.8        // Increase frequency (more detail)
      )
      p += .4*(p.y+2.)*cos(p.zxy*d-3.*t + midFreq * 2.0)/d * audioScale;  // Add scaled noise with mid-frequency influence
    
    // DISTANCE FIELD: Calculate distance to the flame surface
    j=length(p-P);   // Distance from current to original position (used for coloring)
    p=abs(p);        // Mirror space (creates symmetry)
    
    // Audio-reactive flame size
    float flameSize = 1.0 + bassFreq * 0.5;
    
    // Intersection of boxes with audio reactivity
    d=abs(
      min(
          // Box aligned with z
          max(
              p.z-.1           // Plane in Z
            , p.x-flameSize-.3*P.y    // Plane in X, tapered by Y position, scaled by audio
            )
          // Box aligned with x
        , max(
              p.x-.2           // Plane constraint in X  
            , p.z-flameSize-.3*P.y    // Plane constraint in Z, tapered by Y position, scaled by audio
            )
        ))+9e-3;  // Add small epsilon to make boxes translucent
    
    // COLOR CALCULATION: Generate flame colors based on position and movement with audio reactivity
    P = 1.+sin(.5+j-P.y+P.z+vec3(2,3,4) + vec3(bassFreq, midFreq, highFreq) * 2.0);  // RGB color variations with frequency influence
    
    // VOLUMETRIC RENDERING: Accumulate color based on density (1/distance) with audio intensity
    float intensity = 1.0 + iRMSOutput * 2.0;
    o += P.x/d*P * intensity;    // Add color contribution: intensity/distance * color * audio intensity
  }
  
  // TONE MAPPING: Convert accumulated light to final pixel color with audio-reactive brightness
  float brightness = 1.0 + iRMSOutput * 0.5;
  O = tanh(o.xyzx/(2E3/brightness));  // Compress bright values, output RGBA (xyzx creates alpha=x)
}