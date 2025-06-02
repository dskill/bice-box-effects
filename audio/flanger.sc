(
    var defName = \flanger;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(0.5);
        var depth = \depth.kr(0.002);
        var feedback = \feedback.kr(0.5);
        var center = \center.kr(0.005);
        var mix = \mix.kr(0.5);
        
        var sig, flange, mod, wet, final_sig, mono_for_analysis;

        sig = In.ar(in_bus);
        mod = SinOsc.kr(rate).range(center - depth, center + depth);
        
        flange = DelayC.ar(sig + (LocalIn.ar(1) * feedback), 0.02, mod);
        LocalOut.ar(flange);
        
        wet = flange;
        final_sig = XFade2.ar(sig, wet, mix * 2 - 1);

        mono_for_analysis = final_sig;
                
        Out.ar(out, [final_sig,final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'flanger' added".postln;
    
    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        rate: ControlSpec(0.01, 0.1, 'exp', 0, 0.5, "Hz"),
        depth: ControlSpec(0.0001, 0.01, 'lin', 0, 0.002, "s"),
        feedback: ControlSpec(0.0, 0.99, 'lin', 0, 0.5, "%"),
        center: ControlSpec(0.0001, 0.02, 'lin', 0, 0.005, "s"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    // Existing logic to create the synth instance
    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)