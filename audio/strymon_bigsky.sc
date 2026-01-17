// shader: grunge
// category: Reverb
// description: Strymon BigSky reverb with lush Cloud/Shimmer tails and huge space
(
    var defName = \strymon_bigsky;
    var specs = (
        decay: ControlSpec(0.5, 20.0, 'exp', 0, 6.0, "s"),
        pre_delay: ControlSpec(0.0, 0.2, 'lin', 0, 0.02, "s"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        mod: ControlSpec(0.0, 0.3, 'lin', 0, 0.08, ""),
        size: ControlSpec(0.3, 1.0, 'lin', 0, 0.8, ""),
        diffusion: ControlSpec(0.1, 0.95, 'lin', 0, 0.7, ""),
        shimmer_amt: ControlSpec(0.0, 1.0, 'lin', 0, 0.25, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.45, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var decay = \decay.kr(specs[\decay].default);
        var pre_delay = \pre_delay.kr(specs[\pre_delay].default);
        var tone = \tone.kr(specs[\tone].default);
        var mod = \mod.kr(specs[\mod].default);
        var size = \size.kr(specs[\size].default);
        var diffusion = \diffusion.kr(specs[\diffusion].default);
        var shimmer_amt = \shimmer_amt.kr(specs[\shimmer_amt].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, predel, reverbed, tone_cut, wet, shimmer, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        predel = DelayC.ar(sig, 0.25, pre_delay);
        reverbed = JPverb.ar(
            predel,
            t60: decay,
            damp: 1 - tone,
            size: size,
            earlydiff: diffusion,
            modDepth: mod,
            modFreq: 0.4
        );
        tone_cut = tone.linexp(0, 1, 2000, 14000);
        wet = LPF.ar(reverbed, tone_cut);
        shimmer = PitchShift.ar(wet, 0.2, 2.0, 0.0, 0.0);
        wet = wet + (shimmer * shimmer_amt);

        processed = XFade2.ar(dry, wet, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'strymon_bigsky' added".postln;

    ~setupEffect.value(defName, specs);
)