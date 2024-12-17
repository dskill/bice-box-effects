(
    SynthDef(\oscilloscope, {
        |out = 0, in_bus = 0, bits = 8, rate = 0.5, mix = 1.0, 
        cutoff = 2000, reverb = 0.3, delay = 0.25, delay_mix = 0.2|
        var sig, crushed, filtered, wet, delayed, phase, trig, partition;
        var chain_in, chain_out, kr_impulse;
        var fft_output, fft_input;
        var rms_input, rms_output;

        sig = In.ar(in_bus);
         
        // Bitcrusher effect
        crushed = sig;
        crushed = crushed.round(2.pow(bits).reciprocal);
        crushed = Latch.ar(crushed, Impulse.ar(rate * SampleRate.ir * 0.5));

        // Low pass filter
        filtered = LPF.ar(crushed, cutoff);
        
        // Delay
        delayed = DelayL.ar(filtered, 1.0, delay);
        filtered = (filtered * (1 - delay_mix)) + (delayed * delay_mix);
        
        // Reverb
        wet = FreeVerb.ar(filtered, reverb, 0.8, 0.5);

        // Blend original and processed signals
        crushed = (wet * mix) + (sig * (1 - mix));

        // Remove DC offset
        crushed = LeakDC.ar(crushed);
        
        

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // Write to buffers for waveform data
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(crushed, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

         // FFT
        //chain_out = FFT(~fft_buffer_out, distorted, wintype: 1);
        //chain_out.do(~fft_buffer_out);

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(crushed, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        //SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms'); 

        Out.ar(out, [crushed, crushed]);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\oscilloscope, [\in_bus, ~input_bus], ~effectGroup);
    };
)
