// shader: plasma_globe
// category: Filter
(
    var defName = \resonator;
    var specs = (
        freq: ControlSpec(80, 800, 'exp', 0, 220, "Hz"),
        decay: ControlSpec(0.1, 8.0, 'exp', 0, 2.0, "s"),
        detune: ControlSpec(0.0, 50.0, 'lin', 0, 5.0, "cents"),
        spread: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        harmonics: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, ""),
        brightness: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.3, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var freq = \freq.kr(specs[\freq].default);
        var decay = \decay.kr(specs[\decay].default);
        var detune = \detune.kr(specs[\detune].default);
        var spread = \spread.kr(specs[\spread].default);
        var harmonics = \harmonics.kr(specs[\harmonics].default);
        var brightness = \brightness.kr(specs[\brightness].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, processed, resonators, fbNode, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        // Get feedback from previous iteration
        fbNode = LocalIn.ar(1);
        sig = sig + (fbNode * feedback);

        // Create bank of 7 resonators with controllable blend
        resonators = Array.fill(7, { |i|
            var harmonic = i + 1;
            var detuneAmt = LFNoise1.kr(0.1 + (i * 0.05)).bipolar(detune);
            var f = freq * harmonic * (1 + (detuneAmt / 1200));
            var delayTime = f.reciprocal;
            var falloff = (1.0 / harmonic).pow(1.0 - brightness);
            var panPos = (i / 6.0 * 2 - 1) * spread;
            var fade = (harmonics * 7 - i).clip(0, 1);
            var resonator = CombC.ar(sig, 0.1, delayTime, decay);
            resonator * falloff * fade;
        }).sum;

        processed = resonators;

        // Send feedback
        LocalOut.ar(processed * 0.5);

        // Mix wet/dry
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'resonator' added".postln;

    ~setupEffect.value(defName, specs);
)