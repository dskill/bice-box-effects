(
    var defName = \monarch_synth;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var synthFilterCutoff = \synthFilterCutoff.kr(1802);
        var synthFilterResonance = \synthFilterResonance.kr(0.75);
        var synthDrive = \synthDrive.kr(0.7);
        var mix = \mix.kr(0.5);
        var subOctaveMix = \subOctaveMix.kr(0.5);
        
        var sig, dry, freq, hasFreq, mainSynthSig, subOctaveSynthSig, synthSig, filteredSig, distortedSig, finalSig, mono_for_analysis, inputAmp;

        sig = In.ar(in_bus);
        dry = sig;

        // Pitch detection
        # freq, hasFreq = Pitch.kr(
            in: sig,
            ampThreshold: 0.02,
            median: 3);

        // Get amplitude of input signal
        inputAmp = Amplitude.kr(sig);

        // Main sawtooth oscillator
        mainSynthSig = Saw.ar(freq) * hasFreq * inputAmp;

        // Sub-octave sawtooth oscillator
        subOctaveSynthSig = Saw.ar(freq * 0.5) * hasFreq * inputAmp;

        // Mix main and sub-octave signals
        synthSig = XFade2.ar(mainSynthSig, subOctaveSynthSig, subOctaveMix * 2 - 1);

        // Low-pass filter
        filteredSig = MoogFF.ar(
            in: synthSig,
            freq: synthFilterCutoff,
            gain: 1.0,
            res: synthFilterResonance
        );
        
        // Distortion (analog-style saturation)
        distortedSig = (filteredSig * (1 + synthDrive * 20)).tanh;

        // Mix dry and wet signals
        finalSig = XFade2.ar(dry, distortedSig, mix * 2 - 1);

        // Prepare mono signal for analysis
        // Assuming finalSig is mono here based on its construction.
        mono_for_analysis = finalSig;

        Out.ar(out, [finalSig, finalSig]); // Output mono finalSig as stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'monarch_synth' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        synthFilterCutoff: ControlSpec(100, 8000, 'exp', 0, 1802, "Hz"),
        synthFilterResonance: ControlSpec(0.0, 4.0, 'lin', 0, 0.75, "Q"),
        synthDrive: ControlSpec(0.0, 2.0, 'lin', 0, 0.7, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        subOctaveMix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new monarch_synth synth in the effect group
        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)