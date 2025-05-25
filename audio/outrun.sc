(
    SynthDef(\outrun, {
        |out = 0, in_bus = 0, analysis_out_bus, drive = 1.0, gridSpeed = 0.5, sunSize = 0.5, glow = 0.7, synthDepth = 0.5, mix = 0.5|
        var sig, dry, chorus, filtered, mono_for_analysis;
        
        sig = In.ar(in_bus);
        dry = sig;
        
        // Multi-voice chorus for that 80s width
        chorus = Mix.fill(3, {|i|
            var rate = gridSpeed * (i + 1) * 0.5;
            DelayC.ar(sig, 0.05, SinOsc.kr(rate, 0, 0.002 * synthDepth, 0.003))
        }) / 3;

        // Simplified chorus feedback. Original was more complex and used main 'mix' param internally.
        LocalOut.ar(chorus);
        chorus = chorus + (LocalIn.ar(1) * 0.8); 

        chorus = FreeVerb.ar(chorus,
            mix: 0.8,        // This is reverb's internal mix
            room: 10.8,       
            damp: 0.2        
        );
        
        // Frequency shaping
        filtered = BLowShelf.ar(chorus, 400, 1.0, 4.0);
        filtered = BPeakEQ.ar(filtered, 1200, 1.0, 3.0);
        filtered = BHiShelf.ar(filtered, 3000, 1.0, glow * 6.0);
        
        // Final mix (main dry/wet)
        sig = XFade2.ar(dry, filtered, mix * 2 - 1);

        // Prepare mono signal for analysis
        if (sig.isArray) {
            mono_for_analysis = Mix.ar(sig);
        } {
            mono_for_analysis = sig; // Path is likely mono here
        };
                
        Out.ar(out, Pan2.ar(sig)); // Outputting mono 'sig' as stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;
    
    "Effect SynthDef added".postln;
    
    fork {
        s.sync;
        
        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };
        
        ~effect = Synth(\outrun, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \drive, 1.0, 
            \gridSpeed, 0.5, 
            \sunSize, 0.5, 
            \glow, 0.7, 
            \synthDepth, 0.5, 
            \mix, 0.5
        ], ~effectGroup);
        ("New outrun synth created with analysis output bus").postln;
    };
) 