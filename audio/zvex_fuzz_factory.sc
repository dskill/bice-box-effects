// shader: grunge
// category: Distortion
// description: ZVEX Fuzz Factory chaos fuzz with gated velcro bite and oscillation sputter
(
    var defName = \zvex_fuzz_factory;
    var specs = (
        gate: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, ""),
        comp: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        drive: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        stab: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        bias: ControlSpec(-1.0, 1.0, 'lin', 0, 0.0, ""),
        chaos: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        oscillation_amt: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var gate = \gate.kr(specs[\gate].default);
        var comp = \comp.kr(specs[\comp].default);
        var drive = \drive.kr(specs[\drive].default);
        var stab = \stab.kr(specs[\stab].default);
        var bias = \bias.kr(specs[\bias].default);
        var chaos = \chaos.kr(specs[\chaos].default);
        var oscillation_amt = \oscillation_amt.kr(specs[\oscillation_amt].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, fb, chaosMod, pre, compGain, driveGain, clipped, gatedEnv, gateThresh;
        var stabCut, processed, fbAmt, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        fb = LocalIn.ar(1);
        chaosMod = LFNoise2.kr(0.2 + (chaos * 6)).range(-1, 1) * chaos;
        pre = sig + (fb * oscillation_amt) + (bias * 0.4) + (chaosMod * 0.2);

        compGain = 1 + (comp * 6);
        driveGain = 1 + (drive * 20);
        clipped = tanh(pre * compGain * driveGain);
        clipped = (clipped * (1 + (chaosMod * 0.6))).softclip;

        gateThresh = gate.linexp(0, 1, 0.0001, 0.2);
        gatedEnv = Amplitude.kr(clipped.abs, 0.001, 0.06);
        processed = clipped * (gatedEnv > gateThresh).lag(0.005);

        stabCut = stab.linexp(0, 1, 200, 12000);
        processed = LPF.ar(processed, stabCut);
        processed = LeakDC.ar(processed);
        processed = HPF.ar(processed, 30);

        fbAmt = (oscillation_amt * (0.2 + (stab * 0.7)) + (chaos * 0.2)).clip(0, 0.98);
        LocalOut.ar(processed * fbAmt);

        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'zvex_fuzz_factory' added".postln;

    ~setupEffect.value(defName, specs);
)
