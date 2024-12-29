(
    SynthDef(\vocorotator, {
        |out = 0, in_bus = 0, shift = 1.0, feedback = 0.9, mix=0.5|
        var sig, phase, trig, partition;
        var chain, kr_impulse;
        var from, to, rot_index;
        var rms_input, rms_output;
        var fb_sig, out_sig;

        sig = In.ar(in_bus);
        // Create a local buffer for feedback
        fb_sig = LocalIn.ar(1);

        // Mix input with feedback
        out_sig = (sig + (fb_sig * feedback));

       // in = PlayBuf.ar(1, c, BufRateScale.kr(c), loop: 1);
        chain = FFT(LocalBuf(2048), out_sig);

        // pvcalc gets compiled at runtime, so the inputs must be constants
        /*
        from = 0;
        to = 30;
        rot_index = 1;
        chain = chain.pvcalc(2048, {|mags, phases| 
            //rot_index = (K2A.ar(rotation) * 1023).round.asInteger;
            [mags[rot_index..] ++ mags[..rot_index-1], phases[rot_index..] ++ phases[..rot_index-1]];
        }, frombin: from, tobin: to, zeroothers: 1);
        */
        
        chain = PV_BinShift(chain, 
            stretch: 1.0,  // Keep original scaling
            shift: shift      // Shift by 30 bins (equivalent to previous rot_index)
        );
        
        out_sig = IFFT(chain);

        // send feedback before final mix and low pass filter
        LocalOut.ar(out_sig);

        // add a low pass filter
        out_sig = LPF.ar(out_sig, 3000);
        // final mix
        //out_sig = out_sig * mix +  sig * (1.0 - mix);
        //out_sig = out_sig + sig * mix;
        out_sig = XFade2.ar(sig, out_sig, mix);

        FFT(~fft_buffer_out, out_sig, wintype: 1);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Write to buffers for waveform data
        BufWr.ar(out_sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(out_sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(sig, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms'); 

        Out.ar(out, [out_sig, out_sig]);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\vocorotator, [\in_bus, ~input_bus], ~effectGroup);
    };
)