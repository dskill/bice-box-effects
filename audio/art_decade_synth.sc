// shader: oscilloscope
// category: Synth
(
    var defName = \art_decade_synth;
    var numVoices = 8;
    var specs = (
        amp: ControlSpec(0, 1, 'lin', 0, 0.8, ""),
        filter_freq: ControlSpec(300, 3000, 'exp', 0, 1200, "Hz"),
        resonance: ControlSpec(0.1, 1.5, 'lin', 0, 0.6, "Q"),
        lfo_rate: ControlSpec(0.05, 1.0, 'exp', 0, 0.15, "Hz"),
        lfo_depth: ControlSpec(0.0, 0.8, 'lin', 0, 0.3, ""),
        vintage: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, ""),
        attack: ControlSpec(0.1, 4.0, 'exp', 0, 1.2, "s"),
        release: ControlSpec(0.5, 8.0, 'exp', 0, 4.0, "s")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var lfo_rate = \lfo_rate.kr(specs[\lfo_rate].default);
        var lfo_depth = \lfo_depth.kr(specs[\lfo_depth].default);
        var vintage = \vintage.kr(specs[\vintage].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        var voice_signals, mixed_voices, vintage_processed, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, lfo, osc, filtered, voice_out;
            
            freq = voice_freqs[i];
            gate = voice_gates[i];
            vel_amp = voice_amps[i];
            
            // Very slow attack/release for Art Decade's atmospheric quality
            env = EnvGen.ar(Env.asr(attack, 1.0, release), gate);
            
            // Slow, deep LFO like Brian Eno's ambient work
            lfo = SinOsc.kr(lfo_rate + (i * 0.003));
            
            // EMS Synthi triangle wave with subtle detuning
            osc = LFTri.ar(freq + (lfo * lfo_depth * 5));
            
            // Minimoog-style resonant filter
            filtered = RLPF.ar(osc, filter_freq + (lfo * lfo_depth * 300), resonance.reciprocal);
            
            voice_out = filtered * env * vel_amp;
            voice_out;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        // 1970s analog character - warm saturation and high-freq rolloff
        vintage_processed = (mixed_voices * (1 + vintage)).tanh / (1 + (vintage * 0.3));
        vintage_processed = LPF.ar(vintage_processed, 6000 - (vintage * 2000));
        
        final_sig = vintage_processed * amp;
        
        mono_for_analysis = final_sig;
        
        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'art_decade_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)