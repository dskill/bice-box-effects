(
    SynthDef(\test_sin_wave, {
        |out = 0, in_bus = 0, freq = 50|
        // START USER EFFECT CODE
        var sig, final_sig;
        var phase, trig, partition, kr_impulse;
        var chain_out;
        var rms_input, rms_output;

        sig = In.ar(in_bus); 
        final_sig = SinOsc.ar(freq) * 0.2;
        
        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(30);  // Trigger 60 times per second

        BufWr.ar(final_sig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        // FFT Analysis (on the output signal)
        chain_out = FFT(~fft_buffer_out, final_sig, wintype: 1);
        chain_out.do(~fft_buffer_out);

        // send data as soon as it's available
        SendReply.kr(kr_impulse, '/combined_data', partition);

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

        // Create new test_sin_wave synth in the effect group
        ~effect = Synth(\test_sin_wave, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)