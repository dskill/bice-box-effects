(
    SynthDef(\tremolo, {
        |out = 0, in_bus = 0, analysis_out_bus, rate = 2, depth = 0.5, mix = 0.5|
        var sig, trem, dry, wet, finalSig, tremMult, mono_for_analysis;
        var kr_impulse; // kr_impulse is kept for /tremoloData SendReply
        // Removed: phase, trig, partition, chain_out, rms_input, rms_output

        // sig = In.ar(in_bus); // Currently using a test SinOsc instead of in_bus
        sig = SinOsc.ar(800, 0, 0.2);
        tremMult = depth * SinOsc.kr(rate, 0, 1.0) + (1 - depth) + 0.5;
        trem = sig * tremMult; 

        dry = sig * (1.0 - mix); // mix is used here, though it might always be a fully wet signal if sig is just for tremolo effect
        wet = trem * mix;
        finalSig =  dry + wet;

        // Prepare mono signal for analysis
        // finalSig is mono as sig is mono
        mono_for_analysis = finalSig;

        // Removed old analysis machinery (BufWr, RMS for global, Out.kr for RMS, SendReply for /buffer_refresh, /rms)
        // Kept kr_impulse and SendReply for /tremoloData as it's effect-specific data
        kr_impulse = Impulse.kr(30); // Original was 30Hz, keeping that for this specific SendReply
        SendReply.kr(kr_impulse, '/tremoloData', [tremMult, depth]);

        Out.ar(out, [finalSig,finalSig]); // Output mono finalSig as stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;
    "Effect SynthDef added".postln;

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

        ~effect = Synth(\tremolo, [
            \in_bus, ~input_bus, // in_bus is an arg but not used by current SynthDef audio path
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \rate, 2,
            \depth, 0.5,
            \mix, 0.5 // mix is an arg but its effect might be nullified if dry path is always from test SinOsc
        ], ~effectGroup);
        ("New tremolo synth created with analysis output bus").postln;
    };
)