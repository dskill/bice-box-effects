// Pizza Phaser Effect
// Combines phaser with amplitude modulation for a "hungry to full" cycle effect
(
SynthDef(\zeeks_pizza, {
    arg out=0, in_bus=0, analysis_out_bus,
    // Phaser parameters
    rate=0.5, // Speed of phaser oscillation
    depth=0.5, // Depth of phaser effect
    // Amplitude modulation
    modRate=0.2, // Speed of AM
    modDepth=0.3, // Depth of AM
    // Mix
    mix=0.5;

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
}).add;

// Add execution code

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\zeeks_pizza, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \rate, 0.5,
            \depth, 0.5,
            \modRate, 0.2,
            \modDepth, 0.3,
            \mix, 0.5
        ], ~effectGroup);
        ("New zeeks_pizza synth created with analysis output bus").postln;
    };
) 