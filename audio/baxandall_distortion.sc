// shader: skull

(
    var defName = \baxandall_distortion;
    var specs = (
        drive: ControlSpec(1.0, 100.0, 'exp', 0, 10.0, "x"),
        bass: ControlSpec(-12.0, 12.0, 'lin', 0, 0.0, "dB"),
        treble: ControlSpec(-12.0, 12.0, 'lin', 0, 0.0, "dB"),
        level: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(specs[\drive].default);
        var bass = \bass.kr(specs[\bass].default);
        var treble = \treble.kr(specs[\treble].default);
        var level = \level.kr(specs[\level].default);
        var mix = \mix.kr(specs[\mix].default);

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
    "Effect SynthDef 'baxandall_distortion' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)