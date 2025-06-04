// shader: oscilloscope
(
    var defName = \vocorotator;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var shift = \shift.kr(1.0);
        var feedback = \feedback.kr(0.9);
        var mix = \mix.kr(0.5);
        
        var sig, mono_for_analysis;
        var fb_sig, out_sig, chain; // chain is for internal FFT processing
        // Removed: phase, trig, partition, kr_impulse, from, to, rot_index, rms_input, rms_output

        sig = In.ar(in_bus); // Assuming mono input
        fb_sig = LocalIn.ar(1);

        out_sig = (sig + (fb_sig * feedback));

        // Internal FFT processing for the vocorotator effect - THIS IS OK
        chain = FFT(LocalBuf(2048), out_sig);
        chain = PV_BinShift(chain, 
            stretch: 1.0,
            shift: shift
        );
        out_sig = IFFT(chain);

        LocalOut.ar(out_sig); // Feedback path

        out_sig = LPF.ar(out_sig, 3000);
        out_sig = XFade2.ar(sig, out_sig, mix);

        // Prepare mono signal for analysis
        // Assuming out_sig is mono here
        mono_for_analysis = out_sig;

        // Removed old analysis machinery (FFT to global buffer, BufWr, RMS, SendReply)

        Out.ar(out, [out_sig, out_sig]); // Output mono out_sig as stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'vocorotator' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        shift: ControlSpec(0.1, 4.0, 'exp', 0, 1.0, "x"),
        feedback: ControlSpec(0.0, 0.99, 'lin', 0, 0.9, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    fork {
        s.sync;

        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)