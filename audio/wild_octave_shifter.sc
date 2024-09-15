// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\wild_octave_shifter, {
        |out = 0, bpm = 120, reverbDecay = 1, reverbRoomSize = 0.7, wetLevel = 0.5|
        var sig, shifted, verb, dry, clock, pitchShift;

        sig = SoundIn.ar([0]);

        // Clock for quarter note timing based on BPM
        clock = Impulse.kr(bpm / 60 / 4);

        // Alternating pitch shift between one octave up and down
        pitchShift = Demand.kr(clock, 0, Dseq([4, 1, 2, 4], inf));
        shifted = PitchShift.ar(sig, pitchRatio: pitchShift);

        // Reverb effect
        verb = FreeVerb.ar(shifted, mul: reverbDecay, room: reverbRoomSize);

        // Blend dry and wet signals
        dry = sig * (1 - wetLevel);
        sig = dry + (verb * wetLevel);

        Out.ar(out, sig);
    }).add;

    Server.default.sync;

    ~wild_octave_shifter = Synth("wild_octave_shifter");
};