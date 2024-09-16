Server.freeAll;
"Server.freeAll called".postln;

s.boot;
"Server boot initiated".postln;

(
s.waitForBoot{
    var o, chunkSize, chunkDownsample, numChunks, relay_buffer0, relay_buffer1;
    var rms_bus_input, rms_bus_output;

    "Server booted, initializing...".postln;

    o = NetAddr.new("127.0.0.1", 57121);
    chunkSize = 1024;
    chunkDownsample = 2;
    numChunks = 16;

    "Network address and variables initialized".postln;

    // Free existing buffers if they exist
    if(relay_buffer0.notNil, { 
        "Freeing existing relay_buffer0".postln;
        relay_buffer0.free 
    });
    if(relay_buffer1.notNil, { 
        "Freeing existing relay_buffer1".postln;
        relay_buffer1.free 
    });

    relay_buffer0 = Buffer.alloc(s, chunkSize * numChunks);
    relay_buffer1 = Buffer.alloc(s, chunkSize * numChunks);
    "New relay buffers allocated".postln;

    rms_bus_input = Bus.control(s, 1);
    rms_bus_output = Bus.control(s, 1);
    "RMS control buses created".postln;

    SynthDef(\effect, {
        |out = 0, 
        // START USER EFFECT CODE
        rate = 2, depth = 0.5, wetLevel = 0.5, rms_out_input, rms_out_output|
        var sig, trem, dry, phase, trig, partition;

        sig = SoundIn.ar([0]);
        trem = sig * (depth * SinOsc.kr(rate) + (1 - depth));
        dry = sig * (1 - wetLevel);
        sig = dry + (trem * wetLevel);

        // END USER EFFECT CODE
        
        // Calculate RMS values
        Out.kr(rms_out_input, RunningSum.kr(SoundIn.ar([0]).squared, 1024).sqrt / 32);
        Out.kr(rms_out_output, RunningSum.kr(sig.squared, 1024).sqrt / 32);

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(SoundIn.ar([0]), relay_buffer0, phase + (chunkSize * partition));
        BufWr.ar(sig, relay_buffer1, phase + (chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

        Out.ar(out, sig);
    }).add;
    "Effect SynthDef added".postln;

    // Remove existing OSCdef if it exists
    OSCdef(\k).free;
    "Existing OSCdef freed".postln;

    OSCdef(\k, { |msg|
        var partition = (msg[3] - 1) % numChunks;

        relay_buffer0.getn(partition.asInteger * chunkSize, chunkSize, { |data|
            data = data.resamp1(data.size/chunkDownsample);
            o.sendMsg(\waveform0, *(data.as(Array)));
        });

        relay_buffer1.getn(partition.asInteger * chunkSize, chunkSize, { |data|
            data = data.resamp1(data.size/chunkDownsample);
            o.sendMsg(\waveform1, *(data.as(Array)));
        });

        // Send RMS values
        o.sendMsg(\audio_analysis, rms_bus_input.getSynchronous, rms_bus_output.getSynchronous);
    }, '/buffer_refresh');
    "New OSCdef created".postln;

    Server.default.sync;
    "Server synced".postln;

    // Free existing synth if it exists
    if(~effect.notNil, { 
        "Freeing existing effect synth".postln;
        ~effect.free 
    });

    // Create the Synth after synchronization
    ~effect = Synth(\effect, [
        rms_out_input: rms_bus_input,
        rms_out_output: rms_bus_output
    ]);
    "New effect synth created".postln;

    "Initialization complete".postln;
};
)