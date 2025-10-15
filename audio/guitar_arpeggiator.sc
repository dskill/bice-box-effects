// shader: oscilloscope
(
    var defName = \guitar_arpeggiator;
    var specs = (
        start_interval: ControlSpec(1, 7, 'lin', 1, 3, ""), // Which interval to start arpeggio from
        arp_speed: ControlSpec(1, 10, 'exp', 0, 4.0, "Hz"), // Arpeggio speed
        arp_steps: ControlSpec(3, 7, 'lin', 1, 4, ""), // How many notes in arpeggio
        arp_direction: ControlSpec(0, 2, 'lin', 1, 0, ""), // 0=up, 1=down, 2=up-down
        arp_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        pitch_confidence: ControlSpec(0.7, 1.0, 'lin', 0, 0.9, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var start_interval = \start_interval.kr(specs[\start_interval].default);
        var arp_speed = \arp_speed.kr(specs[\arp_speed].default);
        var arp_steps = \arp_steps.kr(specs[\arp_steps].default);
        var arp_direction = \arp_direction.kr(specs[\arp_direction].default);
        var arp_mix = \arp_mix.kr(specs[\arp_mix].default);
        var pitch_confidence = \pitch_confidence.kr(specs[\pitch_confidence].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, freq, hasFreq, note_num, base_note, scale_degree, arp_clock, arp_step, current_degree, current_note, arp_freq, arp_sig, arp_trigger, arp_env, processed, mono_for_analysis;

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
        
        // Map to C major scale degrees
        scale_degree = Select.kr(base_note.round, [
            0, 0, 1, 2, 2, 3, 4, 4, 5, 5, 6, 6
        ]);

        // Simple arpeggio step counter
        arp_clock = Stepper.kr(Impulse.kr(arp_speed), 0, 0, arp_steps - 1, 1);

        // Apply direction
        arp_step = Select.kr(arp_direction, [
            arp_clock,                    // Up
            (arp_steps - 1) - arp_clock,  // Down
            arp_clock                     // Up-down (simplified to up for now)
        ]);

        // Calculate current scale degree for arpeggio
        current_degree = (scale_degree + start_interval - 1 + arp_step) % 7;

        // Map to semitones
        current_note = Select.kr(current_degree, [0, 2, 4, 5, 7, 9, 11]);

        // Calculate frequency
        arp_freq = (note_num.floor - (note_num.floor % 12) + current_note).midicps;

        // Envelope for each arp note
        arp_trigger = Impulse.kr(arp_speed);
        arp_env = EnvGen.ar(Env.perc(0.01, 0.2), arp_trigger);

        // Generate arpeggio
        arp_sig = SinOsc.ar(arp_freq) * arp_env;
        arp_sig = arp_sig * hasFreq * (pitch_confidence * 2 - 1).max(0);
        
        // Mix
        processed = dry + (arp_sig * arp_mix);
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'guitar_arpeggiator' added".postln;

    ~setupEffect.value(defName, specs);
)