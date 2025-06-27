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

			// Clean up voice allocation state
			~voice_allocator = Dictionary.new;
			~voice_states = Array.fill(~maxVoices, \free);
			~voice_freqs = Array.fill(~maxVoices, 440);
			~voice_gates = Array.fill(~maxVoices, 0);
			~voice_amps = Array.fill(~maxVoices, 0);

			// Create new synth in the effect group
			~effect = Synth(defName, finalArgs, ~effectGroup);
			("New '%' synth created with args: %").format(defName, finalArgs).postln;
			
			// Always initialize voice arrays for any synth (they'll ignore them if not needed)
			"Initializing voice arrays for synth (will be ignored if not supported)".postln;
			~effect.set(
				\voice_freqs, ~voice_freqs,
				\voice_gates, ~voice_gates,
				\voice_amps, ~voice_amps
			);
		};
	};
	"~setupEffect helper function defined.".postln;

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
					("MIDI DEBUG: Examining source % - device: %, name: %, uid: %").format(i, deviceName, sourceName, src.uid).postln;
					
					// Skip problematic system devices that cause "Device or resource busy" errors
					if (deviceName.contains("System") or: 
						deviceName.contains("SuperCollider") or:
						sourceName.contains("Timer") or:
						sourceName.contains("Announce") or:
						sourceName.contains("out")) {
						("MIDI DEBUG: Skipping system/virtual device: % - %").format(deviceName, sourceName).postln;
					} {
						// Connect to pisound (Raspberry Pi) or common MIDI controllers, but avoid Midi Through if busy
						if (deviceName.contains("pisound") or: 
							deviceName.containsi("launchkey") or: 
							(deviceName.containsi("midi") and: deviceName.contains("Through").not) or:
							deviceName.containsi("keyboard") or:
							deviceName.containsi("controller")) {
							
							("MIDI DEBUG: Attempting to connect to: % - % (uid: %)").format(deviceName, sourceName, src.uid).postln;
							
							// Try different connection methods, but avoid connectAll on Pi
							try {
								// Method 1: Connect using port 0 and device UID
								MIDIIn.connect(0, src.uid);
								("MIDI DEBUG: Successfully connected via Method 1 (port 0, uid) to: % - %").format(deviceName, sourceName).postln;
							} { |error1|
								("MIDI DEBUG: Method 1 failed: %, trying method 2...").format(error1.errorString).postln;
								try {
									// Method 2: Connect using device index
									MIDIIn.connect(device: i);
									("MIDI DEBUG: Successfully connected via Method 2 (device index) to: % - %").format(deviceName, sourceName).postln;
								} { |error2|
									("MIDI DEBUG: Method 2 failed: %, trying method 3...").format(error2.errorString).postln;
									try {
										// Method 3: Connect using just the UID
										MIDIIn.connect(src.uid);
										("MIDI DEBUG: Successfully connected via Method 3 (uid only) to: % - %").format(deviceName, sourceName).postln;
									} { |error3|
										("MIDI DEBUG: All connection methods failed for: % - %. Errors: [1: %, 2: %, 3: %]")
											.format(deviceName, sourceName, error1.errorString, error2.errorString, error3.errorString).postln;
									}
								}
							};
						} {
							("MIDI DEBUG: Skipping non-target device: % - %").format(deviceName, sourceName).postln;
						}
					}
				});
				"MIDI DEBUG: Finished selective MIDI connection.".postln;
				
				// Add a general MIDI message handler for debugging all incoming MIDI
				if(~midi_all_func.notNil, { ~midi_all_func.free });
				~midi_all_func = MIDIFunc({ |val, num, chan, src|
					("MIDI DEBUG: Raw MIDI message - val: %, num: %, chan: %, src: %").format(val, num, chan, src).postln;
				});
				"MIDI DEBUG: General MIDI message handler created.".postln;
				
			}, {
				"MIDI DEBUG: No MIDI devices detected.".postln;
			});

		~held_notes = []; // for monophonic last-note priority
		
		// Voice allocation for single-synth polyphony
		~maxVoices = 8; // Must match the numVoices in polyphonic synths
		~voice_allocator = (); // Dictionary: midiNote -> voiceIndex
		~voice_states = Array.fill(~maxVoices, \free); // Track voice states: \free, \active
		~voice_freqs = Array.fill(~maxVoices, 440); // Current frequencies
		~voice_gates = Array.fill(~maxVoices, 0); // Current gate states
		~voice_amps = Array.fill(~maxVoices, 0); // Current amplitudes
		
		// Voice allocation helper functions
		~findFreeVoice = {
			var freeIndex = ~voice_states.indexOf(\free);
			if(freeIndex.isNil) {
				// No free voices, steal the oldest (voice 0 for simplicity)
				("MIDI DEBUG: No free voices, stealing voice 0").postln;
				0;
			} {
				freeIndex;
			};
		};
		
		~updateVoiceArrays = {
			// Send updated voice arrays to the effect synth
			if(~effect.notNil) {
				~effect.set(
					\voice_freqs, ~voice_freqs,
					\voice_gates, ~voice_gates,
					\voice_amps, ~voice_amps
				);
				("MIDI DEBUG: Updated voice arrays - freqs: %, gates: %, amps: %")
					.format(~voice_freqs, ~voice_gates, ~voice_amps).postln;
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
				("MIDI DEBUG: Note On - velocity: %, note: % (% Hz), channel: %, source: %, device: %")
					.format(vel, num, num.midicps.round(0.1), chan, src, if(srcDevice.notNil, srcDevice.device, "unknown")).postln;
				
				// Always handle polyphonic voice allocation for any synth
				if (~effect.notNil) {
					("MIDI DEBUG: Processing MIDI note on for %").format(~effect.defName).postln;
					
					// Check if this note is already allocated
					if (~voice_allocator[num].notNil) {
						// Note is already playing, retrigger it
						voiceIndex = ~voice_allocator[num];
						("MIDI DEBUG: Retriggering note % on voice %").format(num, voiceIndex).postln;
						~voice_gates[voiceIndex] = 0; // Quick gate off
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
						
						("MIDI DEBUG: Allocated voice % for note % (% Hz)")
							.format(voiceIndex, num, num.midicps.round(0.1)).postln;
					};
					
					// Always send voice arrays AND monophonic data to any synth
					// Synths will ignore parameters they don't have
					~updateVoiceArrays.value;
					
					// Also update traditional monophonic parameters for backward compatibility
					if (~held_notes.any({|item| item == num}).not) { 
						~held_notes.add(num); 
						("MIDI DEBUG: Added note % to held_notes, now: %").format(num, ~held_notes).postln;
					};
					
					~effect.set(\freq, num.midicps, \gate, 1);
					("MIDI DEBUG: Sent both polyphonic and monophonic MIDI data").postln;
				} {
					"MIDI DEBUG: No effect synth to control".postln;
				};
			});

			~midi_note_off_func = MIDIFunc.noteOff({ |vel, num, chan, src|
				var srcDevice, voiceIndex;
				// Enhanced debug logging for MIDI note-off messages
				srcDevice = MIDIClient.sources.detect{|d| d.uid == src};
				("MIDI DEBUG: Note Off - velocity: %, note: % (% Hz), channel: %, source: %, device: %")
					.format(vel, num, num.midicps.round(0.1), chan, src, if(srcDevice.notNil, srcDevice.device, "unknown")).postln;
				
				// Always handle both polyphonic and monophonic note-off
				if (~effect.notNil) {
					("MIDI DEBUG: Processing MIDI note off for %").format(~effect.defName).postln;
					
					// Handle polyphonic voice release
					if (~voice_allocator[num].notNil) {
						voiceIndex = ~voice_allocator[num];
						("MIDI DEBUG: Releasing voice % for note %").format(voiceIndex, num).postln;
						
						// Release the voice
						~voice_gates[voiceIndex] = 0;
						~voice_states[voiceIndex] = \free;
						~voice_allocator.removeAt(num);
						
						// Update the synth with new voice arrays
						~updateVoiceArrays.value;
					};
					
					// Also handle monophonic note tracking for backward compatibility
					~held_notes.remove(num);
					("MIDI DEBUG: Removed note % from held_notes, now: %").format(num, ~held_notes).postln;
					
					if (~held_notes.isEmpty) {
						"MIDI DEBUG: No more held notes, setting monophonic gate: 0".postln;
						~effect.set(\gate, 0);
					} {
						("MIDI DEBUG: Still holding notes, setting monophonic freq to last note: % (% Hz)")
							.format(~held_notes.last, ~held_notes.last.midicps.round(0.1)).postln;
						~effect.set(\freq, ~held_notes.last.midicps);
					};
					
					("MIDI DEBUG: Sent both polyphonic and monophonic note-off data").postln;
				} {
					"MIDI DEBUG: No effect synth to control".postln;
				};
			});
			"MIDI DEBUG: Note handlers created.".postln;
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
)