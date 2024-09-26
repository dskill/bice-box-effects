(
    SynthDef(\screaming_tube_distortion, {
        |out = 0, in_bus = 0, drive = 20, tone = 0.5, midBoost = 0.7, presence = 0.5, reverbAmount = 0.2,
        feedbackAmount = 0.15, bitCrushAmount = 0.8, stereoWidth = 0.5, pitchShiftRatio = 1|
        // START USER EFFECT CODE
        var sig, distorted, midFreq, presenceEnhance, reverb;
        var phase, trig, partition;
        var feedback, bitCrushed, stereoSig, pitchShifted;

        sig = In.ar(in_bus);

        // Feedback loop with limiter
        feedback = LocalIn.ar(1);
        sig = (sig + (feedback * feedbackAmount)).tanh;  // Use tanh as a soft clipper

        // More complex waveshaping with volume control
        distorted = (sig * drive).tanh * 0.5;  // Reduce the overall volume
        
        midFreq = BPF.ar(distorted, 1500, rq: 1/0.7) * (midBoost * 10).dbamp;  // Reduced midBoost
        presenceEnhance = HPF.ar(distorted, 3000) * presence;
        distorted = Mix([distorted, midFreq, presenceEnhance]) * 0.5;  // Further reduce volume

        // Bit crushing
        bitCrushed = distorted.round(2.pow(SinOsc.kr(0.1).range(1, 16 - (bitCrushAmount * 14))));
        distorted = XFade2.ar(distorted, bitCrushed, bitCrushAmount * 2 - 1);

        // Pitch shifting
        pitchShifted = PitchShift.ar(distorted, 0.2, pitchShiftRatio);
        distorted = XFade2.ar(distorted, pitchShifted, SinOsc.kr(0.05).range(-0.5, 0.5));

        // Stereo widening (commented out for now)
        //stereoSig = [distorted, DelayC.ar(distorted, 0.05, LFNoise2.kr(0.1).range(0, 0.05))];
        //stereoSig = XFade2.ar(distorted, stereoSig, stereoWidth * 2 - 1);

        reverb = FreeVerb.ar(distorted, mix: reverbAmount, damp: 0.5);

        // Complete the feedback loop with limiter
        LocalOut.ar(HPF.ar(reverb, 100).clip2(0.5));

        // Final output limiter
        reverb = Limiter.ar(reverb, 0.95, 0.01);

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