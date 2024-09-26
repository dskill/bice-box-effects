(
    SynthDef(\bypass, {
        |out = 0, in_bus = 0, test = 1|  // Add in_bus parameter
        // START USER EFFECT CODE

        var sig, phase, trig, partition;

        sig = In.ar(in_bus);
        //sig = SoundIn.ar(0);
       // sig = sig + SinOsc.ar(220 * test, 0, 0.5);
        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer0.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer1.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

	Out.ar(out, sig);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new bypass synth in the effect group
        ~effect = Synth(\bypass, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)
