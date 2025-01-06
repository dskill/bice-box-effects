(
    SynthDef(\flames, {
        |out = 0, in_bus = 0, 
        gain = 1.0, tone = 0.1, res = 1.37, flameVol = 0.75, mix = 0.5|  // Simplified parameters
        var sig, distorted, flameSig;
        var rms_input, rms_output;
        var phase, trig, partition, kr_impulse;
        var freq, hasFreq;

        sig = In.ar(in_bus);
        
        // Simplified distortion chain using soft_fuzz approach
        distorted = sig + sig * (gain + 0.1) * 40.0;  // Gain staging similar to soft_fuzz
        distorted = distorted.softclip;  // Simple softclip instead of complex distortion
        
        // MoogFF filter for tone shaping (replacing previous EQ setup)
        distorted = MoogFF.ar(
            in: distorted,
            freq: (100 + (10800 * tone)),  // Same frequency range as soft_fuzz
            gain: res
        );
        
        distorted = LeakDC.ar(distorted);  // Clean up DC offset

        // ---------------------
        // Add a "burning flame" noise
        // ---------------------
        // Calculate RMS values
        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(distorted, 1024);


        # freq, hasFreq = Pitch.kr(
					in: sig,
					ampThreshold: 0.02,
					median: 2);

        // Create a more aggressive, fiery sound with sawtooth and noise components
        flameSig = Select.ar(hasFreq, [
            // When no pitch detected (hasFreq == 0), only use noise
            PinkNoise.ar(0.3),
            
            // When pitch is detected (hasFreq == 1), use full flame sound
            (
                LFSaw.ar(freq * 0.5) * 0.7 + // Base sawtooth wave
                LFSaw.ar(freq * 0.502) * 0.3 + // Slightly detuned saw for thickness
                PinkNoise.ar(0.4) // Add some noise for crackling
            )
        ]) * rms_input * 30.0;
        
        // Multi-band filtering for a more complex flame character
        flameSig = BPF.ar(flameSig, [100, 400, 1200], [0.5, 0.7, 0.8]).sum;
        // Add some random amplitude modulation for crackling
        flameSig = flameSig * (LFNoise2.kr(5).range(0.1, 1.0));

        // Add reverb to the flame sound
        flameSig = flameSig * 0.5 + FreeVerb.ar(
            in: flameSig,
            mix: 0.7,        // 40% wet signal
            room: 2.6,       // Medium room size
            damp: 0.9        // Light dampening
        );
        
        // Adjust overall flame volume
        flameSig = flameSig * flameVol * 2.0;
        
        // Combine flame sound with distorted output
        distorted =  distorted + flameSig;

        distorted = XFade2.ar(sig, distorted, mix*2.0-1.0);


        // END USER EFFECT CODE


        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

      
        // ... existing buffer writing and monitoring ...
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(distorted, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/rms');

        // send 
        //SendReply.kr(Impulse.kr(30), '/flamesData', [delayTime, feedback]);

        

        Out.ar(out, [distorted,distorted]);
    }).add;
    "Effect SynthDef added".postln;

    // OSC responder to send tuner data to the client
    /*
	OSCdef(\flamesData).free;
	OSCdef(\flamesData, { |msg|
		var a = msg[3];
		var b = msg[4];
		// Send the data to the client
		~o.sendMsg(\flamesData, 
			a, b
    );  	}, '/flamesData', s.addr);
    */

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new flames synth in the effect group
        ~effect = Synth(\flames, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
) 