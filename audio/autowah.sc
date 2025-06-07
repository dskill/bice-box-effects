// shader: fft_tunnel
(
    var defName = \autowah;
    var def = SynthDef(defName, {
        // Parameters (NamedControl style)
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sensitivity = \sensitivity.kr(0.7);
        var drive = \drive.kr(1.5);
        var min_freq = \min_freq.kr(200);
        var max_freq = \max_freq.kr(2000);
        var octave_mult = \octave_mult.kr(1.0);
        var resonance = \resonance.kr(0.7);
        var attack = \attack.kr(0.01);
        var decay = \decay.kr(0.3);
        var lfo_rate = \lfo_rate.kr(0.0);
        var lfo_depth = \lfo_depth.kr(0.0);
        var saturation = \saturation.kr(0.0);
        var mix = \mix.kr(0.8);
        
        // Variables (MUST be declared here!)
        var sig, driven_sig, envelope, filter_freq, lfo_mod, filtered, saturated, mono_for_analysis;

        // Signal processing
        sig = In.ar(in_bus);
        
        // Drive the input signal for more dramatic envelope response
        driven_sig = sig * drive;
        
        // Envelope follower - tracks amplitude of driven signal
        envelope = Amplitude.ar(Mix.ar(driven_sig), 
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
        saturated = XFade2.ar(sig, saturated, mix * 2 - 1);
        
        // Analysis output
        mono_for_analysis = Mix.ar(saturated);
        Out.ar(analysis_out_bus, mono_for_analysis);
        
        // Main output
        Out.ar(out, [saturated, saturated]);
    });
    def.add;
    "Effect SynthDef 'autowah' added".postln;

    // Parameter registration
    ~registerEffectSpecs.value(defName, (
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
    ));

    // Synth creation
    fork {
        s.sync;
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });
        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
) 