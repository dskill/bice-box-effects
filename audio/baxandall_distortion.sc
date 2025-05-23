(
    SynthDef(\grittyBaxandallDistortion, {
        |out = 0, in_bus = 0, drive = 10.0, bass = 0.0, treble = 0.0, level = 0.7, mix = 0.5|

        var sig, dry, distortedSig, eqSig, wetSig, finalSig;
        var monoDryForVisuals, monoFinalForVisuals; // For machinery
        var phase, trig, partition, kr_impulse;    // Standard machinery vars
        var rms_input, rms_output;                // RMS vars
        var chain_out;                            // FFT var - FFT UGen writes to buffer, chain_out not strictly needed but good for consistency

        sig = In.ar(in_bus, 2); // Read stereo input
        dry = sig;

        // START USER EFFECT CODE
        // 1. Drive / Distortion
        // 'drive' parameter (1 to 100) acts as a pre-gain before tanh distortion
        distortedSig = sig * drive;
        distortedSig = distortedSig.tanh; // tanh for a gritty distortion character (applies per channel)

        // 2. Baxandall Tone Control
        // 'bass' and 'treble' parameters are in dB (-12 to +12)
        // BLowShelf for bass control
        eqSig = BLowShelf.ar(distortedSig, freq: 150, rs: 0.707, db: bass); // Applies per channel
        // BHiShelf for treble control
        eqSig = BHiShelf.ar(eqSig, freq: 4000, rs: 0.707, db: treble);   // Applies per channel

        // 3. Level for wet signal
        // 'level' parameter (0 to 1) adjusts the amplitude of the processed signal
        wetSig = eqSig * level; // Applies per channel

        // 4. Mix with dry signal
        // 'mix' parameter (0 to 1) blends between dry and wet signal
        finalSig = XFade2.ar(dry, wetSig, mix * 2 - 1); // Applies per channel
        // END USER EFFECT CODE

        // Prepare mono versions of dry and final signals for standard machinery (visuals, RMS, FFT)
        monoDryForVisuals = (dry[0] + dry[1]) * 0.5;       // Mix stereo dry to mono
        monoFinalForVisuals = (finalSig[0] + finalSig[1]) * 0.5; // Mix stereo final to mono


        // STANDARD MACHINERY (Waveform, RMS, FFT)
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;
        kr_impulse = Impulse.kr(30);  // Trigger for SendReply

        // Buffer writing for waveform display
        //BufWr.ar(monoDryForVisuals, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(monoFinalForVisuals, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));
        chain_out = FFT(~fft_buffer_out, monoFinalForVisuals, wintype: 1); // Hanning window by default (wintype: 1)
        chain_out.do(~fft_buffer_out);
        SendReply.kr(kr_impulse, '/combined_data', partition);

        // RMS Calculation
        //rms_input = RunningSum.rms(monoDryForVisuals, 1024);
      //  rms_output = RunningSum.rms(monoFinalForVisuals, 1024);
       // Out.kr(~rms_bus_input, rms_input);    // Send RMS input to control bus
        //Out.kr(~rms_bus_output, rms_output);  // Send RMS output to control bus

        // FFT Analysis (on mono version of the output signal)
        // The FFT UGen writes directly to the ~fft_buffer_out


        // SendReply for GUI updates
        //SendReply.kr(kr_impulse, '/buffer_refresh', partition); // Notify GUI which buffer partition is ready
        //SendReply.kr(kr_impulse, '/rms');                      // Notify GUI that RMS values are updated
        //SendReply.kr(kr_impulse, '/fft_data');               // Notify GUI that FFT data in ~fft_buffer_out is ready

        Out.ar(out, finalSig); // Output stereo (finalSig is already a stereo signal)

    }).add;
    "GrittyBaxandallDistortion SynthDef added".postln;

    fork {
        s.sync; // Wait for server readiness
        // Free any existing synth stored in ~effect
        if(~effect.notNil, {
            "Freeing existing GrittyBaxandallDistortion synth".postln;
            ~effect.free;
        });
        // Create a new instance of the GrittyBaxandallDistortion synth
        ~effect = Synth(\grittyBaxandallDistortion, [\in_bus, ~input_bus], ~effectGroup);
        "New GrittyBaxandallDistortion synth created".postln;
    };
)