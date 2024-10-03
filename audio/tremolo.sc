(
    SynthDef(\tremolo, {
        |out = 0, in_bus = 0, rate = 2, depth = 0.5, wetLevel = 0.5|
        // START USER EFFECT CODE
        var sig, trem, dry, finalSig;
        var phase, trig, partition, kr_impulse;

        sig = In.ar(in_bus);
        trem = sig * (depth * SinOsc.kr(rate) + (1 - depth));
        dry = sig * (1 - wetLevel);
        finalSig = dry + (trem * wetLevel);

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate

        Out.ar(out, finalSig);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new tremolo synth in the effect group
        ~effect = Synth(\tremolo, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)