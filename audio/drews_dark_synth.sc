// shader: oscilloscope
(
    var defName = \drews_dark_synth;
    var numVoices = 8;
    var specs = (
        // Oscillator controls
        sub_level: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        detune: ControlSpec(0.0, 0.2, 'lin', 0, 0.05, ""),
        darkness: ControlSpec(100, 2000, 'exp', 0, 400, "Hz"),
        resonance: ControlSpec(0.1, 4.0, 'lin', 0, 2.0, ""),
        // Envelope
        attack: ControlSpec(0.01, 2.0, 'exp', 0, 0.05, "s"),
        decay: ControlSpec(0.01, 2.0, 'exp', 0, 0.3, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        release: ControlSpec(0.01, 4.0, 'exp', 0, 0.8, "s"),
        // Effects
        reverb: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        saturation: ControlSpec(0.0, 3.0, 'lin', 0, 0.5, ""),
        amp: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        
        var sub_level = \sub_level.kr(specs[\sub_level].default);
        var detune = \detune.kr(specs[\detune].default);
        var darkness = \darkness.kr(specs[\darkness].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        var reverb = \reverb.kr(specs[\reverb].default);
        var saturation = \saturation.kr(specs[\saturation].default);
        var amp = \amp.kr(specs[\amp].default);
        var mix = \mix.kr(specs[\mix].default);
        
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        var voice_signals, mixed_voices, filtered, saturated, reverbed, dry, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, main_osc, sub_osc, detuned_osc, voice_mix, voice_out;

            if(numVoices > 1) {
                freq = voice_freqs[i];
                gate = voice_gates[i];
                vel_amp = voice_amps[i];
            } {
                freq = voice_freqs;
                gate = voice_gates;
                vel_amp = voice_amps;
            };
            
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Dark oscillator blend: saw + sub + detuned
            main_osc = Saw.ar(freq);
            sub_osc = SinOsc.ar(freq * 0.5) * sub_level;
            detuned_osc = Saw.ar(freq * (1 + detune)) * 0.3;
            
            voice_mix = main_osc + sub_osc + detuned_osc;
            voice_out = voice_mix * env * vel_amp;
            voice_out;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        // Dark low-pass filtering with resonance
        filtered = RLPF.ar(mixed_voices, darkness, 1.0 / resonance);
        
        // Soft saturation for warmth
        saturated = (filtered * (1 + saturation)).tanh;
        
        // Dark reverb
        reverbed = FreeVerb.ar(saturated, reverb, 0.8, 0.3);
        
        dry = In.ar(in_bus);
        final_sig = XFade2.ar(dry, reverbed * amp, mix * 2 - 1);
        
        mono_for_analysis = final_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_sig, final_sig]);
    });
    def.add;
    "Effect SynthDef 'drews_dark_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)