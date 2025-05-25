(
    SynthDef(\electremolo, {
        |out = 0, in_bus = 0, analysis_out_bus, rate = 2, depth = 0.5, mix = 1.0|
        var sig, trem, dry, wet, finalSig, tremMult, mono_for_analysis;
        var kr_impulse; // kr_impulse is kept for /electremoloData SendReply
        // Removed: phase, trig, partition, chain_out, rms_input, rms_output
        // var waveShapedSig; // This was commented out
        
        sig = In.ar(in_bus); // Assuming mono input
        tremMult = depth * SinOsc.kr(rate, 0, 1.0) + (1 - depth) + 0.5;
        
        trem = sig * tremMult; 

        dry = sig * (1.0 - mix);
        wet = trem * mix;
        finalSig =  dry + wet;
        
        // Prepare mono signal for analysis
        // Assuming finalSig is mono here
        mono_for_analysis = finalSig;

        // Removed old analysis machinery (BufWr, RMS for global, Out.kr for RMS, SendReply for /buffer_refresh, /rms)
        // Kept kr_impulse and SendReply for /electremoloData as it's effect-specific data
        kr_impulse = Impulse.kr(60);
        SendReply.kr(kr_impulse, '/electremoloData', [tremMult, depth, mix]);

        Out.ar(out, [finalSig,finalSig]); // Output mono finalSig as stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;
    "Effect SynthDef added".postln;

    // OSC responder for electremolo specific data - THIS IS OK TO KEEP
	OSCdef(\electremoloData).free;
	OSCdef(\electremoloData, { |msg|
		var a = msg[3];
        var b = msg[4];
        var c = msg[5];
		~o.sendMsg('\electremoloData', 
			a, b, c
    );  	}, '/electremoloData', s.addr);

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(\electremolo, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \rate, 2,
            \depth, 0.5,
            \mix, 1.0
        ], ~effectGroup);
        ("New electremolo synth created with analysis output bus").postln;
    };
)