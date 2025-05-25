(
    SynthDef(\pitch_shifter, {
        |out = 0, in_bus = 0, analysis_out_bus, pitchShift = 1, wetLevel = 0.5|
        var sig, shifted, dry, finalSig, mono_for_analysis;
        
        sig = In.ar(in_bus);
        
        shifted = PitchShift.ar(sig, pitchRatio: pitchShift);
        
        dry = sig * (1 - wetLevel);
        finalSig = dry + (shifted * wetLevel);
        
        mono_for_analysis = finalSig;

        Out.ar(out, [finalSig,finalSig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(\pitch_shifter, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \pitchShift, 1,
            \wetLevel, 0.5
        ], ~effectGroup);
        ("New pitch_shifter synth created with analysis output bus").postln;
    };
)