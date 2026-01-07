// shader: neon_geometrics
// category: MIDI

(
    var defName = \neon_geometrics;
    var numVoices = 8; // Maximum polyphony
    var specs = (
        // Synthwave analog synth parameters
        amp: ControlSpec(0, 1, 'lin', 0, 0.3, ""),
        analog_saw: ControlSpec(0, 1, 'lin', 0, 0.7, "%"),
        analog_square: ControlSpec(0, 1, 'lin', 0, 0.4, "%"),
        filter_freq: ControlSpec(200, 8000, 'exp', 0, 3500, "Hz"),
        filter_res: ControlSpec(0.1, 4, 'lin', 0, 1.8, ""),
        filter_drive: ControlSpec(1, 8, 'exp', 0, 2.5, "x"),
        chorus_rate: ControlSpec(0.1, 5, 'exp', 0, 1.2, "Hz"),
        chorus_depth: ControlSpec(0, 1, 'lin', 0, 0.6, "%"),
        delay_time: ControlSpec(0.05, 0.8, 'exp', 0, 0.25, "s"),
        delay_feedback: ControlSpec(0, 0.95, 'lin', 0, 0.4, "%"),
        neon_glow: ControlSpec(0, 2, 'lin', 0, 0.8, "x"),
        retro_detune: ControlSpec(0, 50, 'lin', 0, 12, "cents")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        
        // Synthwave synth parameters
        var amp = \amp.kr(specs[\amp].default);
        var analog_saw = \analog_saw.kr(specs[\analog_saw].default);
        var analog_square = \analog_square.kr(specs[\analog_square].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var filter_res = \filter_res.kr(specs[\filter_res].default);
        var filter_drive = \filter_drive.kr(specs[\filter_drive].default);
        var chorus_rate = \chorus_rate.kr(specs[\chorus_rate].default);
        var chorus_depth = \chorus_depth.kr(specs[\chorus_depth].default);
        var delay_time = \delay_time.kr(specs[\delay_time].default);
        var delay_feedback = \delay_feedback.kr(specs[\delay_feedback].default);
        var neon_glow = \neon_glow.kr(specs[\neon_glow].default);
        var retro_detune = \retro_detune.kr(specs[\retro_detune].default);
        
        // Polyphonic voice parameters (arrays)
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        // ALL variables declared here
        var final_sig, mono_for_analysis;
        var voice_signals, mixed_voices, filtered_sig;
        var chorus_sig, delayed_sig, neon_processed;
        
        // Generate all voices with classic synthwave character
        voice_signals = Array.fill(numVoices, { |i|
            var freq, gate, vel_amp;
            var env, saw_osc, square_osc, detune_osc, mixed_wave;
            var voice_out;
            
            // Handle voice parameters
            if(numVoices > 1) {
                freq = voice_freqs[i];
                gate = voice_gates[i];
                vel_amp = voice_amps[i];
            } {
                freq = voice_freqs;
                gate = voice_gates;
                vel_amp = voice_amps;
            };
            
            // Classic synthwave ADSR envelope - punchy attack, long sustain
            env = EnvGen.ar(Env.adsr(0.005, 0.1, 0.8, 0.6), gate);
            
            // Generate classic analog-style waveforms
            saw_osc = Saw.ar(freq) * analog_saw;
            square_osc = Pulse.ar(freq, 0.5) * analog_square;
            
            // Add subtle detuning for that analog warmth
            detune_osc = Saw.ar(freq * (1 + (retro_detune * 0.0001))) * analog_saw * 0.3;
            
            // Mix the classic synthwave waveforms
            mixed_wave = saw_osc + square_osc + detune_osc;
            
            // Add some analog-style saturation
            mixed_wave = tanh(mixed_wave * 1.2) * 0.8;
            
            // Apply voice envelope and velocity
            voice_out = mixed_wave * env * vel_amp;
            voice_out;
        });
        
        // Mix all voices together
        mixed_voices = Mix.ar(voice_signals);
        
        // Classic analog-style Moog filter with drive
        filtered_sig = MoogFF.ar(mixed_voices * filter_drive, filter_freq, filter_res);
        
        // Add classic synthwave chorus effect
        chorus_sig = filtered_sig;
        chorus_sig = chorus_sig + DelayL.ar(filtered_sig, 0.02, 
            SinOsc.kr(chorus_rate, 0, chorus_depth * 0.01, 0.015));
        chorus_sig = chorus_sig + DelayL.ar(filtered_sig, 0.02, 
            SinOsc.kr(chorus_rate * 1.3, pi/2, chorus_depth * 0.008, 0.012));
        
        // Add classic synthwave delay
        delayed_sig = DelayL.ar(chorus_sig, 1.0, delay_time);
        delayed_sig = LPF.ar(delayed_sig, 8000); // High-freq rolloff for vintage feel
        chorus_sig = chorus_sig + (delayed_sig * delay_feedback);
        
        // Apply "neon glow" - subtle harmonic enhancement
        neon_processed = chorus_sig + (chorus_sig * SinOsc.ar(chorus_sig * 200) * neon_glow * 0.1);
        
        // Final amplitude and subtle vintage warmth
        final_sig = neon_processed * amp;
        final_sig = tanh(final_sig * 1.1) * 0.9; // Soft saturation
        
        // Remove DC offset
        final_sig = LeakDC.ar(final_sig);

        // Prepare mono signal for analysis
        mono_for_analysis = final_sig;

        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'neon_geometrics' (polyphonic) added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs, [], numVoices);
)