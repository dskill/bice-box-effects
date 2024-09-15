Server.freeAll;
"Server.freeAll called".postln;

s.boot;
"Server boot initiated".postln;

(
s.waitForBoot{
    var o, chunkSize, chunkDownsample, numChunks, relay_buffer0, relay_buffer1;

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

    SynthDef(\ping_pong_delay, {
        |out = 0, delayTime = 0.4, feedback = 0.5, wetLevel = 0.5, gain = 1|
        var sig, leftDelay, rightDelay, delaySig, dry, fbNode, finalSig;
        var phase, trig, partition;

        sig = SoundIn.ar([0, 1]);
        fbNode = LocalIn.ar(2);
        leftDelay = DelayC.ar(sig[0] + fbNode[1], 2, delayTime);
        rightDelay = DelayC.ar(sig[1] + fbNode[0], 2, delayTime);
        LocalOut.ar([leftDelay, rightDelay] * feedback);
        delaySig = [leftDelay, rightDelay];
        dry = sig * (1 - wetLevel);
        finalSig = (dry + (delaySig * wetLevel)) * gain;

        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig[0], relay_buffer0, phase + (chunkSize * partition));
        BufWr.ar(finalSig[0], relay_buffer1, phase + (chunkSize * partition));

        // send data as soon as it's available
        SendReply.ar(trig, '/buffer_refresh', partition);

        Out.ar(out, finalSig);
    }).add;
    "Ping Pong Delay SynthDef added".postln;

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
    }, '/buffer_refresh');
    "New OSCdef created".postln;

    Server.default.sync;
    "Server synced".postln;

    // Free existing synth if it exists
    if(~ping_pong_delay.notNil, { 
        "Freeing existing ping_pong_delay synth".postln;
        ~ping_pong_delay.free 
    });

    // Create the Synth after synchronization
    ~ping_pong_delay = Synth(\ping_pong_delay);
    "New ping_pong_delay synth created".postln;

    "Initialization complete".postln;
};
)