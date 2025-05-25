(
    SynthDef(\grittyBaxandallDistortion, {
        |out = 0, in_bus = 0, analysis_out_bus, drive = 10.0, bass = 0.0, treble = 0.0, level = 0.7, mix = 0.5|

        var sig, dry, distortedSig, eqSig, wetSig, finalSig;
        var monoFinalForVisuals; // For masterAnalyser

        sig = In.ar(in_bus, 2); // Read stereo input
        dry = sig;

        // START USER EFFECT CODE
        // 1. Drive / Distortion
        // 'drive' parameter (1 to 100) acts as a pre-gain before tanh distortion
        distortedSig = sig * drive;
        distortedSig = distortedSig.tanh; // tanh for a gritty distortion character (applies per channel)

        // 2. Baxandall Tone Control
        // 'bass' and 'treble' parameters are in dB (-12 to +12)
        // BLowShelf for bass control
        eqSig = BLowShelf.ar(distortedSig, freq: 150, rs: 0.707, db: bass); // Applies per channel
        // BHiShelf for treble control
        eqSig = BHiShelf.ar(eqSig, freq: 4000, rs: 0.707, db: treble);   // Applies per channel

        // 3. Level for wet signal
        // 'level' parameter (0 to 1) adjusts the amplitude of the processed signal
        wetSig = eqSig * level; // Applies per channel

        // 4. Mix with dry signal
        // 'mix' parameter (0 to 1) blends between dry and wet signal
        finalSig = XFade2.ar(dry, wetSig, mix * 2 - 1); // Applies per channel
        // END USER EFFECT CODE

        // Prepare mono version of final signal for masterAnalyser
        monoFinalForVisuals = (finalSig[0] + finalSig[1]) * 0.5; // Mix stereo final to mono

        // Output for masterAnalyser
        Out.ar(analysis_out_bus, monoFinalForVisuals);

        Out.ar(out, finalSig); // Output stereo (finalSig is already a stereo signal)

    }).add;
    "GrittyBaxandallDistortion SynthDef added".postln;

    fork {
        s.sync; // Wait for server readiness
        // Free any existing synth stored in ~effect
        if(~effect.notNil, {
            "Freeing existing GrittyBaxandallDistortion synth".postln;
            ~effect.free;
        });
        // Create a new instance of the GrittyBaxandallDistortion synth
        ~effect = Synth(\grittyBaxandallDistortion, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        "New GrittyBaxandallDistortion synth created".postln;
    };
)