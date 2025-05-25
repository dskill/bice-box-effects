(
    SynthDef(\bypass, {
        |out = 0, in_bus = 0, analysis_out_bus, test = 1|  // ADD analysis_out_bus argument
        // START USER EFFECT CODE

        var sig, mono_for_analysis;
        // var freq; // Uncomment if using test signal below

        sig = In.ar(in_bus);
        mono_for_analysis = Mix.ar(sig);
        
        // // Example test signal (ensure mono_for_analysis gets this if used)
        // freq = LFTri.kr(1/10, 0).range(82.41, 82.41*2);  
        // sig = SinOsc.ar(freq, 0, 0.5);
        // mono_for_analysis = sig; // If using the sine wave for testing

        // END USER EFFECT CODE

        // Main audio output (can be stereo or mono depending on 'sig')
	    Out.ar(out, sig);

        // Dedicated mono output for analysis by masterAnalyser
        Out.ar(analysis_out_bus, mono_for_analysis);

        // All internal BufWr, FFT, RunningSum, SendReply for /combined_data are REMOVED.
        // ~masterAnalyser in init.sc now handles all of that.

    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new bypass synth in the effect group
        // Ensure analysis_out_bus argument is passed correctly from init.sc when this effect is chosen
        ~effect = Synth(\bypass, [
            \in_bus, ~input_bus, 
            \analysis_out_bus, ~effect_output_bus_for_analysis.index // Ensure this global var is set in init.sc
        ], ~effectGroup);
        "New bypass effect synth created with analysis output bus".postln; // MODIFIED postln
    };
)
