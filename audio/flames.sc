(
    SynthDef(\flames, {
        |out = 0, in_bus = 0, analysis_out_bus,
        gain = 1.0, tone = 0.1, res = 1.37, flameVol = 0.75, mix = 0.5|  // Simplified parameters
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

    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new flames synth in the effect group
        ~effect = Synth(\flames, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis // Corrected: use bus object
            // Pass other params if needed, e.g., gain: 1.0 etc.
        ], ~effectGroup);
        "New flames effect synth created with analysis output bus".postln;
    };
) 