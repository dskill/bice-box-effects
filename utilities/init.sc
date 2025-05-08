"Freeing synths and setting default options...".postln;
Server.default.options.sampleRate = 48000;
Server.default.options.memSize = 512 * 1024;  // Set memory to 512MB
"About to start booting...".postln;
(
s.waitForBoot{
	"Server booted, initializing...".postln;
	Server.freeAll;

	~useTestLoop = true;  

	~o = NetAddr.new("127.0.0.1", 57121);
	~chunkSize = 512;
	~chunkDownsample = 1;
	~numChunks = 16;
	"Network address and variables initialized".postln;

	~rms_bus_input = Bus.control(s, 1);
	~rms_bus_output = Bus.control(s, 1);
	"RMS control buses created".postln;

	// Free existing buffers if they exist
	if(~relay_buffer_in.notNil, { ~relay_buffer_in.free });
	if(~relay_buffer_out.notNil, { ~relay_buffer_out.free });

	~relay_buffer_in = Buffer.alloc(s, ~chunkSize * ~numChunks);
	~relay_buffer_out = Buffer.alloc(s, ~chunkSize * ~numChunks);
	"New relay buffers allocated".postln;

	~fft_size = 4096;  // Set the FFT size
	~fft_buffer_out = Buffer.alloc(s, ~fft_size);
	"FFT buffers allocated".postln;

	~audio_input_bus = Bus.audio(s, 2);  // Ensure this is defined early
	~test_loop_bus = Bus.audio(s,2);

	// Create groups for effects and sources
	s.sync;
	~effectGroup = Group.new;
	s.sync;
	~sourceGroup = Group.new(~effectGroup, \addBefore);
	"Effect and source groups created".postln;

	SynthDef(\audioIn, {
		var sig = SoundIn.ar([0]);  // Assuming stereo input
		Out.ar(~audio_input_bus, sig);	
	}).add;
	s.sync;
	
	~audioInSynth = Synth(\audioIn, target: ~sourceGroup);

	// in case we want a bus with some looping guitar
	~buffer = Buffer.read(Server.default, "~/bice-box-effects/resources/karaoke_shack_riff_mono.wav".standardizePath, action: { |buf|
		("Buffer loaded. Original sample rate: " ++ buf.sampleRate).postln;
		("Server sample rate: " ++ Server.default.sampleRate).postln;
		("Buffer channels: " ++ buf.numChannels).postln;

		Routine {
			// Update SynthDef to handle stereo input
			SynthDef(\playGuitarRiff, {
				var sig = PlayBuf.ar(buf.numChannels, buf, BufRateScale.kr(buf), loop: 1);
				Out.ar(~test_loop_bus, sig);
			}).add;

			s.sync;

			// Create the guitar riff synth in the source group
			~guitarRiffSynth = Synth(\playGuitarRiff, target: ~sourceGroup);
			"Guitar riff synth created in source group".postln;

			s.status;
			"done with guitar riff".postln;
		}.play;
	});


	// select which bus the effects should use
	~input_bus = if (~useTestLoop,
		{ ~test_loop_bus },
		{ ~audio_input_bus }
	);

	OSCdef(\buffer_refresh, { |msg|
		var partition = (msg[3] - 1) % ~numChunks;

		[~relay_buffer_in, ~relay_buffer_out].do { |buf, i|
			if(buf.notNil, {
				buf.getn(partition.asInteger * ~chunkSize, ~chunkSize, { |data|
					data = data.resamp1(data.size/~chunkDownsample);
					~o.sendMsg(("waveform" ++ i).asSymbol, *(data.as(Array)));
				});
			}, {
				"Warning: Relay buffer % is nil".format(i).postln;
			}); 
		};

		// Send RMS values
		//~o.sendMsg(\audio_analysis, ~rms_bus_input.getSynchronous, ~rms_bus_output.getSynchronous);

	}, '/buffer_refresh');


	OSCdef(\rms, { |msg|
		// Send RMS values
		~o.sendMsg(\audio_analysis, ~rms_bus_input.getSynchronous, ~rms_bus_output.getSynchronous);

	}, '/rms');
	

	// Add new OSCdef for FFT data
	// we only output to fft_data1 for now, because it's processing
	// intensive to send the input buffer to the client
	// and we only really care about the output buffer
	// we use the 1 suffix to match the waveform1 message
	OSCdef(\fftData).free;
	OSCdef(\fftData, { |msg|
		if(~fft_buffer_out.notNil, {
			~fft_buffer_out.getn(0, ~fft_size, { |data|
				~o.sendMsg(\fft_data1, *data);
			});
		}, {
			"Warning: FFT buffer is nil".postln;
		});
	}, '/fft_data');

	"FFT OSCdef created".postln;

	// OSC responder to send tuner data to the client
	OSCdef(\tunerData).free;
	OSCdef(\tunerData, { |msg|
		var freq = msg[3];
		var hasFreq = msg[4];
		var differences = msg.copyRange(5, 10); // Differences for six strings
		var amplitudes = msg.copyRange(11, 16); // Amplitudes for six strings
		// Send the data to the client
		~o.sendMsg(\tuner_data, 
			freq, hasFreq, 
			differences[0], differences[1], differences[2], differences[3], differences[4], differences[5],
			amplitudes[0], amplitudes[1], amplitudes[2], amplitudes[3], amplitudes[4], amplitudes[5]
    );  	}, '/tuner_data', s.addr);

	"New OSCdef created".postln;

	s.sync;
	"Server synced".postln;

	// Add this to verify buffer allocation, input bus, and groups
	["Buffer 0:", ~relay_buffer_in, "Buffer 1:", ~relay_buffer_out, "Input Bus:", ~input_bus].postln;

	"Server booted successfully.".postln;
};
)