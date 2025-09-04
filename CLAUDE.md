# Bice-Box Effects Development Guide

This guide consolidates all the essential information for creating audio effects and visual shaders for Bice-Box.

# SuperCollider Audio Effects Quick Reference

## Critical Rules

### ⚠️ FILENAME/DEFNAME MATCHING IS CRITICAL ⚠️
- **defName MUST EXACTLY match filename** (character for character!)
  - ✅ CORRECT: `reverb.sc` → `var defName = \reverb;`
  - ✅ CORRECT: `synthtoy.sc` → `var defName = \synthtoy;`
  - ✅ CORRECT: `ping_pong_delay.sc` → `var defName = \ping_pong_delay;`
  - ❌ WRONG: `happy-synth.sc` → `var defName = \happy_synth;` (hyphen vs underscore!)
  - ❌ WRONG: `my_effect.sc` → `var defName = \my-effect;` (underscore vs hyphen!)
- **If faders don't appear in UI, check filename vs defName first!**

### Other Critical Rules
- **All variables in ONE block** after parameters - NO `var` declarations anywhere else
- **Use specs defaults**: `\param.kr(specs[\param].default)`
- **Mono-first**: Process in mono, output `[processed, processed]`
- **Analysis out**: Always mono signal to `analysis_out_bus`
- **Maximum 12 faders** fit on screen - design parameters accordingly

## Template
```supercollider
// shader: oscilloscope
(
    var defName = \effect_name;  // ← MUST match filename exactly!
    var specs = (
        param1: ControlSpec(0.1, 10.0, 'exp', 0, 1.0, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var param1 = \param1.kr(specs[\param1].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here!
        var sig, dry, processed, mono_for_analysis;

        // Processing
        sig = In.ar(in_bus);  // Mono input
        dry = sig;
        processed = sig * param1;  // Your effect here
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        // Outputs
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'effect_name' added".postln;

    ~setupEffect.value(defName, specs);
)
```

## Polyphonic Synth Template
For MIDI-controllable polyphonic synthesizers:

```supercollider
// shader: oscilloscope  
(
    var defName = \synth_name;  // ← MUST match filename exactly!
    var numVoices = 8; // Maximum polyphony
    var specs = (
        // Your synth parameters (max 8 faders fit on screen)
        amp: ControlSpec(0, 1, 'lin', 0, 0.5, ""),
        filter_freq: ControlSpec(100, 8000, 'exp', 0, 2000, "Hz"),
        wave_type: ControlSpec(0, 2, 'lin', 1, 0, ""), // discrete values
        // ADSR envelope parameters
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.01, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, ""),
        release: ControlSpec(0.001, 4.0, 'exp', 0, 0.2, "s")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        
        // Your synth parameters
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var wave_type = \wave_type.kr(specs[\wave_type].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        
        // Voice arrays - REQUIRED for polyphonic synths
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        // ALL other variables declared here!
        var voice_signals, mixed_voices, final_sig, mono_for_analysis;
        
        // Generate all voices
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, wave, voice_out;

            // When numVoices > 1, controls are multi-channel and must be indexed.
            // When numVoices == 1, they are single-channel and cannot be indexed.
            if(numVoices > 1) {
                freq = voice_freqs[i];
                gate = voice_gates[i];
                vel_amp = voice_amps[i];
            } {
                freq = voice_freqs;
                gate = voice_gates;
                vel_amp = voice_amps;
            };
            
            // ADSR envelope
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Your oscillator/wave generation here
            wave = Select.ar(wave_type, [
                SinOsc.ar(freq),    // 0 = sine
                Saw.ar(freq),       // 1 = saw  
                Pulse.ar(freq, 0.5) // 2 = square
            ]);
            
            // Apply envelope and velocity
            voice_out = wave * env * vel_amp;
            voice_out;
        });
        
        // Mix all voices together
        mixed_voices = Mix.ar(voice_signals);
        
        // Apply your processing (filters, effects, etc.)
        final_sig = RLPF.ar(mixed_voices, filter_freq, 0.3);
        final_sig = final_sig * amp;
        
        // Outputs
        mono_for_analysis = final_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_sig, final_sig]);
    });
    def.add;
    "Effect SynthDef 'synth_name' (polyphonic) added".postln;

    // CRITICAL: Pass numVoices to ~setupEffect to enable MIDI control
    ~setupEffect.value(defName, specs, [], numVoices);
)
```

