(
    SynthDef(\overdrive, {
        |out = 0, in_bus = 0, drive = 10, tone = 0.5, mix = 1.0|
        var sig, distorted, phase, trig, partition;
        var chain_in, chain_out, kr_impulse;
        var fft_output, fft_input;
        var rms_input, rms_output;

        sig = In.ar(in_bus);

        // Apply soft clipping and add some even harmonics
        // Adjust drive scaling if needed for more subtle or aggressive distortion
        distorted = (sig * drive).clip2(0.8);  
        distorted = (distorted * 0.8) + (distorted.squared * 0.2);

        // Add tone shaping: low shelf, mid peak, and high shelf adjustments
        // These ranges and amounts can be tweaked to taste.
        distorted = BLowShelf.ar(distorted, 400, 1.0, tone * -24);   // Bass attenuation/boost
        distorted = BPeakEQ.ar(distorted, 1200, 0.5, tone * 12);     // Mid presence
        distorted = BHiShelf.ar(distorted, 3200, 1.0, tone * -6);    // High attenuation/boost

        // Blend original and distorted signals
        distorted = (distorted * mix) + (sig * (1 - mix));

        // Remove DC offset that might be introduced
        distorted = LeakDC.ar(distorted);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Write to buffers for waveform data
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(distorted, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

         // FFT
        //chain_out = FFT(~fft_buffer_out, distorted, wintype: 1);
        //chain_out.do(~fft_buffer_out);

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(distorted, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        //SendReply.kr(kr_impulse, '/fft_data');
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

        ~effect = Synth(\overdrive, [\in_bus, ~input_bus], ~effectGroup);
    };
)
