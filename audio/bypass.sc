(
    SynthDef(\bypass, {
        |out = 0, in_bus = 0, test = 1|  // Add in_bus parameter
        // START USER EFFECT CODE

        var sig, phase, trig, partition;

        sig = In.ar(in_bus); 
        //sig = SoundIn.ar(0);
        sig = sig + SinOsc.ar(220 * test, 0, 0.1);
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
        // Wait for the SynthDef to be added to the server
        Server.default.sync;

        if(~guitarRiffSynth.notNil,{
            "Freeing existing guitar riff synth".postln;
            ~guitarRiffSynth.free;
        });

        ["input bus:", ~input_bus].postln;

        // Free existing synth if it exists
        if(~bypass.notNil, {
            "Freeing existing effect synth".postln;
            ~bypass.free;
        });

        // Create new synths
        ~bypass = Synth(\bypass, [\in_bus, ~input_bus]);
        Server.default.sync;
        ~guitarRiffSynth = Synth.before(~bypass, \playGuitarRiff);
        Server.default.sync;
        "New effect synth created".postln;

        ["Buffer 0:", ~relay_buffer0, "Buffer 1:", ~relay_buffer1].postln;
    };
)
