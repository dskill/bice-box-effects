(
    SynthDef(\oscilloscope, {
        |out = 0, in_bus = 0, analysis_out_bus|
        var sig, mono_for_analysis;
        // Removed: phase, trig, partition, chain_in, chain_out, kr_impulse, rms_input, rms_output

        sig = In.ar(in_bus);

        // Prepare mono signal for analysis
        // Assuming sig from In.ar(in_bus) might be stereo, or Mix.ar is safe for mono too.
        mono_for_analysis = Mix.ar(sig);

        // Removed old analysis machinery

        Out.ar(out, sig); // Output original signal (stereo or mono as it came from in_bus)
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;

    "Effect SynthDef added (oscilloscope - passthrough for analysis)".postln;

    fork {
        s.sync;

        if(~effect.notNil) {
            "Freeing existing effect synth (oscilloscope - passthrough)".postln;
            ~effect.free;
        };

        ~effect = Synth(\oscilloscope, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New oscilloscope (passthrough) synth created with analysis output bus").postln;
    };
)
