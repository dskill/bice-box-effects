// shader: oscilloscope
// category: Delay
// description: Line 6 DL4 Delay Modeler multi-mode delay with looper-like tails
(
    var defName = \line6_dl4_delay_modeler;  // ← MUST match filename exactly!
    var specs = (
        mode_select: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, ""),
        time: ControlSpec(0.05, 2.0, 'exp', 0, 0.45, "s"),
        repeats: ControlSpec(0.0, 0.95, 'lin', 0, 0.4, ""),
        tweak: ControlSpec(200, 8000, 'exp', 0, 3200, "Hz"),
        tweez: ControlSpec(0.5, 4.0, 'exp', 0, 1.2, "x"),
        modulation: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        smear: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var mode_select = \mode_select.kr(specs[\mode_select].default);
        var time = \time.kr(specs[\time].default);
        var repeats = \repeats.kr(specs[\repeats].default);
        var tweak = \tweak.kr(specs[\tweak].default);
        var tweez = \tweez.kr(specs[\tweez].default);
        var modulation = \modulation.kr(specs[\modulation].default);
        var smear = \smear.kr(specs[\smear].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here!
        var sig, dry, fb, input, maxDelay, lfoRate, lfoDepth, lfo, delayTime;
        var delayed, digital, analog, tape, wow, flutter, modeOut, smearing, mixed;
        var mono_for_analysis;

        // Processing
        sig = In.ar(in_bus, 1);
        sig = LeakDC.ar(sig);
        dry = sig;

        maxDelay = 3.0;
        lfoRate = LinExp.kr(modulation, 0, 1, 0.1, 6.0);
        lfoDepth = modulation * 0.015;
        lfo = SinOsc.kr(lfoRate);
        delayTime = (time * (1 + (lfo * lfoDepth))).clip(0.001, maxDelay);

        fb = LocalIn.ar(1);
        input = sig + fb;
        delayed = DelayC.ar(input, maxDelay, delayTime);

        // Digital model: clean repeats
        digital = delayed;

        // Analog model: darker tone and soft saturation
        analog = LPF.ar(delayed, tweak);
        analog = HPF.ar(analog, 50);
        analog = (analog * tweez).tanh;

        // Tape model: wow/flutter, darker and softer repeats
        wow = SinOsc.kr(lfoRate * 0.35, 0, modulation * 0.004);
        flutter = LFTri.kr(lfoRate * 2.2, 0, modulation * 0.002);
        tape = DelayC.ar(input, maxDelay, (time * (1 + wow + flutter)).clip(0.001, maxDelay));
        tape = LPF.ar(tape, (tweak * 0.6).clip(400, 6000));
        tape = HPF.ar(tape, 60);
        tape = (tape * (tweez * 0.8)).tanh;

        modeOut = SelectX.ar(mode_select * 2, [digital, analog, tape]);

        // Smear adds diffusion for looper-like trails
        smearing = AllpassC.ar(modeOut, 0.05, (0.001 + (smear * 0.049)), 1.0);
        modeOut = XFade2.ar(modeOut, smearing, smear * 2 - 1);

        LocalOut.ar(modeOut * repeats);

        mixed = XFade2.ar(dry, modeOut, mix * 2 - 1);

        // Outputs
        mono_for_analysis = mixed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [mixed, mixed]);
    });
    def.add;
    "Effect SynthDef 'line6_dl4_delay_modeler' added".postln;

    ~setupEffect.value(defName, specs);
)
