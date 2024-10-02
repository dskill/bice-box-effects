(
    SynthDef(\tuner, {
        |out = 0, in_bus = 0|
        var sig, freq, hasFreq, differences, amplitudes;
        var phase, trig, partition;
        var rms_input, rms_output;
        var guitarStringsHz = #[82.41, 110.00, 146.83, 196.00, 246.94, 329.63]; // Frequencies of E2, A2, D3, G3, B3, E4

        // Input signal
        sig = In.ar(in_bus);

        // Pitch detection 
        # freq, hasFreq = Pitch.kr(sig, ampThreshold: 0.02, median: 7);
        freq = Lag.kr(freq, 0.1); // Smooth frequency changes over 100ms
         
        // Calculate differences between detected frequency and each guitar string
        differences = freq - guitarStringsHz;

        // Extract amplitude for each string frequency using bandpass filters
        amplitudes = guitarStringsHz.collect { |hz|
            var band = BPF.ar(sig, hz, 0.01); // Bandpass filter with narrow bandwidth
            Amplitude.kr(band)
        };

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(sig, 1024);

        // Write to buffers
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        // Send tuner data to the client
        SendReply.ar(trig, '/buffer_refresh', partition);
        SendReply.kr(Impulse.kr(10), '/tuner_data', [freq, hasFreq] ++ differences ++ amplitudes);
        
        Out.ar(out, sig);
    }).add;
    "Tuner SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new tuner synth in the effect group
        ~effect = Synth(\tuner, [\in_bus, ~input_bus], ~effectGroup);
        "New tuner synth created".postln;
    };
)
