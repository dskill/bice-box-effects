// shader: oscilloscope
// category: Experimental
// description: Vocoder wah with envelope sweep and resonance
(
    var defName = \vocoder_wah;
    var specs = (
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        wah_freq: ControlSpec(200, 3000, 'exp', 0, 800, "Hz"),
        wah_amount: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        wah_speed: ControlSpec(0.1, 10.0, 'exp', 0, 2.0, "Hz"),
        resonance: ControlSpec(0.1, 2.0, 'exp', 0, 0.3, "")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var mix = \mix.kr(specs[\mix].default);
        var wah_freq = \wah_freq.kr(specs[\wah_freq].default);
        var wah_amount = \wah_amount.kr(specs[\wah_amount].default);
        var wah_speed = \wah_speed.kr(specs[\wah_speed].default);
        var resonance = \resonance.kr(specs[\resonance].default);

        var sig, dry, wah_lfo, dynamic_freq, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;
        
        // Wah LFO
        wah_lfo = SinOsc.kr(wah_speed);
        dynamic_freq = wah_freq * (1.0 + (wah_lfo * wah_amount));
        
        // Resonant filter for classic wah sound
        processed = RLPF.ar(sig, dynamic_freq, resonance);
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'vocoder_wah' added".postln;

    ~setupEffect.value(defName, specs);
)
