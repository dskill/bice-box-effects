/*
  This version of outrun.js replaces the classic p5.js-based 3D grid drawing with
  a custom shader that mimics a retro outrun aesthetic. It uses the same parameter
  names from outrun.json (drive, gridSpeed, sunSize, glow, synthDepth, mix) and 
  also incorporates waveform data (via p.waveform1) to distort the scene or otherwise
  modulate the visuals.

  Note: We're ignoring FFT data in this version, but waveform data is still read and 
  mapped into the uniform "u_waveform". The "mix" parameter is optionally used at the 
  final color output stage, if desired.
*/

function outrunShaderSketch(p) {
    let passShader;
    let waveformTex;
    let waveformShiftShader;  // New shader for shifting pixels
    let waveformFBO1, waveformFBO2;  // Two FBOs for ping-pong buffering
    let currentFBO = 0;  // Track which FBO is current
    
    const vertexShader = `
      attribute vec3 aPosition;
      attribute vec2 aTexCoord;
      varying vec2 vTexCoord;
      
      void main() {
        vTexCoord = aTexCoord;
        gl_Position = vec4(aPosition, 1.0);
      }
    `;
  
    //---------------------------------------------------------------------------------
    // Adapted Shadertoy-inspired shader.
    // iTime -> u_time
    // iResolution -> u_resolution
    // wave data from u_waveform, read as texture2D(u_waveform, vec2(...))
    // outrun params from the JSON => drive, gridSpeed, sunSize, glow, synthDepth, mix
    // We'll call them uniform float u_drive, u_gridSpeed, u_sunSize, u_glow, u_synthDepth, u_mix
    // Additionally, we read RMS to modulate something. We'll call it uniform float u_rms.
    //---------------------------------------------------------------------------------
    const fragmentShader = `
#ifdef GL_ES
precision highp float;
#endif

uniform sampler2D u_waveform;
uniform vec2 u_resolution;
uniform float u_time;
uniform float u_gridSpeed;
uniform float u_sunSize;
uniform float u_glow;
uniform float u_synthDepth;
uniform float u_mix;
uniform float u_rms;

varying vec2 vTexCoord;

// Retro neon grid
float grid(vec2 uv, float intensity, float speed, float glow, float gridRoam) {
    uv = uv * (.25 + glow);
    vec2 size = .01 * vec2(uv.y, uv.y * uv.y * 0.3);
    // Roaming effect
    uv += vec2(gridRoam, u_time * speed);
    
    // Absolute repetition
    uv = abs(fract(uv) - 0.75);
    
    // Smooth lines
    vec2 lines = smoothstep(size, vec2(0.0), uv);
    // Additional highlight lines
    lines += smoothstep(size * 1.0, vec2(0.0), uv) * intensity;
    
    return clamp(lines.x + lines.y, 0.0, 3.0);
}

// A stylized sun with wavy distortions
float sun(vec2 uv, float battery) {
    float val   = smoothstep(0.35, 0.33, length(uv));
    float bloom = smoothstep(0.8, 0.0, length(uv));
    float cut   = 3.0 * sin((uv.y + u_time * 0.02 * (battery + 0.02)) * 100.0)
                + clamp(uv.y * 14.0 + 3.0, -6.0, 6.0);
    cut = clamp(cut, 0.0, 1.0);
    return clamp(val * cut, 0.0, 1.0) + bloom * 0.9;
}

// Sample waveform with history based on perceived distance
float sampleWaveform(vec2 uv, float distanceFromCamera) {
    // Sample current waveform at x position
    // why * 0.25?  seems wrong
    float xCoord = clamp((uv.x * 0.25 + 0.5), 0.0, 1.0);
    
    // Map distance to history sample - closer to camera (lower y) = older samples
    // Adjust the 0.7 multiplier to control how much history is visible
    float yCoord = clamp(distanceFromCamera * 0.7, 0.0, 1.0);
    vec4 wave = texture2D(u_waveform, vec2(xCoord, yCoord));
    float waveVal = (wave.r - 0.5) * 2.0;  // Convert to -1 to 1 range
    
    // Fade out distant samples slightly
    float fadeWithDistance = 1.0 - (distanceFromCamera * 0.3);
    return waveVal * fadeWithDistance;
}

void main() {
    vec2 uv = vec2(1.0) - vTexCoord.xy;
    uv = uv * 2.0 - 1.0;  // Convert from [0,1] to [-1,1] range
    float aspect = u_resolution.x/u_resolution.y;
    uv.x *= aspect;

    // Calculate perceived distance from camera (0 = closest, 1 = horizon)
    float distanceFromCamera = clamp(1.0 - (uv.y + 1.0) * 0.5, 0.0, 1.0);
    
    // Sample waveform based on distance
    float waveNorm = sampleWaveform(uv, distanceFromCamera) * u_synthDepth;

    // Set horizon line with waveform influence
    float baseHorizon = 0.2;
    float waveInfluence = waveNorm * 3.3;
    float pointOfInflection = baseHorizon + waveInfluence;

    // Fog effect
    float fogSize = 0.25;
    float fogIntensity = -0.0;
    float fog = smoothstep(fogSize, fogIntensity, abs(uv.y - pointOfInflection));

    // Colors
    vec3 startColor = vec3(0.6, 0.0, 1.0); // Neon purple
    vec3 endColor   = vec3(0.0, 1.0, 1.0); // Cyan

    float waveFactor = sin(uv.x * 5.0 + u_time * 2.0) * 0.5 + 0.5;
    float waveY = uv.y + waveFactor * 0.3 + waveNorm * 0.1;

    vec3 gradient = mix(startColor, endColor, waveY);
    vec3 backgroundColor = vec3(0.1, 0.0, 0.1);
    vec3 lineColor = gradient;
    float lineGlow = 0.01 + (u_glow * 0.4);

    float gridRoaming = 0.25;
    if (uv.y < pointOfInflection) {
        // uv is original screen-space uv here.
        // waveNorm is already calculated: sampleWaveform(uv, distanceFromCamera) * u_synthDepth;
        // u_synthDepth controls the amplitude of waveNorm.

        // 1. Calculate base for grid's y-coordinate with perspective
        // This term makes lines appear closer together towards the horizon.
        // (abs(uv.y - pointOfInflection) + 0.05) goes from ~abs(-1 - baseHorizon) to 0.05
        // So perspectiveSpacing is larger at bottom, smaller near horizon.
        float perspectiveSpacing = 2.0 / (abs(uv.y - pointOfInflection) + 0.1); // Original was 0.05, 0.1 avoids extreme values

        // 2. Create the grid's uv coordinates, starting with screen uv.
        vec2 gridCoords = uv; 

        // 3. Define gridCoords.y: base perspective + waveform displacement.
        // waveNorm is already scaled by u_synthDepth.
        gridCoords.y = perspectiveSpacing + waveNorm; 
                                           
        // 4. Handle gridCoords.x: this part creates the converging lines effect.
        // It uses the magnitude of gridCoords.y and distance from screen center.
        float distanceToCenter = length(uv); // Based on original screen uv for consistent perspective distortion.
        float gridLineWidthFactor = abs(gridCoords.y); 
        // Modify gridCoords.x for perspective convergence, with a slight animation on the curve.
        // The original uv.x (from gridCoords.x) is scaled.
        gridCoords.x *= (-1.0 * gridLineWidthFactor - sin(distanceToCenter * 0.5 + u_time * 0.1)); 

        // 5. Calculate line intensity and draw grid
        // Intensity can be based on the new 'height' of the grid line.
        float lineIntensity = -gridCoords.y * 0.005; 
        float gridVal = grid(gridCoords, lineIntensity, u_gridSpeed*2.0, lineGlow, gridRoaming);
        backgroundColor = mix(backgroundColor, lineColor, gridVal);
    }
    else {
        vec2 sunUV = uv - uv * u_sunSize * .2 * (u_rms * 1.1 + 1.0);
        sunUV += vec2(0.0, -0.25 * u_sunSize - u_rms * .3);
        float battery = 15.0 * u_synthDepth;
        float sunVal = sun(sunUV, battery);
        vec3 sunBg = mix(vec3(0.625,0.4,0.2), vec3(1.125,0.439,0.208), sunUV.y * 1.0 + 0.4);
        backgroundColor = mix(vec3(0.0), sunBg, sunVal);
    }

    backgroundColor += .3 * fog * fog * fog;

    //backgroundColor = texture2D(u_waveform, vTexCoord.xy).xyz;

    gl_FragColor = vec4(backgroundColor, 1.0);
}
`;
  
    // Add new vertex shader for the waveform processing
    const waveformShiftVertexShader = `
      attribute vec3 aPosition;
      attribute vec2 aTexCoord;
      varying vec2 vTexCoord;
      
      void main() {
        vTexCoord = aPosition.xy * 0.5 + 0.5;
        gl_Position = vec4(aPosition, 1.0);
      }
    `;

    const waveformShiftFragmentShader = `
      #ifdef GL_ES
      precision highp float;
      #endif

      uniform sampler2D u_texture;        // Previous frame's texture
      uniform sampler2D u_newWaveform;    // New waveform as a texture
      uniform float u_texelSize;
      uniform float u_time;
      uniform vec2 u_resolution;

      varying vec2 vTexCoord;

      void main() {
        vec2 uv = vTexCoord;
        
        // Bottom 10% of the texture: write new waveform
        if (uv.y < 0.05) {
          float waveformValue = texture2D(u_newWaveform, vec2(uv.x, 0.0)).r;
          gl_FragColor = vec4(vec3(waveformValue), 1.0);
        } 
        // Rest of texture: shift previous content up
        else {
          // Map from 0-0.9 to 0-1.0 range for reading previous frame
          float sourceY = uv.y - u_texelSize * 10.0;// (uv.y / 0.9) * 1.0;
          gl_FragColor = texture2D(u_texture, vec2(uv.x, sourceY));
        }
      }
    `;

    p.preload = () => {
      passShader = p.createShader(vertexShader, fragmentShader);
      waveformShiftShader = p.createShader(waveformShiftVertexShader, waveformShiftFragmentShader);
    };
  
    p.setup = () => {
      // Force 1:1 pixel ratio, ignoring Retina scaling
      p.pixelDensity(1);

      p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
      p.noStroke();

      // Create two FBOs for ping-pong buffering
      waveformFBO1 = p.createFramebuffer({ width: 512, height: 512, colorFormat: p.RGB });
      waveformFBO2 = p.createFramebuffer({ width: 512, height: 512, colorFormat: p.RGB });
      
      // Initialize waveform texture properly
      waveformTex = p.createGraphics(512, 1, p.WEBGL);
      waveformTex.pixelDensity(1);
      waveformTex.noSmooth();
    };
  
    p.draw = () => {
      p.background(0);
  
      // If no waveform, fill with dummy data
      if (!p.waveform1 || p.waveform1.length === 0) {
        p.waveform1 = new Array(512).fill(0).map((_, i) => Math.sin(i * 0.1) * 0.5);
      }

      // Update waveform texture
      waveformTex.loadPixels();
      for (let i = 0; i < p.waveform1.length; i++) {
          let val = (p.waveform1[i]*.5 +.5) * 255.0;
          waveformTex.pixels[i * 4] = val;
          waveformTex.pixels[i * 4 + 1] = val;
          waveformTex.pixels[i * 4 + 2] = val;
          waveformTex.pixels[i * 4 + 3] = 255;
      }
      waveformTex.updatePixels();

      // Update waveform history texture using shader
      const currentTarget = currentFBO ? waveformFBO1 : waveformFBO2;
      const currentSource = currentFBO ? waveformFBO2 : waveformFBO1;
      
      currentTarget.begin();
      p.shader(waveformShiftShader);
      waveformShiftShader.setUniform('u_texture', currentSource);
      waveformShiftShader.setUniform('u_newWaveform', waveformTex);
      waveformShiftShader.setUniform('u_texelSize', 1.0/512.0);
      waveformShiftShader.setUniform('u_time', p.millis() * 0.01);
      waveformShiftShader.setUniform("u_resolution", [512, 512]);

      p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
      currentTarget.end();
      
      currentFBO = !currentFBO;  // Swap FBOs

      // Use the updated texture for the main shader
      passShader.setUniform("u_waveform", currentTarget);  // Switch back to using the history FBO
      //passShader.setUniform("u_waveform", waveformTex);  // Comment out raw waveform

      // Pass uniforms
      passShader.setUniform('u_time', p.millis() / 1000.0);
      passShader.setUniform('u_rms', p.rmsOutput);

      // Uniforms from p.params, with defaults
      passShader.setUniform('u_gridSpeed', p.params.gridSpeed != null ? p.params.gridSpeed : 0.2);
      passShader.setUniform('u_sunSize', p.params.sunSize != null ? p.params.sunSize : 0.5);
      passShader.setUniform('u_glow', p.params.glow != null ? p.params.glow : 0.3);
      passShader.setUniform('u_synthDepth', p.params.synthDepth != null ? p.params.synthDepth : 0.5);
      passShader.setUniform('u_mix', p.params.mix != null ? p.params.mix : 0.5);

      // Select correct FBO for reading (previous frame)
      passShader.setUniform("u_resolution", [p.width, p.height]);
  
      p.shader(passShader);
      p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
    };
  
    p.windowResized = () => {
      p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
  }
  
  module.exports = outrunShaderSketch;