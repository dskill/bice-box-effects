// shader: oscilloscope
(
    var defName = \satanic_organ;
    var numVoices = 8; // Polyphonic MIDI voices
    var specs = (
        // Organ pipe parameters
        pipe_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%"),
        drawbar_16: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, ""),
        drawbar_8: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, ""),
        drawbar_4: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        
        // Satanic darkness parameters
        darkness_depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        evil_harmonics: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        
        // Chord progression
        chord_level: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        chord_rate: ControlSpec(0.1, 4.0, 'exp', 0, 0.5, "Hz"),
        root_note: ControlSpec(20, 200, 'exp', 0, 55, "Hz"),
        
        // Filtering and space
        filter_freq: ControlSpec(200, 8000, 'exp', 0, 2000, "Hz"),
        reverb_size: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%"),
        
        // Organ envelope (slow attack/release like real organ)
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, ""),
        release: ControlSpec(0.001, 4.0, 'exp', 0, 0.2, "s"),
        
        // Master controls
        amp: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%")
    );

    var def = SynthDef(defName, {
        // Parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var pipe_mix = \pipe_mix.kr(specs[\pipe_mix].default);
        var drawbar_16 = \drawbar_16.kr(specs[\drawbar_16].default);
        var drawbar_8 = \drawbar_8.kr(specs[\drawbar_8].default);
        var drawbar_4 = \drawbar_4.kr(specs[\drawbar_4].default);
        var darkness_depth = \darkness_depth.kr(specs[\darkness_depth].default);
        var evil_harmonics = \evil_harmonics.kr(specs[\evil_harmonics].default);
        var chord_level = \chord_level.kr(specs[\chord_level].default);
        var chord_rate = \chord_rate.kr(specs[\chord_rate].default);
        var root_note = \root_note.kr(specs[\root_note].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);  
        var reverb_size = \reverb_size.kr(specs[\reverb_size].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        var amp = \amp.kr(specs[\amp].default);
        var mix = \mix.kr(specs[\mix].default);
        
        // Polyphonic voice parameters (arrays)
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));

        // Variables - declare all at once like synthtoy
        var sig, dry, organ_voice, satanic_layer, chord_progression;
        var voice_signals, mixed_voices, final_sig, mono_for_analysis;

        // Get input signal
        sig = In.ar(in_bus);
        dry = sig;

        // --- MIDI Polyphonic Satanic Organ Voices ---
        // Generate dark organ voices triggered by MIDI
        voice_signals = Array.fill(numVoices, { |i|
            var freq = voice_freqs[i];
            var gate = voice_gates[i];
            var vel_amp = voice_amps[i];
            var env, organ_voice, evil_voice;
            
            // Organ-style envelope (slow attack, full sustain)
            env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), gate);
            
            // Create satanic organ voice with drawbar harmonics
            organ_voice = Mix.ar([
                // 16' drawbar (sub-octave) - deep bass foundation
                SinOsc.ar(freq * 0.5) * drawbar_16,
                
                // 8' drawbar (fundamental) - main pitch
                SinOsc.ar(freq) * drawbar_8,
                
                // 4' drawbar (octave up) - brightness
                SinOsc.ar(freq * 2) * drawbar_4,
                
                // Evil harmonics - dissonant intervals for satanic flavor
                SinOsc.ar(freq * 1.5) * evil_harmonics * 0.4, // tritone (devil's interval)
                SinOsc.ar(freq * 2.5) * evil_harmonics * 0.3, // dissonant overtone
                SinOsc.ar(freq * 3.5) * evil_harmonics * 0.2, // more dissonance
                
                // Slight detuning for that ominous organ warble
                SinOsc.ar(freq * 1.003) * drawbar_8 * 0.3,
                SinOsc.ar(freq * 0.997) * drawbar_8 * 0.3
            ]);
            
            // Apply envelope and velocity
            organ_voice * env * vel_amp;
        });
        
        // Mix all MIDI voices
        mixed_voices = Mix.ar(voice_signals);
        
        // --- Simple Bass Drone ---
        // Simple bass foundation
        chord_progression = Mix.ar([
            // Main bass drone
            SinOsc.ar(root_note) * chord_level * 0.7,
            
            // Octave down for depth  
            SinOsc.ar(root_note * 0.5) * chord_level * 0.4,
            
            // Fifth for harmony
            SinOsc.ar(root_note * 1.5) * chord_level * 0.3
        ]);
        
        // --- Satanic Atmospheric Layer ---
        // Dark cathedral ambience
        satanic_layer = Mix.ar([
            // Deep cathedral rumble
            SinOsc.ar(SinOsc.kr(0.05).range(30, 50)) * darkness_depth * 0.6,
            
            // Ominous low-frequency drones
            SinOsc.ar(66.6) * darkness_depth * 0.3, // devil's frequency
            SinOsc.ar(33.3) * darkness_depth * 0.2,
            
            // Dark whispers (filtered noise)
            LPF.ar(PinkNoise.ar(darkness_depth * 0.15), 
                SinOsc.kr(0.08).range(80, 200)),
                
            // Pipe organ air sound
            HPF.ar(PinkNoise.ar(darkness_depth * 0.1), 1000) * 0.1
        ]);

        // --- Process Signal (Mono-First Pattern like synthtoy) ---
        // Combine MIDI organ voices with input
        final_sig = sig + mixed_voices;
        
        // Add chord progression
        final_sig = final_sig + chord_progression;
        
        // Add satanic atmospheric layer
        final_sig = final_sig + satanic_layer;
        
        // Apply filter
        final_sig = RLPF.ar(final_sig, filter_freq, 0.4);
        
        // Apply global amplitude
        final_sig = final_sig * amp;
        
        // Apply cathedral reverb (creates stereo) - longer, darker reverb
        final_sig = FreeVerb.ar(final_sig, mix, reverb_size, 0.1);
        
        // Mix with dry signal (mono dry, stereo wet)
        final_sig = XFade2.ar(dry, final_sig, mix * 2 - 1);
        
        // Gentle limiting
        final_sig = final_sig.tanh * 0.8;

        // Analysis output (mono)
        mono_for_analysis = Mix.ar(final_sig);
        Out.ar(analysis_out_bus, mono_for_analysis);

        // Stereo output
        Out.ar(out, final_sig);
    });
    def.add;
    "Effect SynthDef 'satanic_organ' (polyphonic) added".postln;

    // Register specs and create the polyphonic synth
    ~setupEffect.value(defName, specs, [], numVoices);
)