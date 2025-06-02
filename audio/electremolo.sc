(
    var defName = \electremolo;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(2);
        var depth = \depth.kr(0.5);
        var mix = \mix.kr(1.0);
        
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
    });
    def.add;
    "Effect SynthDef 'electremolo' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        rate: ControlSpec(0.1, 20, 'exp', 0, 2, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    ));

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

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)