## Polyphonic Synth Key Points
- **numVoices**: Set for polyphony (e.g., 8 or more). This MUST be > 1.
- **MIDI Setup**: Pass `numVoices` to `~setupEffect.value(defName, specs, [], numVoices)` to enable MIDI control.
- **Voice arrays**: `voice_freqs`, `voice_gates`, `voice_amps` are automatically managed.
- **Voice generation**: Use `Array.fill(numVoices, { |i| ... })` pattern.
- **Robust Indexing**: Use `if(numVoices > 1)` when accessing voice parameters inside the `Array.fill` block to ensure compatibility.
- **Mix voices**: Use `Mix.ar(voice_signals)` to combine all voices
- **Envelopes**: Typically use `Env.adsr()` with `EnvGen.ar()`
- **Parameters**: Design for max 8 faders - prioritize most important controls

## Feedback Effects Pattern
For delays, reverbs, and other feedback-based effects:
```supercollider
// Get feedback from previous iteration
var fbNode = LocalIn.ar(1);
// Create delay with input + feedback
var delayed = DelayC.ar(sig + fbNode, maxDelayTime, delayTime);
// Send feedback back (with feedback amount control)
LocalOut.ar(delayed * feedback);
```

## Common ControlSpecs
- Linear 0-1: `ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")`
- Frequency: `ControlSpec(20, 2000, 'exp', 0, 440, "Hz")`
- Gain: `ControlSpec(0.1, 5.0, 'exp', 0, 1.0, "x")`
- Time: `ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s")`

## Shader Comments
Add `// shader: visualizer_name` as the first line in `.sc` files to auto-load a visualizer when the effect loads.

---

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

## Optional Resolution Scaling
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

---

# MCP Integration

Bice-Box exposes an MCP server which you should have access to.

## Available Tools
- **get_current_effect**: Returns active effect name and parameters
- **list_effects**: Lists all available audio effects  
- **list_visualizers**: Lists all p5.js sketches and GLSL shaders
- **set_current_effect**: Switch audio effect by name
- **set_effect_parameters**: Update live parameter values (session only)
- **set_visualizer**: Switch visualizer by name
- **create_or_update_audio_effect**: Safely create/update an audio effect with SuperCollider code (compiles before saving)
- **test_supercollider_code**: Test SuperCollider code for compilation errors without saving

## Integration Notes
- **Parameter updates**: `set_effect_parameters` only affects live values. To change defaults, edit the `.sc` file directly.
- **UI synchronization**: Effect/visualizer changes via MCP automatically update the UI faders and display.
- **Hot reloading**: File changes are detected and auto-reload the active effect/visualizer.
- **Audio effects**: Must exist in `audio/` directory with matching filename and defName.
- **Visualizers**: Located in `visual/` (p5.js) or `shaders/` (GLSL) directories.

## Creating/Updating Effects
- **Safe creation**: Use `create_or_update_audio_effect` to create or modify effects - it tests compilation before saving.
- **Testing code**: Use `test_supercollider_code` to validate SC syntax during development without creating files.
- **Error handling**: Both tools return detailed compilation errors if the SuperCollider code fails.
- **Atomic updates**: Effect files are updated atomically to prevent audio interruptions during active playback.

# Directory Structure
- `audio/` - SuperCollider audio effects (.sc files)
- `shaders/` - GLSL visual effects (.glsl files)
- `visual/` - p5.js visual effects (.js files)
- See individual directories for more detailed instructions