(
    SynthDef(\\hyperdrive, {
        |out = 0, in_bus = 0, analysis_out_bus,
        gain = 1.0, tone = 0.1, res = 1.37, level = 0.75, mix = 0.5|  // Simplified parameters
        var sig, distorted, mono_for_analysis;

        sig = In.ar(in_bus);
        
        // Simplified distortion chain using soft_fuzz approach
        distorted = sig + sig * gain * 10.0;  // Gain staging similar to soft_fuzz
        distorted = distorted.softclip;  // Simple softclip instead of complex distortion
        
        // MoogFF filter for tone shaping (replacing previous EQ setup)
        distorted = MoogFF.ar(
            in: distorted,
            freq: (100 + (18e3 * tone)),  // Same frequency range as soft_fuzz
            gain: res
        );
        
        distorted = distorted * level;  // Output level control
        distorted = LeakDC.ar(distorted);  // Clean up DC offset
        distorted = XFade2.ar(sig, distorted, mix*2.0-1.0);

        // Prepare mono signal for analysis
        if (distorted.isArray) { // Check if stereo (though current setup is mono)
            mono_for_analysis = Mix.ar(distorted);
        } {
            mono_for_analysis = distorted;
        };

        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [distorted, distorted]);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\\hyperdrive, [
            \\in_bus, ~input_bus,
            \\analysis_out_bus, ~effect_output_bus_for_analysis
            // Add other params if they had defaults changed or need to be set
        ], ~effectGroup);
        ("New hyperdrive synth created with analysis output bus").postln;
    };
)
