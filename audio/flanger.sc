// shader: scope
// category: Modulation
(
    var defName = \flanger;
    var specs = (
        rate: ControlSpec(0.01, 0.1, 'exp', 0, 0.5, "Hz"),
        depth: ControlSpec(0.0001, 0.01, 'lin', 0, 0.002, "s"),
        feedback: ControlSpec(0.0, 0.99, 'lin', 0, 0.5, "%"),
        center: ControlSpec(0.0001, 0.02, 'lin', 0, 0.005, "s"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var center = \center.kr(specs[\center].default);
        var mix = \mix.kr(specs[\mix].default);
        
        var sig, dry, flange, mod, wet, final_sig, mono_for_analysis;

        sig = In.ar(in_bus); // Sums stereo to mono
        dry = sig;

        mod = SinOsc.kr(rate).range(center - depth, center + depth);
        
        flange = DelayC.ar(sig + (LocalIn.ar(1) * feedback), 0.02, mod);
        LocalOut.ar(flange);
        
        wet = flange;
        final_sig = XFade2.ar(dry, wet, mix * 2 - 1);

        // Analysis output - signal is already mono
        mono_for_analysis = final_sig;
                
        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'flanger' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)