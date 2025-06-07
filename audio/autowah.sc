// shader: fft_tunnel
(
    var defName = \autowah;
    var specs = (
        sensitivity: ControlSpec(0.1, 5.0, 'exp', 0, 0.7, "x"),
        drive: ControlSpec(0.5, 8.0, 'exp', 0, 1.5, "x"),
        min_freq: ControlSpec(50, 1000, 'exp', 0, 200, "Hz"),
        max_freq: ControlSpec(500, 8000, 'exp', 0, 2000, "Hz"),
        octave_mult: ControlSpec(0.25, 4.0, 'exp', 0, 1.0, "x"),
        resonance: ControlSpec(0.1, 0.98, 'lin', 0, 0.7, "%"),
        attack: ControlSpec(0.001, 0.2, 'exp', 0, 0.01, "s"),
        decay: ControlSpec(0.05, 4.0, 'exp', 0, 0.3, "s"),
        lfo_rate: ControlSpec(0.0, 10.0, 'exp', 0, 0.0, "Hz"),
        lfo_depth: ControlSpec(0.0, 0.5, 'lin', 0, 0.0, "%"),
        saturation: ControlSpec(0.0, 2.0, 'lin', 0, 0.0, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        // Parameters (NamedControl style)
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sensitivity = \sensitivity.kr(specs[\sensitivity].default);
        var drive = \drive.kr(specs[\drive].default);
        var min_freq = \min_freq.kr(specs[\min_freq].default);
        var max_freq = \max_freq.kr(specs[\max_freq].default);
        var octave_mult = \octave_mult.kr(specs[\octave_mult].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var lfo_rate = \lfo_rate.kr(specs[\lfo_rate].default);
        var lfo_depth = \lfo_depth.kr(specs[\lfo_depth].default);
        var saturation = \saturation.kr(specs[\saturation].default);
        var mix = \mix.kr(specs[\mix].default);
        
        // Variables (MUST be declared here!)
        var sig, dry, driven_sig, envelope, filter_freq, lfo_mod, filtered, saturated, mono_for_analysis;

        // Signal processing
        // In.ar on a stereo bus with a single argument sums to mono, which is what we want for performance.
        sig = In.ar(in_bus);
        dry = sig;
        
        // Drive the input signal for more dramatic envelope response
        driven_sig = sig * drive;
        
        // Envelope follower - tracks amplitude of driven signal
        envelope = Amplitude.ar(driven_sig, 
            attackTime: attack, 
            releaseTime: decay
        );
        
        // Scale envelope to frequency range with sensitivity control
        filter_freq = LinExp.ar(
            (envelope * sensitivity).clip(0.001, 1.0), 
            0.001, 1.0,  // Input range 
            min_freq, max_freq * octave_mult  // Output frequency range with octave multiplier
        );
        
        // Add LFO modulation to filter frequency for extra movement
        lfo_mod = SinOsc.kr(lfo_rate, 0, lfo_depth * filter_freq);
        filter_freq = (filter_freq + lfo_mod).clip(20, 20000);
        
        // Apply dual-stage resonant filtering for more dramatic effect
        filtered = RLPF.ar(sig, filter_freq, 1.0 - resonance);
        filtered = RLPF.ar(filtered, filter_freq, 1.0 - (resonance * 0.7));
        
        // Optional saturation/distortion for extra grit
        saturated = Select.ar(saturation > 0.01, [
            filtered,
            (filtered * (1.0 + saturation)).tanh * (1.0 / (1.0 + saturation))
        ]);
        
        // Mix control between dry and wet signal
        saturated = XFade2.ar(dry, saturated, mix * 2 - 1);
        
        // Analysis output - the signal is already mono, so no Mix.ar needed
        mono_for_analysis = saturated;
        Out.ar(analysis_out_bus, mono_for_analysis);
        
        // Main output - duplicate the mono signal to create a stereo pair
        Out.ar(out, [saturated, saturated]);
    });
    def.add;
    "Effect SynthDef 'autowah' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
) 