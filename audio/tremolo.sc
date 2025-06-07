// shader: oscilloscope
(
    var defName = \tremolo;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(2);
        var depth = \depth.kr(0.5);
        var mix = \mix.kr(0.5);
        
        var sig, trem, dry, wet, finalSig, tremMult, mono_for_analysis;
        var kr_impulse; // kr_impulse is kept for /tremoloData SendReply
        // Removed: phase, trig, partition, chain_out, rms_input, rms_output

        sig = In.ar(in_bus); // Sums stereo to mono
        tremMult = depth * SinOsc.kr(rate, 0, 1.0) + (1 - depth) + 0.5;
        trem = sig * tremMult; 

        dry = sig * (1.0 - mix);
        wet = trem * mix;
        finalSig =  dry + wet;

        // Prepare mono signal for analysis - already mono
        mono_for_analysis = finalSig;

        // Removed old analysis machinery (BufWr, RMS for global, Out.kr for RMS, SendReply for /buffer_refresh, /rms)
        // Kept kr_impulse and SendReply for /tremoloData as it's effect-specific data
        kr_impulse = Impulse.kr(30); // Original was 30Hz, keeping that for this specific SendReply
        SendReply.kr(kr_impulse, '/tremoloData', [tremMult, depth]);

        Out.ar(out, [finalSig, finalSig]); // Output mono finalSig as stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'tremolo' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        rate: ControlSpec(0.1, 20, 'exp', 0, 2, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));

    // OSC responder for tremolo specific data - THIS IS OK TO KEEP
	OSCdef(\tremoloData).free;
	OSCdef(\tremoloData, { |msg|
		var a = msg[3];
        var b = msg[4];
		~o.sendMsg('\tremoloData', 
			a, b
    );  	}, '/tremoloData', s.addr);

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