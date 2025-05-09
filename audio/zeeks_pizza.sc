// Pizza Phaser Effect
// Combines phaser with amplitude modulation for a "hungry to full" cycle effect
(
SynthDef(\zeeks_pizza, {
    arg out=0, in_bus=0,
    // Phaser parameters
    rate=0.5, // Speed of phaser oscillation
    depth=0.5, // Depth of phaser effect
    // Amplitude modulation
    modRate=0.2, // Speed of AM
    modDepth=0.3, // Depth of AM
    // Mix
    mix=0.5;

    var input = In.ar(in_bus);
    var numStages = 6;
    var freq = 100;
    var modPhase;
    var phaser;
    var output;
    var analysis;
    var chain_out;
    var rms_input;
    var rms_output;
    var phase;
    var trig;
    var partition;
    var kr_impulse;
    
    // Create phaser effect
    modPhase = SinOsc.kr(rate, 0, depth * 800, 1000 + freq);
    phaser = input;
    numStages.do {
        phaser = AllpassL.ar(phaser, 0.1, modPhase.reciprocal, 0);
    };
    
    // Add amplitude modulation
    output = phaser * (1 - (modDepth * SinOsc.kr(modRate)));
    
    // Mix dry and wet signals
    output = (input * (1 - mix)) + (output * mix);
    output = output;
      
    // MACHINERY FOR SAMPLING THE SIGNAL
    phase = Phasor.ar(0, 1, 0, ~chunkSize);
    trig = HPZ1.ar(phase) < 0;
    partition = PulseCount.ar(trig) % ~numChunks;
    kr_impulse = Impulse.kr(60);  // Trigger 60 times per second

    // Write to buffers for waveform data
    BufWr.ar(input, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
    BufWr.ar(output, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

    // FFT Analysis
    //chain_out = FFT(~fft_buffer_out, input, wintype: 1);
    //chain_out.do(~fft_buffer_out);

    rms_input = RunningSum.rms(input, 1024);
    rms_output = RunningSum.rms(output, 1024);

    // Send RMS values to the control buses
    Out.kr(~rms_bus_input, rms_input);
    Out.kr(~rms_bus_output, rms_output);
    SendReply.kr(kr_impulse, '/buffer_refresh', partition);
    //SendReply.kr(kr_impulse, '/fft_data');
    SendReply.kr(kr_impulse, '/rms'); 

    Out.ar(out, [output, output]);
}).add;

// Add execution code

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\zeeks_pizza, [\in_bus, ~input_bus], ~effectGroup);
    };
) 