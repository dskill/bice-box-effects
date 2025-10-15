// shader: oscilloscope
(
    var defName = \the_edge;
    var specs = (
        delay_time: ControlSpec(0.1, 0.8, 'lin', 0, 0.375, "s"),
        feedback: ControlSpec(0.0, 0.85, 'lin', 0, 0.6, ""),
        high_cut: ControlSpec(1000, 8000, 'exp', 0, 4000, "Hz"),
        low_cut: ControlSpec(80, 800, 'exp', 0, 200, "Hz"),
        saturation: ControlSpec(0.0, 2.0, 'lin', 0, 0.3, ""),
        modulation: ControlSpec(0.0, 0.1, 'lin', 0, 0.02, ""),
        stereo_width: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var delay_time = \delay_time.kr(specs[\delay_time].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var high_cut = \high_cut.kr(specs[\high_cut].default);
        var low_cut = \low_cut.kr(specs[\low_cut].default);
        var saturation = \saturation.kr(specs[\saturation].default);
        var modulation = \modulation.kr(specs[\modulation].default);
        var stereo_width = \stereo_width.kr(specs[\stereo_width].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, delay_l, delay_r, feedback_l, feedback_r, mod_delay_l, mod_delay_r, filtered_l, filtered_r, saturated_l, saturated_r, wet_l, wet_r, final_l, final_r, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        // Modulated delay times for slight tape flutter
        mod_delay_l = delay_time * (1.0 + (SinOsc.kr(0.7, 0, modulation)));
        mod_delay_r = delay_time * (1.0 + (SinOsc.kr(0.9, 1.5, modulation)));

        // Initialize delay lines
        delay_l = LocalIn.ar(1);
        delay_r = LocalIn.ar(1);

        // Create stereo spread in delays
        delay_l = DelayL.ar(sig + (delay_r * feedback * 0.7), 1.0, mod_delay_l);
        delay_r = DelayL.ar(sig + (delay_l * feedback * 0.7), 1.0, mod_delay_r * (1.0 + (stereo_width * 0.1)));

        // High and low cut filtering (Edge's signature sound)
        filtered_l = HPF.ar(delay_l, low_cut);
        filtered_l = LPF.ar(filtered_l, high_cut);
        filtered_r = HPF.ar(delay_r, low_cut);
        filtered_r = LPF.ar(filtered_r, high_cut);

        // Subtle saturation for warmth
        saturated_l = (filtered_l * (1.0 + saturation)).tanh;
        saturated_r = (filtered_r * (1.0 + saturation)).tanh;

        // Feed back into delay lines
        LocalOut.ar([saturated_l, saturated_r]);

        // Create stereo image
        wet_l = saturated_l * (1.0 - (stereo_width * 0.5));
        wet_r = saturated_r * (1.0 - (stereo_width * 0.5)) + (saturated_l * stereo_width * 0.3);

        // Mix dry and wet
        final_l = XFade2.ar(dry, wet_l, mix * 2 - 1);
        final_r = XFade2.ar(dry, wet_r, mix * 2 - 1);

        mono_for_analysis = (final_l + final_r) * 0.5;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_l, final_r]);
    });
    def.add;
    "Effect SynthDef 'the_edge' added".postln;

    ~setupEffect.value(defName, specs);
)