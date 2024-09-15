// Ensure the server is booted before proceeding
Server.default.waitForBoot {
    
    Server.freeAll;

    SynthDef(\screaming_tube_distortion, {
        |out = 0, drive = 50, tone = 0.5, midBoost = 0.7, presence = 0.5, reverbAmount = 0.2|
        var sig, distorted, midFreq, presenceEnhance, reverb;

        sig = SoundIn.ar([0]);

        // Extremely high-gain distortion
        distorted = (sig * drive).tanh(); // Tanh for tube-like saturation

        // Mid-range boost for clarity
        midFreq = BPF.ar(distorted, 1500, rq: 1/0.7) * (midBoost * 20).dbamp; // Corrected BPF call

        // Presence enhancement
        presenceEnhance = HPF.ar(distorted, 3000, presence); // High-pass filter for presence

        // Combine all elements
        distorted = Mix([distorted, midFreq, presenceEnhance]);

        // Simple reverb for depth
        reverb = FreeVerb.ar(distorted, mix: reverbAmount);

        // Output
        Out.ar(out, reverb);
    }).add;

    Server.default.sync;

    ~screaming_tube_distortion = Synth("screaming_tube_distortion");
};