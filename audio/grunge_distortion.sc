// Grunge Distortion - Heavy 90s distortion pedal
// Inspired by classic grunge tones with pre-gain, clipping, and tone shaping

var defName = \grunge_distortion;

SynthDef(defName, {
    arg in = 0, out = 0,
        drive = 0.7,      // 0-1: Pre-gain drive amount
        gain = 0.8,       // 0-1: Input gain boost
        tone = 0.5,       // 0-1: Tone control (dark to bright)
        level = 0.7,      // 0-1: Output level
        mix = 1.0;        // 0-1: Wet/dry mix

    var input, preamp, clipped, filtered, wet, dry, output;

    // Get input
    input = In.ar(in, 2);
    dry = input;

    // Pre-gain boost (emulate input gain stage)
    preamp = input * (1 + (gain * 20));

    // Asymmetric soft clipping with drive
    clipped = (preamp * (1 + (drive * 50))).tanh;

    // Add some harmonic saturation
    clipped = (clipped * 1.5).softclip;

    // Tone stack (simulate passive EQ of guitar pedal)
    // Low shelf for darkness, high shelf for brightness
    filtered = BLowShelf.ar(clipped, 800, 1.0, tone.linlin(0, 0.5, 6, 0));
    filtered = BHiShelf.ar(filtered, 1200, 1.0, tone.linlin(0.5, 1, 0, 8));

    // Add slight mid scoop for that scooped grunge sound
    filtered = BMidEQ.ar(filtered, 600, 0.7, -3);

    // Output level control
    wet = filtered * level * 0.5;

    // Mix wet/dry
    output = (dry * (1 - mix)) + (wet * mix);

    ReplaceOut.ar(out, output);
}).add;
