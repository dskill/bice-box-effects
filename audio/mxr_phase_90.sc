// shader: oscilloscope
// category: Modulation
// description: MXR Phase 90 swirl with one-knob sweep and chewy swoosh
(
    var defName = \mxr_phase_90;  // MUST match filename exactly
    var specs = (
        rate: ControlSpec(0.05, 8.0, 'exp', 0, 0.4, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.2, "%"),
        stage_spread: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        sweep_center: ControlSpec(200, 1500, 'exp', 0, 700, "Hz"),
        waveform: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var stage_spread = \stage_spread.kr(specs[\stage_spread].default);
        var sweep_center = \sweep_center.kr(specs[\sweep_center].default);
        var waveform = \waveform.kr(specs[\waveform].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, fb, lfoSin, lfoTri, lfo, sweep, freq, delBase;
        var stage1, stage2, stage3, stage4, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        lfoSin = SinOsc.kr(rate).range(-1, 1);
        lfoTri = LFTri.kr(rate).range(-1, 1);
        lfo = SelectX.kr(waveform, [lfoSin, lfoTri]);

        sweep = (lfo * depth).clip(-1, 1);
        freq = sweep_center * (2 ** (sweep * 1.2));
        delBase = (1 / freq).clip(0.0003, 0.02);

        fb = LocalIn.ar(1);
        sig = sig + (fb * feedback);

        stage1 = AllpassC.ar(sig, 0.02, delBase, 0);
        stage2 = AllpassC.ar(stage1, 0.02, (delBase * (1 + stage_spread * 0.5)).clip(0.0003, 0.02), 0);
        stage3 = AllpassC.ar(stage2, 0.02, (delBase * (1 + stage_spread * 1.0)).clip(0.0003, 0.02), 0);
        stage4 = AllpassC.ar(stage3, 0.02, (delBase * (1 + stage_spread * 1.5)).clip(0.0003, 0.02), 0);
        processed = stage4;

        LocalOut.ar(processed);

        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'mxr_phase_90' added".postln;

    ~setupEffect.value(defName, specs);
)
