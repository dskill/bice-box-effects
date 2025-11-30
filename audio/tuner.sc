// p5: tuner
// category: Utility
(
    var defName = \tuner;
    var specs = (
        bypass: ControlSpec(0.0, 1.0, 'lin', 0, 0, ""),
        smoothing: ControlSpec(0.01, 0.5, 'lin', 0.01, 0.15, "s"),
        sensitivity: ControlSpec(0.001, 0.1, 'exp', 0.001, 0.03, ""),
        median_size: ControlSpec(1, 21, 'lin', 2, 11, ""),
        confidence: ControlSpec(0.1, 0.9, 'lin', 0.01, 0.6, "")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var bypass = \bypass.kr(specs[\bypass].default);
        var smoothing = \smoothing.kr(specs[\smoothing].default);
        var sensitivity = \sensitivity.kr(specs[\sensitivity].default);
        var median_size = \median_size.kr(specs[\median_size].default);
        var confidence = \confidence.kr(specs[\confidence].default);
        
        var sig, filtered_sig, normalized_sig, freq, hasFreq, raw_freq, stable_freq, differences, amplitudes, mono_for_analysis;
        var guitarStringsHz = #[82.41, 110.00, 146.83, 196.00, 246.94, 329.63]; // Frequencies of E2, A2, D3, G3, B3, E4
        var amplitude, gate;

        // Input signal
        sig = In.ar(in_bus);
        
        // Get overall amplitude for gating (use sensitivity parameter)
        amplitude = Amplitude.kr(Mix.ar(sig), attackTime: 0.01, releaseTime: 0.1);
        gate = amplitude > sensitivity; // Adjustable gate threshold
        
        // Pre-filter to focus on fundamentals
        filtered_sig = HPF.ar(sig, 60); // Remove rumble below 60 Hz
        filtered_sig = LPF.ar(filtered_sig, 800); // Focus on fundamentals, not high harmonics
        
        // Normalize after filtering
        normalized_sig = Normalizer.ar(filtered_sig, level: 1, dur: 0.01);

        // Pitch detection - use confidence parameter
        # raw_freq, hasFreq = Pitch.kr(normalized_sig, 
            initFreq: 110,          // Start at A string
            minFreq: 75,            // Just below low E
            maxFreq: 340,           // Just above high E
            ampThreshold: sensitivity * 2, // Scale with sensitivity
            median: median_size,    // Adjustable median filtering
            execFreq: 100.0,        // Fast analysis internally
            maxBinsPerOctave: 32,   // High resolution
            peakThreshold: confidence, // Adjustable confidence threshold
            downSample: 1,
            clar: 1                 // Reject ambiguous detections
        );
        
        // Multi-stage smoothing with adjustable smoothing parameter
        freq = Lag.kr(raw_freq, smoothing);      // First stage: adjustable smoothing
        freq = LagUD.kr(freq, smoothing * 0.33, smoothing * 1.33);   // Second stage: hysteresis
        freq = Median.kr(5, freq);          // Final stage: median filter removes outliers
        
        // Gate the output - only show stable reading
        stable_freq = Select.kr(gate * hasFreq, [0, freq]);
         
        // Calculate differences between detected frequency and each guitar string
        differences = stable_freq - guitarStringsHz;
 
        // Extract amplitude for each string frequency using bandpass filters
        amplitudes = guitarStringsHz.collect { |hz|
            var band = BPF.ar(sig, hz, 0.05);
            Amplitude.kr(band, attackTime: 0.02, releaseTime: 0.15)
        };

        // Prepare mono signal for analysis
        mono_for_analysis = Mix.ar(sig);

        // Update display at moderate rate like real tuners (10Hz)
        SendReply.kr(Impulse.kr(10), '/tuner_data', [stable_freq, gate * hasFreq] ++ differences ++ amplitudes);
        
        Out.ar(out, sig);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'tuner' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)