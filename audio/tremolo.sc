// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\tremolo, {
        |out = 0, rate = 2, depth = 0.5, wetLevel = 0.5|
        var sig, trem, dry;
        sig = SoundIn.ar([0]);
        trem = sig * (depth * SinOsc.kr(rate) + (1 - depth));
        dry = sig * (1 - wetLevel);
        sig = dry + (trem * wetLevel);
        Out.ar(out, sig);
    }).add;

    Server.default.sync;

    ~tremolo = Synth("tremolo");
};