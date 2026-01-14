// shader: oscilloscope
// category: Distortion
// description: ProCo RAT grind with LM308 bite and sweeping Filter roll-off
(
    var defName = \proco_rat;
    var specs = (
        distortion: ControlSpec(0.5, 8.0, 'exp', 0, 2.5, "x"),
        filter: ControlSpec(200, 8000, 'exp', 0, 2000, "Hz"),
        level: ControlSpec(0.0, 2.0, 'lin', 0, 1.0, "x"),
        clipping_hardness: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        sag: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var distortion = \distortion.kr(specs[\distortion].default);
        var filter = \filter.kr(specs[\filter].default);
        var level = \level.kr(specs[\level].default);
        var clipping_hardness = \clipping_hardness.kr(specs[\clipping_hardness].default);
        var sag = \sag.kr(specs[\sag].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pre, env, sag_gain, soft_clip, hard_clip, clipped, filtered, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        pre = HPF.ar(sig, 40);
        env = Amplitude.kr(pre, 0.005, 0.12);
        sag_gain = (1 - (sag * env * 0.8)).clip(0.25, 1.0);
        pre = pre * distortion * 3 * sag_gain;

        soft_clip = tanh(pre);
        hard_clip = pre.clip2(1.0);
        clipped = XFade2.ar(soft_clip, hard_clip, clipping_hardness * 2 - 1);

        filtered = LPF.ar(clipped, filter);
        processed = filtered * level;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'proco_rat' added".postln;

    ~setupEffect.value(defName, specs);
)
