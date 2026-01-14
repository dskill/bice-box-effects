// shader: oscilloscope
// category: Dynamics
// description: Boss SG-1 Slow Gear auto-swell removing pick attack for violin fades
(
    var defName = \boss_sg1_slow_gear;
    var specs = (
        sensitivity: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        attack: ControlSpec(0.005, 1.5, 'exp', 0, 0.15, "s"),
        release: ControlSpec(0.05, 2.5, 'exp', 0, 0.6, "s"),
        swell_curve: ControlSpec(0.5, 4.0, 'exp', 0, 1.6, "x"),
        output: ControlSpec(0.2, 2.5, 'exp', 0, 1.0, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sensitivity = \sensitivity.kr(specs[\sensitivity].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var swell_curve = \swell_curve.kr(specs[\swell_curve].default);
        var output = \output.kr(specs[\output].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, amp, threshold, gate, env, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        sig = LeakDC.ar(sig);
        dry = sig;

        amp = Amplitude.kr(sig, 0.01, 0.1);
        threshold = LinExp.kr(sensitivity, 0.0, 1.0, 0.25, 0.005);
        gate = amp > threshold;
        env = LagUD.kr(gate, attack, release).pow(swell_curve);

        processed = sig * env * output;
        processed = LeakDC.ar(processed);
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'boss_sg1_slow_gear' added".postln;

    ~setupEffect.value(defName, specs);
)
