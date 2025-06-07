// shader: fire3d

(
    var defName = \triangle_distortion;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(10);
        var tone = \tone.kr(0.5);
        var midBoost = \midBoost.kr(0.7);
        var presence = \presence.kr(0.5);
        var reverbAmount = \reverbAmount.kr(0.2);
        var feedbackAmount = \feedbackAmount.kr(0.1);
        var bitCrushAmount = \bitCrushAmount.kr(0.2);
        var stereoWidth = \stereoWidth.kr(0.5);
        var pitchShiftRatio = \pitchShiftRatio.kr(1.0);
        
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
        mono_for_analysis = Mix.ar(distorted);

        Out.ar(out, [distorted,distorted]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'triangle_distortion' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        drive: ControlSpec(1.0, 50.0, 'exp', 0, 10, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        midBoost: ControlSpec(0.0, 2.0, 'lin', 0, 0.7, "x"),
        presence: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        reverbAmount: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, "%"),
        feedbackAmount: ControlSpec(0.0, 0.5, 'lin', 0, 0.1, "%"),
        bitCrushAmount: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, "%"),
        stereoWidth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        pitchShiftRatio: ControlSpec(0.5, 2.0, 'exp', 0, 1.0, "x")
    ));

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)