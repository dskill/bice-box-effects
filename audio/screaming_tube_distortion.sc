(
    SynthDef(\screaming_tube_distortion, {
        |out = 0, in_bus = 0, drive = 50, tone = 0.5, midBoost = 0.7, presence = 0.5, reverbAmount = 0.2|
        // START USER EFFECT CODE
        var sig, distorted, midFreq, presenceEnhance, reverb;
        var phase, trig, partition;

        sig = In.ar(in_bus);

        distorted = (sig * drive).tanh();
        midFreq = BPF.ar(distorted, 1500, rq: 1/0.7) * (midBoost * 20).dbamp;
        presenceEnhance = HPF.ar(distorted, 3000, presence);
        distorted = Mix([distorted, midFreq, presenceEnhance]);
        reverb = FreeVerb.ar(distorted, mix: reverbAmount);

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer0.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(reverb, ~relay_buffer1.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

        Out.ar(out, reverb);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new screaming_tube_distortion synth in the effect group
        ~effect = Synth(\screaming_tube_distortion, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)