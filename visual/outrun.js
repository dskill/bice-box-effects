/*
  This version of outrun.js replaces the classic p5.js-based 3D grid drawing with
  a custom shader that mimics a retro outrun aesthetic. It uses the same parameter
  names from outrun.json (drive, gridSpeed, sunSize, glow, synthDepth, mix) and 
  also incorporates waveform data (via p.waveform1) to distort the scene or otherwise
  modulate the visuals.

  Note: We’re ignoring FFT data in this version, but waveform data is still read and 
  mapped into the uniform "u_waveform". The "mix" parameter is optionally used at the 
  final color output stage, if desired.
*/

function outrunShaderSketch(p) {
    let passShader;
    let waveformTex;
  
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
  
  uniform float u_time;
  uniform vec2  u_resolution;
  
  // Audio/visual effect params
  uniform float u_drive;
  uniform float u_gridSpeed;
  uniform float u_sunSize;
  uniform float u_glow;
  uniform float u_synthDepth;
  uniform float u_mix;
  uniform float u_rms;
  
  // Waveform texture (1D in X dimension)
  uniform sampler2D u_waveform;
  
  // Utility: Shadertoy’s fract-based noise example:
  float noise(vec2 st) {
    return fract(sin(dot(st.xy, vec2(12.9898, 78.233))) * 43758.5453123);
  }
  
  // Retro neon grid, adapted from the user snippet
  float grid(vec2 uv, float intensity, float speed, float glow, float gridRoam) {
    uv = uv * .25 + uv * glow * 2.0;
    vec2 size = .01 * vec2(uv.y, uv.y * uv.y * 0.3);
    // Roaming effect
    uv += vec2(gridRoam, u_time * speed);
  
    // Absolute repetition
    uv = abs(fract(uv) - 0.75);
  
    // Smooth lines
    vec2 lines = smoothstep(size, vec2(0.0), uv);
    // Additional highlight lines
    lines += smoothstep(size * 5.0, vec2(0.0), uv) * intensity;
  
    return clamp(lines.x + lines.y, 0.0, 3.0);
  }
  
  // A stylized sun with some wavy distortions
  float sun(vec2 uv, float battery) {
    float val   = smoothstep(0.35, 0.33, length(uv));
    float bloom = smoothstep(0.8, 0.0, length(uv));
    float cut   = 3.0 * sin((uv.y + u_time * 0.02 * (battery + 0.02)) * 100.0)
                + clamp(uv.y * 14.0 + 3.0, -6.0, 6.0);
    cut = clamp(cut, 0.0, 1.0);
    return clamp(val * cut, 0.0, 1.0) + bloom * 0.9;
  }
  
  void main() {
    // get fragCoord in [-1..1] range
    vec2 fragCoord = gl_FragCoord.xy;
    vec2 uv = (2.0 * fragCoord - u_resolution.xy) / u_resolution.y;
  
    // We'll sample waveform at uv.x, turning it into [0..1] range
    // we want to read within the x dimension of the waveform texture
    // uv.x is in [-someValue..someValue], so let's clamp
    float xCoord   = clamp((uv.x * 0.5 + 0.5), 0.0, 1.0);
    // sample channel from waveform
    float waveVal  = texture2D(u_waveform, vec2((uv.x + 2.0)*0.25, 0.0)).r;  
    // waveVal is in [0..1], shift to [-1..1]
    float waveNorm = (waveVal - 0.5) * 2.0;
  
    // Use waveNorm to distort Y, combined with the user param (drive, synthDepth, etc)
    //uv.y += waveNorm * 0.1 * u_synthDepth * u_drive;
  
    // A basic "fog" effect
    float pointOfInflection = 0.0;
    float fogSize = 0.15;
    float fogIntensity = -0.025;
    float fog = smoothstep(fogSize, fogIntensity, abs(uv.y + pointOfInflection));
  
    // We define two base colors for a gradient
    vec3 startColor = vec3(0.6, 0.0, 1.0); // Neon purple
    vec3 endColor   = vec3(0.0, 1.0, 1.0); // Cyan
  
    // We'll do a waveFactor to modulate the interpolation
    float waveFactor  = sin(uv.x * 5.0 + u_time * 2.0) * 0.5 + 0.5;
    float waveY       = uv.y + waveFactor * 0.3;
  
    // Interpolate background coloring
    vec3 gradient = mix(startColor, endColor, waveY);
  
    // Basic background
    vec3 backgroundColor = vec3(0.1, 0.0, 0.1);
    vec3 lineColor = gradient;
    // Let's slightly modulate line intensity with "drive"
    float lineIntensity = -uv.y * u_drive * 0.5;
    float lineGlow      = 0.01 + (u_glow * 0.4); // smaller base + param
  
    // We'll feed in "gridSpeed" for speed, "u_glow" for glow, etc
    float gridRoaming = 0.25; // fixed
    if (uv.y < pointOfInflection) {
      float distance = length(uv);
      // transform Y for "3D" segmenting
      float spaceBetweenGridSegments = sin(uv.y + u_time) + 2.0 / (abs(uv.y - pointOfInflection) + 0.05);
      uv.y = spaceBetweenGridSegments;
  
      // transform X
      float gridSegmentWidthMultiplier = abs(uv.y);
      uv.x *= -1.0 * gridSegmentWidthMultiplier - sin(distance * 0.5);
  
      float gridVal = grid(uv, lineIntensity, u_gridSpeed*2.0, lineGlow, gridRoaming);
      backgroundColor = mix(backgroundColor, lineColor, gridVal);
    }
    else {
      // Sun portion
      // We'll let "u_sunSize" shift the coordinates for the sun:
      vec2 sunUV = uv - uv * u_sunSize * .2;
      //sunUV.y += waveNorm * 0.02;
      // shift the sun's vertical position by sunSize
      sunUV += vec2(0.0, -0.25 * u_sunSize - u_rms * .1);
  
      // We'll combine "u_synthDepth" with "u_rms" to modulate the sun's waves
      float battery = (u_synthDepth + 0.15) + (u_rms * 2.0);
  
      float sunVal = sun(sunUV, battery);
      // color ramp
      vec3 sunBg = mix(vec3(0.125,0.0,0.2), vec3(0.125,0.439,0.408), sunUV.y * 1.0 + 0.4);
  
      // fade in the sun
      backgroundColor = mix(vec3(0.0), sunBg, sunVal);
    }
  
    // Add in "fog"
    backgroundColor += fog * fog * fog;
  
    // optional final dryness/wetness from "u_mix"
    // todo: implement this

    gl_FragColor = vec4(backgroundColor, 1.0);
  }
  `;
  
    p.preload = () => {
      passShader = p.createShader(vertexShader, fragmentShader);
    };
  
    p.setup = () => {
      p.createCanvas(p.windowWidth, p.windowHeight, p.WEBGL);
      p.noStroke();
  
      // Create a texture the same way as in oscilloscope.js
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
  
      // Pass uniforms
      passShader.setUniform("u_time", p.millis() * 0.001);
      passShader.setUniform("u_resolution", [p.width, p.height]);
  
      // Hook up outrun.json parameters (with default fallbacks):
      const drive       = p.params.drive;
      const gridSpeed   = p.params.gridSpeed;
      const sunSize     = p.params.sunSize;
      const glow        = p.params.glow;
      const synthDepth  = p.params.synthDepth;
      const mixVal      = p.params.mix;
      // We'll read RMS from p.rmsOutput (sent from audio analysis)
      const rmsOutput   = p.rmsOutput;
  
      passShader.setUniform("u_drive", drive);
      passShader.setUniform("u_gridSpeed", gridSpeed);
      passShader.setUniform("u_sunSize", sunSize);
      passShader.setUniform("u_glow", glow);
      passShader.setUniform("u_synthDepth", synthDepth);
      passShader.setUniform("u_mix", mixVal);
      passShader.setUniform("u_rms", rmsOutput);
  
      // Waveform texture
      passShader.setUniform("u_waveform", waveformTex);
  
      p.shader(passShader);
      p.quad(-1, 1, 1, 1, 1, -1, -1, -1);
    };
  
    p.windowResized = () => {
      p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
  }
  
  module.exports = outrunShaderSketch;