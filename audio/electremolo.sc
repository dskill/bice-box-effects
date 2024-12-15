(
    SynthDef(\electremolo, {
        |out = 0, in_bus = 0, rate = 2, depth = 0.5, mix = 1.0|
        // START USER EFFECT CODE
        var sig, trem, dry, wet, finalSig, tremMult;
        var phase, trig, partition, kr_impulse, chain_out, rms_input, rms_output;
        var waveShapedSig;
        sig = In.ar(in_bus);
        tremMult = depth * SinOsc.kr(rate, 0, 1.0) + (1 - depth) + 0.5;
        // waveShapedSig = (sig * 10.0).tanh;  // Using tanh as waveshaper
        // low pass filter the wave shaped signal
        //waveShapedSig = LPF.ar(waveShapedSig, 2000);
        // Better phaser
        /*
        waveShapedSig = Mix.fill(6, {|i|
            var delayTime = SinOsc.kr(
                rate * 0.5, // modulation rate
                i * (2pi/6), // phase offset for each stage
                0.001, // modulation depth
                0.002  // center delay time
            );
            AllpassC.ar(waveShapedSig, 0.02, delayTime, 0.1)
        }) / 6 + waveShapedSig;
        */
        
        trem = sig * tremMult; 

        // note, don't put these in the same line, it's a bug
        dry = sig * (1.0 - mix);
        wet = trem * mix;
        finalSig =  dry + wet;
        
        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(finalSig, 1024);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // FFT Analysis
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // FFT
        //chain_out = FFT(~fft_buffer_out, sig, wintype: 1);
        //chain_out.do(~fft_buffer_out);

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/rms'); //trig if you want audio rate
        SendReply.kr(kr_impulse, '/electremoloData', [tremMult, depth, mix]);

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        //SendReply.kr(kr_impulse, '/fft_data');

        Out.ar(out, [finalSig,finalSig]);
    }).add;
    "Effect SynthDef added".postln;

    // OSC responder to send tuner data to the client
	OSCdef(\electremoloData).free;
	OSCdef(\electremoloData, { |msg|
		var a = msg[3];
        var b = msg[4];
        var c = msg[5];
		// Send the data to the client
		~o.sendMsg(\electremoloData, 
			a, b, c
    );  	}, '/electremoloData', s.addr);

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new tremolo synth in the effect group
        ~effect = Synth(\electremolo, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)