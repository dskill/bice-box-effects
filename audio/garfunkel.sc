(
    SynthDef(\simple_reverb, {
        |out = 0, in_bus = 0, decay = 1, roomSize = 0.7, wetLevel = 0.5, gain = 1|
        // START USER EFFECT CODE
        var sig, verb, dry, finalSig;
        var phase, trig, partition, kr_impulse;
        var rms_input, rms_output;
        var chain_out, chain_in;
        var predelay, dampedSig;
        var doubled, detune, modulation;

        sig = In.ar(in_bus);
        
        // Create voice doubling effect
        detune = SinOsc.kr(0.5).range(0.99, 1.01);  // Subtle pitch variation
        modulation = SinOsc.kr(0.2).range(0.005, 0.012);  // Time modulation
        doubled = DelayC.ar(sig, 0.05, modulation) * detune;  // Delayed & modulated copy
        doubled = PitchShift.ar(doubled, 0.2, detune, 0.01, 0.01);  // Slight pitch shifting
        
        // Mix original and doubled signal
        sig = (sig + (doubled * 0.8)) * 0.7;  // Blend signals with some level adjustment
        
        // Longer predelay for that classic Garfunkel sound 
        predelay = DelayN.ar(sig, 0.05, 0.04);
        
        // Gentle high shelf boost before reverb for air
        dampedSig = BHiShelf.ar(predelay, 8000, 1, 2);
        
        // More complex reverb chain for that cathedral-like sound
        verb = FreeVerb.ar(dampedSig, 
            mul: decay * 1.5,  // Increased decay multiplier
            room: roomSize * 1.4,  // Larger room size
            damp: 0.2  // Less damping for brighter reverb tail
        );
        
        // Additional reverb layer for depth
        verb = verb + (FreeVerb.ar(DelayN.ar(dampedSig, 0.03, 0.02),
            mul: decay * 0.8,
            room: roomSize * 1.2,
            damp: 0.3
        ) * 0.4);
        
        // Smoother compression
        verb = CompanderD.ar(verb, 0.4, 1, 1/2);
        
        dry = sig * (1 - wetLevel);
        finalSig = (dry + (verb * wetLevel)) * gain;
        
        // Enhanced warmth with subtle harmonics
        finalSig = finalSig + (LPF.ar(finalSig, 300) * 0.15) + (HPF.ar(finalSig, 8000) * 0.1);

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(30);

        // Add FFT Analysis
        chain_out = FFT(~fft_buffer_out, finalSig, wintype: 1);
        chain_out.do(~fft_buffer_out);

        // Add RMS calculations
        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(finalSig, 1024);

        // Add RMS outputs
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        // Add new SendReply messages
        SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms');

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate

        Out.ar(out, [finalSig,finalSig]);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new simple_reverb synth in the effect group
        ~effect = Synth(\simple_reverb, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)