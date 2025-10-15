// shader: oscilloscope
(
    var defName = \the_ascent_sound;
    var numVoices = 12;
    var specs = (
        amp: ControlSpec(0, 1, 'lin', 0, 0.8, ""),
        ascent_rate: ControlSpec(0.1, 10.0, 'exp', 0, 2.0, "Hz"),
        ascent_range: ControlSpec(0.1, 4.0, 'exp', 0, 2.0, "oct"),
        filter_freq: ControlSpec(200, 8000, 'exp', 0, 3000, "Hz"),
        filter_sweep: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        shimmer: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, ""),
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        release: ControlSpec(0.1, 8.0, 'exp', 0, 2.0, "s")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var amp = \amp.kr(specs[\amp].default);
        var ascent_rate = \ascent_rate.kr(specs[\ascent_rate].default);
        var ascent_range = \ascent_range.kr(specs[\ascent_range].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var filter_sweep = \filter_sweep.kr(specs[\filter_sweep].default);
        var shimmer = \shimmer.kr(specs[\shimmer].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        var voice_signals, mixed_voices, ascending_pitch, filtered, shimmered, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, base_wave, octave_wave, ascending_mod, voice_out;
            
            freq = voice_freqs[i];
            gate = voice_gates[i];
            vel_amp = voice_amps[i];
            
            // Smooth envelope for ethereal quality
            env = EnvGen.ar(Env.asr(attack, 1.0, release), gate);
            
            // Ascending pitch modulation
            ascending_mod = 1 + (LFSaw.kr(ascent_rate, 1).range(0, 1) * ascent_range);
            
            // Base oscillator with ascending pitch
            base_wave = Saw.ar(freq * ascending_mod) * 0.6;
            
            // Octave doubling for richness
            octave_wave = Saw.ar(freq * ascending_mod * 2) * 0.3;
            
            voice_out = (base_wave + octave_wave) * env * vel_amp;
            voice_out;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        // Dynamic filter that sweeps up with the ascent
        ascending_pitch = LFSaw.kr(ascent_rate, 1).range(0, 1);
        filtered = RLPF.ar(mixed_voices, filter_freq * (1 + (ascending_pitch * filter_sweep * 2)), 0.3);
        
        // Shimmer effect - delayed octave up
        shimmered = filtered + DelayC.ar(
            PitchShift.ar(filtered, 0.2, 2.0, 0.01, 0.01), 
            0.3, 
            0.1
        ) * shimmer * 0.4;
        
        final_sig = shimmered * amp;
        
        mono_for_analysis = final_sig;
        
        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'the_ascent_sound' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)