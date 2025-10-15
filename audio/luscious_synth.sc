// shader: oscilloscope
(
    var defName = \luscious_synth;
    var numVoices = 8;
    var specs = (
        amp: ControlSpec(0, 1, 'lin', 0, 0.7, ""),
        filter_freq: ControlSpec(200, 8000, 'exp', 0, 2500, "Hz"),
        resonance: ControlSpec(0.1, 1.0, 'lin', 0, 0.4, "Q"),
        detuning: ControlSpec(0.0, 0.2, 'lin', 0, 0.05, ""),
        chorus_depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.3, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        release: ControlSpec(0.001, 4.0, 'exp', 0, 0.8, "s")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var detuning = \detuning.kr(specs[\detuning].default);
        var chorus_depth = \chorus_depth.kr(specs[\chorus_depth].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        var voice_signals, mixed_voices, filtered_sig, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, osc1, osc2, osc3, mixed_oscs, voice_out;
            var chorus_lfo1, chorus_lfo2;

            if(numVoices > 1, {
                freq = voice_freqs[i];
                gate = voice_gates[i];
                vel_amp = voice_amps[i];
            }, {
                freq = voice_freqs;
                gate = voice_gates;
                vel_amp = voice_amps;
            });
            
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Three detuned oscillators for richness
            osc1 = Saw.ar(freq);
            osc2 = Saw.ar(freq * (1 + detuning));
            osc3 = Saw.ar(freq * (1 - detuning));
            
            // Mix oscillators
            mixed_oscs = (osc1 + osc2 + osc3) * 0.33;
            
            // Add subtle chorus modulation for lushness
            chorus_lfo1 = SinOsc.kr(0.3 + (i * 0.1), 0, chorus_depth * 0.01, 1);
            chorus_lfo2 = SinOsc.kr(0.4 + (i * 0.07), 0, chorus_depth * 0.008, 1);
            
            voice_out = mixed_oscs * chorus_lfo1 * chorus_lfo2 * env * vel_amp;
            voice_out;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        // Rich low-pass filtering with resonance
        filtered_sig = RLPF.ar(mixed_voices, filter_freq, 1 - resonance);
        
        // Add some harmonic warmth
        final_sig = (filtered_sig + (filtered_sig.distort * 0.1)) * amp;
        
        mono_for_analysis = final_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_sig, final_sig]);
    });
    def.add;
    "Effect SynthDef 'luscious_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)