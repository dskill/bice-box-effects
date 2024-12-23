// Pizza Phaser Effect
// Combines phaser with amplitude modulation for a "hungry to full" cycle effect

SynthDef(\pizza_phaser, {
    arg inBus=0, outBus=0,
    // Phaser parameters
    rate=0.5, // Speed of phaser oscillation
    depth=0.5, // Depth of phaser effect
    // Amplitude modulation
    modRate=0.2, // Speed of AM
    modDepth=0.3, // Depth of AM
    // Mix
    mix=0.5, // Dry/wet mix
    // Global
    amp=1.0; // Output amplitude

    var input = In.ar(inBus, 2);
    var numStages = 6;
    var freq = 100;
    var modPhase;
    var phaser;
    var output;
    var analysis;
    
    // Create phaser effect
    modPhase = SinOsc.kr(rate, 0, depth * 800, 1000 + freq);
    phaser = input;
    numStages.do {
        phaser = AllpassL.ar(phaser, 0.1, modPhase.reciprocal, 0);
    };
    
    // Add amplitude modulation
    output = phaser * (1 - (modDepth * SinOsc.kr(modRate)));
    
    // Mix dry and wet signals
    output = (input * (1 - mix)) + (output * mix);
    output = output * amp;
    
    // Send output
    Out.ar(outBus, output);
    
    // Analysis for visualization
    analysis = FFT(LocalBuf(4096), output[0]);
    SendReply.kr(Impulse.kr(60), '/fft_data0', analysis.asArray); // Send FFT data
    
    // Send waveform data
    SendReply.kr(
        Impulse.kr(60),
        '/waveform1',
        output[0].asArray
    );
    
    // Send RMS data
    SendReply.kr(
        Impulse.kr(60),
        '/audio_analysis',
        [
            Amplitude.kr(input[0]), // Input RMS
            Amplitude.kr(output[0]) // Output RMS
        ]
    );
}); 