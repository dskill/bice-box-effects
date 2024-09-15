// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\bypass, {
        |out = 0|
        var sig;
        sig = SoundIn.ar([0]);
        Out.ar(out, sig);
    }).add;

    // Use Server.default.sync instead of s.sync
    Server.default.sync;

    // Create the Synth after synchronization
    ~bypass = Synth("bypass");
};