// shader: oscilloscope

(
    var defName = \test_sin_wave;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var freq = \freq.kr(50);
        
        // START USER EFFECT CODE
        var sig, final_sig, mono_for_analysis;

        sig = In.ar(in_bus); 
        final_sig = SinOsc.ar(freq) * 0.2;
        
        // END USER EFFECT CODE

        // Prepare mono signal for analysis
        mono_for_analysis = final_sig; // final_sig is already mono

        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'test_sin_wave' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        freq: ControlSpec(20, 2000, 'exp', 0, 50, "Hz")
    ));

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new test_sin_wave synth in the effect group
        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)