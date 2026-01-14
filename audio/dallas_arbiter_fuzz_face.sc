// shader: oscilloscope
// category: Distortion
// description: Dallas Arbiter Fuzz Face cleanup fuzz with warm biased sag
(
    var defName = \dallas_arbiter_fuzz_face;
    var specs = (
        fuzz: ControlSpec(0.5, 10.0, 'exp', 0, 3.0, "x"),
        volume: ControlSpec(0.0, 2.0, 'lin', 0, 0.8, "x"),
        input_impedance: ControlSpec(0.2, 1.0, 'lin', 0, 0.7, ""),
        cleanup_amount: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, ""),
        bias: ControlSpec(-0.6, 0.6, 'lin', 0, 0.0, ""),
        warmth: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        gating: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var fuzz = \fuzz.kr(specs[\fuzz].default);
        var volume = \volume.kr(specs[\volume].default);
        var input_impedance = \input_impedance.kr(specs[\input_impedance].default);
        var cleanup_amount = \cleanup_amount.kr(specs[\cleanup_amount].default);
        var bias = \bias.kr(specs[\bias].default);
        var warmth = \warmth.kr(specs[\warmth].default);
        var gating = \gating.kr(specs[\gating].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pre, env, envScaled, inputLP, biasOffset, lpCut, postCut;
        var fuzzed, cleanBlend, gateThresh, gateGain, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        env = Amplitude.kr(sig, 0.005, 0.08).clip(0, 1);
        envScaled = env.linexp(0.0005, 0.5, 0.05, 1.0).clip(0.0, 1.0);

        lpCut = input_impedance.linexp(0.2, 1.0, 1800, 9000);
        inputLP = LPF.ar(sig, lpCut);

        pre = inputLP * fuzz;
        biasOffset = bias * 0.6;
        fuzzed = (pre + biasOffset).tanh;
        fuzzed = (fuzzed * 1.2).softclip;

        postCut = (1 - warmth).linexp(0.0, 1.0, 7000, 1800);
        fuzzed = LPF.ar(fuzzed, postCut);

        cleanBlend = (1 - (envScaled * cleanup_amount)).clip(0, 1);
        processed = (fuzzed * (1 - cleanBlend)) + (sig * cleanBlend);

        gateThresh = gating.linexp(0.0, 1.0, 0.0003, 0.05);
        gateGain = ((env - gateThresh) / (1 - gateThresh)).clip(0, 1);
        processed = processed * gateGain;

        processed = processed * volume;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'dallas_arbiter_fuzz_face' added".postln;

    ~setupEffect.value(defName, specs);
)
