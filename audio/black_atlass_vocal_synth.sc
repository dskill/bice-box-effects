// shader: oscilloscope
// category: Synth
(
    var defName = \black_atlass_vocal_synth;
    var numVoices = 8;
    var specs = (
        amp: ControlSpec(0, 1, 'lin', 0, 0.7, ""),
        vocal_filter: ControlSpec(400, 2000, 'exp', 0, 1000, "Hz"),
        vibrato_rate: ControlSpec(4.0, 7.0, 'lin', 0, 5.5, "Hz"),
        vibrato_depth: ControlSpec(0.0, 0.5, 'lin', 0, 0.25, ""),
        breathiness: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        attack: ControlSpec(0.001, 0.5, 'exp', 0, 0.02, "s"),
        decay: ControlSpec(0.001, 1.0, 'exp', 0, 0.1, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, ""),
        release: ControlSpec(0.01, 3.0, 'exp', 0, 0.8, "s")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var amp = \amp.kr(specs[\amp].default);
        var vocal_filter = \vocal_filter.kr(specs[\vocal_filter].default);
        var vibrato_rate = \vibrato_rate.kr(specs[\vibrato_rate].default);
        var vibrato_depth = \vibrato_depth.kr(specs[\vibrato_depth].default);
        var breathiness = \breathiness.kr(specs[\breathiness].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        var voice_signals, mixed_voices, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, vibrato_mod, modulated_freq, vocal_wave, filtered, breathy, voice_out;
            
            freq = voice_freqs[i];
            gate = voice_gates[i];
            vel_amp = voice_amps[i];
            
            // Vocal envelope
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Vibrato
            vibrato_mod = 1 + (SinOsc.kr(vibrato_rate + (i * 0.05)) * vibrato_depth * 0.01);
            modulated_freq = freq * vibrato_mod;
            
            // Vocal waveform - rich harmonics like human voice
            vocal_wave = Saw.ar(modulated_freq) + (Pulse.ar(modulated_freq, 0.2) * 0.5);
            
            // Vocal formant filtering
            filtered = RLPF.ar(vocal_wave, vocal_filter, 0.5);
            
            // Breathiness
            breathy = filtered + (LPF.ar(PinkNoise.ar(breathiness * 0.05), 3000) * env);
            
            voice_out = breathy * env * vel_amp * 0.3;
            voice_out;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        final_sig = mixed_voices * amp;
        
        mono_for_analysis = final_sig;
        
        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'black_atlass_vocal_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)