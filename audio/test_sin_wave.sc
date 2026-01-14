// shader: oscilloscope
// category: Utility
// description: Test sine wave generator with frequency and level

(
    var defName = \test_sin_wave;
    var specs = (
        freq: ControlSpec(20, 2000, 'exp', 0, 1200, "Hz")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var freq = \freq.kr(specs[\freq].default);
        
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

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
