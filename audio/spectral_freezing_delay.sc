(
    SynthDef(\spectral_freezing_delay, {
        |out = 0, in_bus = 0, analysis_out_bus, delayTime = 0.5, feedback = 0.5, freezeTrig = 0, freezeMix = 0.5, mix = 0.5|
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
    }).add;

    "SpectralFreezingDelay added".postln;

    fork {
        s.sync;
        if(~effect.notNil, { ~effect.free; });
        ~effect = Synth(\spectral_freezing_delay, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \delayTime, 0.5,
            \feedback, 0.5,
            \freezeTrig, 0,
            \freezeMix, 0.5,
            \mix, 0.5
        ], ~effectGroup);
        ("New spectral_freezing_delay synth created with analysis output bus").postln;
    };
)