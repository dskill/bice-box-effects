// shader: kaleidoscope
(
    var defName = \kaleidoscope;
    var specs = (
        sparkle: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        delayTime: ControlSpec(0.01, 1.0, 'exp', 0, 0.3, "s"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.6, "%"),
        shimmer: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        rotation: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sparkle = \sparkle.kr(specs[\sparkle].default);
        var delayTime = \delayTime.kr(specs[\delayTime].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var shimmer = \shimmer.kr(specs[\shimmer].default);
        var rotation = \rotation.kr(specs[\rotation].default);
        var mix = \mix.kr(specs[\mix].default);
        
        var sig, wet, dry, delayedSig, shiftedSig, sparkles;
        var mono_for_analysis;

        sig = In.ar(in_bus); // Sums stereo to mono
        dry = sig;

        // Create sparkles using resonant filters and noise - this part is mono
        sparkles = Mix.fill(8, {
            var freq = TRand.kr(2000, 12000, Dust.kr(sparkle * 10 + 0.1));
            var amp = LFNoise1.kr(rotation * 2).range(0, sparkle);
            Ringz.ar(Dust.ar(sparkle * 20 + 1) * 0.04, freq, 0.05) * amp;
        });

        // Shimmer delay with pitch shifting - all mono processing
        delayedSig = CombL.ar(sig + (sparkles * 0.3), 1.0, delayTime, feedback * 4);
        shiftedSig = PitchShift.ar(delayedSig, 0.2,
            LFNoise1.kr(rotation).range(1.0, 1.5 + (shimmer * 0.5)),
            shimmer * 0.2,
            0.1
        );

        wet = Mix([delayedSig, shiftedSig * shimmer]) * 0.5;
        sig = XFade2.ar(dry, wet, mix * 2 - 1);

        // Prepare mono signal for analysis - already mono
        mono_for_analysis = sig;

        Out.ar(out, [sig, sig]); // Duplicate mono signal for stereo output
        Out.ar(analysis_out_bus, mono_for_analysis);

    });
    def.add;
    "Effect SynthDef 'kaleidoscope' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
) 