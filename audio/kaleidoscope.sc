(
    SynthDef(\kaleidoscope, {
        |out = 0, in_bus = 0, sparkle = 0.5, delayTime = 0.3, feedback = 0.6, shimmer = 0.4, rotation = 0.5, mix = 0.5|
        var sig, wet, dry, phase, trig, partition;
        var chain_in, chain_out, kr_impulse;
        var rms_input, rms_output;
        var delayedSig, shiftedSig, sparkles;

        sig = In.ar(in_bus);
        dry = sig;

        // Create sparkles using resonant filters and noise
        sparkles = Mix.fill(8, {
            var freq = TRand.kr(2000, 12000, Dust.kr(sparkle * 10 + 0.1));
            var amp = LFNoise1.kr(rotation * 2).range(0, sparkle);
            Ringz.ar(Dust.ar(sparkle * 20 + 1) * 0.04, freq, 0.05) * amp;
        });

        // Shimmer delay with pitch shifting
        delayedSig = CombL.ar(sig + (sparkles * 0.3), 1.0, delayTime, feedback * 4);
        shiftedSig = PitchShift.ar(delayedSig, 0.2, 
            LFNoise1.kr(rotation).range(1.0, 1.5 + (shimmer * 0.5)), 
            shimmer * 0.2, 
            0.1
        );

        wet = Mix([delayedSig, shiftedSig * shimmer]) * 0.5;
        sig = XFade2.ar(dry, wet, mix * 2 - 1);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);

        // Write to buffers for waveform data
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // FFT Analysis
        //chain_out = FFT(~fft_buffer_out, sig, wintype: 1);
        //chain_out.do(~fft_buffer_out);

        rms_input = RunningSum.rms(dry, 1024);
        rms_output = RunningSum.rms(sig, 1024);

        // Send analysis data
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        //SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms');

        Out.ar(out, [sig, sig]);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\kaleidoscope, [\in_bus, ~input_bus], ~effectGroup);
    };
) 