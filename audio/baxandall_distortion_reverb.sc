// shader: skull
(
    var defName = \baxandall_distortion_reverb;

    // 1. Define parameter specifications
    var specs = (
        drive: ControlSpec(1.0, 100.0, 'exp', 0, 10.0, "x"),
        bass: ControlSpec(-12.0, 12.0, 'lin', 0, 0.0, "dB"),
        treble: ControlSpec(-12.0, 12.0, 'lin', 0, 0.0, "dB"),
        level: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        room: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        damp: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        reverb_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    // 2. Define the SynthDef
    var def = SynthDef(defName, {
        // Parameters (NamedControl style)
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(specs[\drive].default);
        var bass = \bass.kr(specs[\bass].default);
        var treble = \treble.kr(specs[\treble].default);
        var level = \level.kr(specs[\level].default);
        var room = \room.kr(specs[\room].default);
        var damp = \damp.kr(specs[\damp].default);
        var reverb_mix = \reverb_mix.kr(specs[\reverb_mix].default);
        var mix = \mix.kr(specs[\mix].default);

        // Declare all local variables here
        var sig, dry, distortedSig, eqSig, wetDist, reverbSig, wetReverb, procSig, finalSig;
        var mono_for_analysis;

        // --- Signal Path ---
        sig = In.ar(in_bus, 2); // Read stereo input (dual-mono)
        dry = sig; // Preserve dry for final mix

        // 1. Distortion Drive
        distortedSig = (sig * drive).tanh; // Pre-gain then tanh

        // 2. Baxandall EQ Tone Control
        eqSig = BLowShelf.ar(distortedSig, 150, 0.707, bass);
        eqSig = BHiShelf.ar(eqSig, 4000, 0.707, treble);

        // 3. Output level for the distorted path
        wetDist = eqSig * level;

        // 4. Reverb processing (FreeVerb outputs stereo)
        // Use internal wet=1.0 to keep pure reverb, then blend externally with reverb_mix
        reverbSig = FreeVerb.ar(wetDist, 1.0, room, damp);
        wetReverb = XFade2.ar(wetDist, reverbSig, reverb_mix * 2 - 1);

        // 5. Dry / Wet mix
        procSig = wetReverb;
        finalSig = XFade2.ar(dry, procSig, mix * 2 - 1);

        // --- Analysis & Output ---
        mono_for_analysis = (finalSig[0] + finalSig[1]) * 0.5; // Mix to mono for analysis
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, finalSig); // Stereo output
    });
    def.add;
    "Effect SynthDef 'baxandall_distortion_reverb' added".postln;

    // 3. Register specs and create the synth
    ~setupEffect.value(defName, specs);
)