// Simple polyphonic synthesizer template
// This demonstrates how easy it is to create new polyphonic effects
// category: MIDI

(
    var defName = \simple_synth_template;
    var numVoices = 16; // Polyphonic synth
    var specs = (
        // Define your effect parameters here
        amp: ControlSpec(0, 1, 'lin', 0, 0.5, ""),
        filter_freq: ControlSpec(100, 8000, 'exp', 0, 2000, "Hz"),
        wave_type: ControlSpec(0, 2, 'lin', 1, 0, ""), // 0=sine, 1=saw, 2=square
        // ADSR envelope parameters
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.01, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, ""),
        release: ControlSpec(0.001, 4.0, 'exp', 0, 0.2, "s")
    );
 
    var def = SynthDef(defName, {
        // ALL VARIABLE DECLARATIONS MUST BE AT THE TOP
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var wave_type = \wave_type.kr(specs[\wave_type].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        var voice_signals, mixed_voices, final_sig, mono_for_analysis;
        
        // Generate all voices
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, wave, voice_out;

            // Access the parameters for the current voice
            freq = voice_freqs[i];
            gate = voice_gates[i];
            vel_amp = voice_amps[i];
            
            // ADSR envelope using the parameters
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Choose waveform based on wave_type parameter
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
        
        // Apply filter
        final_sig = RLPF.ar(mixed_voices, filter_freq, 0.3);
        
        // Apply global amplitude
        final_sig = final_sig * amp;
        
        // Prepare mono signal for analysis
        mono_for_analysis = final_sig;

        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'simple_synth_template' (polyphonic) added".postln;

    // Register specs and create the synth - NOTE THE \poly MIDI MODE!
    ~setupEffect.value(defName, specs, [], numVoices);
) 