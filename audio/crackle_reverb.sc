(
    SynthDef(\crackle_reverb, {
        |out = 0, in_bus = 0, mix = 0.5, room = 0.7, dist_amount = 0.5, dist_hardness = 2.0|
        var sig, dry, reverb_sig, reverb_channels, reverb_amp, norm_reverb_amp;
        var max_dist_gain_val, distortion_drive_mod, distorted_reverb_eff;
        var crackle_level, crackle_noise, wet_sig, final_sig;
        var phase, trig, partition, kr_impulse;
        var rms_input, rms_output;
        var fft_chain, fft_output_sig;

        // Get input signal
        sig = In.ar(in_bus); // Assumes mono input, or stereo summed to mono by In.ar
        dry = sig;

        // --- Start Effect Processing ---

        // 1. Reverb
        // FreeVerb outputs stereo. We want full wet signal here for processing.
        // 'room' (0..1) controls room size, 'damp' (0..1) controls high-frequency damping.
        reverb_sig = FreeVerb.ar(sig, 1.0, room, 0.5);
        reverb_channels = Pan2.ar(reverb_sig); // Split stereo UGen into an array of [L, R] channels

        // 2. Create distortion ramp based on reverb amplitude
        // Get amplitude of the reverb tail (it's stereo, so take average of channels' absolute values for Amplitude.kr)
        reverb_amp = Amplitude.kr((reverb_channels[0].abs + reverb_channels[1].abs) * 0.5);
        // Normalize reverb_amp to roughly 0..1 range.
        // Amplitude.kr output depends on input signal level. Clipping ensures it's within 0..1.
        norm_reverb_amp = reverb_amp.clip(0, 1.0);

        // As norm_reverb_amp (reverb loudness) decreases, (1 - norm_reverb_amp) increases.
        // This increasing value drives the distortion.
        // 'dist_hardness' controls the curve of this ramp (higher values = sharper ramp).
        // 'dist_amount' controls the overall intensity of distortion.
        max_dist_gain_val = dist_amount * 20.0; // dist_amount (0..1) maps to a gain factor up to 20x for distortion
        distortion_drive_mod = (1 - norm_reverb_amp).pow(dist_hardness) * max_dist_gain_val;

        // 3. Apply distortion to reverb signal
        // Increase drive based on distortion_drive_mod and apply tanh saturation
        distorted_reverb_eff = (reverb_sig * (1 + distortion_drive_mod)).tanh;

        // 4. Add crackle noise, also driven by the ramp and distortion amount
        // 'crackle_level' determines the amplitude of the noise.
        crackle_level = (1 - norm_reverb_amp).pow(dist_hardness) * dist_amount;
        // PinkNoise.ar outputs values in -1 to 1. Scale it. Use stereo noise.
        crackle_noise = PinkNoise.ar([1.0, 1.0]) * crackle_level * 0.3; // Adjust 0.3 for desired crackle audibility

        // Combine distorted reverb with crackle noise
        wet_sig = distorted_reverb_eff + crackle_noise;
        wet_sig = wet_sig.clip(-1,1); // Clip final wet signal to prevent extreme levels if crackle sum is too high

        // 5. Mix dry signal with processed wet signal
        // XFade2.ar(inA, inB, pan) where pan is -1 (A) through 0 (A+B) to 1 (B)
        // dry is mono, wet_sig is stereo. Expand dry to stereo for XFade2.
        final_sig = XFade2.ar([dry, dry], wet_sig, mix * 2.0 - 1.0);

        // --- End Effect Processing ---

        // --- FFT Analysis for Visualization ---
        // Perform FFT on the final output signal (mono version for analysis)
        fft_output_sig = (final_sig[0] + final_sig[1]) * 0.5; // Convert stereo to mono for FFT
        fft_chain = FFT(~fft_buffer_out, fft_output_sig);
        fft_chain.do(~fft_buffer_out); // This line was missing - it actually writes the FFT data to the buffer
        // The FFT buffer is automatically updated by the FFT UGen

        // --- Standard Machinery for GUI ---
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60); // Standard rate for GUI updates

        // RMS calculation
        // Input RMS (using mono sig)
        rms_input = RunningSum.rms(sig, 1024);
        // Output RMS (using stereo final_sig, so average channels)
        rms_output = RunningSum.rms((final_sig[0] + final_sig[1]) * 0.5, 1024);

        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        // Buffer writing for waveform display
        // Input waveform (using mono sig)
        //BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        // Output waveform (using stereo final_sig, so average channels)
        BufWr.ar((final_sig[0] + final_sig[1]) * 0.5, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // Send notifications to GUI
        //SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        //SendReply.kr(kr_impulse, '/rms');
        SendReply.kr(kr_impulse, '/combined_data', partition);

	    Out.ar(out, final_sig); // Output the final stereo signal
    }).add;

    "CrackleReverb SynthDef added".postln;

    fork {
        s.sync;
        if(~effect.notNil, {
            "Freeing existing CrackleReverb synth".postln;
            ~effect.free;
        });
        ~effect = Synth(\crackle_reverb, [\in_bus, ~input_bus], ~effectGroup);
        "New CrackleReverb synth created".postln;
    };
)