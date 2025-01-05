(
    SynthDef(\spacedrive, {
        |out = 0, in_bus = 0, 
        gain = 1.0, tone = 0.1, res = 1.37, level = 0.75, mix = 0.5|  // Simplified parameters
        var sig, distorted;
        var rms_input, rms_output;
        var phase, trig, partition, kr_impulse;

        sig = In.ar(in_bus);
        
        // Simplified distortion chain using soft_fuzz approach
        distorted = sig + sig * gain * 10.0;  // Gain staging similar to soft_fuzz
        distorted = distorted.softclip;  // Simple softclip instead of complex distortion
        
        // MoogFF filter for tone shaping (replacing previous EQ setup)
        distorted = MoogFF.ar(
            in: distorted,
            freq: (100 + (18e3 * tone)),  // Same frequency range as soft_fuzz
            gain: res
        );
        
        distorted = distorted * level;  // Output level control
        distorted = LeakDC.ar(distorted);  // Clean up DC offset
        distorted = XFade2.ar(sig, distorted, mix*2.0-1.0);

        // ... existing monitoring code ...
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);

        // ... existing buffer writing and monitoring ...
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(distorted, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(distorted, 1024);
        
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/rms'); 

        Out.ar(out, [distorted, distorted]);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\spacedrive, [\in_bus, ~input_bus], ~effectGroup);
    };
)
