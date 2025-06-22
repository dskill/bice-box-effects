// shader: oscilloscope

(
    var defName = \synthtoy;
    var specs = (
        freq: ControlSpec(20, 2000, 'exp', 0, 440, "Hz"),
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
        var freq = \freq.kr(specs[\freq].default);
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
        var gate = \gate.kr(0); // gate for MIDI control, default closed
        
        // START USER EFFECT CODE
        var final_sig, mono_for_analysis;
        var vibrato, modulated_freq, tremolo, env;
        var sine_wave, saw_wave, pulse_wave, mixed_wave;
        var ring_mod, noise, sub_osc;

        // sig = In.ar(in_bus); // THIS IS REMOVED
        
        // Add vibrato modulation to frequency
        vibrato = SinOsc.kr(vibrato_rate) * vibrato_depth;
        modulated_freq = freq + vibrato;
        
        // Generate different waveforms
        sine_wave = SinOsc.ar(modulated_freq);
        saw_wave = Saw.ar(modulated_freq);
        pulse_wave = Pulse.ar(modulated_freq, pulse_width);
        
        // Mix between waveforms based on wave_mix parameter
        // 0 = sine, 0.5 = saw, 1 = pulse
        mixed_wave = SelectX.ar(wave_mix * 2, [sine_wave, saw_wave, pulse_wave]);
        
        // Add sub-oscillator (one octave down)
        sub_osc = SinOsc.ar(modulated_freq * 0.5) * sub_level;
        mixed_wave = mixed_wave + sub_osc;
        
        // Add ring modulation for metallic/bell timbres
        ring_mod = SinOsc.ar(ring_mod_freq);
        mixed_wave = mixed_wave * (1 + (ring_mod * 0.5)); // subtle ring mod when freq > 0
        
        // Add noise for texture
        noise = WhiteNoise.ar() * noise_level;
        mixed_wave = mixed_wave + noise;
        
        // Apply filter
        final_sig = RLPF.ar(mixed_wave, filter_freq, filter_res);
        
        // Add tremolo (amplitude modulation)
        tremolo = SinOsc.kr(tremolo_rate) * 0.3 + 0.7; // oscillates between 0.4 and 1.0
        
        // Create an ADSR envelope controlled by the gate
        env = EnvGen.ar(Env.adsr(0.01, 0.2, 0.7, 0.3), gate);

        // Apply amplitude with tremolo and envelope
        final_sig = final_sig * amp * tremolo * env;
        
        // END USER EFFECT CODE

        // Prepare mono signal for analysis
        mono_for_analysis = final_sig; // final_sig is already mono

        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'synthtoy' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)