// shader: kaleidoscope
// category: Delay & Reverb
// description: Granular convolution reverb with shimmer
(
    var defName = \spectral_convolve;
    var specs = (
        ir_length: ControlSpec(0.01, 0.5, 'exp', 0, 0.1, "s"),
        density: ControlSpec(0.1, 1.0, 'lin', 0, 0.5, ""),
        shimmer: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        damping: ControlSpec(100, 8000, 'exp', 0, 3000, "Hz"),
        spread: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, ""),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.3, ""),
        tone: ControlSpec(200, 5000, 'exp', 0, 1500, "Hz"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var ir_length = \ir_length.kr(specs[\ir_length].default);
        var density = \density.kr(specs[\density].default);
        var shimmer = \shimmer.kr(specs[\shimmer].default);
        var damping = \damping.kr(specs[\damping].default);
        var spread = \spread.kr(specs[\spread].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var tone = \tone.kr(specs[\tone].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, processed, mono_for_analysis;
        var fbNode, grains, grain_sig, delay, amp;

        sig = In.ar(in_bus);
        dry = sig;

        // Get feedback from previous iteration
        fbNode = LocalIn.ar(1);
        sig = sig + (fbNode * feedback);

        // Generate synthetic impulse response using 16 grains
        grains = Mix.fill(16, { arg i;
            var grain_delay, grain_amp, grain_output;
            // Exponentially distributed grain delays based on ir_length
            grain_delay = ir_length * ((i / 16).squared);
            // Exponential amplitude decay, modulated by density
            grain_amp = ((1 - (i / 16)).pow(2.5) * (1.0 / 4.0)) * density.linlin(0.1, 1.0, 0.3, 1.0);
            // Create grain - use input signal delayed
            grain_output = DelayC.ar(sig, 0.5, grain_delay.clip(0.001, 0.499));
            // Add shimmer (pitch shift up an octave)
            grain_output = grain_output + (PitchShift.ar(grain_output, 0.1, 2.0, 0, 0.01) * shimmer);
            // Apply damping filter to each grain
            grain_output = LPF.ar(grain_output, damping);
            // Add stereo spread using random phase
            grain_output * grain_amp
        });

        // Apply tone shaping
        processed = RLPF.ar(grains, tone, 0.5);

        // Send feedback
        LocalOut.ar(processed);

        // Final mix
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'spectral_convolve' added".postln;

    ~setupEffect.value(defName, specs);
)
