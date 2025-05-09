(
    SynthDef(\spectral_freezing_delay, {
        |out = 0, in_bus = 0, delayTime = 0.5, feedback = 0.5, freezeTrig = 0, freezeMix = 0.5, mix = 0.5|
        var sig, delayed, frozen, phase, trig, partition, kr_impulse;
        var rms_input, rms_output, fbNode;
        var buf, freezePhase, freq;

        // Allocate a buffer for the frozen sound
        buf = LocalBuf.new(2048, 2);

        sig = In.ar(in_bus, 2);
        fbNode = LocalIn.ar(2);

        delayed = DelayC.ar(sig + fbNode, 2, delayTime);
        LocalOut.ar(delayed * feedback);
        
        // Freeze section
        freezePhase = Phasor.ar(freezeTrig, BufRateScale.kr(buf), 0, buf.numFrames);
        frozen = BufRd.ar(2, buf, freezePhase);

        // Record into the freeze buffer when freezeTrig is 1
        RecordBuf.ar(sig, buf, freezeTrig, loop: 1);


        // Mix the delayed and frozen signals
        sig = XFade2.ar(delayed, frozen, freezeMix * 2 - 1);

        // Overall mix control
        sig = XFade2.ar(In.ar(in_bus, 2), sig, mix * 2 - 1);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Calculate RMS values
        rms_input = RunningSum.rms(In.ar(in_bus), 1024);
        rms_output = RunningSum.rms(sig, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(In.ar(in_bus), ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/rms');

        Out.ar(out, sig);
    }).add;

    "SpectralFreezingDelay added".postln;

    fork {
        s.sync;
        if(~effect.notNil, { ~effect.free; });
        ~effect = Synth(\spectral_freezing_delay, [\in_bus, ~input_bus], ~effectGroup);
    };
)