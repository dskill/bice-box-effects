(
    SynthDef(\neon_love, {
        |out = 0, in_bus = 0, decay = 1.0, roomSize = 0.7, intensity = 1.3, speed = -0.5, mix = 0.5|
        var sig, verb, dry, finalSig,
            dampedSig,
            phase, trig, partition, kr_impulse,
            rms_input, rms_output,
            chain_out,
            flanger, flangerTime, flangerDepth, flangerRate;

        sig = In.ar(in_bus);
        
        //////////////////////////////////////////////
        // Gentle EQ adjustments before the reverb
        //////////////////////////////////////////////

        // Reduced overall boost and frequency for a less "tizzy" high end
        dampedSig = BHiShelf.ar(sig, 6000, 0.7, 1.2);
        
        // Slightly more predelay for spaciousness
        // dampedSig = DelayN.ar(dampedSig, 0.2, predelay);
        
        //////////////////////////////////////////////
        // Main reverb with increased damping
        //////////////////////////////////////////////

        verb = FreeVerb.ar(
            dampedSig, 
            mul: decay,    // slightly less reverb multiplier
            room: roomSize * 2.0,      // keep default room size
            damp: 0.4            // increased damping for less brightness
        );
        
        //////////////////////////////////////////////
        // Secondary reverb layer, lowered volume
        //////////////////////////////////////////////

        verb = verb + (
            FreeVerb.ar(
                DelayN.ar(dampedSig, 0.03, 0.02),
                mul: decay * 0.5,
                room: roomSize * 4.0,
                damp: 0.5
            ) * 0.3  // lower blend
        );
        
        //////////////////////////////////////////////
        // Smooth compression
        //////////////////////////////////////////////

        verb = CompanderD.ar(verb, 0.4, 1, 0.5);
        
        //////////////////////////////////////////////
        // Combine dry + wet signals
        //////////////////////////////////////////////

        dry = sig * (1 - mix);
        finalSig = dry + (verb * mix);
        
        //////////////////////////////////////////////
        // Subtle low-frequency warmth + dialed-down high shelf
        //////////////////////////////////////////////

        finalSig = finalSig
          + (LPF.ar(finalSig, 300) * 0.1)
          + (HPF.ar(finalSig, 8000) * 0.05); // smaller high-end contribution

        //////////////////////////////////////////////
        // Flanger section, toned down
        //////////////////////////////////////////////

        flangerDepth = 0.008 * intensity;   // slightly reduced depth
        flangerRate  = speed;              // keep speed param
        flangerTime  = 0.004;              // slightly shorter base delay

        // CombN for flanger, lowered feedback
        flanger = CombN.ar(
            finalSig,
            0.1,
            flangerTime + SinOsc.kr(flangerRate, 0, flangerDepth, flangerDepth),
            0.2 // reduced decay for less metallic tone
        ) * 0.3; // lowered mix of the flanger

        // Mix flanger in gently, no additional amplitude boost
        finalSig = finalSig + flanger;

        //////////////////////////////////////////
        // MACHINERY FOR SAMPLING / OSC REPORTING
        //////////////////////////////////////////

        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60); // 60 times per second

        // Write to buffers for waveform data
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // FFT Analysis
        chain_out = FFT(~fft_buffer_out, finalSig, wintype: 1);
        chain_out.do(~fft_buffer_out);

        // RMS calculations
        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(finalSig, 1024);

        // Send analysis data (RMS, buffer refresh, FFT)
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms');


        // Output the effect in stereo
        Out.ar(out, [finalSig, finalSig]);
    }).add;

    "Effect SynthDef added".postln;

 
    

    // Launch the effect
    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\neon_love, [\in_bus, ~input_bus], ~effectGroup);
    };
) 