// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\distortion_with_reverb, {
        |out = 0, drive = 0.5, tone = 0.5, decay = 1, roomSize = 0.7, wetLevelDist = 0.5, wetLevelRev = 0.5|
        var sig, distorted, verb, dryDist, dryRev;
        
        sig = SoundIn.ar([0]);
        
        // Distortion effect
        distorted = (sig * drive).tanh();
        distorted = LPF.ar(distorted, tone.linexp(0, 1, 100, 20000));
        dryDist = sig * (1 - wetLevelDist);
        sig = dryDist + (distorted * wetLevelDist);
        
        // Reverb effect
        verb = FreeVerb.ar(sig, mul: decay, room: roomSize);
        dryRev = sig * (1 - wetLevelRev);
        sig = dryRev + (verb * wetLevelRev);
        
        Out.ar(out, sig);
    }).add;

    Server.default.sync;

    ~distortion_with_reverb = Synth("distortion_with_reverb");
};