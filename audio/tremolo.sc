(
    SynthDef(\tremolo, {
        |out = 0, in_bus = 0, rate = 2, depth = 0.5, wetLevel = 0.5, reverbMix = 0.3, reverbRoom = 2.5, reverbDamp = 0.5|
        // START USER EFFECT CODE
        var sig, trem, dry, wetTrem, reverbSig, finalSig;
        var phase, trig, partition, kr_impulse, chain_out, rms_input, rms_output;

        sig = In.ar(in_bus);
        
        // Spectral processing for ghostly effect
        sig = FFT(LocalBuf(2048), sig);
        sig = PV_MagSmear(sig, 3);
        sig = PV_MagShift(sig, 1.5);
        sig = IFFT(sig);
        
        // Modulated tremolo with slower, irregular rate
        trem = sig * (depth * SinOsc.kr(LFNoise2.kr(0.2).range(rate * 0.5, rate * 1.5)) + (1 - depth));
        
        // Add pitch-shifting for eerie effect
        // trem = PitchShift.ar(trem, 0.1, LFNoise2.kr(0.1).range(0.95, 1.05));
        
        dry = sig * (1 - wetLevel);
        wetTrem = trem * wetLevel;
        
        // Apply reverb with longer decay
        reverbSig = FreeVerb.ar(wetTrem, reverbMix, reverbRoom * 1.5, reverbDamp * 0.7);
        
        // Add subtle distortion and filtering
        reverbSig = (reverbSig * 10).tanh * 0.5;
        reverbSig = LPF.ar(reverbSig, LFNoise2.kr(0.1).range(200, 5000));
        
        finalSig = Mix([dry, reverbSig]);
        
        // Add subtle white noise for ghostly atmosphere
        finalSig = finalSig + (PinkNoise.ar(0.01) * LFNoise2.kr(0.1).range(0, 1));

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
        chain_out = FFT(~fft_buffer_out, sig, wintype: 1);
        chain_out.do(~fft_buffer_out);

        // write to buffers that will contain the waveform data we send via OSC
        //BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        //BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/rms'); //trig if you want audio rate

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        SendReply.kr(kr_impulse, '/fft_data');

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

        // Create new tremolo synth in the effect group
        ~effect = Synth(\tremolo, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)
