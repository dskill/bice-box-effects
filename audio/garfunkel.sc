// shader: scope
// category: Experimental
(
    var defName = \garfunkel;
    var specs = (
        decay: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "s"),
        roomSize: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        reverb: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        gain: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var decay = \decay.kr(specs[\decay].default);
        var roomSize = \roomSize.kr(specs[\roomSize].default);
        var reverb = \reverb.kr(specs[\reverb].default);
        var gain = \gain.kr(specs[\gain].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, verb_sig, dry, wet, finalSig, mono_for_analysis;
        var predelay, dampedSig, processed_sig;
        var doubled, detune, modulation;

        sig = In.ar(in_bus, 2); // Process in stereo
        dry = sig;

        // --- WET SIGNAL PATH ---
        detune = SinOsc.kr(0.5).range(0.99, 1.01);
        modulation = SinOsc.kr(0.2).range(0.005, 0.012);
        doubled = DelayC.ar(sig, 0.05, modulation) * detune;
        doubled = PitchShift.ar(doubled, 0.2, detune, 0.01, 0.01);

        processed_sig = (sig + (doubled * 0.8)) * 0.7;

        predelay = DelayN.ar(processed_sig, 0.05, 0.04);
        dampedSig = BHiShelf.ar(predelay, 8000, 1, 2);

        verb_sig = FreeVerb.ar(dampedSig,
            mul: decay * 1.5,
            room: roomSize * 1.4,
            damp: 0.2
        );
        verb_sig = verb_sig + (FreeVerb.ar(DelayN.ar(dampedSig, 0.03, 0.02),
            mul: decay * 0.8,
            room: roomSize * 1.2,
            damp: 0.3
        ) * 0.4);
        verb_sig = CompanderD.ar(verb_sig, 0.4, 1, 1/2);

        // Mix between doubled signal and reverb signal for the wet path
        wet = XFade2.ar(processed_sig, verb_sig, reverb * 2 - 1);

        // Apply final EQ shaping and gain to the wet signal
        wet = wet + (LPF.ar(wet, 300) * 0.15) + (HPF.ar(wet, 8000) * 0.1);
        wet = wet * gain;

        // --- FINAL MIX ---
        finalSig = XFade2.ar(dry, wet, mix * 2 - 1);

        // Prepare mono signal for analysis
        mono_for_analysis = Mix.ar(finalSig);

        Out.ar(out, finalSig); // finalSig is already stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'garfunkel' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)