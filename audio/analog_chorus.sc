// Analog Chorus - Lush 90s chorus ensemble
// Inspired by classic Boss CE-2/CH-1 style chorus pedals

var defName = \analog_chorus;

SynthDef(defName, {
    arg in = 0, out = 0,
        rate = 0.4,       // 0-1: LFO rate (0.2-5 Hz)
        depth = 0.6,      // 0-1: Modulation depth
        voices = 0.7,     // 0-1: Number of chorus voices (1-4)
        tone = 0.6,       // 0-1: Tone control
        mix = 0.5;        // 0-1: Wet/dry mix

    var input, wet, dry, output;
    var lfo1, lfo2, lfo3, lfo4;
    var delay1, delay2, delay3, delay4;
    var chorused, numVoices;

    // Get input
    input = In.ar(in, 2);
    dry = input;

    // Map rate to Hz (0.2 to 5 Hz)
    rate = rate.linexp(0, 1, 0.2, 5.0);

    // Map depth to delay time modulation (0.5ms to 8ms)
    depth = depth.linlin(0, 1, 0.5, 8.0);

    // Create multiple LFOs with slight phase differences for richer sound
    lfo1 = SinOsc.kr(rate, 0);
    lfo2 = SinOsc.kr(rate * 1.03, 1.57); // Slightly detuned, 90° phase
    lfo3 = SinOsc.kr(rate * 0.97, 3.14); // Slightly detuned, 180° phase
    lfo4 = SinOsc.kr(rate * 1.01, 4.71); // Slightly detuned, 270° phase

    // Create modulated delays (base delay + LFO modulation)
    delay1 = DelayC.ar(input, 0.02, (5 + (lfo1 * depth)) / 1000);
    delay2 = DelayC.ar(input, 0.02, (6 + (lfo2 * depth)) / 1000);
    delay3 = DelayC.ar(input, 0.02, (7 + (lfo3 * depth)) / 1000);
    delay4 = DelayC.ar(input, 0.02, (8 + (lfo4 * depth)) / 1000);

    // Mix chorus voices based on voices parameter
    numVoices = voices.linlin(0, 1, 1, 4);
    chorused = SelectX.ar(numVoices - 1, [
        delay1,                              // 1 voice
        (delay1 + delay2) * 0.5,            // 2 voices
        (delay1 + delay2 + delay3) * 0.33,  // 3 voices
        (delay1 + delay2 + delay3 + delay4) * 0.25  // 4 voices
    ]);

    // Tone control (low-pass filter)
    chorused = LPF.ar(chorused, tone.linexp(0, 1, 2000, 12000));

    wet = chorused;

    // Mix wet/dry
    output = (dry * (1 - mix)) + (wet * mix);

    ReplaceOut.ar(out, output);
}).add;
