// shader: radial_fft_line
// Jet Flanger - Extreme 90s jet flanger effect
// Inspired by classic through-zero flanging with dramatic swooshes
(
    var defName = \jet_flanger;
    var specs = (
        rate: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        feedback: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%"),
        manual: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var manual = \manual.kr(specs[\manual].default);
        var mix = \mix.kr(specs[\mix].default);

        var input, wet, dry, output;
        var lfo, delayTime, flanged, feedbackSig;
        var maxDelay = 0.02;  // 20ms max delay
        var mono_for_analysis, rate_hz;

        // Get input (stereo -> mono)
        input = In.ar(in_bus);
        dry = input;

        // Map rate to Hz (0.05 to 2 Hz for slow to fast jet swooshes)
        rate_hz = rate.linexp(0.01, 1, 0.05, 2.0);

        // Triangle wave LFO for smoother flanging
        lfo = LFTri.kr(rate_hz);

        // Map depth to delay time range (0.5ms to 10ms)
        // Manual control offsets the center point
        delayTime = manual.linlin(0, 1, 2, 8) + (lfo * depth.linlin(0, 1, 0.5, 8));
        delayTime = (delayTime / 1000).clip(0.0005, maxDelay);

        // Feedback loop with LocalIn/LocalOut for resonance
        feedbackSig = LocalIn.ar(1);

        // Create the flanged signal with feedback
        flanged = DelayC.ar(input + (feedbackSig * feedback.linlin(0, 1, 0, 0.9)), maxDelay, delayTime);

        // Send to feedback loop
        LocalOut.ar(flanged);

        // Add some subtle filtering to tame harsh resonances
        flanged = LPF.ar(flanged, 12000);

        // Phase inversion for that classic flanger sound
        wet = dry + (flanged * -1);

        // Mix wet/dry
        output = (dry * (1 - mix)) + (wet * mix);

        // Soft limiting to prevent clipping from feedback
        output = output.softclip;

        // Analysis output
        mono_for_analysis = output;
        Out.ar(analysis_out_bus, mono_for_analysis);

        // Main output - duplicate mono to stereo
        Out.ar(out, [output, output]);
    });
    def.add;
    "Effect SynthDef 'jet_flanger' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)