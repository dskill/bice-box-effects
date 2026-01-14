// shader: oscilloscope
// category: Experimental
// description: Phaser and AM blend for a hungry-to-full cycle
// Pizza Phaser Effect
// Combines phaser with amplitude modulation for a "hungry to full" cycle effect
(
    var defName = \zeeks_pizza;
    var specs = (
        rate: ControlSpec(0.01, 5.0, 'exp', 0, 0.5, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        modRate: ControlSpec(0.01, 2.0, 'exp', 0, 0.2, "Hz"),
        modDepth: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        // Phaser parameters
        var rate = \rate.kr(specs[\rate].default); // Speed of phaser oscillation
        var depth = \depth.kr(specs[\depth].default); // Depth of phaser modulation
        // Amplitude modulation parameters
        var modRate = \modRate.kr(specs[\modRate].default); // Speed of amplitude modulation
        var modDepth = \modDepth.kr(specs[\modDepth].default); // Depth of amplitude modulation
        var mix = \mix.kr(specs[\mix].default); // Mix between dry and wet
        
        var sig, dry, phased, modulated, final, mono_for_analysis;
        var phaser, lfo, modulator;
        
        // Input signal
        sig = In.ar(in_bus); // Mono input
        dry = sig;

        // Phaser effect: modulated all-pass filters
        lfo = SinOsc.kr(rate, 0).range(0.001, 0.01) * depth;
        phaser = sig;
        4.do({
            phaser = AllpassC.ar(phaser, 0.01, lfo, 0.2);
        });

        // Amplitude modulation
        modulator = SinOsc.kr(modRate, 0).range(1.0 - modDepth, 1.0 + modDepth);
        modulated = phaser * modulator;

        // Mix dry and wet
        final = XFade2.ar(dry, modulated, mix * 2 - 1);

        // Analysis output
        mono_for_analysis = final;
        Out.ar(analysis_out_bus, mono_for_analysis);

        // Output stereo
        Out.ar(out, [final, final]);
    });
    def.add;
    "Effect SynthDef 'zeeks_pizza' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
