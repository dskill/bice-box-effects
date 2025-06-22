// shader: oscilloscope
(
    var defName = \dan;
    var specs = (
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%"),
        room_size: ControlSpec(0.0, 1.0, 'lin', 0, 0.95, "%"),
        damp: ControlSpec(0.0, 1.0, 'lin', 0, 0.1, "%"),
        pre_delay: ControlSpec(0.0, 0.5, 'lin', 0, 0.2, "s"),
        reverb_gain: ControlSpec(1.0, 8.0, 'exp', 0, 4.0, "x"),
        feedback: ControlSpec(0.0, 0.99, 'lin', 0, 0.85, "%"),
        high_cut: ControlSpec(1000, 20000, 'exp', 0, 8000, "Hz"),
        low_cut: ControlSpec(20, 500, 'exp', 0, 80, "Hz"),
        diffusion: ControlSpec(0.0, 1.0, 'lin', 0, 0.9, "%"),
        decay_time: ControlSpec(1.0, 30.0, 'exp', 0, 15.0, "s"),
        modulation: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        shimmer: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var mix = \mix.kr(specs[\mix].default);
        var room_size = \room_size.kr(specs[\room_size].default);
        var damp = \damp.kr(specs[\damp].default);
        var pre_delay = \pre_delay.kr(specs[\pre_delay].default);
        var reverb_gain = \reverb_gain.kr(specs[\reverb_gain].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var high_cut = \high_cut.kr(specs[\high_cut].default);
        var low_cut = \low_cut.kr(specs[\low_cut].default);
        var diffusion = \diffusion.kr(specs[\diffusion].default);
        var decay_time = \decay_time.kr(specs[\decay_time].default);
        var modulation = \modulation.kr(specs[\modulation].default);
        var shimmer = \shimmer.kr(specs[\shimmer].default);
        
        var sig, dry, delayed_sig, reverb1, reverb2, reverb3, reverb4;
        var filtered_sig, modulated_reverb, shimmer_reverb, final_reverb;
        var wet_sig, final_sig, mono_for_analysis;

        // Get input signal
        sig = In.ar(in_bus);
        dry = sig;

        // --- Start Effect Processing ---

        // Apply pre-delay
        delayed_sig = DelayL.ar(sig, 0.5, pre_delay);
        
        // Apply filtering before reverb
        filtered_sig = BLowPass.ar(delayed_sig, high_cut, 0.5);
        filtered_sig = BHiPass.ar(filtered_sig, low_cut, 0.5);

        // Layer 1: Massive FreeVerb with extended parameters
        reverb1 = FreeVerb.ar(filtered_sig * reverb_gain, 
            mix: 1.0, 
            room: room_size, 
            damp: damp
        );
        
        // Layer 2: GVerb for even more spaciousness
        reverb2 = GVerb.ar(
            filtered_sig * reverb_gain * 0.7,
            roomsize: room_size * 100 + 10,  // Scale to GVerb's range
            revtime: decay_time,
            damping: damp,
            inputbw: 0.5,
            spread: diffusion * 15,
            drylevel: 0,
            earlyreflevel: 0.3,
            taillevel: 1.0
        );
        
        // Layer 3: JPverb for additional complexity
        reverb3 = JPverb.ar(
            filtered_sig * reverb_gain * 0.5,
            t60: decay_time * 2,
            damp: damp,
            size: room_size * 2 + 1,
            earlyDiff: diffusion,
            modDepth: modulation * 0.1,
            modFreq: 0.5,
            low: 1.0,
            mid: 1.0,
            high: 1.0 - (damp * 0.5),
            lowcut: low_cut,
            highcut: high_cut
        );
        
        // Layer 4: Another FreeVerb with different characteristics
        reverb4 = FreeVerb.ar(Mix.ar(reverb1 + reverb2) * 0.3, 
            mix: 1.0, 
            room: (room_size * 0.8).clip(0, 1), 
            damp: (damp * 1.5).clip(0, 1)
        );

        // Add modulation to create movement in the reverb tail
        modulated_reverb = reverb1 + reverb2 + reverb3 + reverb4;
        modulated_reverb = modulated_reverb * (1 + (SinOsc.kr(0.3, 0, modulation * 0.1)));
        
        // Add shimmer effect (pitch-shifted reverb)
        shimmer_reverb = PitchShift.ar(
            Mix.ar(modulated_reverb), 
            0.2, 
            2.0, // Octave up
            0, 
            0.02
        );
        shimmer_reverb = shimmer_reverb * shimmer;
        
        // Combine all reverb layers
        final_reverb = modulated_reverb + [shimmer_reverb, shimmer_reverb];
        
        // Apply feedback for even more sustain
        final_reverb = LocalIn.ar(2) * feedback + final_reverb;
        final_reverb = BLowPass.ar(final_reverb, high_cut * 0.8, 0.7);
        LocalOut.ar(final_reverb * 0.3);
        
        // Final wet signal
        wet_sig = final_reverb * 0.7; // Scale down to prevent clipping
        
        // Mix dry and wet signals
        final_sig = XFade2.ar(dry, wet_sig, mix * 2.0 - 1.0);
        
        // Soft limiter to prevent clipping from the massive reverb
        final_sig = final_sig.softclip;

        // --- End Effect Processing ---

        // Create mono mix for analysis
        mono_for_analysis = Mix.ar(final_sig);

        Out.ar(out, final_sig);
        Out.ar(analysis_out_bus, mono_for_analysis);

    });
    def.add;
    "Effect SynthDef 'dan' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)