// shader: oscilloscope
(
    var defName = \wah_synth;
    var numVoices = 8;
    var specs = (
        amp: ControlSpec(0.0, 1.0, 'lin', 0, 0.108, ""),
        wah_freq: ControlSpec(200, 3000, 'exp', 0, 668, "Hz"),
        wah_amount: ControlSpec(0.0, 1.0, 'lin', 0, 0.628, ""),
        wah_speed: ControlSpec(0.1, 10.0, 'exp', 0, 2.0, "Hz"),
        resonance: ControlSpec(0.1, 2.0, 'exp', 0, 0.3, ""),
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 1.501, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 1.097, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, ""),
        release: ControlSpec(0.001, 4.0, 'exp', 0, 2.252, "s")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        
        var amp = \amp.kr(specs[\amp].default);
        var wah_freq = \wah_freq.kr(specs[\wah_freq].default);
        var wah_amount = \wah_amount.kr(specs[\wah_amount].default);
        var wah_speed = \wah_speed.kr(specs[\wah_speed].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        var voice_signals, mixed_voices, wah_lfo, dynamic_freq, final_sig, mono_for_analysis;
        
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp, env, wave;

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
            wave = Saw.ar(freq) * env * vel_amp;
            wave;
        });
        
        mixed_voices = Mix.ar(voice_signals);
        
        wah_lfo = SinOsc.kr(wah_speed);
        dynamic_freq = wah_freq * (1.0 + (wah_lfo * wah_amount));
        
        final_sig = RLPF.ar(mixed_voices, dynamic_freq, resonance);
        final_sig = final_sig * amp;
        final_sig = final_sig.clip(-1.0, 1.0);
        
        mono_for_analysis = final_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_sig, final_sig]);
    });
    def.add;
    "Effect SynthDef 'wah_synth' (polyphonic) added".postln;

    ~setupEffect.value(defName, specs, [], numVoices);
)