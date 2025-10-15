// shader: oscilloscope
(
    var defName = \donny_vintage_synth;
    var numVoices = 8;
    var specs = (
        amp: ControlSpec(0, 1, 'lin', 0, 0.7, ""),
        filter_freq: ControlSpec(200, 8000, 'exp', 0, 2000, "Hz"),
        resonance: ControlSpec(0.1, 2.0, 'lin', 0, 0.3, "Q"),
        wave_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        attack: ControlSpec(0.001, 1.0, 'exp', 0, 0.01, "s"),
        decay: ControlSpec(0.001, 1.0, 'exp', 0, 0.2, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        release: ControlSpec(0.001, 2.0, 'exp', 0, 0.4, "s")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var wave_mix = \wave_mix.kr(specs[\wave_mix].default);
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
            var env, saw_wave, pulse_wave, sub_osc, wave_blend, filtered;
            
            freq = voice_freqs[i];
            gate = voice_gates[i];
            vel_amp = voice_amps[i];
            
            // Classic ADSR envelope
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Vintage synth oscillators - OB8/Prophet 5 inspired
            saw_wave = Saw.ar(freq) * 0.7;
            pulse_wave = Pulse.ar(freq, 0.5) * 0.6;
            sub_osc = Pulse.ar(freq * 0.5, 0.5) * 0.4;
            
            // Mix between saw and pulse with subtle sub oscillator
            wave_blend = XFade2.ar(saw_wave, pulse_wave, wave_mix * 2 - 1);
            wave_blend = wave_blend + (sub_osc * 0.3);
            
            // Classic vintage filter - Oberheim/Prophet style
            filtered = RLPF.ar(wave_blend, filter_freq, resonance.reciprocal);
            
            // Apply envelope and velocity
            filtered * env * vel_amp;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        // Subtle vintage character - soft saturation
        final_sig = (mixed_voices * 1.2).tanh * 0.8;
        final_sig = final_sig * amp;
        
        mono_for_analysis = final_sig;
        
        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'donny_vintage_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)