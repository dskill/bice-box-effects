(
    SynthDef(\monarch_synth, {
        |out = 0, in_bus = 0, analysis_out_bus, synthOctave = 0, synthFilterCutoff = 5000, synthFilterResonance = 0.2, synthDrive = 0.1, mix = 0.5|
        var sig, dry, freq, hasFreq, synthSig, filteredSig, distortedSig, finalSig, mono_for_analysis, inputAmp;

        sig = In.ar(in_bus);
        dry = sig;

        // Pitch detection
        # freq, hasFreq = Pitch.kr(
            in: sig,
            ampThreshold: 0.02,
            median: 3);

        // Get amplitude of input signal
        inputAmp = Amplitude.kr(sig);

        // Sawtooth oscillator driven by detected pitch, transposable by octaves
        // Modulate by input amplitude and only play if frequency is detected
        synthSig = Saw.ar(freq * (2.pow(synthOctave))) * hasFreq * inputAmp;

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
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new monarch_synth synth in the effect group
        ~effect = Synth(\monarch_synth, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \synthOctave, 0,
            \synthFilterCutoff, 5000,
            \synthFilterResonance, 0.2,
            \synthDrive, 0.1,
            \mix, 0.5
        ], ~effectGroup);
        ("New monarch_synth synth created with analysis output bus").postln;
    };
)