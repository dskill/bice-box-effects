// shader: scope
(
    var defName = \garfunkel;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var decay = \decay.kr(1);
        var roomSize = \roomSize.kr(0.7);
        var wetLevel = \wetLevel.kr(0.5);
        var gain = \gain.kr(1);
        
        var sig, verb, dry, finalSig, mono_for_analysis;
        var predelay, dampedSig;
        var doubled, detune, modulation;

        sig = In.ar(in_bus);
        
        detune = SinOsc.kr(0.5).range(0.99, 1.01);
        modulation = SinOsc.kr(0.2).range(0.005, 0.012);
        doubled = DelayC.ar(sig, 0.05, modulation) * detune;
        doubled = PitchShift.ar(doubled, 0.2, detune, 0.01, 0.01);
        
        sig = (sig + (doubled * 0.8)) * 0.7;
        
        predelay = DelayN.ar(sig, 0.05, 0.04);
        dampedSig = BHiShelf.ar(predelay, 8000, 1, 2);
        
        verb = FreeVerb.ar(dampedSig, 
            mul: decay * 1.5,
            room: roomSize * 1.4,
            damp: 0.2
        );
        verb = verb + (FreeVerb.ar(DelayN.ar(dampedSig, 0.03, 0.02),
            mul: decay * 0.8,
            room: roomSize * 1.2,
            damp: 0.3
        ) * 0.4);
        verb = CompanderD.ar(verb, 0.4, 1, 1/2);
        
        dry = sig * (1 - wetLevel);
        finalSig = (dry + (verb * wetLevel)) * gain;
        finalSig = finalSig + (LPF.ar(finalSig, 300) * 0.15) + (HPF.ar(finalSig, 8000) * 0.1);

        // Prepare mono signal for analysis
        mono_for_analysis = Mix.ar(finalSig);

        Out.ar(out, [finalSig,finalSig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'garfunkel' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        decay: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "s"),
        roomSize: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        wetLevel: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        gain: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "x")
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