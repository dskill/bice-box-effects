(
    SynthDef(\test_sin_wave, {
        |out = 0, in_bus = 0, freq = 50|
        // START USER EFFECT CODE
        var sig, final_sig;
        var phase, trig, partition;

        sig = In.ar(in_bus);
        final_sig = SinOsc.ar(freq) * 0.2;
        
        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(final_sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

        Out.ar(out, final_sig);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new test_sin_wave synth in the effect group
        ~effect = Synth(\test_sin_wave, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)