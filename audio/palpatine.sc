(
    SynthDef(\palpatine, {
        |out = 0, in_bus = 0, drive = 50, tone = 2000, mix = 1.0, 
        reverb = 0.3, delay = 0.25, delay_mix = 0.2, feedback = 0.5|
        var sig, distorted, filtered, wet, delayed, phase, trig, partition;
        var chain_in, chain_out, kr_impulse;
        var fft_output, fft_input;
        var rms_input, rms_output;
        var mod_freq = LFNoise1.kr(0.5).range(4, 8); // Modulation frequency for "electric" character
        var mod = SinOsc.ar(mod_freq) * 0.3; // Modulation amount

        sig = In.ar(in_bus);
         
        // Force Lightning Fuzz effect
        distorted = sig * drive;
        distorted = distorted.tanh; // Soft clipping
        
        // Add some "electric" character with frequency modulation
        distorted = distorted * (1 + mod);
        
        // Multi-stage distortion for more aggressive sound
        distorted = (distorted * 2).tanh * 0.5;
        distorted = (distorted * 3).clip2(0.7);
        
        // Tone control with resonant filter
        filtered = RLPF.ar(distorted, tone, 0.7);
        
        // Delay with modulated time and feedback for "sparking" effect
        delayed = LocalIn.ar(1) * feedback;  // Add feedback from previous delay
        delayed = delayed + filtered;  // Mix with input signal
        delayed = DelayL.ar(delayed, 1.0, 
            (LFNoise2.kr(10.2)*0.001 + delay)  // Slightly modulate delay time
        );
        LocalOut.ar(delayed);  // Send delayed signal back for feedback
        
        filtered = (filtered * (1 - delay_mix)) + (delayed * delay_mix);
        
        // Reverb with pre-delay for "chamber" effect
        wet = DelayN.ar(filtered, 0.03, 0.03);
        wet = FreeVerb.ar(wet, reverb, 0.8, 0.2);

        // Blend original and processed signals
        distorted = (wet * mix) + (sig * (1 - mix));

        // Remove DC offset
        distorted = LeakDC.ar(distorted);
        
        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Write to buffers for waveform data
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(distorted, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(distorted, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/rms'); 

        Out.ar(out, [distorted, distorted]);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\palpatine, [\in_bus, ~input_bus], ~effectGroup);
    };
) 