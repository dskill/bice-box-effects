// shader: oscilloscope
(
    var defName = \sad_synth;
    var numVoices = 8;
    var specs = (
        cutoff: ControlSpec(100, 2000, 'exp', 0, 400, "Hz"),
        resonance: ControlSpec(0.1, 1.0, 'lin', 0, 0.7, ""),
        detune: ControlSpec(0.0, 0.2, 'lin', 0, 0.05, ""),
        release: ControlSpec(0.1, 4.0, 'exp', 0, 1.5, "s"),
        vocoder: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var cutoff = \cutoff.kr(specs[\cutoff].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var detune = \detune.kr(specs[\detune].default);
        var release = \release.kr(specs[\release].default);
        var vocoder = \vocoder.kr(specs[\vocoder].default);
        
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        var voice_signals, mixed_voices, filtered, formant_filtered, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, vocoder_env, osc1, osc2, base_voice, formant_voice, voice_out;

            if(numVoices > 1) {
                freq = voice_freqs[i];
                gate = voice_gates[i];
                vel_amp = voice_amps[i];
            } {
                freq = voice_freqs;
                gate = voice_gates;
                vel_amp = voice_amps;
            };
            
            env = EnvGen.ar(Env.adsr(0.1, 0.3, 0.6, release), gate);
            vocoder_env = EnvGen.ar(Env([0, 1, 0.3, 0.1], [0.1, release * 0.3, release * 0.7], 'exp'), gate);
            
            osc1 = Saw.ar(freq);
            osc2 = Saw.ar(freq * (1 + detune));
            base_voice = (osc1 + osc2) * 0.5;
            
            formant_voice = BPF.ar(base_voice, [270, 530, 840, 1200, 1800, 2400, 3200], [0.02, 0.02, 0.02, 0.02, 0.02, 0.02, 0.02], [3, 4, 5, 4, 3, 2, 1.5]).sum * 8;
            voice_out = XFade2.ar(base_voice, formant_voice, vocoder_env * vocoder * 2 - 1);
            voice_out = voice_out * env * vel_amp;
            voice_out;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        filtered = RLPF.ar(mixed_voices, cutoff, resonance);
        final_sig = filtered * 0.3;
        
        mono_for_analysis = final_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_sig, final_sig]);
    });
    def.add;
    "Effect SynthDef 'sad_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)