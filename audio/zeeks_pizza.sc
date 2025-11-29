// category: Experimental
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
        var depth = \depth.kr(specs[\depth].default); // Depth of phaser effect
        // Amplitude modulation
        var modRate = \modRate.kr(specs[\modRate].default); // Speed of AM
        var modDepth = \modDepth.kr(specs[\modDepth].default); // Depth of AM
        // Mix
        var mix = \mix.kr(specs[\mix].default);

        var input, numStages = 6, freq = 100, modPhase, phaser, output, mono_for_analysis;
        
        input = In.ar(in_bus); // Assuming mono input
        
        // Create phaser effect
        modPhase = SinOsc.kr(rate, 0, depth * 800, 1000 + freq);
        phaser = input;
        numStages.do {
            phaser = AllpassL.ar(phaser, 0.1, modPhase.reciprocal, 0);
        };
        
        // Add amplitude modulation
        output = phaser * (1 - (modDepth * SinOsc.kr(modRate)));
        
        // Mix dry and wet signals
        output = (input * (1 - mix)) + (output * mix);

        // Prepare mono signal for analysis
        // Assuming 'output' is mono here
        mono_for_analysis = output;
          
        Out.ar(out, [output, output]); // Output mono 'output' as stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'zeeks_pizza' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
) 