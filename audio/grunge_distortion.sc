// shader: grunge
// category: Distortion
// Grunge Distortion - Heavy 90s distortion pedal
// Inspired by classic grunge tones with pre-gain, clipping, and tone shaping
(
    var defName = \grunge_distortion;
    var specs = (
        drive: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        gain: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.0, "%"),
        level: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(specs[\drive].default);
        var gain = \gain.kr(specs[\gain].default);
        var tone = \tone.kr(specs[\tone].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var level = \level.kr(specs[\level].default);
        var mix = \mix.kr(specs[\mix].default);

        var input, preamp, clipped, filtered, wet, dry, output;
        var mono_for_analysis;
        var low_freq, high_freq;
        var fb_sig;

        // Get input (stereo -> mono)
        input = In.ar(in_bus);
        dry = input;

        // Feedback loop with local buffer (mono)
        fb_sig = LocalIn.ar(1);
        
        // Convert stereo input to mono, add feedback
        input = Mix.ar(input) * 0.5;
        input = input + (fb_sig * feedback);

        // Pre-gain boost (emulate input gain stage)
        preamp = input * (1 + (gain * 20));

        // Asymmetric soft clipping with drive
        clipped = (preamp * (1 + (drive * 50))).tanh;

        // Add some harmonic saturation
        clipped = (clipped * 1.5).softclip;

        // Tone control using HPF and LPF
        // Map tone from dark (more low-pass) to bright (more high-pass)
        low_freq = tone.linexp(0, 1, 100, 4000);
        high_freq = tone.linexp(0, 1, 800, 12000);
        
        filtered = HPF.ar(clipped, low_freq);
        filtered = LPF.ar(filtered, high_freq);

        // Output level control
        wet = filtered * level * 0.5;

        // Mix wet/dry (convert dry to mono first)
        output = ((Mix.ar(dry) * 0.5) * (1 - mix)) + (wet * mix);

        // Send feedback signal back to the beginning
        LocalOut.ar(output);

        // Analysis output
        mono_for_analysis = output;
        Out.ar(analysis_out_bus, mono_for_analysis);

        // Main output - duplicate mono to stereo
        Out.ar(out, [output, output]);
    });
    def.add;
    "Effect SynthDef 'grunge_distortion' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)