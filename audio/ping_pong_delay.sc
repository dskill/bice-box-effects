// shader: radial_fft_line
// category: Delay & Reverb
// description: Stereo ping-pong delay with feedback and mix
(
    var defName = \ping_pong_delay;
    var specs = (
        delayTime: ControlSpec(0.01, 2.0, 'lin', 0, 0.4, "s"),
        feedback: ControlSpec(0.0, 0.99, 'lin', 0, 0.5, "%"),
        wetLevel: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        gain: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "x")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var delayTime = \delayTime.kr(specs[\delayTime].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        var wetLevel = \wetLevel.kr(specs[\wetLevel].default);
        var gain = \gain.kr(specs[\gain].default);
        
        var sig, leftDelay, rightDelay, delaySig, dry, fbNode, finalSig, mono_for_analysis;

        sig = In.ar(in_bus, 2);
        fbNode = LocalIn.ar(2);
        leftDelay = DelayC.ar(sig[0] + fbNode[1], 2, delayTime);
        rightDelay = DelayC.ar(sig[1] + fbNode[0], 2, delayTime);
        LocalOut.ar([leftDelay, rightDelay] * feedback);
        delaySig = [leftDelay, rightDelay];
        dry = sig;
        
        delaySig = delaySig * gain;
        
        finalSig = dry.blend(delaySig, wetLevel);

        mono_for_analysis = Mix.ar(finalSig);

        SendReply.kr(Impulse.kr(30), '/pingPongData', [delayTime, feedback]);

        Out.ar(out, finalSig);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'ping_pong_delay' added".postln;

    // Register parameter specifications
    ~registerEffectSpecs.value(defName, specs);

    OSCdef(\pingPongData).free;
	OSCdef(\pingPongData, { |msg|
		var a = msg[3];
		var b = msg[4];
		~o.sendMsg('\pingPongData', 
			a, b
    );  	}, '/pingPongData', s.addr);

    // Create the synth
    ~setupEffect.value(defName, specs);
)
