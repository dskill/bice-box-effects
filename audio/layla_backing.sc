// shader: oscilloscope
(
    var defName = \layla_backing;
    
    var specs = (
        // Tempo control
        tempo: ControlSpec(60, 180, 'lin', 0, 120, "BPM"),
        // Chord duration multiplier
        chord_duration: ControlSpec(0.5, 4.0, 'lin', 0, 1.0, "x"),
        // Overall volume
        amp: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        // Filter frequency for tone shaping
        filter_freq: ControlSpec(200, 8000, 'exp', 0, 2000, "Hz"),
        // Wave type: 0=sine, 1=saw, 2=square
        wave_type: ControlSpec(0, 2, 'lin', 1, 1, ""),
        // Attack time for each chord
        attack: ControlSpec(0.001, 0.5, 'exp', 0, 0.01, "s"),
        // Release time for each chord
        release: ControlSpec(0.01, 2.0, 'exp', 0, 0.1, "s"),
        // Progression selector: 0=main riff, 1=verse
        progression: ControlSpec(0, 1, 'lin', 1, 0, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        // ALL VARIABLE DECLARATIONS MUST BE AT THE TOP
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var tempo = \tempo.kr(specs[\tempo].default);
        var chord_duration = \chord_duration.kr(specs[\chord_duration].default);
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var wave_type = \wave_type.kr(specs[\wave_type].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var progression = \progression.kr(specs[\progression].default);
        var mix = \mix.kr(specs[\mix].default);
        var sig, dry, beat_duration, chord_trigger, chord_index;
        var chord_freqs, chord_notes, chord_signal, final_sig, mono_for_analysis;
        var main_riff, verse_prog;
        
        // Define chord progressions
        // Main riff: Dm - Bb - C - Dm (MIDI notes)
        main_riff = [
            [62, 65, 69], // Dm: D=62, F=65, A=69
            [58, 62, 65], // Bb: Bb=58, D=62, F=65  
            [60, 64, 67], // C: C=60, E=64, G=67
            [62, 65, 69]  // Dm: D=62, F=65, A=69
        ];
        
        // Verse: C#m - G#m - A - B - E - A - B - C#m (MIDI notes)
        verse_prog = [
            [61, 64, 68], // C#m: C#=61, E=64, G#=68
            [56, 59, 63], // G#m: G#=56, B=59, D#=63
            [57, 61, 64], // A: A=57, C#=61, E=64
            [59, 63, 66], // B: B=59, D#=63, F#=66
            [52, 56, 59], // E: E=52, G#=56, B=59
            [57, 61, 64], // A: A=57, C#=61, E=64
            [59, 63, 66], // B: B=59, D#=63, F#=66
            [61, 64, 68]  // C#m: C#=61, E=64, G#=68
        ];
        
        // Get input signal
        sig = In.ar(in_bus);
        dry = sig;
        
        // Convert BPM to beat duration
        beat_duration = 60 / tempo * chord_duration;
        
        // Create chord progression trigger
        chord_trigger = Impulse.kr(1 / beat_duration);
        
        // Select progression and get chord index
        chord_index = Select.kr(progression, [
            PulseCount.kr(chord_trigger) % 4,  // Main riff (4 chords)
            PulseCount.kr(chord_trigger) % 8   // Verse (8 chords)
        ]);
        
        // Get current chord notes
        chord_notes = Select.kr(progression, [
            Select.kr(chord_index, main_riff),
            Select.kr(chord_index, verse_prog)
        ]);
        
        // Convert MIDI notes to frequencies and create chord
        chord_freqs = chord_notes.midicps;
        
        // Create chord signal with envelope
        chord_signal = Mix.ar(
            chord_freqs.collect({ |freq|
                var env, wave;
                env = EnvGen.ar(
                    Env.perc(attack, release),
                    chord_trigger
                );
                wave = Select.ar(wave_type, [
                    SinOsc.ar(freq),    // 0 = sine
                    Saw.ar(freq),       // 1 = saw  
                    Pulse.ar(freq, 0.5) // 2 = square
                ]);
                wave * env;
            })
        );
        
        // Apply filter and amplitude
        chord_signal = RLPF.ar(chord_signal, filter_freq, 0.3) * amp;
        
        // Mix with dry signal
        final_sig = XFade2.ar(dry, chord_signal, mix * 2 - 1);
        
        // Analysis output (mono)
        mono_for_analysis = final_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        
        // Final stereo output
        Out.ar(out, [final_sig, final_sig]);
    });
    def.add;
    "Effect SynthDef 'layla_backing' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)