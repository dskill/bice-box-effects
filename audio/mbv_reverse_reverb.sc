// shader: oscilloscope
// category: Reverb
// description: Dreamy reverse reverb inspired by My Bloody Valentine's shoegaze sound
(
    var defName = \mbv_reverse_reverb;
    var specs = (
        predelay: ControlSpec(0.01, 0.5, 'exp', 0, 0.1, "s"),
        reverb_time: ControlSpec(0.5, 8.0, 'exp', 0, 3.0, "s"),
        damping: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        shimmer: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        mod_depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        mod_rate: ControlSpec(0.1, 5.0, 'exp', 0, 0.7, "Hz"),
        low_cut: ControlSpec(20, 500, 'exp', 0, 80, "Hz"),
        high_cut: ControlSpec(1000, 16000, 'exp', 0, 8000, "Hz"),
        diffusion: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var predelay = \predelay.kr(specs[\predelay].default);
        var reverb_time = \reverb_time.kr(specs[\reverb_time].default);
        var damping = \damping.kr(specs[\damping].default);
        var shimmer = \shimmer.kr(specs[\shimmer].default);
        var mod_depth = \mod_depth.kr(specs[\mod_depth].default);
        var mod_rate = \mod_rate.kr(specs[\mod_rate].default);
        var low_cut = \low_cut.kr(specs[\low_cut].default);
        var high_cut = \high_cut.kr(specs[\high_cut].default);
        var diffusion = \diffusion.kr(specs[\diffusion].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, processed, mono_for_analysis;
        var predelayed, diffused, reverb, shimmer_sig;
        var mod1, mod2, mod3, mod4;
        var allpass1, allpass2, allpass3, allpass4;
        var comb1, comb2, comb3, comb4;
        var damped_fb, fb_node;
        var reverse_env, grain_trig, grains;
        var buf_dur, rec_phase, play_phase, reverse_grain;

        sig = In.ar(in_bus);
        dry = sig;

        // Predelay
        predelayed = DelayC.ar(sig, 0.5, predelay);

        // Modulation LFOs for that wobbly MBV sound
        mod1 = SinOsc.kr(mod_rate * 1.0, 0) * mod_depth * 0.002;
        mod2 = SinOsc.kr(mod_rate * 1.23, 0.5) * mod_depth * 0.002;
        mod3 = SinOsc.kr(mod_rate * 0.87, 1.0) * mod_depth * 0.002;
        mod4 = SinOsc.kr(mod_rate * 1.41, 1.5) * mod_depth * 0.002;

        // Diffusion network (allpass chain for smearing transients)
        diffused = predelayed;
        allpass1 = AllpassC.ar(diffused, 0.05, 0.012 + mod1, diffusion * 0.15);
        allpass2 = AllpassC.ar(allpass1, 0.05, 0.017 + mod2, diffusion * 0.15);
        allpass3 = AllpassC.ar(allpass2, 0.05, 0.023 + mod3, diffusion * 0.15);
        allpass4 = AllpassC.ar(allpass3, 0.05, 0.031 + mod4, diffusion * 0.15);
        diffused = allpass4;

        // Feedback network for reverse reverb tail
        fb_node = LocalIn.ar(1);

        // Parallel comb filters with modulated delay times (lush reverb tank)
        comb1 = CombC.ar(diffused + (fb_node * 0.3), 0.2, 0.0397 + mod1, reverb_time);
        comb2 = CombC.ar(diffused + (fb_node * 0.3), 0.2, 0.0467 + mod2, reverb_time * 0.95);
        comb3 = CombC.ar(diffused + (fb_node * 0.3), 0.2, 0.0537 + mod3, reverb_time * 1.05);
        comb4 = CombC.ar(diffused + (fb_node * 0.3), 0.2, 0.0607 + mod4, reverb_time * 0.9);

        reverb = (comb1 + comb2 + comb3 + comb4) * 0.25;

        // Damping filter (high frequency absorption)
        damped_fb = LPF.ar(reverb, LinExp.kr(damping, 0, 1, 12000, 800));
        damped_fb = HPF.ar(damped_fb, low_cut);
        damped_fb = LPF.ar(damped_fb, high_cut);

        // Shimmer: pitch-shifted feedback for that ethereal MBV quality
        shimmer_sig = PitchShift.ar(
            damped_fb,
            0.2,
            2.0,  // Octave up
            0.01 + (mod_depth * 0.02),  // Pitch dispersion
            0.01 + (mod_depth * 0.02)   // Time dispersion
        );
        damped_fb = damped_fb + (shimmer_sig * shimmer * 0.5);

        // Additional smearing allpasses for that "reversed" swell character
        damped_fb = AllpassC.ar(damped_fb, 0.1, 0.067 + (mod1 * 2), 0.2);
        damped_fb = AllpassC.ar(damped_fb, 0.1, 0.083 + (mod2 * 2), 0.2);

        // Send to feedback loop
        LocalOut.ar(damped_fb * 0.6);

        processed = damped_fb;

        // Final EQ shaping
        processed = HPF.ar(processed, low_cut);
        processed = LPF.ar(processed, high_cut);

        // Soft saturation for warmth
        processed = (processed * 1.2).tanh * 0.9;

        // Mix dry/wet
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        // Output
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'mbv_reverse_reverb' added".postln;

    ~setupEffect.value(defName, specs);
)
