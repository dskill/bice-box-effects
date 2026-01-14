// shader: oscilloscope
// category: Pitch
// description: Digitech Whammy-style pitch bends with smooth dive/soar and tight tracking
(
    var defName = \digitech_whammy;
    var specs = (
        interval: ControlSpec(-24, 24, 'lin', 1, 12, "st"),
        expression_pos: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%"),
        glide_time: ControlSpec(0.001, 0.3, 'exp', 0, 0.03, "s"),
        tracking_smooth: ControlSpec(0.005, 0.2, 'exp', 0, 0.02, "s"),
        detune: ControlSpec(-0.5, 0.5, 'lin', 0, 0.0, "st"),
        formant: ControlSpec(400, 8000, 'exp', 0, 4000, "Hz"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var interval = \interval.kr(specs[\interval].default);
        var expression_pos = \expression_pos.kr(specs[\expression_pos].default);
        var glide_time = \glide_time.kr(specs[\glide_time].default);
        var tracking_smooth = \tracking_smooth.kr(specs[\tracking_smooth].default);
        var detune = \detune.kr(specs[\detune].default);
        var formant = \formant.kr(specs[\formant].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, targetSemi, ratio, shifted, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;
        sig = LeakDC.ar(sig);

        targetSemi = (interval * expression_pos) + detune;
        ratio = Lag.kr(targetSemi.midiratio, glide_time);

        shifted = PitchShift.ar(sig, tracking_smooth, ratio, 0.01, 0.01);
        shifted = LPF.ar(shifted, formant);

        processed = XFade2.ar(dry, shifted, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'digitech_whammy' added".postln;

    ~setupEffect.value(defName, specs);
)
