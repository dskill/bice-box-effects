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
							paramValues.keysValuesDo({ |paramName, value|
								var lastValue = lastValues[paramName];
								// Much larger tolerance to prevent any oscillations
								// Only broadcast significant changes (> 2% of parameter range)
								var tolerance = 0.0001; // 0.5% tolerance for responsive UI updates
								var diff = if(lastValue.isNil, { 999 }, { (value - lastValue).abs });
								if (lastValue.isNil or: { diff > tolerance }) {
									paramData = paramData ++ [paramName.asString, value];
									lastValues[paramName] = value;
									hasChanges = true;
																						// Parameter changed, broadcasting
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
			
			// MIDI CC Handler for parameter control
			if(~midi_cc_func.notNil, { ~midi_cc_func.free });
			~midi_cc_func = MIDIFunc.cc({ |val, ccNum, chan, src|
				var paramIndex, normalizedValue, paramName, paramSpec;
				
				// Handle CC 117 for push-to-talk functionality
				if (chan == 15 and: { ccNum == 117 }) { // Channel 16 (15 in SC) Controller 117
					// Send OSC message to Electron for push-to-talk control
					~o.sendMsg('/midi/cc117', val);
					("[MIDI DEBUG] CC117 push-to-talk: val=%").format(val).postln;
				};
				
				// Only respond to CC 21-28 on channel 1 (channel 0 in SC)
				if (chan == 0 and: { ccNum >= 21 and: { ccNum <= 28 }}) {
					paramIndex = ccNum - 21; // Map CC 21-28 to param index 0-7
					normalizedValue = val / 127.0; // MIDI CC is 0-127, normalize to 0-1
					
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
																																																																																		("[MIDI DEBUG] CC% val=% -> %=% (norm=%)").format(ccNum, val, paramName, mappedValue, normalizedValue).postln;
											
																												// MIDI is the ONLY source that updates parameter tracking
																	// This prevents conflicts between OSC and MIDI tracking
																	if(~effectParameterValues[currentEffectName.asSymbol].isNil, {
																		~effectParameterValues[currentEffectName.asSymbol] = IdentityDictionary.new;
																	});
																	~effectParameterValues[currentEffectName.asSymbol][paramName] = mappedValue;
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
			"MIDI CC handler created for CC 21-28 on channel 1".postln;
		}, {
			"MIDI DEBUG: No MIDI devices found, MIDI handlers not created.".postln;
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