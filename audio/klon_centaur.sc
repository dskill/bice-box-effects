// shader: oscilloscope
// category: Distortion
// description: Klon Centaur transparent drive with clean blend and subtle mid lift
(
    var defName = \klon_centaur;
    var specs = (
        gain: ControlSpec(0.5, 20.0, 'exp', 0, 2.5, "x"),
        treble: ControlSpec(-9.0, 9.0, 'lin', 0, 0.0, "dB"),
        output: ControlSpec(0.1, 2.0, 'exp', 0, 1.0, "x"),
        clean_blend: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        mid_emphasis: ControlSpec(0.0, 6.0, 'lin', 0, 2.0, "dB"),
        headroom: ControlSpec(0.5, 2.0, 'exp', 0, 1.0, "x"),
        saturation: ControlSpec(0.5, 3.0, 'exp', 0, 1.2, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var gain = \gain.kr(specs[\gain].default);
        var treble = \treble.kr(specs[\treble].default);
        var output = \output.kr(specs[\output].default);
        var clean_blend = \clean_blend.kr(specs[\clean_blend].default);
        var mid_emphasis = \mid_emphasis.kr(specs[\mid_emphasis].default);
        var headroom = \headroom.kr(specs[\headroom].default);
        var saturation = \saturation.kr(specs[\saturation].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pre, midBoost, highShelf, clipped, wet, outputSig, mono_for_analysis;

        sig = In.ar(in_bus);
        sig = LeakDC.ar(sig);
        dry = sig;

        pre = HPF.ar(sig, 40);
        midBoost = BPeakEQ.ar(pre, 850, 0.7, mid_emphasis);
        highShelf = BHiShelf.ar(midBoost, 3500, 0.7, treble);

        clipped = tanh((highShelf * gain / headroom) * saturation) * headroom;
        wet = XFade2.ar(clipped, highShelf, (clean_blend * 2) - 1);
        outputSig = wet * output;

        outputSig = XFade2.ar(dry, outputSig, (mix * 2) - 1);

        mono_for_analysis = outputSig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [outputSig, outputSig]);
    });
    def.add;
    "Effect SynthDef 'klon_centaur' added".postln;

    ~setupEffect.value(defName, specs);
)
