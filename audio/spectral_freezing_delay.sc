(
    var defName = \spectral_freezing_delay;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var delayTime = \delayTime.kr(0.5);
        var feedback = \feedback.kr(0.5);
        var freezeTrig = \freezeTrig.kr(0);
        var freezeMix = \freezeMix.kr(0.5);
        var mix = \mix.kr(0.5);
        
        var sig, delayed, frozen, fbNode, buf, freezePhase, mono_for_analysis;

        // Allocate a buffer for the frozen sound
        buf = LocalBuf.new(2048, 2);

        sig = In.ar(in_bus, 2);
        fbNode = LocalIn.ar(2);

        delayed = DelayC.ar(sig + fbNode, 2, delayTime);
        LocalOut.ar(delayed * feedback);
        
        // Freeze section
        freezePhase = Phasor.ar(freezeTrig, BufRateScale.kr(buf), 0, buf.numFrames);
        frozen = BufRd.ar(2, buf, freezePhase);

        // Record into the freeze buffer when freezeTrig is 1
        RecordBuf.ar(sig, buf, freezeTrig, loop: 1);

        // Mix the delayed and frozen signals
        sig = XFade2.ar(delayed, frozen, freezeMix * 2 - 1);

        // Overall mix control with original dry input (which is stereo In.ar(in_bus, 2))
        sig = XFade2.ar(In.ar(in_bus, 2), sig, mix * 2 - 1);

        // Prepare mono signal for analysis
        mono_for_analysis = Mix.ar(sig);

        Out.ar(out, sig);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'spectral_freezing_delay' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        delayTime: ControlSpec(0.01, 2.0, 'exp', 0, 0.5, "s"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.5, "%"),
        freezeTrig: ControlSpec(0, 1, 'lin', 1, 0, ""),
        freezeMix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    fork {
        s.sync;
        if(~effect.notNil, { ~effect.free; });
        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)