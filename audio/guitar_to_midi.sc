// shader: oscilloscope
(
    var defName = \guitar_to_midi;
    var specs = (
        sensitivity: ControlSpec(0.01, 0.3, 'exp', 0, 0.04, ""),
        min_freq: ControlSpec(60, 200, 'exp', 0, 60, "Hz"),
        max_freq: ControlSpec(400, 1200, 'exp', 0, 800, "Hz"),
        smoothing: ControlSpec(0.01, 1.0, 'exp', 0, 0.2, "s"),
        gate_threshold: ControlSpec(0.001, 0.1, 'exp', 0, 0.02, ""),
        retrigger_time: ControlSpec(0.05, 1.0, 'exp', 0, 0.2, "s"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr(0);
        var sensitivity = \sensitivity.kr(specs[\sensitivity].default);
        var min_freq = \min_freq.kr(specs[\min_freq].default);
        var max_freq = \max_freq.kr(specs[\max_freq].default);
        var smoothing = \smoothing.kr(specs[\smoothing].default);
        var gate_threshold = \gate_threshold.kr(specs[\gate_threshold].default);
        var retrigger_time = \retrigger_time.kr(specs[\retrigger_time].default);
        var mix = \mix.kr(specs[\mix].default);
        
        // Variables
        var sig, normalized_sig, freq, hasFreq, amplitude, gate_sig;
        var midi_note, prev_midi_note, note_changed, retrigger_timer;
        var mono_for_analysis;

        // Input signal
        sig = In.ar(in_bus);
        
        // Normalize and get amplitude for gating
        normalized_sig = Normalizer.ar(sig, level: 1, dur: 0.01);
        amplitude = Amplitude.kr(sig, attackTime: 0.01, releaseTime: 0.1);

        // Pitch detection with wider range for guitar
        # freq, hasFreq = Pitch.kr(normalized_sig, 
            initFreq: 100, 
            minFreq: min_freq, 
            maxFreq: max_freq, 
            ampThreshold: sensitivity, 
            median: 7,
            execFreq: 50.0,
            maxBinsPerOctave: 16,
            peakThreshold: 0.5, 
            downSample: 1, 
            clar: 0
        );
        
        // Smooth the frequency to reduce jitter
        freq = Lag.kr(freq, smoothing);
        
        // Convert frequency to MIDI note number
        midi_note = freq.cpsmidi.round;
        
        // Detect note changes
        prev_midi_note = Delay1.kr(midi_note);
        note_changed = (midi_note != prev_midi_note);
        
        // Create gate signal based on amplitude and pitch detection confidence
        gate_sig = (amplitude > gate_threshold) * hasFreq;
        
        // Retrigger timer to prevent rapid note changes
        retrigger_timer = Timer.kr(note_changed);
        
        // Send MIDI data when conditions are met
        SendReply.kr(
            trigger: (note_changed * (retrigger_timer > retrigger_time) * gate_sig),
            cmdName: '/guitar_midi_note_on',
            values: [midi_note, (amplitude * 127).clip(1, 127)]
        );
        
        // Send note off when amplitude drops
        SendReply.kr(
            trigger: Trig1.kr((amplitude < (gate_threshold * 0.5)), 0.1),
            cmdName: '/guitar_midi_note_off', 
            values: [midi_note]
        );
        
        // Debug info
        SendReply.kr(Impulse.kr(5), '/guitar_pitch_debug', 
            [freq, hasFreq, amplitude, midi_note, gate_sig]);
        
        // Analysis output (mono)
        mono_for_analysis = Mix.ar(sig);
        Out.ar(analysis_out_bus, mono_for_analysis);
        
        // Pass through original signal
        Out.ar(out, [sig, sig]);
    });
    def.add;
    "Effect SynthDef 'guitar_to_midi' added".postln;

    ~setupEffect.value(defName, specs);
    
    // Set up OSC responders for MIDI conversion
    OSCdef(\guitarMidiNoteOn, { |msg|
        var midi_note = msg[3];
        var velocity = msg[4];
        
        // Only trigger if SynthToy is loaded and we have MIDI capability
        if(~effect.notNil and: (~currentSynthNumVoices > 0)) {
            // Find a free voice
            var voiceIndex = ~findFreeVoice.value;
            if(voiceIndex.notNil) {
                // Allocate voice
                ~voice_allocator[midi_note] = voiceIndex;
                ~voice_states[voiceIndex] = \active;
                ~voice_freqs[voiceIndex] = midi_note.midicps;
                ~voice_gates[voiceIndex] = 1;
                ~voice_amps[voiceIndex] = velocity / 127;
                
                ~updateVoiceArrays.value;
                
                ("Guitar MIDI: Note ON - MIDI: %, Freq: %, Vel: %")
                    .format(midi_note, midi_note.midicps.round(0.1), velocity).postln;
            };
        };
    }, '/guitar_midi_note_on');
    
    OSCdef(\guitarMidiNoteOff, { |msg|
        var midi_note = msg[3];
        
        // Release the voice
        if(~voice_allocator[midi_note].notNil) {
            var voiceIndex = ~voice_allocator[midi_note];
            ~voice_gates[voiceIndex] = 0;
            ~voice_states[voiceIndex] = \free;
            ~voice_allocator.removeAt(midi_note);
            
            ~updateVoiceArrays.value;
            
            ("Guitar MIDI: Note OFF - MIDI: %").format(midi_note).postln;
        };
    }, '/guitar_midi_note_off');
    
    OSCdef(\guitarPitchDebug, { |msg|
        var freq = msg[3];
        var hasFreq = msg[4];
        var amplitude = msg[5];
        var midi_note = msg[6];
        var gate_sig = msg[7];
        
        if(gate_sig > 0) {
            ("Guitar Pitch: %.1f Hz, MIDI: %, Amp: %.3f, Conf: %")
                .format(freq, midi_note, amplitude, hasFreq).postln;
        };
    }, '/guitar_pitch_debug');
    
    "Guitar-to-MIDI OSC responders installed".postln;
)