// shader: oscilloscope
// category: Synth
(
    var defName = \black_atlass_synth;
    var numVoices = 8;
    var specs = (
        amp: ControlSpec(0, 1, 'lin', 0, 0.6, ""),
        filter_freq: ControlSpec(200, 4000, 'exp', 0, 1200, "Hz"),
        resonance: ControlSpec(0.1, 1.0, 'lin', 0, 0.4, "Q"),
        detuning: ControlSpec(0.0, 0.02, 'lin', 0, 0.005, ""),
        warmth: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.08, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.4, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        release: ControlSpec(0.01, 4.0, 'exp', 0, 1.5, "s")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var detuning = \detuning.kr(specs[\detuning].default);
        var warmth = \warmth.kr(specs[\warmth].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        var voice_signals, mixed_voices, filtered, warmed, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, wave, voice_out;

            freq = voice_freqs[i];
            gate = voice_gates[i];
            vel_amp = voice_amps[i];
            
            // Smooth, atmospheric envelope
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Detuned saw waves for that Korg Triton character
            wave = Saw.ar([freq, freq * (1 + detuning), freq * (1 - detuning)]) * 0.4;
            wave = Mix.ar(wave);
            
            voice_out = wave * env * vel_amp;
            voice_out;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        // Warm, resonant lowpass filter
        filtered = RLPF.ar(mixed_voices, filter_freq, resonance.reciprocal);
        
        // Analog warmth - soft saturation with subtle noise
        warmed = filtered + (PinkNoise.ar(warmth * 0.003));
        warmed = (warmed * (1 + warmth)).tanh * (1 / (1 + warmth));
        
        final_sig = warmed * amp;
        
        mono_for_analysis = final_sig;

        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'black_atlass_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)