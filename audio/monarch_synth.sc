(
    SynthDef(\monarch_synth, {
        |out = 0, in_bus = 0, synthOctave = 0, synthFilterCutoff = 5000, synthFilterResonance = 0.2, synthDrive = 0.1, mix = 0.5|
        var sig, dry, freq, hasFreq, synthSig, filteredSig, distortedSig, finalSig;
        var phase, trig, partition, kr_impulse;
        var rms_input, rms_output;


        sig = In.ar(in_bus);
        dry = sig;

        // Pitch detection
        # freq, hasFreq = Pitch.kr(
					in: sig,
					ampThreshold: 0.02,
					median: 3);

        // Sawtooth oscillator driven by detected pitch, transposable by octaves
        synthSig = Saw.ar(freq * (2.pow(synthOctave)));

        // Low-pass filter
        filteredSig = MoogFF.ar(
            in: synthSig,
            freq: synthFilterCutoff,
            gain: 1.0,
            res: synthFilterResonance
        );
        
        // Distortion (analog-style saturation)
        distortedSig = (filteredSig * (1 + synthDrive * 20)).tanh;


        // Mix dry and wet signals
        finalSig = XFade2.ar(dry, distortedSig, mix * 2 - 1);


        // MACHINERY FOR SAMPLING THE SIGNAL
        phase = Phasor.ar(0, 1, 0, ~chunkSize);
        trig = HPZ1.ar(phase) < 0;
        partition = PulseCount.ar(trig) % ~numChunks;

        // write to buffers that will contain the waveform data we send via OSC
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        rms_input = RunningSum.rms(sig, 1024);
        rms_output = RunningSum.rms(finalSig, 1024);

        // Send RMS values to the control buses
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        kr_impulse = Impulse.kr(60);  // Trigger 60 times per second
        SendReply.kr(kr_impulse, '/buffer_refresh', partition); //trig if you want audio rate
        SendReply.kr(kr_impulse, '/rms'); 


	    Out.ar(out, [finalSig, finalSig]);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        // Create new monarch_synth synth in the effect group
        ~effect = Synth(\monarch_synth, [\in_bus, ~input_bus], ~effectGroup);
        "New effect synth created".postln;
    };
)