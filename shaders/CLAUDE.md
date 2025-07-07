# BICE Box Shader Quick Reference

## Essential Structure
- GLSL ES 3.00 only (`#version 300 es`) - **DO NOT DECLARE IN SHADER FILE** (system adds this)
- Function: `void mainImage(out vec4 fragColor, in vec2 fragCoord)`
- Files: `name.glsl` (single) or `name_bufferA.glsl`, `name_image.glsl` (multi-pass)
- **DO NOT DECLARE** `#version 300 es` or `precision` directives - system handles these

## Standard Uniforms (DO NOT redeclare)
```glsl
uniform vec3  iResolution;    // Screen resolution
uniform float iTime;          // Shader time (seconds)
uniform vec4  iMouse;         // Mouse coordinates
uniform sampler2D iChannel0;  // Buffer A output (in image pass)
uniform sampler2D iChannel1;  // Buffer B output (in image pass)
```

## Audio Uniforms
```glsl
uniform float iRMSInput;      // Real-time input audio (0.0-1.0+) - USE FOR REACTIVITY
uniform float iRMSOutput;     // Real-time output audio (0.0-1.0+) - USE FOR REACTIVITY
uniform float iRMSTime;       // Cumulative time - NOT for reactivity, grows with audio
uniform sampler2D iAudioTexture; // FFT/waveform data
```

## Audio Texture Sampling
```glsl
// FFT (frequency spectrum) - Row 0
float fftMag = texture(iAudioTexture, vec2(u_freq, 0.25)).x;

// Waveform (time domain) - Row 1
float waveVal = texture(iAudioTexture, vec2(u_time, 0.75)).x;
float waveValSigned = (waveVal * 2.0) - 1.0; // Convert 0-1 back to -1,1
```

## Common Patterns
```glsl
// Normalized coordinates
vec2 uv = fragCoord.xy / iResolution.xy;

// Aspect-corrected coordinates (for circles)
vec2 uv_centered = fragCoord.xy - 0.5 * iResolution.xy;
vec2 uv_aspect = uv_centered / iResolution.y;

// Audio reactivity
float intensity = 0.3 + 0.7 * iRMSOutput; // Pulse with audio
```

## Optioanl Resolution Scaling
```glsl
// resolution: 0.5  // Half resolution (add to top of file)
```

## Multi-Pass Setup
- Buffer A self-references via `iChannel0` (previous frame)
- Image pass reads buffers via `iChannel0` (A), `iChannel1` (B), etc.
- JSON effect uses base name: `"shader": "shaders/effect_name"`

## Quick Audio-Reactive Tips
- Use `iRMSOutput` or `iRMSInput` for real-time audio pulsing
- Use `iRMSTime` only for cumulative timing effects
- Sample FFT at `y=0.25`, waveform at `y=0.75`
