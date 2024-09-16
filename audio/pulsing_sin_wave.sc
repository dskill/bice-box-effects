Server.freeAll;

s.boot;
(
s.waitForBoot{

var o = NetAddr.new("127.0.0.1", 57121);
// from the wonderful forum post:
// https://scsynth.org/t/is-it-possible-to-get-the-output-of-a-ugen-as-an-array-of-floats-audio-buffer/6539/14
var chunkSize = 1024; // 2 waveforms at 128 resolution is about the most the PI will handle right now
// when we send OSC messages, we need to downsample the waveform data.  On PI, it can handle about 6k samples per second.
// we're running at 44k, so downsampling by 8 is enough for good performance
// the waveform resolution can be as high as 2048 and the webGL thread will still be able to keep up.
// if we wanted higher resolution, we could compromise by sending the data at a lower framerate
// that would create discontinuities between OSC messages, but in the extreme case we could just send
// a single OSC message of 2048 samples.  That should probably be fine, but we would only want to send the sample
// a few times a second.  Anyway... there are a lot of knobs to tune and as long as we stay under 6k samples per second
// we should be fine.


var chunkDownsample = 2;
var inputChannel = 0;
var numChunks = 16;
var relay_buffer0 = Buffer.alloc(s, chunkSize * numChunks);
var relay_buffer1 = Buffer.alloc(s, chunkSize * numChunks);
var relay_buffer2 = Buffer.alloc(s, 4096 * 2);

    SynthDef(\pulsing_sin_wave, {
        |out = 0, rate = 2, depth = 0.5, randFreqMin = 200, randFreqMax = 800|

        // Create the LFO and signal
        var lfo = SinOsc.kr(rate) * depth + (1 - depth);
        var freq = LFNoise1.kr(rate).exprange(randFreqMin, randFreqMax);
        var sig = SinOsc.ar(freq) * lfo;
        var final_sig = SinOsc.ar(freq * 1) * 0.2;

	// MACHINERY FOR SAMPLING THE SIGNAL
	var phase = Phasor.ar(0, 1, 0, chunkSize);
	// btw this is already guaranteed to be only a single-sample trigger
	// Trig.ar is not needed
	var trig = HPZ1.ar(phase) < 0;
	var partition = PulseCount.ar(trig) % numChunks;
	var fixed_timing_reset_trig = Impulse.ar(10);
	// write to buffers that will contain the waveform data we send via OSC
	BufWr.ar(sig, relay_buffer0, phase + (chunkSize * partition));
	BufWr.ar(final_sig, relay_buffer1, phase + (chunkSize * partition));

	// send data as soon as it's available and orrect
	SendReply.ar(trig, '/buffer_refresh', partition);
	// or send data at a specified framerate. This is wrong if you're sending a real audio signal
	//SendReply.ar(fixed_timing_reset_trig, '/buffer_refresh', partition);

	Out.ar(out, final_sig);


    }).add;

	OSCdef(\k, { |msg|
	// the partition to retrieve is the one BEFORE the latest transition point
	var partition = (msg[3] - 1) % numChunks;
	relay_buffer0.getn(partition.asInteger * chunkSize, chunkSize, { |data|
		// downsample data
		//data.size.postln;
		data = data.resamp1(data.size/chunkDownsample);
		//data.size.postln;
		o.sendMsg(\waveform0, *(data.as(Array)));
	});

	relay_buffer1.getn(partition.asInteger * chunkSize, chunkSize, { |data|
		data = data.resamp1(data.size/chunkDownsample);
		o.sendMsg(\waveform1, *(data.as(Array)));
	});

}, '/buffer_refresh');

    Server.default.sync;


    // Create the Synth after synchronization
    ~pulsing_sin_wave = Synth(\pulsing_sin_wave);
};
)
