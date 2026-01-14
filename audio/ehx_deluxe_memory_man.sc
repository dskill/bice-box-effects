// shader: oscilloscope
// category: Delay
// description: EHX Deluxe Memory Man analog delay: warm repeats with chorus/vibrato
(
    var defName = \ehx_deluxe_memory_man;
    var specs = (
        delay_time: ControlSpec(0.03, 1.2, 'exp', 0, 0.35, "s"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.45, ""),
        blend: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        mod_rate: ControlSpec(0.05, 6.0, 'exp', 0, 0.6, "Hz"),
        mod_depth: ControlSpec(0.0, 0.01, 'lin', 0, 0.003, "s"),
        saturation: ControlSpec(0.5, 6.0, 'exp', 0, 1.5, "x"),
        hi_cut: ControlSpec(800, 12000, 'exp', 0, 4500, "Hz"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.45, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var delay_time = \delay_time.kr(specs[\delay_time].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var blend = \blend.kr(specs[\blend].default);
        var mod_rate = \mod_rate.kr(specs[\mod_rate].default);
        var mod_depth = \mod_depth.kr(specs[\mod_depth].default);
        var saturation = \saturation.kr(specs[\saturation].default);
        var hi_cut = \hi_cut.kr(specs[\hi_cut].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, lfo, mod_time, fb_sig, delayed, filtered, saturated, wet, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        lfo = SinOsc.kr(mod_rate).range(mod_depth * -1, mod_depth);
        mod_time = (delay_time + lfo).clip(0.001, 1.5);

        fb_sig = LocalIn.ar(1);
        delayed = DelayC.ar(sig + fb_sig, 1.5, mod_time);
        filtered = LPF.ar(delayed, hi_cut);
        saturated = (filtered * saturation).tanh;
        wet = saturated * blend;

        LocalOut.ar(wet * feedback);

        wet = wet + filtered * (1.0 - blend) * 0.3;

        mono_for_analysis = XFade2.ar(dry, wet, mix * 2 - 1);
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [mono_for_analysis, mono_for_analysis]);
    });
    def.add;
    "Effect SynthDef 'ehx_deluxe_memory_man' added".postln;

    ~setupEffect.value(defName, specs);
)
