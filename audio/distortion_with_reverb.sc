(
    SynthDef(\distortion_with_reverb, {
        |out = 0, in_bus = 0, drive = 0.5, tone = 0.5, decay = 1, roomSize = 0.7, wetLevelDist = 0.5, wetLevelRev = 0.5|
        // START USER EFFECT CODE
        var sig, distorted, verb, dryDist, dryRev, phase, trig, partition;
        
        sig = In.ar(in_bus);
        
        // Distortion effect
        distorted = (sig * drive).tanh();
        distorted = LPF.ar(distorted, tone.linexp(0, 1, 100, 20000));
        dryDist = sig * (1 - wetLevelDist);
        sig = dryDist + (distorted * wetLevelDist);
        
        // Reverb effect
        verb = FreeVerb.ar(sig, mul: decay, room: roomSize);
        dryRev = sig * (1 - wetLevelRev);
        sig = dryRev + (verb * wetLevelRev);
        
        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(In.ar(in_bus), ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

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

        // Create new distortion_with_reverb synth in the effect group
        ~effect = Synth(\distortion_with_reverb, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)