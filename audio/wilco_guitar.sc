// shader: oscilloscope
// category: Experimental
(
    var defName = \wilco_guitar;
    var specs = (
        warmth: ControlSpec(0.1, 3.0, 'exp', 0, 1.2, "x"),
        presence: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%"), 
        compression: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        ambience: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, "%"),
        chorus: ControlSpec(0.0, 1.0, 'lin', 0, 0.15, "%"),
        sparkle: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        drum_level: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        kick_punch: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "x"),
        snare_snap: ControlSpec(0.1, 3.0, 'exp', 0, 1.2, "x"),
        chord_level: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr(0);
        var warmth = \warmth.kr(specs[\warmth].default);
        var presence = \presence.kr(specs[\presence].default);
        var compression = \compression.kr(specs[\compression].default);
        var ambience = \ambience.kr(specs[\ambience].default);
        var chorus = \chorus.kr(specs[\chorus].default);
        var sparkle = \sparkle.kr(specs[\sparkle].default);
        var drum_level = \drum_level.kr(specs[\drum_level].default);
        var kick_punch = \kick_punch.kr(specs[\kick_punch].default);
        var snare_snap = \snare_snap.kr(specs[\snare_snap].default);
        var chord_level = \chord_level.kr(specs[\chord_level].default);
        var mix = \mix.kr(specs[\mix].default);
        
        // All variables declared in single block
        var sig, dry, processed, compressed, shaped, chorused_left, chorused_right, reverbed, mono_for_analysis;
        var kick, snare, hihat, drums, tempo, beat;
        var chord_seq, chord_freq, chord_synth, chord_env;

        sig = In.ar(in_bus);
        dry = sig;
        
        // Gentle tube-style saturation for warmth
        processed = (sig * warmth).tanh * 0.8;
        
        // Soft compression for sustain and evenness
        compressed = Compander.ar(processed, processed, 
            thresh: -12.dbamp, 
            slopeBelow: 1.0, 
            slopeAbove: 0.3 + (compression * 0.4),
            clampTime: 0.01, 
            relaxTime: 0.1
        );
        
        // EQ shaping for indie rock character
        // Slight low-mid cut, presence boost, gentle high rolloff
        shaped = BLowShelf.ar(compressed, 200, 1.0, -2);      // Clean up low end
        shaped = BPeakEQ.ar(shaped, 800, 0.8, -3);            // Reduce muddiness
        shaped = BPeakEQ.ar(shaped, 2500, 0.6, presence * 8); // Presence boost
        shaped = BHiShelf.ar(shaped, 8000, 1.0, sparkle * 6 - 3); // Adjustable highs
        
        // Subtle chorus for dimension (mono becomes stereo here)
        chorused_left = DelayC.ar(shaped + (SinOsc.ar(0.7, 0, 0.003 * chorus)), 0.02, 
                      0.008 + SinOsc.ar(0.7, 0, 0.003 * chorus));
        chorused_right = DelayC.ar(shaped + (SinOsc.ar(0.53, 0, 0.003 * chorus)), 0.02, 
                      0.012 + SinOsc.ar(0.53, 0, 0.003 * chorus));
        
        // Gentle ambience reverb
        reverbed = FreeVerb2.ar(chorused_left, chorused_right, 
            mix: ambience * 0.5, 
            room: 0.6, 
            damp: 0.8
        );
        
        // Drum synthesis at 103 BPM (Jesus Etc tempo)
        tempo = 103/60; // BPM to Hz
        beat = Impulse.ar(tempo); // Quarter note pulse
        
        // Kick drum (on beats 1 and 3)
        kick = (
            SinOsc.ar(60 * kick_punch, 0, 0.8) + 
            SinOsc.ar(45, 0, 0.4)
        ) * EnvGen.ar(Env.perc(0.01, 0.3), Impulse.ar(tempo)) * 0.6;
        
        // Snare drum (on beats 2 and 4)
        snare = (
            WhiteNoise.ar(0.5) + 
            SinOsc.ar(200 * snare_snap, 0, 0.3)
        ) * EnvGen.ar(Env.perc(0.01, 0.15), Impulse.ar(tempo, 0.5)) * 0.4;
        
        // Hi-hat (8th notes)
        hihat = WhiteNoise.ar(0.2) * 
                EnvGen.ar(Env.perc(0.01, 0.08), Impulse.ar(tempo * 2)) * 0.15;
        
        // Mix drums
        drums = (kick + snare + hihat) * drum_level;
        
        // Chord progression: Am-F-C-G (4 beats per chord)
        chord_seq = Stepper.ar(
            Impulse.ar(tempo/4), // trigger every 4 beats
            0, // reset
            0, // min
            3, // max (4 chords: 0,1,2,3)
            1  // step
        );
        
        // Root frequencies for Am-F-C-G progression 
        chord_freq = Select.ar(chord_seq, [
            110.0,  // Am (A2)
            87.3,   // F (F2) 
            98.0,   // C (C3)
            98.0    // G (G2)
        ]);
        
        // Chord envelope (sustained)
        chord_env = EnvGen.ar(
            Env.new([0, 1, 0.9, 0], [0.2, 3.0, 0.8], 'lin'),
            Impulse.ar(tempo/4)
        );
        
        // Simple chord synthesis (root + fifth)
        chord_synth = (
            SinOsc.ar(chord_freq, 0, 0.3) +          // Root
            SinOsc.ar(chord_freq * 1.5, 0, 0.2)     // Fifth
        ) * chord_env * chord_level;
        
        // Mix wet/dry guitar with drums and chords
        processed = XFade2.ar([dry, dry], reverbed, mix * 2 - 1) + [drums, drums] + [chord_synth, chord_synth];
        
        // Analysis output (mono)
        mono_for_analysis = Mix.ar(processed);
        Out.ar(analysis_out_bus, mono_for_analysis);
        
        // Stereo output
        Out.ar(out, processed);
    });
    def.add;
    "Effect SynthDef 'wilco_guitar' added".postln;

    ~setupEffect.value(defName, specs);
)