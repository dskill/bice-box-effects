// shader: oscilloscope
// category: Modulation
(
    var defName = \freq_shifter;
    var specs = (
        shift_hz: ControlSpec(-500, 500, 'lin', 0, 0, "Hz"),
        mod_rate: ControlSpec(0.01, 10.0, 'exp', 0, 0.5, "Hz"),
        mod_depth: ControlSpec(0.0, 200.0, 'lin', 0, 0, "Hz"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var shift_hz = \shift_hz.kr(specs[\shift_hz].default);
        var mod_rate = \mod_rate.kr(specs[\mod_rate].default);
        var mod_depth = \mod_depth.kr(specs[\mod_depth].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here!
        var sig, dry, processed, shifted, fbNode, lfo, modulated_shift, mono_for_analysis;

        // Processing
        sig = In.ar(in_bus);
        dry = sig;

        // Get feedback from previous iteration
        fbNode = LocalIn.ar(1);
        sig = sig + (fbNode * feedback);

        // Modulate the shift amount with LFO
        lfo = SinOsc.kr(mod_rate);
        modulated_shift = shift_hz + (lfo * mod_depth);

        // Frequency shift using Hilbert transform
        shifted = FreqShift.ar(sig, modulated_shift);

        // Send feedback back
        LocalOut.ar(shifted);

        processed = shifted;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        // Outputs
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'freq_shifter' added".postln;

    ~setupEffect.value(defName, specs);
)