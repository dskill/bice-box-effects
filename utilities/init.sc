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
	("NetAddr configured to send to: 127.0.0.1:57121").postln;
	("Server default options: " ++ Server.default.options).postln;
	("Current server: " ++ s).postln;

	// Log OSC server configuration for debugging
	("Server addr: " ++ s.addr).postln;
	("SuperCollider server is listening for OSC on: " ++ s.addr).postln;
	
	// Set up language-side OSC listening
	// The language needs to listen on a different port from the server
	~langOSCPort = 57122; // Language OSC port
	thisProcess.openUDPPort(~langOSCPort);
	("SuperCollider language is listening for OSC on port: " ++ ~langOSCPort).postln;
	
	// Send port configuration to Electron for dynamic discovery
	~o.sendMsg("/sc/config", 
		"server_port", s.addr.port,
		"lang_port", ~langOSCPort,
		"electron_port", 57121
	);
	("SC: Sent port configuration to Electron").postln;

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
		var paramName, value, currentEffectName;
		// msg[0] is the address, e.g. '/effect/param/set'
		// msg[1] should be the parameter name (symbol or string)
		// msg[2] should be the value
		if(msg.size >= 3, {
			paramName = msg[1];
			value = msg[2];
			if(~effect.notNil, {
				// Debug: log param set
				//("OSCdef setEffectParam: set % to %").format(paramName, value).postln;
				~effect.set(paramName.asSymbol, value);
				
				// Track ALL parameter updates for broadcasting (OSC and MIDI)
				// This ensures UI gets feedback when it sends parameter changes
				currentEffectName = ~effect.defName.asString;
				if(~effectParameterValues[currentEffectName.asSymbol].isNil, {
					~effectParameterValues[currentEffectName.asSymbol] = IdentityDictionary.new;
				});
				~effectParameterValues[currentEffectName.asSymbol][paramName.asSymbol] = value;
				// Parameter tracked for broadcasting
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
	
	// Initialize parameter values dictionary to track current values
	~effectParameterValues ?? { ~effectParameterValues = IdentityDictionary.new; };
	"~effectParameterValues initialized for broadcasting.".postln;

	// Helper function to register effect parameter specifications
	~registerEffectSpecs = { |effectName, specsDict|
		// Ensure ~effectParameterSpecs is an IdentityDictionary
		~effectParameterSpecs ?? { ~effectParameterSpecs = IdentityDictionary.new; };
		if(~effectParameterSpecs.isKindOf(IdentityDictionary).not) {
			"CRITICAL WARNING: ~effectParameterSpecs is not an IdentityDictionary! Re-initializing.".postln;
			~effectParameterSpecs = IdentityDictionary.new;
		};

		// Register parameter specifications for this SynthDef
		~effectParameterSpecs.put(effectName, specsDict);
		
		("Parameter specs for % registered in SC.".format(effectName)).postln;
	};
	"~registerEffectSpecs function defined.".postln;

	// --- ROTO CONTROL HELPERS START ---
	~rotoId = [0xF0, 0x00, 0x22, 0x03, 0x02]; // Header
	~rotoCmdGeneral = 0x0A;
	~rotoCmdMix = 0x0C;
	~rotoScDawStarted = 0x01;
	~rotoScPingDaw = 0x02;
	~rotoScDawPingResponse = 0x03;
	~rotoScTrackDetails = 0x07;
	~rotoScTrackDetailsEnd = 0x08;
	~rotoScRotoDawConnected = 0x0C;

	~hasRoto = false;
	~rotoOut = nil;

	~sendRotoSysEx = { |cmdType, subCmd, data = #[]|
		if (~rotoOut.notNil, {
			var msg = Int8Array.newFrom(~rotoId ++ [cmdType, subCmd] ++ data ++ [0xF7]);
			~rotoOut.sysex(msg);
		});
	};

	~to14Bit = { |val|
		// Big Endian for Roto SysEx Track Index (per notes)
		[(val >> 7) & 0x7F, val & 0x7F]
	};

	~toAsciiPadded = { |str, len = 13|
		var bytes = Int8Array.newClear(len);
		var strBytes = str.ascii;
		len.do({ |i|
			if (i < strBytes.size, {
				bytes[i] = strBytes[i];
			}, {
				bytes[i] = 0x20; // Pad with spaces
			});
		});
		bytes
	};

	~syncRotoToEffect = { |effectName|
		if (~hasRoto, {
			fork {
				var specs = ~effectParameterSpecs[effectName.asSymbol];
				var values = ~effectParameterValues[effectName.asSymbol];
				var paramNames;

				if (specs.notNil, {
					paramNames = specs.keys.asArray.sort;
					
					// Update LCDs and Motors for first 8 params
					min(paramNames.size, 8).do({ |i|
						var name = paramNames[i];
						var spec = specs[name];
						var val = values[name] ? spec.default;
						var normalizedVal = spec.unmap(val);
						var midi14BitVal = (normalizedVal * 16383).asInteger;
						var msb = (midi14BitVal >> 7) & 0x7F;
						var lsb = midi14BitVal & 0x7F;
						
						// 1. Update Track Details (LCD)
						// TRACK DETAILS: 07 <TI:2 TN:0D CS GT>
						var trackIdx = ~to14Bit.value(i);
						var trackName = ~toAsciiPadded.value(name.asString, 13);
						var color = 10 + (i * 5); // Arbitrary color
						var grouped = 0;
						var ccMsb, ccLsb;

						~sendRotoSysEx.value(~rotoCmdGeneral, ~rotoScTrackDetails, 
							trackIdx ++ trackName ++ [color, grouped]);
						
						// 2. Move Motor (Channel 16)
						// Knob 0: CC 12 (MSB) / CC 44 (LSB)
						ccMsb = 12 + i;
						ccLsb = ccMsb + 32;
						~rotoOut.control(15, ccMsb, msb);
						~rotoOut.control(15, ccLsb, lsb);
						
						0.01.wait; // Slight throttle
					});
					
					// Commit LCD updates
					~sendRotoSysEx.value(~rotoCmdGeneral, ~rotoScTrackDetailsEnd);
					("Roto Control synced to effect: " ++ effectName).postln;
				});
			};
		});
	};
	// --- ROTO CONTROL HELPERS END ---

	~setupEffect = { |defName, specs, additionalArgs = #[], numVoices = 0|
		var finalArgs;

		// First, register the parameter specifications
		~registerEffectSpecs.value(defName, specs);

		// Initialize parameter values with defaults for this effect
		if(~effectParameterValues[defName.asSymbol].isNil, {
			~effectParameterValues[defName.asSymbol] = IdentityDictionary.new;
		});
		specs.keysValuesDo({ |paramName, spec|
			~effectParameterValues[defName.asSymbol][paramName] = spec.default;
		});
		("Initialized parameter values for % with defaults").format(defName).postln;

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

			// Clean up and initialize voice allocation state for MIDI-enabled effects
			~currentSynthNumVoices = numVoices; // Store numVoices for MIDI handlers
			if(numVoices > 0, {
				("Initializing MIDI state for '%' with % voices.").format(defName, numVoices).postln;
				~voice_allocator = Dictionary.new;
				~voice_states = Array.fill(numVoices, \free);
				~voice_freqs = Array.fill(numVoices, 440);
				~voice_gates = Array.fill(numVoices, 0);
				~voice_amps = Array.fill(numVoices, 0);
				~held_notes = []; // Used for both mono and poly tracking
			});

			// Create new synth in the effect group
			~effect = Synth(defName, finalArgs, ~effectGroup);
			("New '%' synth created with args: %").format(defName, finalArgs).postln;

			// Sync Roto Control if active
			~syncRotoToEffect.value(defName);

			// Initialize voice arrays for MIDI-enabled synths
			if(numVoices > 0, {
				"Initializing voice arrays on new synth instance".postln;
				~effect.set(
					\voice_freqs, ~voice_freqs,
					\voice_gates, ~voice_gates,
					\voice_amps, ~voice_amps
				);
			});
		};
	};
	"~setupEffect helper function defined with unified MIDI handling.".postln;

	// Free and recreate the OSCdef to ensure it's properly registered
	OSCdef(\get_effect_specs_handler).free;
	
	// Handler for getting effect parameter specifications
	OSCdef(\get_effect_specs_handler, { |msg|
		var effectName = msg[1].asSymbol;
		var specs = ~effectParameterSpecs[effectName];
		var specsForJSON, jsonString, paramCount;

		("SC: Processing /effect/get_specs for: " ++ effectName).postln;

		if(specs.isNil) {
			("SC: No specs found for effect: " ++ effectName).postln;
			~o.sendMsg("/effect/specs_reply", effectName.asString, "{}"); // Send empty JSON
		} {
			
			// Convert ControlSpecs to JSON-serializable format
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
			
			// Manually build JSON string as asJSON can be unreliable
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

			// Send the reply
			~o.sendMsg("/effect/specs_reply", effectName.asString, jsonString);
			("SC: Sent specs reply for " ++ effectName).postln;
		};
	}, '/effect/get_specs');
	"OSCdef for /effect/get_specs created.".postln;

	// Free and recreate test OSC handler
	OSCdef(\test_osc_reception).free;
	OSCdef(\test_osc_reception, { |msg|
		("TEST: Received OSC message at /test with args: " ++ msg).postln;
	}, '/test');
	"Test OSC handler created for /test".postln;
	
	// Verify OSCdefs are registered
	("OSCdef.all keys: " ++ OSCdef.all.keys.asArray).postln;

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

	// Parameter Broadcasting Routine - broadcasts only when parameters actually change
	// This makes SuperCollider the single source of truth for parameter values
	~lastBroadcastValues ?? { ~lastBroadcastValues = IdentityDictionary.new; };
	~parameterBroadcastRoutine = fork {
		// Parameter broadcast routine started
		loop {
			0.05.wait; // Faster rate: 50ms = 20Hz for responsive UI updates
			if (~effect.notNil and: { ~effectParameterSpecs.notNil }) {
				try {
					var currentEffectName = ~effect.defName.asString;
					var specs = ~effectParameterSpecs[currentEffectName.asSymbol];
					
					if (specs.notNil) {
						// Get current parameter values from our tracking dictionary
						var paramValues = ~effectParameterValues[currentEffectName.asSymbol];
						var paramData = [];
						var hasChanges = false;
						var lastValues = ~lastBroadcastValues[currentEffectName.asSymbol];
						
						if (lastValues.isNil) {
							~lastBroadcastValues[currentEffectName.asSymbol] = IdentityDictionary.new;
							lastValues = ~lastBroadcastValues[currentEffectName.asSymbol];
						};
						
						if (paramValues.notNil) {
							var paramNames = specs.keys.asArray.sort;

							paramValues.keysValuesDo({ |paramName, value|
								var lastValue = lastValues[paramName];
								// Broadcast all parameter changes without tolerance check
								if (lastValue.isNil or: { value != lastValue }) {
									paramData = paramData ++ [paramName.asString, value];
									lastValues[paramName] = value;
									hasChanges = true;
									// Parameter changed, broadcasting

									// --- ROTO UPDATE ---
									// If Roto is connected, update the corresponding motor
									if (~hasRoto) {
										var paramIdx = paramNames.indexOf(paramName);
										if (paramIdx.notNil and: { paramIdx < 8 }) {
											var spec = specs[paramName];
											var normalizedVal = spec.unmap(value);
											var midi14BitVal = (normalizedVal * 16383).asInteger;
											var msb = (midi14BitVal >> 7) & 0x7F;
											var lsb = midi14BitVal & 0x7F;
											var ccMsb = 12 + paramIdx;
											var ccLsb = ccMsb + 32;
											
											~rotoOut.control(15, ccMsb, msb);
											~rotoOut.control(15, ccLsb, lsb);
										};
									};
								};
							});
							
							if (hasChanges and: { paramData.size > 0 }) {
								~o.sendMsg('/effect/state', currentEffectName, *paramData);
																	// Broadcasting parameter changes to UI
							};
						} {
							// Fallback to defaults if no values tracked yet - only broadcast once
							if (lastValues.size == 0) {
								specs.keysValuesDo({ |paramName, spec|
									paramData = paramData ++ [paramName.asString, spec.default];
									lastValues[paramName] = spec.default;
								});
								
								if (paramData.size > 0) {
									~o.sendMsg('/effect/state', currentEffectName, *paramData);
																				// Broadcasting default parameters to UI
								};
							};
						};
					};
				} { |error|
					// Silently handle errors to prevent routine from stopping
					// Silently handle broadcast errors
				};
			};
		};
	};
	"Parameter broadcast routine started".postln;

	["Buffer 0 (in):", ~relay_buffer_in, "Buffer 1 (out):", ~relay_buffer_out, "Input Bus Object:", ~input_bus, "Input Bus Index:", ~input_bus.index].postln;

	// MIDI SETUP START
			// Wrap in a try block to prevent boot failure on systems with MIDI issues (e.g., Raspberry Pi)
		try {
			MIDIClient.init;
			"MIDI Initialized.".postln;
			~hasMIDI = MIDIClient.sources.notEmpty;

			if(~hasMIDI, {
				// Log the list of connected MIDI sources
				("MIDI Sources: " ++ MIDIClient.sources).postln;

				// Connect to hardware MIDI inputs, avoiding problematic system devices
				MIDIClient.sources.do({ |src, i|
					var deviceName = src.device.asString;
					var sourceName = src.name.asString;
					// Examining MIDI source
					
					// Skip problematic system devices that cause "Device or resource busy" errors
					if (deviceName.contains("System") or: 
						deviceName.contains("SuperCollider") or:
						sourceName.contains("Timer") or:
						sourceName.contains("Announce") or:
						sourceName.contains("out")) {
													// Skipping system/virtual device
					} {
						// Connect to pisound (Raspberry Pi) or common MIDI controllers, but avoid Midi Through if busy
						if (deviceName.contains("pisound") or: 
							deviceName.containsi("launchkey") or: 
							(deviceName.containsi("midi") and: deviceName.contains("Through").not) or:
							deviceName.containsi("keyboard") or:
							deviceName.containsi("roto") or: // Added Roto explicitly
							deviceName.containsi("controller")) {
							
															// Attempting MIDI connection
							
							// Try different connection methods, but avoid connectAll on Pi
							try {
								// Method 1: Connect using port 0 and device UID
								MIDIIn.connect(0, src.uid);
																		// Successfully connected to MIDI device
							} { |error1|
																		// Connection method 1 failed, trying method 2
								try {
									// Method 2: Connect using device index
									MIDIIn.connect(device: i);
																				// Successfully connected via method 2
								} { |error2|
																				// Method 2 failed, trying method 3
									try {
										// Method 3: Connect using just the UID
										MIDIIn.connect(src.uid);
																						// Successfully connected via method 3
									} { |error3|
																						// All MIDI connection methods failed
									}
								}
							};
						} {
							// Skipping non-target device
						}
					}
				});
				"MIDI connection setup complete.".postln;

				// ROTO CONTROL SETUP
				~rotoDest = MIDIClient.destinations.detect { |d| d.device.containsi("Roto") };
				if (~rotoDest.notNil, {
					"Found Roto Control destination.".postln;
					~rotoOut = MIDIOut.newByName(~rotoDest.device, ~rotoDest.name);
					~hasRoto = true;
					
					// Send Handshake: DAW STARTED
					~sendRotoSysEx.value(~rotoCmdGeneral, ~rotoScDawStarted);
					"Sent Roto Handshake: DAW STARTED".postln;
					
					// Roto SysEx Handler for Handshake
					MIDIFunc.sysex({ |data|
						var cmdType = data[5];
						var subCmd = data[6];
						
						if (cmdType == ~rotoCmdGeneral, {
							if (subCmd == ~rotoScPingDaw, {
								"Roto: Received PING DAW. Sending response...".postln;
								// DAW PING RESPONSE: 03 <DAW_ID> (01 = Ableton)
								~sendRotoSysEx.value(~rotoCmdGeneral, ~rotoScDawPingResponse, [0x01]);
							});
							if (subCmd == ~rotoScRotoDawConnected, {
								"Roto: Connected successfully!".postln;
							});
						});
					}, nil, ~rotoId); // Filter by Roto Header (argTemplate)
					
				}, {
					"Roto Control not found.".postln;
				});
				
				// Add a general MIDI message handler for debugging all incoming MIDI
				if(~midi_all_func.notNil, { ~midi_all_func.free });
				~midi_all_func = MIDIFunc({ |val, num, chan, src|
					// Raw MIDI message received
				});
				"MIDI message handler created.".postln;
				
			}, {
				"No MIDI devices detected.".postln;
			});

		// Voice allocation for polyphonic effects only
		~currentSynthNumVoices = 0; // The number of voices for the current synth
		~voice_allocator = (); // Dictionary: midiNote -> voiceIndex
		~voice_states = Array.new; // Track voice states: \free, \active
		~voice_freqs = Array.new; // Current frequencies
		~voice_gates = Array.new; // Current gate states
		~voice_amps = Array.new; // Current amplitudes
		
		// Monophonic state for simple effects
		~held_notes = []; // for monophonic last-note priority
		
		// Voice allocation helper functions (for polyphonic mode)
		~findFreeVoice = {
			var freeIndex = ~voice_states.indexOf(\free);
			if(freeIndex.isNil) {
				// No free voices, steal the oldest (voice 0 for simplicity)
				// No free voices, stealing voice 0
				0;
			} {
				freeIndex;
			};
		};
		
		~updateVoiceArrays = {
			// Send updated voice arrays to the effect synth (polyphonic mode only)
			if(~effect.notNil and: (~currentSynthNumVoices > 0)) {
				~effect.set(
					\voice_freqs, ~voice_freqs,
					\voice_gates, ~voice_gates,
					\voice_amps, ~voice_amps
				);
				// ("MIDI DEBUG: Updated voice arrays - freqs: %, gates: %, amps: %")
				// 	.format(~voice_freqs, ~voice_gates, ~voice_amps).postln;
			};
		};

		// Free previous MIDI funcs if they exist, to allow re-evaluation of this script
		if(~midi_note_on_func.notNil, { ~midi_note_on_func.free });
		if(~midi_note_off_func.notNil, { ~midi_note_off_func.free });

		if(~hasMIDI, {
			~midi_note_on_func = MIDIFunc.noteOn({ |vel, num, chan, src|
				var srcDevice, voiceIndex;
				// Enhanced debug logging for MIDI note-on messages
				srcDevice = MIDIClient.sources.detect{|d| d.uid == src};
				
				if (~effect.notNil and: {~currentSynthNumVoices > 0}) {
					// --- POLYPHONIC LOGIC ---
					
					// Check if this note is already allocated (re-trigger)
					if (~voice_allocator[num].notNil) {
						voiceIndex = ~voice_allocator[num];
						~voice_gates[voiceIndex] = 0; // Quick gate off for re-trigger
						0.01.wait; // Brief pause
						~voice_gates[voiceIndex] = 1; // Gate back on
						~voice_amps[voiceIndex] = vel / 127;
					} {
						// Allocate a new voice
						voiceIndex = ~findFreeVoice.value;
						~voice_allocator[num] = voiceIndex;
						~voice_states[voiceIndex] = \active;
						~voice_freqs[voiceIndex] = num.midicps;
						~voice_gates[voiceIndex] = 1;
						~voice_amps[voiceIndex] = vel / 127;
					};
					
					~updateVoiceArrays.value; // Send updates to the synth
				} {
					// No MIDI-enabled effect synth available
				};
			});

			~midi_note_off_func = MIDIFunc.noteOff({ |vel, num, chan, src|
				var srcDevice, voiceIndex;
				srcDevice = MIDIClient.sources.detect{|d| d.uid == src};
				
				if (~effect.notNil and: {~currentSynthNumVoices > 0}) {
					// --- POLYPHONIC LOGIC ---
					if (~voice_allocator[num].notNil) {
						voiceIndex = ~voice_allocator[num];
						
						// Release the voice by setting its gate to 0
						~voice_gates[voiceIndex] = 0;
						~voice_states[voiceIndex] = \free;
						~voice_allocator.removeAt(num);
					};
					
					~updateVoiceArrays.value; // Send updates to the synth
				} {
					// No MIDI-enabled effect synth available
				};
			});
			"MIDI handlers created.".postln;
			
					// Initialize 14-bit MIDI storage if not already done
		if (~midi14BitValues.isNil) {
			~midi14BitValues = IdentityDictionary.new; // Store MSB/LSB values for 14-bit CCs
		};
		
		// MIDI CC Handler for parameter control (supports both 7-bit and 14-bit)
		if(~midi_cc_func.notNil, { ~midi_cc_func.free });
		~midi_cc_func = MIDIFunc.cc({ |val, ccNum, chan, src|
			var paramIndex, normalizedValue, paramName, paramSpec, is14Bit = false, finalValue;
			
			// Debug logging for ALL MIDI CC
			// ("MIDI CC Received: Channel " ++ chan ++ ", CC " ++ ccNum ++ ", Value " ++ val).postln;
			
			// MIDI CC processing (debug logging removed for performance)
			
			// Handle CC 117 for push-to-talk functionality
			if (chan == 15 and: { ccNum == 117 }) { // Channel 16 (15 in SC) Controller 117
				// Send OSC message to Electron for push-to-talk control
				~o.sendMsg('/midi/cc117', val);

			};
			
			// --- ROTO CONTROL (Channel 16) ---
			// Observed Mapping from Logs:
			// Knob 1 (Index 0): Control 1 (Coarse/MSB) / Control 33 (Fine/LSB) ??  -- WAIT, logs show "Effect Control 1" = CC 12/44?
			// Let's re-read the logs carefully.
			
			// Log 1: Knob 1 moved.
			// "From Roto-Control Control 16 Effect Control 1 (coarse) 54"
			// "From Roto-Control Control 16 Effect Control 1 (fine) 101"
			// "Effect Control 1" usually maps to CC 12 (0x0C) or CC 16? 
			// Standard General MIDI: Effect Control 1 = CC 12.
			// So Knob 1 (Index 0) is CC 12 / 44.
			
			// Log 2: Knob 2 moved.
			// "From Roto-Control Control 16 Effect Control 2 (coarse) 43"
			// Effect Control 2 = CC 13.
			// So Knob 2 (Index 1) is CC 13 / 45.
			
			// Log 3: Knob 3 moved.
			// "From Roto-Control Control 16 Controller 14 80"
			// Controller 14 = CC 14.
			// So Knob 3 (Index 2) is CC 14 / 46.
			
			// Log 4: Knob 4 moved.
			// "From Roto-Control Control 16 Controller 15 38"
			// Controller 15 = CC 15.
			// So Knob 4 (Index 3) is CC 15 / 47.

			// Log 5: Knob 5 moved.
			// "From Roto-Control Control 16 General Purpose 1 (coarse) 71"
			// General Purpose 1 = CC 16.
			// So Knob 5 (Index 4) is CC 16 / 48.

			// Log 6: Knob 6 moved.
			// "From Roto-Control Control 16 General Purpose 2 (coarse) 115"
			// General Purpose 2 = CC 17.
			// So Knob 6 (Index 5) is CC 17 / 49.

			// Log 7: Knob 7 moved.
			// "From Roto-Control Control 16 General Purpose 3 (coarse) 100"
			// General Purpose 3 = CC 18.
			// So Knob 7 (Index 6) is CC 18 / 50.

			// Log 8: Knob 8 moved.
			// "From Roto-Control Control 16 General Purpose 4 (coarse) 62"
			// General Purpose 4 = CC 19.
			// So Knob 8 (Index 7) is CC 19 / 51.
			
			// CONCLUSION: 
			// Channel 16 (SC Channel 15).
			// Knobs 0-7 map to CC 12-19 (MSB) and 44-51 (LSB).
			
			// MY IMPLEMENTATION:
			// if (chan == 15 and: { ccNum >= 12 and: { ccNum <= 19 } })
			// if (chan == 15 and: { ccNum >= 44 and: { ccNum <= 51 } })
			
			// This logic matches the logs perfectly. 
			// So why isn't it updating?
			
			// Possible Issue 1: `normalizedValue` is not persisting out of the block?
			// The `normalizedValue` variable is local to the `cc` function.
			// But my Roto blocks only set `midi14BitValues` and calculate `normalizedValue`.
			// They do NOT set `paramIndex`.
			// AND... `paramIndex` is a local variable initialized to nil.
			
			// FIX: I need to set `paramIndex` inside the Roto blocks so the subsequent
			// logic (which uses `paramIndex`) knows which parameter to update!
			
			if (chan == 15 and: { ccNum >= 12 and: { ccNum <= 19 } }) {
				paramIndex = ccNum - 12;
				is14Bit = true;
				if (~midi14BitValues[paramIndex].isNil) { ~midi14BitValues[paramIndex] = [0, 0]; };
				~midi14BitValues[paramIndex][0] = val;
				
				finalValue = (~midi14BitValues[paramIndex][0] * 128) + ~midi14BitValues[paramIndex][1];
				normalizedValue = finalValue / 16383.0;
			};
			if (chan == 15 and: { ccNum >= 44 and: { ccNum <= 51 } }) {
				paramIndex = ccNum - 44;
				is14Bit = true;
				if (~midi14BitValues[paramIndex].isNil) { ~midi14BitValues[paramIndex] = [0, 0]; };
				~midi14BitValues[paramIndex][1] = val;
				
				finalValue = (~midi14BitValues[paramIndex][0] * 128) + ~midi14BitValues[paramIndex][1];
				normalizedValue = finalValue / 16383.0;
			};
			
			// Handle 14-bit MIDI encoders: Multiple schemes supported
			
			// Scheme 1: Standard CC 1-8 (MSB) + CC 33-40 (LSB) 
			if (chan == 0 and: { ccNum >= 1 and: { ccNum <= 8 }}) {
				paramIndex = ccNum - 1; // Map CC 1-8 to param index 0-7
				is14Bit = true;
				
				if (~midi14BitValues[paramIndex].isNil) {
					~midi14BitValues[paramIndex] = [0, 0]; // [MSB, LSB]
				};
				~midi14BitValues[paramIndex][0] = val;
				
				finalValue = (~midi14BitValues[paramIndex][0] * 128) + ~midi14BitValues[paramIndex][1];
				normalizedValue = finalValue / 16383.0;
				

			};
			
			if (chan == 0 and: { ccNum >= 33 and: { ccNum <= 40 }}) {
				paramIndex = ccNum - 33; // Map CC 33-40 to param index 0-7
				is14Bit = true;
				
				if (~midi14BitValues[paramIndex].isNil) {
					~midi14BitValues[paramIndex] = [0, 0]; // [MSB, LSB]
				};
				~midi14BitValues[paramIndex][1] = val;
				
				finalValue = (~midi14BitValues[paramIndex][0] * 128) + ~midi14BitValues[paramIndex][1];
				normalizedValue = finalValue / 16383.0;
				

			};
			
			// Scheme 2: CC 21-28 (MSB) + CC 53-60 (LSB) - Common on BCR2000/BCF2000
			if (chan == 0 and: { ccNum >= 21 and: { ccNum <= 28 }}) {
				paramIndex = ccNum - 21; // Map CC 21-28 to param index 0-7
				is14Bit = true;
				
				if (~midi14BitValues[paramIndex].isNil) {
					~midi14BitValues[paramIndex] = [0, 0]; // [MSB, LSB]
				};
				~midi14BitValues[paramIndex][0] = val;
				
				finalValue = (~midi14BitValues[paramIndex][0] * 128) + ~midi14BitValues[paramIndex][1];
				normalizedValue = finalValue / 16383.0;
				

			};
			
			if (chan == 0 and: { ccNum >= 53 and: { ccNum <= 60 }}) {
				paramIndex = ccNum - 53; // Map CC 53-60 to param index 0-7
				is14Bit = true;
				
				if (~midi14BitValues[paramIndex].isNil) {
					~midi14BitValues[paramIndex] = [0, 0]; // [MSB, LSB]
				};
				~midi14BitValues[paramIndex][1] = val;
				
				finalValue = (~midi14BitValues[paramIndex][0] * 128) + ~midi14BitValues[paramIndex][1];
				normalizedValue = finalValue / 16383.0;
				

			};
			
			// Legacy 7-bit MIDI support removed for CC 21-28 (now used for 14-bit MSB)
			// If you need 7-bit support, use a different CC range or configure your controller differently
			
			// Process parameter update for 14-bit controllers
			if (normalizedValue.notNil) {
					
					//("[MIDI DEBUG] CC % received - raw val: %, normalized: %").format(ccNum, val, normalizedValue).postln;
					
					// Get current effect's parameter specs
					if (~effect.notNil and: { ~effectParameterSpecs.notNil }) {
						var currentEffectName = ~effect.defName.asString;
						var specs = ~effectParameterSpecs[currentEffectName.asSymbol];
						
						if (specs.notNil) {
							// Sort param names alphabetically to match UI fader layout
							// CC 21 = first param (alphabetically), CC 22 = second param, etc.
							var paramNames = specs.keys.asArray.sort;
							//("[MIDI DEBUG] Effect % has params: %").format(currentEffectName, paramNames).postln;
							
							if (paramIndex < paramNames.size) {
								paramName = paramNames[paramIndex];
								paramSpec = specs[paramName];
								//("[MIDI DEBUG] Param index % maps to param: %").format(paramIndex, paramName).postln;
								
								if (paramSpec.notNil) {
									// Map normalized value to parameter range
									var mappedValue = paramSpec.map(normalizedValue);
									//("[MIDI DEBUG] Spec for %: min=%, max=%, warp=%, default=%").format(
									//	paramName, paramSpec.minval, paramSpec.maxval, 
									//	paramSpec.warp.asSpecifier, paramSpec.default
									//).postln;
									//("[MIDI DEBUG] Mapped value: % -> % (using spec.map)").format(normalizedValue, mappedValue).postln;
									
																																																																																																						// Update the parameter in SuperCollider
																																																																																		~effect.set(paramName, mappedValue);
																																																																																		// Parameter updated (debug logging removed for performance)
											
																												// MIDI is the ONLY source that updates parameter tracking
																	// This prevents conflicts between OSC and MIDI tracking
																	if(~effectParameterValues[currentEffectName.asSymbol].isNil, {
																		~effectParameterValues[currentEffectName.asSymbol] = IdentityDictionary.new;
																	});
																	
																	// Only update if value has changed significantly to avoid jitter
																	if (~effectParameterValues[currentEffectName.asSymbol][paramName] != mappedValue) {
																		~effectParameterValues[currentEffectName.asSymbol][paramName] = mappedValue;
																		
																		// Force broadcast on next cycle
																		if (~lastBroadcastValues[currentEffectName.asSymbol].notNil) {
																			// Invalidating last value ensures the broadcast routine picks it up
																			~lastBroadcastValues[currentEffectName.asSymbol][paramName] = nil;
																		};
																	};
																	// MIDI parameter tracked for broadcasting
											
											// Note: UI updates will come from the parameter broadcast routine
											// No direct OSC message to UI needed - eliminates feedback loop
								}
							} {
								// ("MIDI CC: CC % ignored - effect has only % parameters").format(ccNum, paramNames.size).postln;
							}
						} {
							// ("MIDI CC: No parameter specs found for effect %").format(currentEffectName).postln;
						}
					} {
						// ("MIDI CC: No active effect to control").postln;
					}
				}
			});
			"MIDI CC handler created for 14-bit encoders (multiple schemes supported) on channel 1".postln;
		}, {
			"No MIDI devices found, MIDI handlers not created.".postln;
		});
	} { |error|
		("MIDI Setup Failed: " ++ error.errorString).postln;
		"Continuing boot without MIDI.".postln;
		~hasMIDI = false; // Ensure ~hasMIDI is false if setup fails
	};
	// MIDI SETUP END

	"Server booted successfully. END OF SCRIPT".postln;
};

// Cleanup function to stop parameter broadcasting when server shuts down
ServerQuit.add({
	if (~parameterBroadcastRoutine.notNil) {
		~parameterBroadcastRoutine.stop;
		~parameterBroadcastRoutine = nil;
		"Parameter broadcast routine stopped".postln;
	};
});
)