// shader: oscilloscope
(
    var defName = \guitar_harmony_synth;
    var specs = (
        key: ControlSpec(0, 11, 'lin', 1, 0, ""), // 0=C, 1=C#/Db, 2=D, 3=D#/Eb, 4=E, 5=F, 6=F#/Gb, 7=G, 8=G#/Ab, 9=A, 10=A#/Bb, 11=B
        harmony_interval: ControlSpec(2, 7, 'lin', 1, 3, ""), // 2=second, 3=third, 5=fifth, etc.
        wave_type: ControlSpec(0, 3, 'lin', 1, 1, ""), // 0=sine, 1=saw, 2=square, 3=tri
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.05, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.2, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        release: ControlSpec(0.001, 4.0, 'exp', 0, 0.8, "s"),
        filter_freq: ControlSpec(200, 8000, 'exp', 0, 2000, "Hz"),
        trigger_threshold: ControlSpec(0.01, 0.3, 'exp', 0, 0.05, ""),
        synth_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var key = \key.kr(specs[\key].default);
        var harmony_interval = \harmony_interval.kr(specs[\harmony_interval].default);
        var wave_type = \wave_type.kr(specs[\wave_type].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var trigger_threshold = \trigger_threshold.kr(specs[\trigger_threshold].default);
        var synth_mix = \synth_mix.kr(specs[\synth_mix].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, freq, hasFreq, note_num, base_note, scale_degree, harmony_semitone, harmony_freq, amplitude, trigger, gate, env, wave, synth_sig, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        // Pitch detection
        # freq, hasFreq = Pitch.kr(sig, 
            initFreq: 220,
            minFreq: 80,
            maxFreq: 1000,
            execFreq: 50,
            maxBinsPerOctave: 12,
            median: 1,
            ampThreshold: 0.02,
            peakThreshold: 0.5,
            downSample: 1
        );

        note_num = freq.cpsmidi.clip(48, 84);
        base_note = (note_num % 12).round;
        
        // Quantize input to selected key's major scale
        scale_degree = Select.kr(base_note, [
            0, 0, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6  // C major scale mapping
        ]);
        
        // Calculate harmony interval in semitones for major scale
        harmony_semitone = Select.kr(harmony_interval - 2, [
            2,  // 2nd = major second (2 semitones)
            4,  // 3rd = major third (4 semitones) 
            5,  // 4th = perfect fourth (5 semitones)
            7,  // 5th = perfect fifth (7 semitones)
            9,  // 6th = major sixth (9 semitones)
            11  // 7th = major seventh (11 semitones)
        ]);
        
        // Add harmony interval and transpose to selected key
        harmony_freq = (60 + key + harmony_semitone).midicps;

        // Trigger
        amplitude = Amplitude.kr(sig, 0.1, 0.1);
        trigger = Trig1.kr(amplitude > trigger_threshold, 0.1);
        gate = trigger * hasFreq;

        // Envelope
        env = EnvGen.kr(Env.adsr(attack, decay, sustain, release), gate);

        // Oscillator
        wave = Select.ar(wave_type, [
            SinOsc.ar(harmony_freq),
            Saw.ar(harmony_freq),
            Pulse.ar(harmony_freq, 0.5),
            LFTri.ar(harmony_freq)
        ]);

        // Apply envelope
        synth_sig = wave * env * 0.3;

        // Simple filter
        synth_sig = LPF.ar(synth_sig, filter_freq);

        // Mix
        processed = dry + (synth_sig * synth_mix);
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'guitar_harmony_synth' added".postln;

    ~setupEffect.value(defName, specs);
)