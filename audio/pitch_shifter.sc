(
    SynthDef(\pitch_shifter, {
        |out = 0, in_bus = 0, pitchShift = 1, wetLevel = 0.5|
        // START USER EFFECT CODE
        var sig, shifted, dry, finalSig;
        var phase, trig, partition;
        
        sig = In.ar(in_bus);
        
        // Pitch shifting
        shifted = PitchShift.ar(sig, pitchRatio: pitchShift);
        
        // Blend dry and wet signals
        dry = sig * (1 - wetLevel);
        finalSig = dry + (shifted * wetLevel);
        
        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer0.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(finalSig, ~relay_buffer1.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

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

        // Create new pitch_shifter synth in the effect group
        ~effect = Synth(\pitch_shifter, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)