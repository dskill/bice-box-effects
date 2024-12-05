(
    SynthDef(\overdrive, {
        |out = 0, in_bus = 0, drive = 100.5, tone = 0.5|
        // START USER EFFECT CODE
        var sig, distorted, phase, trig, partition;
        var chain_in, chain_out, kr_impulse;
        var fft_output, fft_input;
        var rms_input, rms_output;

        sig = In.ar(in_bus);
        distorted = (sig * drive).tanh();
        distorted = LPF.ar(distorted, tone.linexp(0, 1, 100, 20000));

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(distorted, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

         // FFT
        chain_out = FFT(~fft_buffer_out, distorted, wintype: 1);
        chain_out.do(~fft_buffer_out);

        

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(sig, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        //SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms'); 

        Out.ar(out, [distorted,distorted]);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

	    ~effect = Synth(\overdrive, [\in_bus, ~input_bus], ~effectGroup);
    };
)
