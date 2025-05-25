"Freeing synths and setting default options...".postln;
Server.default.options.sampleRate = 48000;
Server.default.options.memSize = 512 * 1024;  // Set memory to 512MB
"About to start booting...".postln;
(
s.waitForBoot{
	"Server booted, initializing...".postln;
	Server.freeAll;

	~useTestLoop = false;  

	~o = NetAddr.new("127.0.0.1", 57121);
	~chunkSize = 1024;
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

	~fft_size = 2048 * 2;  // Increased from 2048 to get 1024 real FFT values after processing. Target 1024 bins.
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



	// Add new OSCdef for combined waveform + FFT data (1024 samples total)
	OSCdef(\combinedData).free;
	OSCdef(\combinedData, { |msg|
		var fftMagnitudes, i, dataIdx, numComplexBins, binsToProcess, dcMag, real, imag, mag, nyquistMag, combinedData;
		var partition = (msg[3] - 1) % ~numChunks;

		if(~relay_buffer_out.notNil and: ~fft_buffer_out.notNil, {
			~relay_buffer_out.getn(partition.asInteger * ~chunkSize, ~chunkSize, { |waveformData| // waveformData is an arg
				// Variables for this waveformData callback scope

				// Resample waveform data if needed, target 1024 samples
				if(waveformData.size != 1024, {
					waveformData = waveformData.resamp1(1024);
				});
				
				~fft_buffer_out.getn(0, ~fft_size, { |fftData| // fftData is an arg
					// All local vars for this fftData callback scope must be declared FIRST

					// THEN, initial assignments to those declared vars:
					fftMagnitudes = Array.newClear(1024); // Increased from 512
					i = 0;
					numComplexBins = (~fft_size / 2).asInteger; // This will be 2048
					binsToProcess = min(1024, numComplexBins); // Target 1024 bins
					// dataIdx, dcMag, real, imag, mag, nyquistMag, combinedData will be initialized later, before use.

					// THEN, the rest of the logic:
					if(fftData.size < ~fft_size, {
						"Warning: Insufficient FFT data for combinedData, got % samples, expected %".format(fftData.size, ~fft_size).postln;
						fftMagnitudes = Array.fill(1024, 0.0); // Fill 1024
					}, {
						// Bin 0: DC component
						if (binsToProcess > 0 and: fftData.size > 0) {
							dcMag = fftData[0].abs; 
							fftMagnitudes[i] = (dcMag + 0.001).log / 10.log;
							i = i + 1;
						};

						// Initialize dataIdx just before the loop that uses it
						dataIdx = 2; // Start from index 2 for complex pairs (real, imag)
						while({i < (binsToProcess -1) and: (dataIdx + 1 < fftData.size)}) { // Ensure we stop before Nyquist if binsToProcess is 1024
							real = fftData[dataIdx] ? 0;
							imag = fftData[dataIdx+1] ? 0;
							mag = (real.squared + imag.squared).sqrt;
							fftMagnitudes[i] = (mag + 0.001).log / 10.log;
							i = i + 1;
							dataIdx = dataIdx + 2;
						};

						// Bin N/2: Nyquist frequency (real part at index 1)
						// For FFT size S, the packed format is:
						// DC (real), Nyquist (real), Real1, Imag1, Real2, Imag2 ... Real(S/2-1), Imag(S/2-1)
						// So fftData[1] is Nyquist.
						if (i < binsToProcess and: fftData.size > 1) { 
							nyquistMag = fftData[1].abs; 
							fftMagnitudes[i] = (nyquistMag + 0.001).log / 10.log;
							i = i + 1;
						};
						
						// Fill remaining fftMagnitudes if any (e.g. if fftData was too short or binsToProcess was less than 1024)
						while({i < 1024}) {
							fftMagnitudes[i] = 0.0; // Ensure it's filled to 1024
							i = i + 1;
						};
					});

					if(waveformData.size == 1024 and: fftMagnitudes.size == 1024) { // Check for 1024
						combinedData = waveformData ++ fftMagnitudes;
						// Get RMS values synchronously
						combinedData = combinedData.add(~rms_bus_input.getSynchronous);
						combinedData = combinedData.add(~rms_bus_output.getSynchronous);
						~o.sendMsg(\combined_data, *combinedData);
					} {
						"Warning: combinedData array size mismatch before sending - waveform: %, FFT: %".format(waveformData.size, fftMagnitudes.size).postln;
					}
				});
			});
		}, {
			"Warning: Relay buffer or FFT buffer is nil for combinedData".postln;
		});
	}, '/combined_data');

	"Combined waveform+FFT OSCdef created".postln;

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