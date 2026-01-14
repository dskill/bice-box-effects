// shader: oscilloscope
// category: Dynamics
// description: Three-band compressor with per-band dynamics
(
    var defName = \multiband_comp;  // MUST match filename exactly!
    var specs = (
        low_thresh: ControlSpec(-40, -10, 'lin', 0, -20, "dB"),
        low_ratio: ControlSpec(1.0, 10.0, 'exp', 0, 3.0, ":1"),
        mid_thresh: ControlSpec(-40, -10, 'lin', 0, -20, "dB"),
        mid_ratio: ControlSpec(1.0, 10.0, 'exp', 0, 3.0, ":1"),
        high_thresh: ControlSpec(-40, -10, 'lin', 0, -20, "dB"),
        high_ratio: ControlSpec(1.0, 10.0, 'exp', 0, 3.0, ":1"),
        low_freq: ControlSpec(100, 800, 'exp', 0, 300, "Hz"),
        high_freq: ControlSpec(1000, 8000, 'exp', 0, 3000, "Hz"),
        attack: ControlSpec(0.001, 0.1, 'exp', 0, 0.01, "s"),
        release: ControlSpec(0.01, 1.0, 'exp', 0, 0.1, "s"),
        makeup: ControlSpec(0.1, 4.0, 'exp', 0, 1.0, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var low_thresh = \low_thresh.kr(specs[\low_thresh].default);
        var low_ratio = \low_ratio.kr(specs[\low_ratio].default);
        var mid_thresh = \mid_thresh.kr(specs[\mid_thresh].default);
        var mid_ratio = \mid_ratio.kr(specs[\mid_ratio].default);
        var high_thresh = \high_thresh.kr(specs[\high_thresh].default);
        var high_ratio = \high_ratio.kr(specs[\high_ratio].default);
        var low_freq = \low_freq.kr(specs[\low_freq].default);
        var high_freq = \high_freq.kr(specs[\high_freq].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var makeup = \makeup.kr(specs[\makeup].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here!
        var sig, dry, low, mid, high, low_env, mid_env, high_env;
        var low_gain, mid_gain, high_gain, low_comp, mid_comp, high_comp;
        var processed, mono_for_analysis;

        // Processing
        sig = In.ar(in_bus);  // Mono input
        dry = sig;

        // Split into three bands
        low = LPF.ar(sig, low_freq);
        high = HPF.ar(sig, high_freq);
        mid = sig - low - high;  // Mid is what's left

        // Envelope followers for each band
        low_env = Amplitude.kr(low, attack, release);
        mid_env = Amplitude.kr(mid, attack, release);
        high_env = Amplitude.kr(high, attack, release);

        // Calculate compression gain for each band
        low_gain = (low_env.ampdb - low_thresh).max(0) * (1 - (1/low_ratio));
        low_gain = low_gain.neg.dbamp;
        mid_gain = (mid_env.ampdb - mid_thresh).max(0) * (1 - (1/mid_ratio));
        mid_gain = mid_gain.neg.dbamp;
        high_gain = (high_env.ampdb - high_thresh).max(0) * (1 - (1/high_ratio));
        high_gain = high_gain.neg.dbamp;

        // Apply compression to each band
        low_comp = low * low_gain;
        mid_comp = mid * mid_gain;
        high_comp = high * high_gain;

        // Mix bands back together and apply makeup gain
        processed = (low_comp + mid_comp + high_comp) * makeup;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        // Outputs
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'multiband_comp' added".postln;

    ~setupEffect.value(defName, specs);
)