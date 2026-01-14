// shader: fft_tunnel
// category: Modulation
// description: Lush analog chorus with multi-voice LFO modulation
// Analog Chorus - Lush 90s chorus ensemble
// Inspired by classic Boss CE-2/CH-1 style chorus pedals
(
    var defName = \analog_chorus;
    var specs = (
        rate: ControlSpec(0.0, 1.0, 'lin', 0, 0.492, "%"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.378, "%"),
        voices: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.578, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var voices = \voices.kr(specs[\voices].default);
        var tone = \tone.kr(specs[\tone].default);
        var mix = \mix.kr(specs[\mix].default);

        var input, wet, dry, output;
        var lfo1, lfo2, lfo3, lfo4;
        var delay1, delay2, delay3, delay4;
        var chorused, mono_for_analysis;
        var rate_hz, depth_ms;

        // Get input (stereo -> mono for processing)
        input = In.ar(in_bus);
        dry = input;

        // Map rate to Hz (0.2 to 5 Hz)
        rate_hz = rate.linexp(0.01, 1.0, 0.2, 5.0);

        // Map depth to delay time modulation (0.5ms to 8ms)
        depth_ms = depth.linlin(0.0, 1.0, 0.5, 8.0);

        // Create multiple LFOs with slight phase differences for richer sound
        lfo1 = SinOsc.kr(rate_hz, 0);
        lfo2 = SinOsc.kr(rate_hz * 1.03, 1.57); // Slightly detuned, 90° phase
        lfo3 = SinOsc.kr(rate_hz * 0.97, 3.14); // Slightly detuned, 180° phase
        lfo4 = SinOsc.kr(rate_hz * 1.01, 4.71); // Slightly detuned, 270° phase

        // Create modulated delays (base delay + LFO modulation)
        delay1 = DelayC.ar(input, 0.02, (5 + (lfo1 * depth_ms)) / 1000);
        delay2 = DelayC.ar(input, 0.02, (6 + (lfo2 * depth_ms)) / 1000);
        delay3 = DelayC.ar(input, 0.02, (7 + (lfo3 * depth_ms)) / 1000);
        delay4 = DelayC.ar(input, 0.02, (8 + (lfo4 * depth_ms)) / 1000);

        // Simple mixing: blend all 4 voices, with voices controlling the mix
        // At 0: mostly delay1, at 1: equal mix of all 4
        chorused = delay1.blend(
            (delay1 + delay2 + delay3 + delay4) * 0.25,
            voices
        );

        // Tone control (low-pass filter)
        chorused = LPF.ar(chorused, tone.linexp(0.01, 1.0, 2000, 12000));

        wet = chorused;

        // Mix wet/dry
        output = (dry * (1 - mix)) + (wet * mix);

        // Analysis output
        mono_for_analysis = output;
        Out.ar(analysis_out_bus, mono_for_analysis);

        // Main output - duplicate mono to stereo
        Out.ar(out, [output, output]);
    });
    def.add;
    "Effect SynthDef 'analog_chorus' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
