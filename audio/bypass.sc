// shader: oscilloscope
// category: Utility
(
    var defName = \bypass;
    var specs = (
        gain: ControlSpec(0, 3, 'lin', 0.001, 0.2, "gain")  // Changed step from 0.1 to 0.001 for smooth MIDI control
    );

    var def = SynthDef(defName, {
        // Parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var gain = \gain.kr(specs[\gain].default);

        // Variables
        var sig, output_sig, mono_for_analysis;

        // Read input as stereo
        sig = In.ar(in_bus, 2);
        output_sig = sig * gain;

        // Analysis output is always a mono mix of the final output
        mono_for_analysis = Mix.ar(output_sig);
        // Main audio output
	    Out.ar(out, output_sig);

        // Dedicated mono output for analysis
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'bypass' (Stereo Test) added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
