(
    SynthDef(\ping_pong_delay, {
        |out = 0, in_bus = 0, analysis_out_bus, delayTime = 0.4, feedback = 0.5, wetLevel = 0.5, gain = 1|
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
    }).add;
    "Effect SynthDef added".postln;

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

        ~effect = Synth(\ping_pong_delay, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \delayTime, 0.4,
            \feedback, 0.5,
            \wetLevel, 0.5,
            \gain, 1
        ], ~effectGroup);
        ("New ping_pong_delay synth created with analysis output bus").postln;
    };
)