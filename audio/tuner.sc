// shader: oscilloscope
(
    var defName = \tuner;
    var specs = (
        bypass: ControlSpec(0.0, 1.0, 'lin', 0, 0, "")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var bypass = \bypass.kr(specs[\bypass].default); // Simple parameter for consistency
        
        var sig, normalized_sig, freq, hasFreq, differences, amplitudes, mono_for_analysis;
        var guitarStringsHz = #[82.41, 110.00, 146.83, 196.00, 246.94, 329.63]; // Frequencies of E2, A2, D3, G3, B3, E4

        // Input signal
        sig = In.ar(in_bus);
        normalized_sig = Normalizer.ar(sig, level: 1, dur: 0.01);

        // Pitch detection 
        # freq, hasFreq = Pitch.kr(normalized_sig, 
            initFreq: 50, 
            minFreq: 60, 
            maxFreq: 400, 
            ampThreshold: 0.04, 
            median: 10,
            execFreq: 100.0,
             maxBinsPerOctave: 16,
             peakThreshold: 0.5, downSample: 1, clar: 0
        );
        freq = Lag.kr(freq, 0.2); // Increase smoothing time slightly
         
        // Calculate differences between detected frequency and each guitar string
        differences = freq - guitarStringsHz;
 
        // Extract amplitude for each string frequency using bandpass filters
        amplitudes = guitarStringsHz.collect { |hz|
            var band = BPF.ar(sig, hz, 0.01); // Bandpass filter with narrow bandwidth
            Amplitude.kr(band)
        };

        // Prepare mono signal for analysis
        // If sig from In.ar can be stereo, Mix.ar ensures mono. If always mono, this is still safe.
        mono_for_analysis = Mix.ar(sig);

        // Kept effect-specific SendReply for /tuner_data
        SendReply.kr(Impulse.kr(10), '/tuner_data', [freq, hasFreq] ++ differences ++ amplitudes);
        
        Out.ar(out, sig);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'tuner' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
