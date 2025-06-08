// shader: oscilloscope
(
    var defName = \granular_delay;
    var specs = (
        grain_size: ControlSpec(0.01, 0.2, 'exp', 0, 0.05, "s"),
        density: ControlSpec(1, 50, 'exp', 0, 10, "Hz"),
        delay_time: ControlSpec(0.01, 2.0, 'lin', 0, 0.5, "s"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.5, "%"),
        pitch_shift_semitones: ControlSpec(0, 12, 'lin', 0, 7, "st"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var grain_size = \grain_size.kr(specs[\grain_size].default); /* Grain duration in seconds (10ms to 200ms) */
        var density = \density.kr(specs[\density].default);    /* Grains per second (1 to 50) */
        var delay_time = \delay_time.kr(specs[\delay_time].default);  /* Delay time in seconds (0ms to 2000ms) */
        var feedback = \feedback.kr(specs[\feedback].default);    /* Feedback amount (0 to 0.95) */
        var pitch_shift_semitones = \pitch_shift_semitones.kr(specs[\pitch_shift_semitones].default); /* Max random pitch shift in semitones (0 to 12) */
        var mix = \mix.kr(specs[\mix].default);          /* Wet/Dry mix (0 to 1.0) */

        var sig, dry_sig, wet_sig, final_sig, buf, max_delay_time, write_head_samples,
            current_write_pos_secs, grain_read_pos_secs_unwrapped, grain_read_pos_secs,
            grain_trig, grain_pitch_ratio, grain_pan_pos, feedback_sig_in,
            signal_to_write_to_buffer, mono_for_analysis;

        // --- Main Signal Input ---
        sig = In.ar([in_bus,in_bus]); // sig is stereo. It will be mixed down for the mono buffer.
        dry_sig = sig;

        // --- Buffer Setup ---
        max_delay_time = 2.0; // Maximum possible delay, defines buffer size
        // Buffer for 1 channel of audio, up to max_delay_time seconds long
        buf = LocalBuf.new(s.sampleRate * max_delay_time, 1);

        // --- Feedback Path ---
        // Stereo feedback signal from TGrains output, initialized to 0
        feedback_sig_in = LocalIn.ar(2, 0.0);

        // Mix input signal with mono-mixed feedback signal
        // The feedback signal is stereo from TGrains, mix it down to mono before adding to input
        signal_to_write_to_buffer = sig + (((feedback_sig_in[0] + feedback_sig_in[1]) * 0.5) * feedback);
        signal_to_write_to_buffer = signal_to_write_to_buffer.tanh; // Clip before writing to buffer to prevent runaway feedback

        // --- Buffer Writing (INTERNAL TO EFFECT LOGIC - THIS IS OK) ---
        // Phasor to drive the write head position in samples, wraps around the buffer
        write_head_samples = Phasor.ar(0, 1, 0, BufFrames.kr(buf));
        BufWr.ar(signal_to_write_to_buffer, buf, write_head_samples);

        // --- Granular Synthesis Read Position ---
        // Current write position in seconds
        current_write_pos_secs = write_head_samples / BufSampleRate.kr(buf);
        // Calculate the base read position for grains, delayed from write position
        grain_read_pos_secs_unwrapped = current_write_pos_secs - delay_time;
        // Wrap the read position within the buffer's duration (0 to max_delay_time)
        grain_read_pos_secs = Wrap.ar(grain_read_pos_secs_unwrapped, 0, max_delay_time);

        // --- Granular Synthesis (TGrains) ---
        // Trigger for initiating new grains
        grain_trig = Impulse.ar(density);

        // Random pitch shift per grain.
        // TRand generates a new random value (semitone shift) for each trigger.
        // .midiratio converts semitone shift to a playback rate ratio.
        // If pitch_shift_semitones is 0, range is 0 to 0, so midiratio is 1.0 (no shift).
        grain_pitch_ratio = TRand.ar(0 - pitch_shift_semitones, pitch_shift_semitones, grain_trig).midiratio;

        // Random panning position for each grain (-0.8 to +0.8 for some stereo width)
        grain_pan_pos = TRand.ar(-0.8, 0.8, grain_trig);

        // Generate a stereo granular stream using TGrains.
        // TGrains arguments: numChannels, trigger, bufnum, rate, centerPos (in sec), dur (in sec), pan, amp
        // Amplitude is scaled by sqrt of density to help maintain similar overall loudness with varying density.
        wet_sig = TGrains.ar(
            numChannels: 2, // Output stereo grains
            trigger: grain_trig,
            bufnum: buf,
            rate: grain_pitch_ratio,
            centerPos: grain_read_pos_secs,
            dur: grain_size,
            pan: grain_pan_pos,
            amp: 0.7 / density.sqrt // Ensure density > 0 (guaranteed by param range)
        );

        // --- Output Feedback Signal ---
        LocalOut.ar(wet_sig); // Send the wet_sig (output of TGrains) to the LocalIn for the feedback loop

        // --- Wet/Dry Mix ---
        // dry_sig is stereo, wet_sig is stereo.
        final_sig = XFade2.ar(dry_sig, wet_sig, mix * 2.0 - 1.0);

        // --- Prepare mono signal for analysis ---
        mono_for_analysis = Mix.ar(final_sig);

        Out.ar(out, final_sig); // Output the final stereo signal
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'granular_delay' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)