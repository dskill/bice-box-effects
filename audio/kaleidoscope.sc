(
    SynthDef(\kaleidoscope, {
        |out = 0, in_bus, analysis_out_bus, sparkle = 0.5, delayTime = 0.3, feedback = 0.6, shimmer = 0.4, rotation = 0.5, mix = 0.5|
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

    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\kaleidoscope, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            // Add any other effect-specific parameters here if they have defaults different from SynthDef
            // For example:
            // \sparkle, 0.5,
            // \delayTime, 0.3,
            // \feedback, 0.6,
            // \shimmer, 0.4,
            // \rotation, 0.5,
            // \mix, 0.5
        ], ~effectGroup);
        ("New kaleidoscope synth created with analysis output bus").postln;
    };
) 