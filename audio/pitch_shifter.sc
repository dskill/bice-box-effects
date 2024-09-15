// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\pitch_shifter, {
        |out = 0, pitchShift = 1, wetLevel = 0.5|
        var sig, shifted, dry;
        
        sig = SoundIn.ar([0]);
        
        // Pitch shifting
        shifted = PitchShift.ar(sig, pitchRatio: pitchShift);
        
        // Blend dry and wet signals
        dry = sig * (1 - wetLevel);
        sig = dry + (shifted * wetLevel);
        
        Out.ar(out, sig);
    }).add;

    Server.default.sync;

    ~pitch_shifter = Synth("pitch_shifter");
};