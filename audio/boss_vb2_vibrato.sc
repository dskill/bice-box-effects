// shader: oscilloscope
// category: Modulation
// description: Boss VB-2 Vibrato BBD pitch wobble with slow-rise throb
(
    var defName = \boss_vb2_vibrato;
    var specs = (
        rate: ControlSpec(0.05, 10.0, 'exp', 0, 3.5, "Hz"),
        depth: ControlSpec(0.0001, 0.010, 'exp', 0, 0.003, "s"),
        rise_time: ControlSpec(0.0, 3.0, 'lin', 0, 0.4, "s"),
        hi_cut: ControlSpec(800, 12000, 'exp', 0, 7000, "Hz"),
        instability: ControlSpec(0.0, 0.5, 'lin', 0, 0.08, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var rise_time = \rise_time.kr(specs[\rise_time].default);
        var hi_cut = \hi_cut.kr(specs[\hi_cut].default);
        var instability = \instability.kr(specs[\instability].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, lfo, wobble, depth_smooth, mod, delay_time, wet, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        lfo = SinOsc.kr(rate);
        wobble = LFNoise2.kr(rate * 0.6).range(-1, 1) * instability;
        depth_smooth = Lag.kr(depth, rise_time.max(0.001));
        mod = (lfo + wobble).clip2(1.0);
        delay_time = (0.002 + (mod * depth_smooth)).clip(0.0001, 0.02);
        wet = DelayC.ar(sig, 0.02, delay_time);
        wet = LPF.ar(wet, hi_cut);

        processed = XFade2.ar(dry, wet, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'boss_vb2_vibrato' added".postln;

    ~setupEffect.value(defName, specs);
)
