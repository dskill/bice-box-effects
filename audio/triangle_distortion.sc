(
    SynthDef(\triangle_distortion, {
        |out = 0, in_bus = 0, analysis_out_bus, drive = 10, tone = 0.5, midBoost = 0.7, presence = 0.5, reverbAmount = 0.2,
        feedbackAmount = 0.1, bitCrushAmount = 0.2, stereoWidth = 0.5, pitchShiftRatio = 1.0|
        var sig, distorted, midFreq, presenceEnhance, reverb, mono_for_analysis;
        var feedback_sig, bitCrushed, stereoSig, pitchShifted;

        sig = In.ar(in_bus);
 
        // Feedback loop with limiter
        feedback_sig = LocalIn.ar(1, 0.0);
        sig = (sig + (feedback_sig * feedbackAmount)).tanh;

        distorted = (sig * drive).fold2(1) * 0.5;
        
        midFreq = BPF.ar(distorted, 1500, rq: 1/0.7) * (midBoost * 10).dbamp;
        presenceEnhance = HPF.ar(distorted, 3000) * presence;
        distorted = Mix([distorted, midFreq, presenceEnhance]) * 0.5;

        bitCrushed = distorted.round(2.pow(SinOsc.kr(0.1).range(1, 16 - (bitCrushAmount * 14))));
        distorted = XFade2.ar(distorted, bitCrushed, bitCrushAmount * 2 - 1);

        pitchShifted = PitchShift.ar(distorted, 0.2, pitchShiftRatio);
        distorted = XFade2.ar(distorted, pitchShifted, SinOsc.kr(0.05).range(-0.5, 0.5));

        // Complete the feedback loop
        LocalOut.ar(HPF.ar(distorted, 200).clip2(0.5));

        distorted = Limiter.ar(distorted, 0.95, 0.01);

        // Prepare mono signal for analysis
        mono_for_analysis = distorted;

        Out.ar(out, [distorted, distorted]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(\triangle_distortion, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \drive, 10,
            \tone, 0.5,
            \midBoost, 0.7,
            \presence, 0.5,
            \reverbAmount, 0.2,
            \feedbackAmount, 0.1,
            \bitCrushAmount, 0.2,
            \stereoWidth, 0.5,
            \pitchShiftRatio, 1.0
        ], ~effectGroup);
        ("New triangle_distortion synth created with analysis output bus").postln;
    };
)