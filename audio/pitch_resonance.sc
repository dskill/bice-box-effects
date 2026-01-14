// shader: oscilloscope
// category: Filter
// description: Pitch-tracked resonant filter bank
(
    var defName = \pitch_resonance;
    var specs = (
        sensitivity: ControlSpec(0.1, 2.0, 'exp', 0, 0.5, "x"),
        resonance: ControlSpec(0.1, 0.95, 'lin', 0, 0.7, "Q"),
        spread: ControlSpec(0.5, 4.0, 'exp', 0, 2.0, "x"),
        harmonics: ControlSpec(2, 8, 'lin', 1, 5, ""),
        detune: ControlSpec(0.0, 50.0, 'lin', 0, 5.0, "Hz"),
        feedback: ControlSpec(0.0, 0.8, 'lin', 0, 0.3, ""),
        brightness: ControlSpec(0.1, 4.0, 'exp', 0, 1.0, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sensitivity = \sensitivity.kr(specs[\sensitivity].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var spread = \spread.kr(specs[\spread].default);
        var harmonics = \harmonics.kr(specs[\harmonics].default);
        var detune = \detune.kr(specs[\detune].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var brightness = \brightness.kr(specs[\brightness].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pitch, hasFreq, baseFreq, filtered, fbNode, harmonic, freq, bandpassed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        // Get feedback from previous iteration
        fbNode = LocalIn.ar(1);
        sig = sig + (fbNode * feedback);

        // Pitch tracking
        # pitch, hasFreq = Pitch.kr(sig, ampThreshold: 0.02, median: 7);
        pitch = Lag.kr(pitch, 0.05);
        baseFreq = pitch * sensitivity;
        baseFreq = Clip.kr(baseFreq, 50, 2000);

        // Create resonant filter bank tuned to detected pitch harmonics
        filtered = 0;
        harmonics.do { |i|
            harmonic = i + 1;
            freq = baseFreq * harmonic * spread;
            freq = freq + LFNoise1.kr(0.5 ! 2).range(detune.neg, detune);
            freq = Clip.kr(freq, 50, 12000);
            bandpassed = BPF.ar(sig, freq, 1 - resonance);
            filtered = filtered + (bandpassed * (1 / harmonics));
        };

        // Brightness control
        filtered = LPF.ar(filtered, 2000 * brightness);

        // Send feedback
        LocalOut.ar(filtered);

        // Mix
        filtered = XFade2.ar(dry, filtered, mix * 2 - 1);

        mono_for_analysis = filtered;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [filtered, filtered]);
    });
    def.add;
    "Effect SynthDef 'pitch_resonance' added".postln;

    ~setupEffect.value(defName, specs);
)