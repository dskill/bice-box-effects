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
		// Mix the stereo hardware input down to a single mono channel.
		// This handles a mono source plugged into either L or R of a stereo jack.
		sig = Mix.ar(SoundIn.ar([0, 1]));
		// Output the resulting mono signal to BOTH channels of our main input bus.
		Out.ar(~audio_input_bus, [sig, sig]);
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



	// OSC Handler for setting effect parameters
	OSCdef(\setEffectParam, { |msg|
		var paramName, value;
		// msg[0] is the address, e.g. '/effect/param/set'
		// msg[1] should be the parameter name (symbol or string)
		// msg[2] should be the value
		if(msg.size >= 3, {
			paramName = msg[1];
			value = msg[2];
			if(~effect.notNil, {
				// Ensure paramName is a symbol for .set
				// "~effect.set(paramName: %, value: %);".format(paramName.asSymbol, value).postln; // For debugging
				~effect.set(paramName.asSymbol, value);
			}, {
				"OSCdef setEffectParam: ~effect is nil, cannot set % to %".format(paramName, value).postln;
			});
		}, {
			"OSCdef setEffectParam: insufficient arguments in message %".format(msg).postln;
		});
	}, '/effect/param/set');
	"OSCdef for /effect/param/set created".postln;

	// Initialize the global specs dictionary (if not already done by an effect file)
	~effectParameterSpecs ?? { ~effectParameterSpecs = IdentityDictionary.new; };
	"~effectParameterSpecs initialized or confirmed existing.".postln;

	// Helper function to register effect parameter specifications
	~registerEffectSpecs = { |effectName, specsDict|
		var specsForJSON, jsonString, paramCount;
		
		// Ensure ~effectParameterSpecs is an IdentityDictionary
		~effectParameterSpecs ?? { ~effectParameterSpecs = IdentityDictionary.new; };
		if(~effectParameterSpecs.isKindOf(IdentityDictionary).not) {
			"CRITICAL WARNING: ~effectParameterSpecs is not an IdentityDictionary! Re-initializing.".postln;
			~effectParameterSpecs = IdentityDictionary.new;
		};

		// Register parameter specifications for this SynthDef
		~effectParameterSpecs.put(effectName, specsDict);
		
		// Debug: Check what was stored
		("DEBUG: ~effectParameterSpecs[%] = %").format(effectName, ~effectParameterSpecs[effectName]).postln;
		("DEBUG: ~effectParameterSpecs[%] class = %").format(effectName, ~effectParameterSpecs[effectName].class).postln;
		
		// Convert ControlSpecs to JSON-serializable format
		specsForJSON = ();
		("DEBUG: specsForJSON initialized as: %").format(specsForJSON).postln;
		
		~effectParameterSpecs[effectName].keysValuesDo({ |key, spec|
			("DEBUG: Processing key: %, spec: %, spec.class: %").format(key, spec, spec.class).postln;
			("DEBUG: spec.minval: %, spec.maxval: %, spec.default: %").format(spec.minval, spec.maxval, spec.default).postln;
			
			specsForJSON.put(key, (
				minval: spec.minval,
				maxval: spec.maxval,
				warp: spec.warp.asSpecifier.asString,
				step: spec.step,
				default: spec.default,
				units: spec.units.asString
			));
			("DEBUG: specsForJSON after adding %: %").format(key, specsForJSON).postln;
		});
		
		("DEBUG: Final specsForJSON: %").format(specsForJSON).postln;
		
		// Manually build JSON string since asJSON doesn't work reliably with nested Events
		jsonString = "{";
		paramCount = 0;
		specsForJSON.keysValuesDo({ |key, paramData|
			if(paramCount > 0, { jsonString = jsonString ++ "," });
			jsonString = jsonString ++ "\"" ++ key.asString ++ "\":{";
			jsonString = jsonString ++ "\"minval\":" ++ paramData.minval.asString;
			jsonString = jsonString ++ ",\"maxval\":" ++ paramData.maxval.asString;
			jsonString = jsonString ++ ",\"warp\":\"" ++ paramData.warp.asString ++ "\"";
			jsonString = jsonString ++ ",\"step\":" ++ paramData.step.asString;
			jsonString = jsonString ++ ",\"default\":" ++ paramData.default.asString;
			jsonString = jsonString ++ ",\"units\":\"" ++ paramData.units.asString ++ "\"";
			jsonString = jsonString ++ "}";
			paramCount = paramCount + 1;
		});
		jsonString = jsonString ++ "}";
		
		("DEBUG: Manual JSON string: %").format(jsonString).postln;
		("Parameter specs for % registered: %").format(effectName, jsonString).postln;
	};
	"~registerEffectSpecs helper function defined.".postln;

	~setupEffect = { |defName, specs, additionalArgs = #[]|
		var finalArgs;

		// First, register the parameter specifications
		~registerEffectSpecs.value(defName, specs);

		// Then, create or replace the synth instance
		finalArgs = [
			\in_bus, ~input_bus,
			\analysis_out_bus, ~effect_output_bus_for_analysis
		] ++ additionalArgs;

		fork {
			s.sync;

			// Free existing synth if it exists
			if(~effect.notNil, {
				"Freeing existing effect synth".postln;
				~effect.free;
				s.sync;
			});

			// Create new synth in the effect group
			~effect = Synth(defName, finalArgs, ~effectGroup);
			("New '%' synth created with args: %").format(defName, finalArgs).postln;
		};
	};
	"~setupEffect helper function defined.".postln;

	// OSC Handler for getting effect parameter specifications
	OSCdef(\getEffectSpecs, { |msg, time, addr|
		var effectName, specs, specsForJSON, jsonString, paramCount;
		// msg[0] is the address, e.g. '/effect/get_specs'
		// msg[1] should be the effect name (Symbol or String)
		if(msg.size >= 2, {
			effectName = msg[1].asSymbol; // Ensure it's a symbol for dictionary lookup
			"OSCdef getEffectSpecs: Received request for specs of %".format(effectName).postln;
			specs = ~effectParameterSpecs[effectName];
			if(specs.notNil, {
				// Convert ControlSpec objects to JSON-serializable format
				specsForJSON = ();
				specs.keysValuesDo({ |key, spec|
					specsForJSON.put(key, (
						minval: spec.minval,
						maxval: spec.maxval,
						warp: spec.warp.asSpecifier.asString,
						step: spec.step,
						default: spec.default,
						units: spec.units.asString
					));
				});
				
				// Manually build JSON string since asJSON doesn't work reliably with nested Events
				jsonString = "{";
				paramCount = 0;
				specsForJSON.keysValuesDo({ |key, paramData|
					if(paramCount > 0, { jsonString = jsonString ++ "," });
					jsonString = jsonString ++ "\"" ++ key.asString ++ "\":{";
					jsonString = jsonString ++ "\"minval\":" ++ paramData.minval.asString;
					jsonString = jsonString ++ ",\"maxval\":" ++ paramData.maxval.asString;
					jsonString = jsonString ++ ",\"warp\":\"" ++ paramData.warp.asString ++ "\"";
					jsonString = jsonString ++ ",\"step\":" ++ paramData.step.asString;
					jsonString = jsonString ++ ",\"default\":" ++ paramData.default.asString;
					jsonString = jsonString ++ ",\"units\":\"" ++ paramData.units.asString ++ "\"";
					jsonString = jsonString ++ "}";
					paramCount = paramCount + 1;
				});
				jsonString = jsonString ++ "}";
				
				"Sending specs for % to %: %".format(effectName, addr, jsonString).postln;
				addr.sendMsg('/effect/specs_reply', effectName.asString, jsonString);
			}, {
				"No specs found for effect: %".format(effectName).postln;
				addr.sendMsg('/effect/specs_reply', effectName.asString, "{}"); // Send empty JSON object
			});
		}, {
			"OSCdef getEffectSpecs: insufficient arguments in message %".format(msg).postln;
		});
	}, '/effect/get_specs');
	"OSCdef for /effect/get_specs created".postln;


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