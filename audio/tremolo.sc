// shader: oscilloscope
// category: Modulation
// description: Amplitude tremolo with rate and depth
(
    var defName = \tremolo;
    var specs = (
        rate: ControlSpec(0.1, 20, 'exp', 0, 2, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var mix = \mix.kr(specs[\mix].default);
        
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

    // Register parameter specifications
    ~registerEffectSpecs.value(defName, specs);

    // OSC responder for tremolo specific data - THIS IS OK TO KEEP
	OSCdef(\tremoloData).free;
	OSCdef(\tremoloData, { |msg|
		var a = msg[3];
        var b = msg[4];
		~o.sendMsg('\tremoloData',
			a, b
    );  	}, '/tremoloData', s.addr);

    // Create the synth
    ~setupEffect.value(defName, specs);
)
