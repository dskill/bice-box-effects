// shader: silexar_ascii
// category: Distortion
(
    var defName = \hyperdrive;
    var specs = (
        gain: ControlSpec(0.1, 5.0, 'exp', 0, 1.0, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.1, "%"),
        res: ControlSpec(0.1, 4.0, 'exp', 0, 1.37, "x"),
        level: ControlSpec(0.0, 2.0, 'lin', 0, 0.75, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var gain = \gain.kr(specs[\gain].default);
        var tone = \tone.kr(specs[\tone].default);
        var res = \res.kr(specs[\res].default);
        var level = \level.kr(specs[\level].default);
        var mix = \mix.kr(specs[\mix].default);
        
        var sig, dry, distorted, mono_for_analysis;

        sig = In.ar(in_bus); // Sums stereo to mono
        dry = sig;
        
        // Simplified distortion chain using soft_fuzz approach
        distorted = sig + sig * gain * 10.0;  // Gain staging similar to soft_fuzz
        distorted = distorted.softclip;  // Simple softclip instead of complex distortion
        
        // MoogFF filter for tone shaping (replacing previous EQ setup)
        distorted = MoogFF.ar(
            in: distorted,
            freq: (100 + (18e3 * tone)),  // Same frequency range as soft_fuzz
            gain: res
        );
        
        distorted = distorted * level;  // Output level control
        distorted = LeakDC.ar(distorted);  // Clean up DC offset
        distorted = XFade2.ar(dry, distorted, mix*2.0-1.0);

        // Prepare mono signal for analysis - already mono
        mono_for_analysis = distorted;

        Out.ar(out, [distorted, distorted]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'hyperdrive' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
