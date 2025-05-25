(
    SynthDef(\neon_love, {
        |out = 0, in_bus = 0, analysis_out_bus, decay = 1.0, roomSize = 0.7, intensity = 1.3, speed = -0.5, mix = 0.5|
        var sig, verb, dry, finalSig,
            dampedSig,
            mono_for_analysis,
            flanger, flangerTime, flangerDepth, flangerRate;

        sig = In.ar(in_bus);
        
        //////////////////////////////////////////////
        // Gentle EQ adjustments before the reverb
        //////////////////////////////////////////////

        // Reduced overall boost and frequency for a less "tizzy" high end
        dampedSig = BHiShelf.ar(sig, 6000, 0.7, 1.2);
        
        // Slightly more predelay for spaciousness
        // dampedSig = DelayN.ar(dampedSig, 0.2, predelay);
        
        //////////////////////////////////////////////
        // Main reverb with increased damping
        //////////////////////////////////////////////

        verb = FreeVerb.ar(
            dampedSig, 
            mul: decay,    // slightly less reverb multiplier
            room: roomSize * 2.0,      // keep default room size
            damp: 0.4            // increased damping for less brightness
        );
        
        //////////////////////////////////////////////
        // Secondary reverb layer, lowered volume
        //////////////////////////////////////////////

        verb = verb + (
            FreeVerb.ar(
                DelayN.ar(dampedSig, 0.03, 0.02),
                mul: decay * 0.5,
                room: roomSize * 4.0,
                damp: 0.5
            ) * 0.3  // lower blend
        );
        
        //////////////////////////////////////////////
        // Smooth compression
        //////////////////////////////////////////////

        verb = CompanderD.ar(verb, 0.4, 1, 0.5);
        
        //////////////////////////////////////////////
        // Combine dry + wet signals
        //////////////////////////////////////////////

        dry = sig * (1 - mix);
        finalSig = dry + (verb * mix);
        
        //////////////////////////////////////////////
        // Subtle low-frequency warmth + dialed-down high shelf
        //////////////////////////////////////////////

        finalSig = finalSig
          + (LPF.ar(finalSig, 300) * 0.1)
          + (HPF.ar(finalSig, 8000) * 0.05); // smaller high-end contribution

        //////////////////////////////////////////////
        // Flanger section, toned down
        //////////////////////////////////////////////

        flangerDepth = 0.008 * intensity;   // slightly reduced depth
        flangerRate  = speed;              // keep speed param
        flangerTime  = 0.004;              // slightly shorter base delay

        // CombN for flanger, lowered feedback
        flanger = CombN.ar(
            finalSig,
            0.1,
            flangerTime + SinOsc.kr(flangerRate, 0, flangerDepth, flangerDepth),
            0.2 // reduced decay for less metallic tone
        ) * 0.3; // lowered mix of the flanger

        // Mix flanger in gently, no additional amplitude boost
        finalSig = finalSig + flanger;

        // Prepare mono signal for analysis
        if (finalSig.isArray) {
            mono_for_analysis = Mix.ar(finalSig);
        } {
            mono_for_analysis = finalSig;
        };

        // Output the effect in stereo
        Out.ar(out, [finalSig, finalSig]);
        Out.ar(analysis_out_bus, mono_for_analysis); // Output mono signal for analysis
    }).add;

    "Effect SynthDef added".postln;

 
    

    // Launch the effect
    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\neon_love, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \decay, 1.0,
            \roomSize, 0.7,
            \intensity, 1.3,
            \speed, -0.5,
            \mix, 0.5
        ], ~effectGroup);
        ("New neon_love synth created with analysis output bus").postln; // Updated postln message
    };
) 