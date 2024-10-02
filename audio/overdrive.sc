(
    SynthDef(\overdrive, {
        |out = 0, in_bus = 0, drive = 100.5, tone = 0.5|
        // START USER EFFECT CODE
        var sig, distorted, phase, trig, partition;

        sig = In.ar(in_bus);
        distorted = (sig * drive).tanh();
        distorted = LPF.ar(distorted, 10*tone.linexp(0, 1, 100, 20000));

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(distorted, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

        Out.ar(out, distorted);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

	    ~effect = Synth(\overdrive, [\in_bus, ~input_bus], ~effectGroup);
    };
)
