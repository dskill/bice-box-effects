// shader: oscilloscope

(
    var defName = \test_sin_wave;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var freq = \freq.kr(440);
        var amp = \amp.kr(0.2);
        var wave_mix = \wave_mix.kr(0);
        var filter_freq = \filter_freq.kr(2000);
        var filter_res = \filter_res.kr(0.1);
        var vibrato_rate = \vibrato_rate.kr(5);
        var vibrato_depth = \vibrato_depth.kr(10);
        var pulse_width = \pulse_width.kr(0.5);
        var tremolo_rate = \tremolo_rate.kr(3);
        var ring_mod_freq = \ring_mod_freq.kr(0);
        var noise_level = \noise_level.kr(0);
        var sub_level = \sub_level.kr(0);
        
        // START USER EFFECT CODE
        var sig, final_sig, mono_for_analysis;
        var vibrato, modulated_freq, tremolo;
        var sine_wave, saw_wave, pulse_wave, mixed_wave;
        var ring_mod, noise, sub_osc;

        sig = In.ar(in_bus); 
        
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
        
        // Apply amplitude with tremolo
        final_sig = final_sig * amp * tremolo;
        
        // END USER EFFECT CODE

        // Prepare mono signal for analysis
        mono_for_analysis = final_sig; // final_sig is already mono

        Out.ar(out, [final_sig, final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'test_sin_wave' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
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
    ));

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new test_sin_wave synth in the effect group
        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)