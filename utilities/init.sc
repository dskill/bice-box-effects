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
	if(~relay_buffer0.notNil, { ~relay_buffer0.free });
	if(~relay_buffer1.notNil, { ~relay_buffer1.free });

	~relay_buffer0 = Buffer.alloc(s, ~chunkSize * ~numChunks);
	~relay_buffer1 = Buffer.alloc(s, ~chunkSize * ~numChunks);
	"New relay buffers allocated".postln;

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
	~buffer = Buffer.read(Server.default, "~/bice-box-effects/resources/guitar_riff.wav".standardizePath, action: { |buf|
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

	// Remove existing OSCdef if it exists
	OSCdef(\k).free;
	"Existing OSCdef freed".postln;

	OSCdef(\k, { |msg|
		var partition = (msg[3] - 1) % ~numChunks;

		[~relay_buffer0, ~relay_buffer1].do { |buf, i|
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
		~o.sendMsg(\audio_analysis, ~rms_bus_input.getSynchronous, ~rms_bus_output.getSynchronous);
	}, '/buffer_refresh');

	"New OSCdef created".postln;

	s.sync;
	"Server synced".postln;

	// Add this to verify buffer allocation, input bus, and groups
	["Buffer 0:", ~relay_buffer0, "Buffer 1:", ~relay_buffer1, "Input Bus:", ~input_bus].postln;

	"Server booted successfully.".postln;
};
)


