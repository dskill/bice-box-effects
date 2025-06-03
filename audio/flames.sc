(
    var defName = \flames;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var gain = \gain.kr(1.0);
        var tone = \tone.kr(0.1);
        var res = \res.kr(1.37);
        var flameVol = \flameVol.kr(0.75);
        var mix = \mix.kr(0.5);
        
        var sig, distorted, flameSig, mono_for_analysis;
        var freq, hasFreq;

        sig = In.ar(in_bus); // Assuming in_bus is stereo
        
        // Simplified distortion chain using soft_fuzz approach
        distorted = sig + sig * (gain + 0.1) * 40.0;  // Gain staging similar to soft_fuzz
        distorted = distorted.softclip;  // Simple softclip instead of complex distortion
        
        // MoogFF filter for tone shaping (replacing previous EQ setup)
        distorted = MoogFF.ar(
            in: distorted,
            freq: (100 + (10800 * tone)),  // Same frequency range as soft_fuzz
            gain: res
        );
        
        distorted = LeakDC.ar(distorted);  // Clean up DC offset

        // ---------------------
        // Add a "burning flame" noise
        // ---------------------
        // RMS calculation for flameSig internal logic (not for global RMS reporting)
        // This uses a local RMS of the input to modulate the flame effect, which is fine.
        # freq, hasFreq = Pitch.kr(
					in: sig,
					ampThreshold: 0.02,
					median: 2);

        // Create a more aggressive, fiery sound with sawtooth and noise components
        flameSig = Select.ar(hasFreq, [
            // When no pitch detected (hasFreq == 0), only use noise
            PinkNoise.ar(0.3),
            
            // When pitch is detected (hasFreq == 1), use full flame sound
            (
                LFSaw.ar(freq * 0.5) * 0.7 + // Base sawtooth wave
                LFSaw.ar(freq * 0.502) * 0.3 + // Slightly detuned saw for thickness
                PinkNoise.ar(0.4) // Add some noise for crackling
            )
        ]) * RunningSum.rms(Mix.ar(sig), 256) * 30.0; // Using a local RMS for effect modulation
        
        // Multi-band filtering for a more complex flame character
        flameSig = BPF.ar(flameSig, [100, 400, 1200], [0.5, 0.7, 0.8]).sum;
        // Add some random amplitude modulation for crackling
        flameSig = flameSig * (LFNoise2.kr(5).range(0.1, 1.0));

        // Add reverb to the flame sound
        flameSig = flameSig * 0.5 + FreeVerb.ar(
            in: flameSig,
            mix: 0.7,        // 40% wet signal
            room: 2.6,       // Medium room size
            damp: 0.9        // Light dampening
        );
        
        // Adjust overall flame volume
        flameSig = flameSig * flameVol * 2.0;
        
        // Combine flame sound with distorted output
        // If distorted is stereo and flameSig is mono, flameSig will be added to both channels.
        distorted =  distorted + flameSig;

        distorted = XFade2.ar(sig, distorted, mix*2.0-1.0);

        // END USER EFFECT CODE

        // Prepare mono version of final signal for masterAnalyser
        // Assuming 'distorted' is stereo at this point
        mono_for_analysis = Mix.ar(distorted);

        // Output for masterAnalyser
        Out.ar(analysis_out_bus, mono_for_analysis);

        Out.ar(out, distorted); // Output stereo

    });
    def.add;
    "Effect SynthDef 'flames' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        gain: ControlSpec(0.1, 5.0, 'exp', 0, 1.0, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.1, "%"),
        res: ControlSpec(0.1, 4.0, 'exp', 0, 1.37, "x"),
        flameVol: ControlSpec(0.0, 2.0, 'lin', 0, 0.75, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new flames synth in the effect group
        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
) 