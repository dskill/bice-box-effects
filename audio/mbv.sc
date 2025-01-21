(
    SynthDef(\mbv, {
        |out = 0, in_bus = 0, 
        gain = 0.5, tone = 0.5, res = 1.37, level = 0.75, reverse_verb = 0.0, mix = 0.5,
        pitch_rate = 0.1, pitch_depth = 0.01|
        
        var sig, processed;
        var rms_input, rms_output;
        var phase, trig, partition, kr_impulse;
        var freq, hasFreq;
        var reverb_buffer, reverse_sig, reverse_verb_sig;
        var pitch_mod;

        sig = In.ar(in_bus);
        
        // Add slow pitch oscillation
        pitch_mod = SinOsc.kr(pitch_rate).range(1 - pitch_depth, 1 + pitch_depth);
        sig = PitchShift.ar(sig, 0.2, pitch_mod);
        
        // Pre-emphasis filter to boost mids before distortion
        sig = BPF.ar(sig, 800, 2.0, 2.0) + sig;
        
        // Gain stage with asymmetrical soft clipping
        processed = sig * (gain * 400 + 1);
        processed = Select.ar(processed > 0, [
            processed * 0.8,  // Negative values get less gain
            processed        // Positive values get full gain
        ]);
        processed = processed.softclip;
        
        // Tone control using MoogFF
        processed = MoogFF.ar(
            in: processed,
            freq: 100 + (tone * 8000),  // Tone sweeps from 100Hz to 8.1kHz
            gain: res
        );

        // Additional filtering for character
        processed = BPeakEQ.ar(processed, 1200, 0.5, 3); // Mid presence boost
        processed = BHiShelf.ar(processed, 3000, 1.0, 2); // High end sparkle

        // Create a local buffer for reverse reverb
        reverb_buffer = LocalBuf(SampleRate.ir * 0.75); // 1 second buffer
        
        // Continuously record into the buffer
        RecordBuf.ar(processed, reverb_buffer, loop: 1); // Set loop to 1 for continuous recording

        // Play back the buffer in reverse
        reverse_sig = PlayBuf.ar(1, reverb_buffer, rate: -1, loop: 1, startPos: BufFrames.kr(reverb_buffer));

        // Apply reverb to the reversed signal
        reverse_verb_sig = FreeVerb.ar(reverse_sig, mix: 1, room: 1.25, damp: 0.5);
        reverse_verb_sig = reverse_verb_sig + reverse_sig;
        
        // Mix in the reverse reverb
        processed = processed + (reverse_verb_sig * reverse_verb);
        
        // Level control and final shaping
        processed = processed * level * 0.8;
        processed = LeakDC.ar(processed);
        //processed = reverse_verb_sig;
        // Final mix
        processed = XFade2.ar(sig, processed, mix*2.0-1.0);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Calculate RMS values
        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(processed, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        // Write to buffers for waveform data
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(processed, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // Send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/rms');

        Out.ar(out, [processed, processed]);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new synth in the effect group
        ~effect = Synth(\mbv, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
) 