// shader: oscilloscope
// category: Modulation
// description: EHX Electric Mistress flanger with filter-matrix freeze and lush sweep
(
    var defName = \ehx_electric_mistress;
    var specs = (
        rate: ControlSpec(0.05, 5.0, 'exp', 0, 0.3, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "x"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.35, "x"),
        manual: ControlSpec(0.0005, 0.005, 'exp', 0, 0.002, "s"),
        filter_matrix: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, "x"),
        hi_cut: ControlSpec(800, 12000, 'exp', 0, 8000, "Hz"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var manual = \manual.kr(specs[\manual].default);
        var filter_matrix = \filter_matrix.kr(specs[\filter_matrix].default);
        var hi_cut = \hi_cut.kr(specs[\hi_cut].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, lfo, modDelay, fixedDelay, delayTime, fbNode, delayed, fbSend, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        lfo = SinOsc.kr(rate);
        modDelay = manual + (depth * 0.003 * (lfo * 0.5 + 0.5));
        fixedDelay = manual + (depth * 0.0015);
        delayTime = SelectX.kr(filter_matrix, [modDelay, fixedDelay]);

        fbNode = LocalIn.ar(1);
        delayed = DelayC.ar(sig + fbNode, 0.01, delayTime);
        delayed = LPF.ar(delayed, hi_cut);
        fbSend = (delayed * feedback).tanh;
        LocalOut.ar(fbSend);

        processed = delayed;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'ehx_electric_mistress' added".postln;

    ~setupEffect.value(defName, specs);
)
