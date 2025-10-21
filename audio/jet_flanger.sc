// Jet Flanger - Extreme 90s jet flanger effect
// Inspired by classic through-zero flanging with dramatic swooshes

var defName = \jet_flanger;

SynthDef(defName, {
    arg in = 0, out = 0,
        rate = 0.3,       // 0-1: LFO rate
        depth = 0.7,      // 0-1: Flange depth
        feedback = 0.6,   // 0-1: Feedback amount (can go into oscillation)
        manual = 0.5,     // 0-1: Manual delay time offset
        mix = 0.8;        // 0-1: Wet/dry mix

    var input, wet, dry, output;
    var lfo, delayTime, flanged, feedbackSig;
    var maxDelay = 0.02;  // 20ms max delay

    // Get input
    input = In.ar(in, 2);
    dry = input;

    // Map rate to Hz (0.05 to 2 Hz for slow to fast jet swooshes)
    rate = rate.linexp(0, 1, 0.05, 2.0);

    // Triangle wave LFO for smoother flanging
    lfo = LFTri.kr(rate);

    // Map depth to delay time range (0.5ms to 10ms)
    // Manual control offsets the center point
    delayTime = manual.linlin(0, 1, 2, 8) + (lfo * depth.linlin(0, 1, 0.5, 8));
    delayTime = (delayTime / 1000).clip(0.0005, maxDelay);

    // Feedback loop with LocalIn/LocalOut for resonance
    feedbackSig = LocalIn.ar(2);

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

    ReplaceOut.ar(out, output);
}).add;
