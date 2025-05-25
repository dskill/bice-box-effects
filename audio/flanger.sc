(
    SynthDef(\flanger, {
        |out = 0, in_bus = 0, analysis_out_bus, rate = 0.5, depth = 0.002, feedback = 0.5, center = 0.005, mix = 0.5|
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
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(\flanger, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \rate, 0.5,
            \depth, 0.002,
            \feedback, 0.5,
            \center, 0.005,
            \mix, 0.5
        ], ~effectGroup);
        ("New flanger synth created with analysis output bus").postln;
    };
)