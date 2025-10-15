// shader: oscilloscope
(
    var defName = \vocoder_envelope;
    var numVoices = 8;
    var specs = (
        carrier_freq: ControlSpec(100, 800, 'exp', 0, 200, "Hz"),
        mod_amount: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%"),
        attack: ControlSpec(0.001, 1.0, 'exp', 0, 0.05, "s"),
        release: ControlSpec(0.01, 2.0, 'exp', 0, 0.3, "s"),
        filter_spread: ControlSpec(0.5, 3.0, 'lin', 0, 1.5, "")
    );

    SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var carrier_freq = \carrier_freq.kr(specs[\carrier_freq].default);
        var mod_amount = \mod_amount.kr(specs[\mod_amount].default);
        var attack = \attack.kr(specs[\attack].default);
        var release = \release.kr(specs[\release].default);
        var filter_spread = \filter_spread.kr(specs[\filter_spread].default);
        
        // Voice arrays
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));

        // Variables
        var sig, total_env, band_frequencies, vocoder_bands, final_mix, mono_for_analysis;

        // Input signal
        sig = In.ar(in_bus);

        // Calculate total envelope from all active voices
        total_env = Mix.ar(Array.fill(numVoices, { |i|
            var gate, vel_amp;
            if(numVoices > 1) {
                gate = voice_gates[i];
                vel_amp = voice_amps[i];
            } {
                gate = voice_gates;
                vel_amp = voice_amps;
            };
            EnvGen.ar(Env.asr(attack, 1.0, release), gate) * vel_amp;
        }));

        // Pre-calculate 8 frequency bands
        band_frequencies = [
            carrier_freq * 0.5,
            carrier_freq * 0.7,
            carrier_freq * 1.0,
            carrier_freq * 1.4,
            carrier_freq * 2.0,
            carrier_freq * 2.8,
            carrier_freq * 4.0,
            carrier_freq * 5.6
        ];

        // Create 8 vocoder frequency bands
        vocoder_bands = Array.fill(8, { |i|
            var band_freq, modulator, carriers, band_out;
            
            band_freq = band_frequencies[i];
            
            // Band-pass filter the input signal for this frequency band
            modulator = BPF.ar(sig, band_freq, 0.2 / filter_spread);
            
            // Envelope follower to extract amplitude
            modulator = Amplitude.ar(modulator, 0.005, 0.05);
            
            // Generate carrier tones from all active voices for this band
            carriers = Mix.ar(Array.fill(numVoices, { |j|
                var freq, gate, vel_amp, env, voice_carrier;
                
                if(numVoices > 1) {
                    freq = voice_freqs[j];
                    gate = voice_gates[j];
                    vel_amp = voice_amps[j];
                } {
                    freq = voice_freqs;
                    gate = voice_gates;
                    vel_amp = voice_amps;
                };
                
                env = EnvGen.ar(Env.asr(attack, 1.0, release), gate);
                
                // Harmonically related carrier for this band
                voice_carrier = SinOsc.ar((freq * (i + 1) * 0.5) + band_freq) * env * vel_amp;
                voice_carrier;
            }));
            
            // Modulate carriers with processed input envelope
            band_out = carriers * modulator * mod_amount;
            band_out;
        });

        // Mix all vocoder bands together
        final_mix = Mix.ar(vocoder_bands);
        
        // Add some of the original carrier mix for clarity
        final_mix = final_mix + (Mix.ar(Array.fill(numVoices, { |i|
            var freq, gate, vel_amp, env;
            if(numVoices > 1) {
                freq = voice_freqs[i];
                gate = voice_gates[i];
                vel_amp = voice_amps[i];
            } {
                freq = voice_freqs;
                gate = voice_gates;
                vel_amp = voice_amps;
            };
            env = EnvGen.ar(Env.asr(attack, 1.0, release), gate);
            SinOsc.ar(freq) * env * vel_amp * 0.2;
        })));
        
        // Light compression
        final_mix = Compander.ar(final_mix, final_mix, 0.6, 1, 0.5, 0.01, 0.1);
        
        // Outputs
        mono_for_analysis = final_mix;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_mix, final_mix]);
    }).add;
    
    "Effect SynthDef 'vocoder_envelope' (polyphonic) added".postln;
    ~setupEffect.value(defName, specs, [], numVoices);
)