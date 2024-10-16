(
    SynthDef(\bypass, {
        |out = 0, in_bus = 0, test = 1|  // Add in_bus parameter
        // START USER EFFECT CODE

        var sig, phase, trig, partition;
        var rms_input, rms_output;
        var chain_in, chain_out, kr_impulse;
        var fft_output, fft_input;
        var freq; 

        sig = In.ar(in_bus);
        //sig = SoundIn.ar(0);
        //freq = LFTri.kr(1/10, 0).range(82.41, 82.41*2);  // Triangle LFO from low E to E one octave up
        //sig = SinOsc.ar(freq, 0, 0.5);

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));
 
        // FFT Analysis
        kr_impulse = Impulse.kr(30);  // Trigger 60 times per second

        // FFT
        chain_out = FFT(~fft_buffer_out, sig, wintype: 1);
        chain_out.do(~fft_buffer_out);

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(sig, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms'); 

	    Out.ar(out, [sig, sig]);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new bypass synth in the effect group
        ~effect = Synth(\bypass, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)
