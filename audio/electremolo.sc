(
    SynthDef(\electremolo, {
        |out = 0, in_bus = 0, rate = 2, depth = 0.5, mix = 0.5|
        // START USER EFFECT CODE
        var sig, trem, dry, wet, finalSig, tremMult;
        var phase, trig, partition, kr_impulse, chain_out, rms_input, rms_output;

        sig = In.ar(in_bus);
        tremMult = depth * SinOsc.kr(rate) + (1 - depth);
        trem = sig * tremMult;

        // note, don't put these in the same line, it's a bug
        dry = sig * (1.0 - mix);
        wet = trem * mix;
        finalSig = dry + wet;

        // END USER EFFECT CODE
        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(finalSig, 1024);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // FFT Analysis
        kr_impulse = Impulse.kr(30);  // Trigger 60 times per second

        // FFT
        //chain_out = FFT(~fft_buffer_out, sig, wintype: 1);
        //chain_out.do(~fft_buffer_out);

        // write to buffers that will contain the waveform data we send via OSC
        //BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        //BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/rms'); //trig if you want audio rate
        SendReply.kr(kr_impulse, '/tremoloData', [tremMult, depth]);

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        //SendReply.kr(kr_impulse, '/fft_data');

        Out.ar(out, [finalSig,finalSig]);
    }).add;
    "Effect SynthDef added".postln;

    // OSC responder to send tuner data to the client
	OSCdef(\tremoloData).free;
	OSCdef(\tremoloData, { |msg|
		var a = msg[3];
        var b = msg[4];
		// Send the data to the client
		~o.sendMsg(\tremoloData, 
			a, b
    );  	}, '/tremoloData', s.addr);

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