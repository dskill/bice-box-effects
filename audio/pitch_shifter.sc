// shader: oscilloscope
(
    var defName = \pitch_shifter;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var pitchShift = \pitchShift.kr(1);
        var wetLevel = \wetLevel.kr(0.5);
        
        var sig, shifted, dry, finalSig, mono_for_analysis;
        
        sig = In.ar(in_bus);
        
        shifted = PitchShift.ar(sig, pitchRatio: pitchShift);
        
        dry = sig * (1 - wetLevel);
        finalSig = dry + (shifted * wetLevel);
        
        mono_for_analysis = Mix.ar(finalSig);

        Out.ar(out, [finalSig,finalSig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'pitch_shifter' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        pitchShift: ControlSpec(0.25, 4.0, 'exp', 0, 1.0, "x"),
        wetLevel: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

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