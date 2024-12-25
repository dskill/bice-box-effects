(
    SynthDef(\flanger, {
        |out = 0, in_bus = 0, rate = 0.5, depth = 0.002, feedback = 0.5, center = 0.005, mix = 0.5|
        // START USER EFFECT CODE
        var sig, flange, mod, wet, final_sig;
        var phase, trig, partition;
        var rms_input, rms_output;
        var chain_in, chain_out, kr_impulse;


        sig = In.ar(in_bus);
        mod = SinOsc.kr(rate).range(center - depth, center + depth);
        flange = DelayC.ar(sig + (LocalIn.ar(1) * feedback), 0.02, mod);
        LocalOut.ar(flange);
        
        wet = flange;
        final_sig = XFade2.ar(sig, wet, mix * 2 - 1);
                
        // Calculate RMS values
        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(final_sig, 1024);
        
        // END USER EFFECT CODE
 
        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second


        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(final_sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // FFT Analysis
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second
        chain_out = FFT(~fft_buffer_out, sig);  // In bypass, input = output
        chain_out.do(~fft_buffer_out);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output); 

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/buffer_refresh', partition);
        SendReply.kr(kr_impulse, '/fft_data');


        Out.ar(out, [final_sig,final_sig]);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new flanger synth in the effect group
        ~effect = Synth(\flanger, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)