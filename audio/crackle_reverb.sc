// shader: outrun
(
    var defName = \crackle_reverb;
    var specs = (
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        room: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        dist_amount: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        dist_hardness: ControlSpec(0.5, 10.0, 'exp', 0, 2.0, "x")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var mix = \mix.kr(specs[\mix].default);
        var room = \room.kr(specs[\room].default);
        var dist_amount = \dist_amount.kr(specs[\dist_amount].default);
        var dist_hardness = \dist_hardness.kr(specs[\dist_hardness].default);
        
        var sig, dry, reverb_sig, reverb_channels, reverb_amp, norm_reverb_amp;
        var max_dist_gain_val, distortion_drive_mod, distorted_reverb_eff;
        var crackle_level, crackle_noise, wet_sig, final_sig;
        var mono_for_analysis;

        // Get input signal
        sig = In.ar(in_bus); // Sums stereo to mono
        dry = sig;

        // --- Start Effect Processing ---

        // 1. Reverb
        // FreeVerb outputs stereo.
        reverb_sig = FreeVerb.ar(sig, 1.0, room, 0.5);

        // 2. Create distortion ramp based on reverb amplitude
        // Get amplitude of the reverb tail (it's stereo, so we mix to mono first)
        reverb_amp = Amplitude.kr(Mix.ar(reverb_sig).abs);
        // Normalize reverb_amp to roughly 0..1 range.
        norm_reverb_amp = reverb_amp.clip(0, 1.0);

        // As norm_reverb_amp (reverb loudness) decreases, (1 - norm_reverb_amp) increases.
        // This increasing value drives the distortion.
        // 'dist_hardness' controls the curve of this ramp (higher values = sharper ramp).
        // 'dist_amount' controls the overall intensity of distortion.
        max_dist_gain_val = dist_amount * 20.0; // dist_amount (0..1) maps to a gain factor up to 20x for distortion
        distortion_drive_mod = (1 - norm_reverb_amp).pow(dist_hardness) * max_dist_gain_val;

        // 3. Apply distortion to reverb signal
        // Increase drive based on distortion_drive_mod and apply tanh saturation
        distorted_reverb_eff = (reverb_sig * (1 + distortion_drive_mod)).tanh;

        // 4. Add crackle noise, also driven by the ramp and distortion amount
        // 'crackle_level' determines the amplitude of the noise.
        crackle_level = (1 - norm_reverb_amp).pow(dist_hardness) * dist_amount;
        // PinkNoise.ar outputs values in -1 to 1. Scale it. Use stereo noise.
        crackle_noise = PinkNoise.ar([1.0, 1.0]) * crackle_level * 0.3; // Adjust 0.3 for desired crackle audibility

        // Combine distorted reverb with crackle noise
        wet_sig = distorted_reverb_eff + crackle_noise;
        wet_sig = wet_sig.clip(-1,1); // Clip final wet signal to prevent extreme levels if crackle sum is too high

        // 5. Mix dry signal with processed wet signal
        // dry is mono, wet_sig is stereo. XFade2 expands dry to stereo automatically.
        final_sig = XFade2.ar(dry, wet_sig, mix * 2.0 - 1.0);

        // --- End Effect Processing ---

        // Create mono mix for analysis
        mono_for_analysis = Mix.ar(final_sig);

	    Out.ar(out, final_sig); // Output the final stereo signal
        Out.ar(analysis_out_bus, mono_for_analysis);

    });
    def.add;
    "Effect SynthDef 'crackle_reverb' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)