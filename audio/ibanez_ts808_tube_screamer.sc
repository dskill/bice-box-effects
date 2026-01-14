// shader: oscilloscope
// category: Distortion
// description: Ibanez TS808 Tube Screamer mid-hump overdrive with smooth grit
(
    var defName = \ibanez_ts808_tube_screamer;
    var specs = (
        drive: ControlSpec(0.5, 20.0, 'exp', 0, 3.5, "x"),
        tone: ControlSpec(400, 6000, 'exp', 0, 1800, "Hz"),
        level: ControlSpec(0.0, 2.0, 'lin', 0, 1.0, "x"),
        mid_hump: ControlSpec(-6.0, 12.0, 'lin', 0, 6.0, "dB"),
        bass_cut: ControlSpec(40, 400, 'exp', 0, 120, "Hz"),
        clipping_symmetry: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(specs[\drive].default);
        var tone = \tone.kr(specs[\tone].default);
        var level = \level.kr(specs[\level].default);
        var mid_hump = \mid_hump.kr(specs[\mid_hump].default);
        var bass_cut = \bass_cut.kr(specs[\bass_cut].default);
        var clipping_symmetry = \clipping_symmetry.kr(specs[\clipping_symmetry].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pre, midEQ, bias, clipped, post, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        pre = HPF.ar(sig, bass_cut);
        midEQ = BPeakEQ.ar(pre, 720, 0.7, mid_hump);
        bias = (clipping_symmetry * 2 - 1) * 0.25;
        clipped = (midEQ * drive + bias).tanh - bias;
        post = LPF.ar(clipped, tone);
        processed = post * level;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'ibanez_ts808_tube_screamer' added".postln;

    ~setupEffect.value(defName, specs);
)
