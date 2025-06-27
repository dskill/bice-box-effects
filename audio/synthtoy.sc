// shader: oscilloscope

(
    var defName = \synthtoy;
    var numVoices = 8; // Maximum polyphony
    var specs = (
        // Global parameters
        amp: ControlSpec(0, 1, 'lin', 0, 0.2, ""),
        wave_mix: ControlSpec(0, 1, 'lin', 0, 0, ""),
        filter_freq: ControlSpec(100, 8000, 'exp', 0, 2000, "Hz"),
        filter_res: ControlSpec(0.1, 2, 'lin', 0, 0.1, ""),
        vibrato_rate: ControlSpec(0.1, 20, 'exp', 0, 5, "Hz"),
        vibrato_depth: ControlSpec(0, 100, 'lin', 0, 0, "Hz"),
        pulse_width: ControlSpec(0, 1, 'lin', 0, 0.5, ""),
        tremolo_rate: ControlSpec(0.1, 20, 'exp', 0, 3, "Hz"),
        ring_mod_freq: ControlSpec(0, 2000, 'exp', 0, 0, "Hz"),
        noise_level: ControlSpec(0, 1, 'lin', 0, 0, ""),
        sub_level: ControlSpec(0, 1, 'lin', 0, 0, "")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        
        // Global parameters
        var amp = \amp.kr(specs[\amp].default);
        var wave_mix = \wave_mix.kr(specs[\wave_mix].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var filter_res = \filter_res.kr(specs[\filter_res].default);
        var vibrato_rate = \vibrato_rate.kr(specs[\vibrato_rate].default);
        var vibrato_depth = \vibrato_depth.kr(specs[\vibrato_depth].default);
        var pulse_width = \pulse_width.kr(specs[\pulse_width].default);
        var tremolo_rate = \tremolo_rate.kr(specs[\tremolo_rate].default);
        var ring_mod_freq = \ring_mod_freq.kr(specs[\ring_mod_freq].default);
        var noise_level = \noise_level.kr(specs[\noise_level].default);
        var sub_level = \sub_level.kr(specs[\sub_level].default);
        
        // Polyphonic voice parameters (arrays)
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        // START USER EFFECT CODE
        var final_sig, mono_for_analysis;
        var tremolo, voice_signals, mixed_voices;
        var vibrato, modulated_freqs;
        
        // Global vibrato modulation
        vibrato = SinOsc.kr(vibrato_rate) * vibrato_depth;
        modulated_freqs = voice_freqs + vibrato;
        
        // Generate all voices
        voice_signals = Array.fill(numVoices, { |i|
            var freq = modulated_freqs[i];
            var gate = voice_gates[i];
            var vel_amp = voice_amps[i];
            var env, sine_wave, saw_wave, pulse_wave, mixed_wave;
            var ring_mod, noise, sub_osc, voice_out;
            
            // Create envelope for this voice
            env = EnvGen.ar(Env.adsr(0.01, 0.2, 0.7, 0.3), gate);
            
            // Generate different waveforms for this voice
            sine_wave = SinOsc.ar(freq);
            saw_wave = Saw.ar(freq);
            pulse_wave = Pulse.ar(freq, pulse_width);
            
            // Mix between waveforms based on wave_mix parameter
            mixed_wave = SelectX.ar(wave_mix * 2, [sine_wave, saw_wave, pulse_wave]);
            
            // Add sub-oscillator (one octave down)
            sub_osc = SinOsc.ar(freq * 0.5) * sub_level;
            mixed_wave = mixed_wave + sub_osc;
            
            // Add ring modulation for metallic/bell timbres
            ring_mod = SinOsc.ar(ring_mod_freq);
            mixed_wave = mixed_wave * (1 + (ring_mod * 0.5));
            
            // Apply voice envelope and velocity
            voice_out = mixed_wave * env * vel_amp;
            voice_out;
        });
        
        // Mix all voices together
        mixed_voices = Mix.ar(voice_signals);
        
        // Add global noise
        mixed_voices = mixed_voices + (WhiteNoise.ar() * noise_level);
        
        // Apply global filter to the mixed signal
        final_sig = RLPF.ar(mixed_voices, filter_freq, filter_res);
        
        // Add global tremolo (amplitude modulation)
        tremolo = SinOsc.kr(tremolo_rate) * 0.3 + 0.7;
        
        // Apply global amplitude and tremolo
        final_sig = final_sig * amp * tremolo;
        
        // END USER EFFECT CODE

        // Prepare mono signal for analysis
        mono_for_analysis = final_sig;

        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'synthtoy' (polyphonic) added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs, [], \poly);
)