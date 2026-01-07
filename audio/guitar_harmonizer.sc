// shader: oscilloscope
// category: Experimental
(
    var defName = \guitar_harmonizer;
    var specs = (
        harmony_interval: ControlSpec(2, 7, 'lin', 1, 3, ""), // 2=second, 3=third, 5=fifth, etc.
        harmony_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%"),
        pitch_confidence: ControlSpec(0.7, 1.0, 'lin', 0, 0.9, ""),
        transpose: ControlSpec(-12, 12, 'lin', 1, 0, "semi"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var harmony_interval = \harmony_interval.kr(specs[\harmony_interval].default);
        var harmony_mix = \harmony_mix.kr(specs[\harmony_mix].default);
        var pitch_confidence = \pitch_confidence.kr(specs[\pitch_confidence].default);
        var transpose = \transpose.kr(specs[\transpose].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, freq, hasFreq, note_num, base_note, scale_degree, harmony_note, harmony_freq, harmony_sig, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        // Pitch detection
        # freq, hasFreq = Pitch.kr(sig, 
            initFreq: 110,
            minFreq: 60,
            maxFreq: 1500,
            execFreq: 100,
            maxBinsPerOctave: 16,
            median: 7,
            ampThreshold: 0.01,
            peakThreshold: 0.5,
            downSample: 1
        );

        note_num = freq.cpsmidi;
        base_note = note_num % 12;
        
        // Map chromatic notes to C major scale degrees (0=C, 1=D, 2=E, 3=F, 4=G, 5=A, 6=B)
        scale_degree = Select.kr(base_note.round, [
            0, // C -> C (degree 0)
            0, // C# -> C (degree 0)
            1, // D -> D (degree 1)
            2, // D# -> E (degree 2)
            2, // E -> E (degree 2)
            3, // F -> F (degree 3)
            4, // F# -> G (degree 4)
            4, // G -> G (degree 4)
            5, // G# -> A (degree 5)
            5, // A -> A (degree 5)
            6, // A# -> B (degree 6)
            6  // B -> B (degree 6)
        ]);

        // Calculate harmony scale degree (with wrap-around)
        harmony_note = (scale_degree + harmony_interval - 1) % 7;

        // Map scale degrees back to semitones in C major
        // C=0, D=2, E=4, F=5, G=7, A=9, B=11
        harmony_note = Select.kr(harmony_note, [0, 2, 4, 5, 7, 9, 11]);

        // Calculate harmony frequency (preserve octave relationship)
        harmony_freq = (note_num.floor - (note_num.floor % 12) + harmony_note + 
                       ((harmony_note < (note_num.floor % 12)).if(12, 0)) + transpose).midicps;

        // Generate harmony signal
        harmony_sig = SinOsc.ar(harmony_freq);
        harmony_sig = harmony_sig * hasFreq * (pitch_confidence * 2 - 1).max(0);
        
        // Mix signals
        processed = dry + (harmony_sig * harmony_mix);
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'guitar_harmonizer' added".postln;

    ~setupEffect.value(defName, specs);
)