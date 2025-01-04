(
    SynthDef(\outrun, {
        |out = 0, in_bus = 0, drive = 1.0, gridSpeed = 0.5, sunSize = 0.5, glow = 0.7, synthDepth = 0.5, mix = 0.5|
        var sig, wet, dry;
        var chain_in, chain_out, kr_impulse;
        var rms_input, rms_output;
        var chorus, filtered;
        var phase, trig, partition;
        
        sig = In.ar(in_bus);
        dry = sig;
        wet = sig;        
        //wet = sig * (drive * 2);
        //wet = (2/pi) * atan(wet);
        


        // Multi-voice chorus for that 80s width
        chorus = Mix.fill(3, {|i|
            var rate = gridSpeed * (i + 1) * 0.5;
            DelayC.ar(wet, 0.05, SinOsc.kr(rate, 0, 0.002 * synthDepth, 0.003))
        }) / 3;

        LocalOut.ar(chorus); // Send reverb output back as feedback

        // Big fat reverb with feedback loop
        chorus = XFade2.ar(chorus, LocalIn.ar(1) * 0.8 + chorus * 0.2, mix * 2 - 1);

        chorus = FreeVerb.ar(chorus,
            mix: 0.8,        // reverb mix
            room: 0.8,       // room size (0-1)
            damp: 0.2        // high frequency damping
        );
        
        // Frequency shaping
        filtered = BLowShelf.ar(chorus, 400, 1.0, 4.0);
        filtered = BPeakEQ.ar(filtered, 1200, 1.0, 3.0);
        filtered = BHiShelf.ar(filtered, 3000, 1.0, glow * 6.0);
        
        // Final mix
        sig = XFade2.ar(dry, filtered, mix * 2 - 1);
                
        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second
       
        // Write to buffers for waveform data
        BufWr.ar(dry, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));
        
        rms_input = RunningSum.rms(dry, 1024);
        rms_output = RunningSum.rms(sig, 1024);
        
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/rms');
        
        Out.ar(out, [sig, sig]);
    }).add;
    
    "Effect SynthDef added".postln;
    
    fork {
        s.sync;
        
        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };
        
        ~effect = Synth(\outrun, [\in_bus, ~input_bus], ~effectGroup);
    };
) 