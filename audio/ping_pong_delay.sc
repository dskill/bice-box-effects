(
    SynthDef(\ping_pong_delay, {
        |out = 0, in_bus = 0, delayTime = 0.4, feedback = 0.5, wetLevel = 0.5, gain = 1|
        // START USER EFFECT CODE
        var sig, leftDelay, rightDelay, delaySig, dry, fbNode, finalSig, kr_impulse;
        var phase, trig, partition;
        var rms_input, rms_output;

        sig = In.ar(in_bus, 2);
        fbNode = LocalIn.ar(2);
        leftDelay = DelayC.ar(sig[0] + fbNode[1], 2, delayTime);
        rightDelay = DelayC.ar(sig[1] + fbNode[0], 2, delayTime);
        LocalOut.ar([leftDelay, rightDelay] * feedback);
        delaySig = [leftDelay, rightDelay];
        dry = sig;
        delaySig = delaySig * gain + dry;
        finalSig = dry.blend(dry + delaySig, wetLevel);


        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Calculate RMS values
        rms_input = RunningSum.rms(sig[0], 1024);
        rms_output = RunningSum.rms((finalSig[0] + finalSig[1]) * 0.5, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig[0], ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar((finalSig[0] + finalSig[1]) * 0.5, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/rms');

        Out.ar(out, [delaySig,delaySig]);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new ping_pong_delay synth in the effect group
        ~effect = Synth(\ping_pong_delay, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)