(
    SynthDef(\bypass, {
        |out = 0, in_bus = 0, test = 1|  // Add in_bus parameter
        // START USER EFFECT CODE

        var sig, phase, trig, partition;
        var rms_input, rms_output;
        var chain_in, chain_out, fft_trig;

        sig = In.ar(in_bus);
        //sig = SoundIn.ar(0);
       // sig = sig + SinOsc.ar(220 * test, 0, 0.5);
        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // FFT Analysis
        fft_trig = Impulse.kr(30);  // Trigger 60 times per second
        chain_in = FFT(~fft_buffer_in, sig);
        chain_out = FFT(~fft_buffer_out, sig);  // In bypass, input = output

        // Store FFT data in buffers
        chain_in.do(~fft_buffer_in);
        chain_out.do(~fft_buffer_out);

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(sig, 1024);

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        // ... existing code ...

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);
        SendReply.ar(trig, '/buffer_refresh', partition);
        SendReply.kr(fft_trig, '/fft_data');


	Out.ar(out, sig);
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
