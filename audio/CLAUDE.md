# SuperCollider Audio Effects Quick Reference

## Critical Rules
- **defName MUST match filename** (e.g., `reverb.sc` → `\reverb`)
- **All variables in ONE block** after parameters - NO `var` declarations anywhere else
- **Use specs defaults**: `\param.kr(specs[\param].default)`
- **Mono-first**: Process in mono, output `[processed, processed]`
- **Analysis out**: Always mono signal to `analysis_out_bus`
- **Maximum 12 faders** fit on screen - design parameters accordingly

## Template
```supercollider
// shader: oscilloscope
(
    var defName = \effect_name;
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
    var defName = \synth_name;
    var numVoices = 8; // Maximum polyphony
    var specs = (
        // Your synth parameters (max 12 faders fit on screen)
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
- **Parameters**: Design for max 12 faders - prioritize most important controls

## Common ControlSpecs
- Linear 0-1: `ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")`
- Frequency: `ControlSpec(20, 2000, 'exp', 0, 440, "Hz")`
- Gain: `ControlSpec(0.1, 5.0, 'exp', 0, 1.0, "x")`
- Time: `ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s")`