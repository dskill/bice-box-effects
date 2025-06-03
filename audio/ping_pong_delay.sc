(
    var defName = \ping_pong_delay;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var delayTime = \delayTime.kr(0.4);
        var feedback = \feedback.kr(0.5);
        var wetLevel = \wetLevel.kr(0.5);
        var gain = \gain.kr(1);
        
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

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        delayTime: ControlSpec(0.01, 2.0, 'lin', 0, 0.4, "s"),
        feedback: ControlSpec(0.0, 0.99, 'lin', 0, 0.5, "%"),
        wetLevel: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        gain: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "x")
    ));

    OSCdef(\pingPongData).free;
	OSCdef(\pingPongData, { |msg|
		var a = msg[3];
		var b = msg[4];
		~o.sendMsg('\pingPongData', 
			a, b
    );  	}, '/pingPongData', s.addr);

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