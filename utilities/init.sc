"Freeing synths and setting default options...BEGIN".postln;
Server.default.options.sampleRate = 48000;
Server.default.options.memSize = 512 * 1024;  // Set memory to 512MB
"About to start booting...PRE-WAIT".postln;
(
s.waitForBoot{
	"Server booted, inside waitForBoot block - INITIALIZING...".postln;
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

	~effect_output_bus_for_analysis = Bus.audio(s, 1);
	"Dedicated analysis output bus created".postln;

	// Free existing buffers if they exist
	if(~relay_buffer_in.notNil, { ~relay_buffer_in.free });
	if(~relay_buffer_out.notNil, { ~relay_buffer_out.free });

	~relay_buffer_in = Buffer.alloc(s, ~chunkSize * ~numChunks);
	~relay_buffer_out = Buffer.alloc(s, ~chunkSize * ~numChunks);
	"New relay buffers allocated".postln;

	~fft_size = 2048 * 2; 
	~fft_buffer_out = Buffer.alloc(s, ~fft_size);
	"FFT buffers allocated".postln;

	~audio_input_bus = Bus.audio(s, 2); 
	~test_loop_bus = Bus.audio(s,2);
	"Audio input and test loop buses defined".postln;

	// Create groups for effects and sources
	s.sync;
	~effectGroup = Group.new;
	s.sync;
	~sourceGroup = Group.new(~effectGroup, \addBefore);
	s.sync; 
	~analysisGroup = Group.new(~effectGroup, \addAfter); 
	"Effect, source, and analysis groups created".postln;

	SynthDef(\audioIn, {
		var sig; 
		sig = SoundIn.ar([0]);
		Out.ar(~audio_input_bus, sig);
	}).add;
	s.sync;
	"SynthDef(audioIn) added".postln;
	
	~audioInSynth = Synth(\audioIn, target: ~sourceGroup);
	"~audioInSynth created".postln;

	// ADDED: masterAnalyser SynthDef
	SynthDef(\masterAnalyser, { | relay_buf_out_num, fft_buf_out_num, rms_bus_in_num, rms_bus_out_num, input_bus_num, effect_analysis_bus_num |

		var effect_input_sig = In.ar(input_bus_num, 1);
		var effect_output_sig = In.ar(effect_analysis_bus_num, 1);

		var phase_for_bufwr = Phasor.ar(0, 1, 0, ~chunkSize);
		var trig_for_partition = HPZ1.ar(phase_for_bufwr) < 0;
		var partition_for_bufwr = PulseCount.ar(trig_for_partition) % ~numChunks;

		var kr_impulse_for_sendreply = Impulse.kr(60);
		var latched_partition = Latch.kr(partition_for_bufwr, kr_impulse_for_sendreply);

		// Declare all local variables for SynthDef at the top
		var rms_input_val;
		var rms_output_val;

		BufWr.ar(effect_output_sig, relay_buf_out_num, phase_for_bufwr + (~chunkSize * partition_for_bufwr));
		FFT(fft_buf_out_num, effect_output_sig, hop: 0.5, wintype: 1);

		// Assign to already declared variables
		rms_input_val = RunningSum.rms(effect_input_sig, 1024);
		rms_output_val = RunningSum.rms(effect_output_sig, 1024);

		Out.kr(rms_bus_in_num, rms_input_val);
		Out.kr(rms_bus_out_num, rms_output_val);

		//SendReply.kr(kr_impulse_for_sendreply, '/master_combined_data_trigger', latched_partition); // OLD Trigger
		SendReply.kr(kr_impulse_for_sendreply, '/combined_data', latched_partition); // NEW: Trigger main OSCdef

		Silent.ar(1);
	}).add;
	s.sync;
	"SynthDef(masterAnalyser) added".postln;

	// in case we want a bus with some looping guitar
	~buffer = Buffer.read(Server.default, "~/bice-box-effects/resources/karaoke_shack_riff_mono.wav".standardizePath, action: { |buf|
		("Buffer for test loop loaded. Original SR: " ++ buf.sampleRate ++ ", Channels: " ++ buf.numChannels).postln;

		Routine {
			SynthDef(\playGuitarRiff, {
				var sig = PlayBuf.ar(buf.numChannels, buf, BufRateScale.kr(buf), loop: 1);
				Out.ar(~test_loop_bus, sig);
			}).add;

			s.sync;
			"SynthDef(playGuitarRiff) added".postln;

			~guitarRiffSynth = Synth(\playGuitarRiff, target: ~sourceGroup);
			"Guitar riff synth created in source group".postln;

			s.status;
			"Done with guitar riff routine block".postln;
		}.play;
	});


	~input_bus = if (~useTestLoop,
		{ "USING TEST LOOP BUS".postln; ~test_loop_bus },
		{ "USING AUDIO INPUT BUS".postln; ~audio_input_bus }
	);
	"~input_bus selected".postln;

	OSCdef(\buffer_refresh, { |msg|
		var partition_index;
		//("OSCdef buffer_refresh received msg: " ++ msg).postln;
		partition_index = msg[3].asInteger; 

		[~relay_buffer_in, ~relay_buffer_out].do { |buf, i|
			if(buf.notNil, {
				buf.getn(partition_index * ~chunkSize, ~chunkSize, { |data|
					data = data.resamp1(data.size/~chunkDownsample);
					~o.sendMsg(("waveform" ++ i).asSymbol, *(data.as(Array)));
				});
			}, {
				"Warning: Relay buffer % is nil".format(i).postln;
			}); 
		};
	}, '/buffer_refresh');
	"OSCdef(buffer_refresh) created".postln;


	OSCdef(\rms, { |msg|
		~o.sendMsg(\audio_analysis, ~rms_bus_input.getSynchronous, ~rms_bus_output.getSynchronous);
	}, '/rms');
	"OSCdef(rms) created".postln;
	

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



	OSCdef(\combinedData).free;
	OSCdef(\combinedData, { |msg|
		var fftMagnitudes, i, dataIdx, numComplexBins, binsToProcess, dcMag, real, imag, mag, nyquistMag, combinedData;
		var received_partition_index, partition_to_read; // Variables for clarity
		//("OSCdef combinedData received msg: " ++ msg).postln;
		received_partition_index = msg[3].asInteger;

		// Calculate the index of the partition that was *just completed* by masterAnalyser
		// This is the partition that contains stable, fully written data.
		partition_to_read = (received_partition_index - 1 + ~numChunks) % ~numChunks;

		if(~relay_buffer_out.notNil and: ~fft_buffer_out.notNil, {
			// Read waveform data from the *previously completed* partition
			~relay_buffer_out.getn(partition_to_read * ~chunkSize, ~chunkSize, { |waveformData| 
				if(waveformData.size != 1024, {
					// This case should ideally not happen if ~chunkSize is 1024 and reading is correct
					"Warning: Waveform data size mismatch, expected 1024, got % samples. Resampling.".format(waveformData.size).postln;
					waveformData = waveformData.resamp1(1024);
				});
				
				~fft_buffer_out.getn(0, ~fft_size, { |fftData| 
					fftMagnitudes = Array.newClear(1024); 
					i = 0;
					numComplexBins = (~fft_size / 2).asInteger; 
					binsToProcess = min(1024, numComplexBins); 

					if(fftData.size < ~fft_size, {
						"Warning: Insufficient FFT data for combinedData, got % samples, expected %".format(fftData.size, ~fft_size).postln;
						fftMagnitudes = Array.fill(1024, 0.0); 
					}, {
						if (binsToProcess > 0 and: fftData.size > 0) {
							dcMag = fftData[0].abs; 
							fftMagnitudes[i] = (dcMag + 0.001).log / 10.log;
							i = i + 1;
						};

						dataIdx = 2; 
						while({i < (binsToProcess -1) and: (dataIdx + 1 < fftData.size)}) { 
							real = fftData[dataIdx] ? 0;
							imag = fftData[dataIdx+1] ? 0;
							mag = (real.squared + imag.squared).sqrt;
							fftMagnitudes[i] = (mag + 0.001).log / 10.log;
							i = i + 1;
							dataIdx = dataIdx + 2;
						};

						if (i < binsToProcess and: fftData.size > 1) { 
							nyquistMag = fftData[1].abs; 
							fftMagnitudes[i] = (nyquistMag + 0.001).log / 10.log;
							i = i + 1;
						};
						
						while({i < 1024}) {
							fftMagnitudes[i] = 0.0; 
							i = i + 1;
						};
					});

					if(waveformData.size == 1024 and: fftMagnitudes.size == 1024) { 
						combinedData = waveformData ++ fftMagnitudes;
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

	// OSCdef(\\masterCombinedDataTriggerTest, { |msg|
	// 	var partition;
	// 	("OSCdef masterCombinedDataTriggerTest received msg: " ++ msg).postln; // DEBUG: Print raw message
	// 	partition = msg[3].asInteger; 
	// 	("Master Analyser triggered with partition: " ++ partition).postln;
	// }, '/master_combined_data_trigger', s.addr);
	// "Master Analyser trigger test OSCdef created".postln;
	// REMOVED: Temporary OSCdef for masterAnalyser trigger testing, masterAnalyser now triggers /combined_data directly

	OSCdef(\tunerData).free;
	OSCdef(\tunerData, { |msg|
		var freq = msg[3];
		var hasFreq = msg[4];
		var differences = msg.copyRange(5, 10);
		var amplitudes = msg.copyRange(11, 16);
		~o.sendMsg(\tuner_data, 
			freq, hasFreq, 
			differences[0], differences[1], differences[2], differences[3], differences[4], differences[5],
			amplitudes[0], amplitudes[1], amplitudes[2], amplitudes[3], amplitudes[4], amplitudes[5]
    );  	}, '/tuner_data', s.addr);
	"Tuner OSCdef created".postln;

	s.sync;
	"Server synced before masterAnalyserSynth instantiation".postln;

	~masterAnalyserSynth = Synth(\masterAnalyser,
		[
			\relay_buf_out_num, ~relay_buffer_out.bufnum,
			\fft_buf_out_num, ~fft_buffer_out.bufnum,
			\rms_bus_in_num, ~rms_bus_input.index,
			\rms_bus_out_num, ~rms_bus_output.index,
			\input_bus_num, ~input_bus.index,
			\effect_analysis_bus_num, ~effect_output_bus_for_analysis.index
		],
		target: ~analysisGroup
	);
	"masterAnalyserSynth instantiated in analysisGroup".postln;

	["Buffer 0 (in):", ~relay_buffer_in, "Buffer 1 (out):", ~relay_buffer_out, "Input Bus Object:", ~input_bus, "Input Bus Index:", ~input_bus.index].postln;

	"Server booted successfully. END OF SCRIPT".postln;
};
)