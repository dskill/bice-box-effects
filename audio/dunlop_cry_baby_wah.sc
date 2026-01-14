// shader: oscilloscope
// category: Filter
// description: Dunlop Cry Baby Wah with vocal mid sweep and parked boost bite
(
    var defName = \dunlop_cry_baby_wah;
    var specs = (
        wah_position: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        q: ControlSpec(0.2, 1.0, 'lin', 0, 0.6, ""),
        range: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, ""),
        resonance: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        drive: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        pre_emphasis: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, ""),
        noise: ControlSpec(0.0, 1.0, 'lin', 0, 0.1, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var wah_position = \wah_position.kr(specs[\wah_position].default);
        var q = \q.kr(specs[\q].default);
        var range = \range.kr(specs[\range].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var drive = \drive.kr(specs[\drive].default);
        var pre_emphasis = \pre_emphasis.kr(specs[\pre_emphasis].default);
        var noise = \noise.kr(specs[\noise].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pre, min_freq, max_freq, sweep_freq, driven, wah, peak, hiss, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;
        pre = HPF.ar(sig, 90);
        pre = pre + (BPF.ar(pre, 700, 0.6) * pre_emphasis);

        driven = (pre * (1 + (drive * 4))).tanh;

        min_freq = 300;
        max_freq = 2000 + (range * 2000);
        sweep_freq = LinExp.kr(wah_position, 0, 1, min_freq, max_freq);

        wah = RLPF.ar(driven, sweep_freq, q);
        peak = BPF.ar(driven, sweep_freq, q) * resonance;
        processed = wah + peak;

        hiss = BPF.ar(WhiteNoise.ar(1), sweep_freq, 0.5) * (noise * 0.02);
        processed = processed + hiss;

        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'dunlop_cry_baby_wah' added".postln;

    ~setupEffect.value(defName, specs);
)
