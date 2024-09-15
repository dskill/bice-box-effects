// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\flanger, {
        |out = 0, rate = 0.25, depth = 0.1, feedback = 0.5, delayTime = 0.005|
        var sig, flange, mod;
        sig = SoundIn.ar([0]);
        mod = SinOsc.kr(rate, 0, depth, delayTime);
        flange = CombL.ar(sig, 0.1, mod, feedback);
        Out.ar(out, flange);
    }).add;

    Server.default.sync;

    ~flanger = Synth("flanger");
};