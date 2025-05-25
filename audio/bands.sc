(
    SynthDef(\bands, {
        |out = 0, in_bus = 0, analysis_out_bus, drive = 10, tone = 0.5, mix = 1.0|
        var sig, distorted, mono_for_analysis;

        sig = In.ar(in_bus);
        
        // Apply soft clipping and add some even harmonics
        // Adjust drive scaling if needed for more subtle or aggressive distortion
        distorted = (sig * drive).clip2(0.8);  
        distorted = (distorted * 0.8) + (distorted.squared * 0.2);

        // Add tone shaping: low shelf, mid peak, and high shelf adjustments
        // These ranges and amounts can be tweaked to taste.
        distorted = BLowShelf.ar(distorted, 400, 1.0, tone * -24);   // Bass attenuation/boost
        distorted = BPeakEQ.ar(distorted, 1200, 0.5, tone * 12);     // Mid presence
        distorted = BHiShelf.ar(distorted, 3200, 1.0, tone * -6);    // High attenuation/boost

        // Blend original and distorted signals
        distorted = (distorted * mix) + (sig * (1 - mix));

        // Remove DC offset that might be introduced
        distorted = LeakDC.ar(distorted);

        // Prepare mono signal for analysis
        mono_for_analysis = distorted;

        Out.ar(out, [distorted, distorted]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\bands, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \drive, 10,
            \tone, 0.5,
            \mix, 1.0
        ], ~effectGroup);
        ("New bands synth created with analysis output bus").postln;
    };
) 