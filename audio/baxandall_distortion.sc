// shader: skull

(
    var defName = \baxandall_distortion;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(10.0);
        var bass = \bass.kr(0.0);
        var treble = \treble.kr(0.0);
        var level = \level.kr(0.7);
        var mix = \mix.kr(0.5);

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
    });
    def.add;
    "Effect SynthDef 'grittyBaxandallDistortion' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        drive: ControlSpec(1.0, 100.0, 'exp', 0, 10.0, "x"),
        bass: ControlSpec(-12.0, 12.0, 'lin', 0, 0.0, "dB"),
        treble: ControlSpec(-12.0, 12.0, 'lin', 0, 0.0, "dB"),
        level: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    // Existing logic to create the synth instance
    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)