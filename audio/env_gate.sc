// shader: oscilloscope
// category: Modulation
(
    var defName = \env_gate;
    var specs = (
        threshold: ControlSpec(-60, 0, 'lin', 0, -30, "dB"),
        attack: ControlSpec(0.001, 0.1, 'exp', 0, 0.005, "s"),
        release: ControlSpec(0.01, 2.0, 'exp', 0, 0.2, "s"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%"),
        rate: ControlSpec(0.1, 20.0, 'exp', 0, 4.0, "Hz"),
        pattern: ControlSpec(0, 7, 'lin', 1, 0, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var threshold = \threshold.kr(specs[\threshold].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var depth = \depth.kr(specs[\depth].default);
        var rate = \rate.kr(specs[\rate].default);
        var pattern = \pattern.kr(specs[\pattern].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, envelope, threshLin, gate, patterns, selectedPattern, rhythmicGate, finalGate, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        // Envelope follower
        envelope = Amplitude.kr(sig, attack, release);

        // Convert threshold from dB to linear
        threshLin = threshold.dbamp;

        // Basic gate based on threshold
        gate = (envelope > threshLin);

        // Rhythmic patterns (8 different patterns)
        patterns = [
            Impulse.kr(rate) > 0,  // Pattern 0: straight pulses
            LFPulse.kr(rate, 0, 0.5) > 0,  // Pattern 1: 50% duty cycle
            LFPulse.kr(rate, 0, 0.25) > 0,  // Pattern 2: 25% duty cycle
            LFPulse.kr(rate, 0, 0.75) > 0,  // Pattern 3: 75% duty cycle
            ((LFSaw.kr(rate) > 0) + (LFSaw.kr(rate * 2) > 0)) > 0,  // Pattern 4: syncopated
            LFPulse.kr(rate, [0, 0.5], 0.1).sum > 0,  // Pattern 5: double hits
            ((LFPulse.kr(rate, 0, 0.2) > 0) * (LFPulse.kr(rate * 0.5, 0, 0.8) > 0)),  // Pattern 6: gated pulses
            LFNoise0.kr(rate) > 0  // Pattern 7: random gates
        ];

        // Select pattern based on parameter
        selectedPattern = Select.kr(pattern.min(7), patterns);

        // Combine envelope gate with rhythmic pattern
        rhythmicGate = gate * selectedPattern;

        // Smooth the gate with lag for less clicky behavior
        finalGate = Lag.kr(rhythmicGate, attack);

        // Apply gate with depth control
        processed = sig * ((1 - depth) + (depth * finalGate));

        // Mix dry/wet
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'env_gate' added".postln;

    ~setupEffect.value(defName, specs);
)