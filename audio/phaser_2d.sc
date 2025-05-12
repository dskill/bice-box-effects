(
    SynthDef(\phaser_2d, {
        |out = 0, in_bus = 0, x = 0.5, y = 0.5, mix = 1.0|
        var sig, dry, processed, phase, trig, partition, kr_impulse;
        var rms_input, rms_output;
        var numStages, freq, feedback;

        sig = In.ar(in_bus);
        dry = sig;

        // Map x to number of stages (2 to 12)
        numStages = x.linlin(0, 1, 2, 12).round;

        // Map y to LFO frequency (0.1 Hz to 5 Hz)
        freq = y.linexp(0, 1, 0.1, 5);

        // Create the phaser effect
        processed = sig; // Start with dry signal
        1.do({ arg i; // Do this 'numStages' times
            processed = AllpassN.ar(
                in: processed,
                delaytime: LFNoise1.kr(freq / (i + 1), 0.01, 0.02), // Varying delay times for each stage
                decaytime: 0.1, // Constant decay time
                mul: 0.99 // Prevent runaway feedback
            );
        });


        // Mix the dry and processed signals
        processed = XFade2.ar(dry, processed, mix * 2 - 1);
       
        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(processed, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(processed, 1024);
        
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        kr_impulse = Impulse.kr(60);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        SendReply.kr(kr_impulse, '/rms'); 

	    Out.ar(out, [processed, processed]);
    }).add;
    "Phaser 2D SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, { ~effect.free; });

        ~effect = Synth(\phaser_2d, [\in_bus, ~input_bus], ~effectGroup);

        // OSC responder for phaser parameters
        OSCdef.new(
            \phaserParamsListener, // A unique key for this OSCdef
            { |msg, time, addr, recvPort|
                var x_val = msg[1];
                var y_val = msg[2];
                // ("Received /phaser/params: X=" ++ x_val ++ ", Y=" ++ y_val).postln; // For debugging
                if(~effect.notNil, {
                    ~effect.set(\x, x_val, \y, y_val);
                });
            },
            '/params', // The OSC address to listen to
            nil // Listen on any client address
        );
    };
)