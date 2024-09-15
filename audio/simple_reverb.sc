// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\simple_reverb, {
        |out = 0, decay = 1, roomSize = 0.7, wetLevel = 0.5, gain = 1|
        var sig, verb, dry, finalSig;
        sig = SoundIn.ar([0]);
        verb = FreeVerb.ar(sig, mul: decay, room: roomSize);
        dry = sig * (1 - wetLevel);
        finalSig = (dry + (verb * wetLevel)) * gain;
        Out.ar(out, finalSig);
    }).add;

    // Use Server.default.sync instead of s.sync
    Server.default.sync;

    // Create the Synth after synchronization
    ~simple_reverb = Synth("simple_reverb");
};