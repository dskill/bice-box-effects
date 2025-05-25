(
    SynthDef(\test_sin_wave, {
        |out = 0, in_bus = 0, analysis_out_bus, freq = 50|
        // START USER EFFECT CODE
        var sig, final_sig, mono_for_analysis;

        sig = In.ar(in_bus); 
        final_sig = SinOsc.ar(freq) * 0.2;
        
        // END USER EFFECT CODE

        // Prepare mono signal for analysis
        mono_for_analysis = final_sig; // final_sig is already mono

        Out.ar(out, [final_sig, final_sig]);
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

        // Create new test_sin_wave synth in the effect group
        ~effect = Synth(\test_sin_wave, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \freq, 50
        ], ~effectGroup);
        ("New test_sin_wave synth created with analysis output bus").postln;
    };
)