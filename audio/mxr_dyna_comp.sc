// shader: oscilloscope
// category: Dynamics
// description: MXR Dyna Comp classic squash for clicky attack and long sustain
(
    var defName = \mxr_dyna_comp;  // ← MUST match filename exactly!
    var specs = (
        sensitivity: ControlSpec(0.02, 0.5, 'exp', 0, 0.15, ""),
        output: ControlSpec(0.2, 2.5, 'exp', 0, 1.0, "x"),
        attack: ControlSpec(0.0005, 0.02, 'exp', 0, 0.003, "s"),
        release: ControlSpec(0.03, 0.5, 'exp', 0, 0.12, "s"),
        color: ControlSpec(-6.0, 6.0, 'lin', 0, 2.5, "dB"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sensitivity = \sensitivity.kr(specs[\sensitivity].default);
        var output = \output.kr(specs[\output].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var color = \color.kr(specs[\color].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here!
        var sig, dry, pre, comp, shaped, mono_for_analysis;

        // Processing
        sig = In.ar(in_bus);  // Mono input
        dry = sig;

        // Pre-emphasis to bring out pick attack; color in dB.
        pre = BPeakEQ.ar(sig, 2200, 0.7, color);
        // Dyna Comp style heavy squash.
        comp = Compander.ar(
            pre, pre,
            sensitivity,  // threshold
            1.0,          // slope below
            0.25,         // slope above (strong compression)
            attack,
            release
        );
        shaped = comp * output;
        shaped = XFade2.ar(dry, shaped, mix * 2 - 1);

        // Outputs
        mono_for_analysis = shaped;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [shaped, shaped]);
    });
    def.add;
    "Effect SynthDef 'mxr_dyna_comp' added".postln;

    ~setupEffect.value(defName, specs);
)
