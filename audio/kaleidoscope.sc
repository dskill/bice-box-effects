(
    var defName = \kaleidoscope;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sparkle = \sparkle.kr(0.5);
        var delayTime = \delayTime.kr(0.3);
        var feedback = \feedback.kr(0.6);
        var shimmer = \shimmer.kr(0.4);
        var rotation = \rotation.kr(0.5);
        var mix = \mix.kr(0.5);
        
        var sig, wet, dry, delayedSig, shiftedSig, sparkles;
        var mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        // Create sparkles using resonant filters and noise
        sparkles = Mix.fill(8, {
            var freq = TRand.kr(2000, 12000, Dust.kr(sparkle * 10 + 0.1));
            var amp = LFNoise1.kr(rotation * 2).range(0, sparkle);
            Ringz.ar(Dust.ar(sparkle * 20 + 1) * 0.04, freq, 0.05) * amp;
        });

        // Shimmer delay with pitch shifting
        delayedSig = CombL.ar(sig + (sparkles * 0.3), 1.0, delayTime, feedback * 4);
        shiftedSig = PitchShift.ar(delayedSig, 0.2,
            LFNoise1.kr(rotation).range(1.0, 1.5 + (shimmer * 0.5)),
            shimmer * 0.2,
            0.1
        );

        wet = Mix([delayedSig, shiftedSig * shimmer]) * 0.5;
        sig = XFade2.ar(dry, wet, mix * 2 - 1);

        // Prepare mono signal for analysis
        if (sig.isArray) { // Check if stereo
            mono_for_analysis = Mix.ar(sig); // Mix stereo to mono
        } {
            mono_for_analysis = sig; // Already mono
        };

        Out.ar(out, sig); // Output main signal (can be stereo)
        Out.ar(analysis_out_bus, mono_for_analysis); // Output mono signal for analysis

    });
    def.add;
    "Effect SynthDef 'kaleidoscope' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        sparkle: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        delayTime: ControlSpec(0.01, 1.0, 'exp', 0, 0.3, "s"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.6, "%"),
        shimmer: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        rotation: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
) 