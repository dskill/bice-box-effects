// shader: oscilloscope
(
    var defName = \bypass;
    var def = SynthDef(defName, {
        // Parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var channel_mode = \channel_mode.kr(0); // 0: Stereo, 1: Left, 2: Right, 3: Swap

        // Variables
        var sig, left, right, output_sig, mono_for_analysis;

        // Read input as stereo
        sig = In.ar(in_bus, 2);
        left = sig[0];
        right = sig[1];

        // Select output based on channel_mode
        output_sig = Select.ar(channel_mode, [
            [left, right],   // 0: Normal Stereo
            [left, left],    // 1: Left channel on both outputs
            [right, right],  // 2: Right channel on both outputs
            [right, left]    // 3: Swapped channels
        ]);

        // Analysis output is always a mono mix of the final output
        mono_for_analysis = Mix.ar(output_sig);

        // Main audio output
	    Out.ar(out, right);

        // Dedicated mono output for analysis
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'bypass' (Stereo Test) added".postln;

    // Register parameter specifications
    ~registerEffectSpecs.value(defName, (
        channel_mode: ControlSpec(0, 3, 'lin', 1, 0, "mode")
    ));

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new bypass synth in the effect group
        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)
