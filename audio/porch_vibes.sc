(
    SynthDef(\porch_vibes, {
        |out = 0, in_bus = 0, warmth = 0.7, smoke = 0.5, rock_speed = 0.3, sip_amount = 0.4, gain = 1|
        
        // START USER EFFECT CODE
        var sig, dry, finalSig;
        var rocking_mod, smoke_filter, warmth_enhance;
        var sip_effect, ambient_crackle;
        
        sig = In.ar(in_bus);
        
        // Rocking chair movement simulation
        // Creates a gentle back-and-forth filter modulation
        rocking_mod = SinOsc.kr(rock_speed).range(0.7, 1.3);
        sig = RLPF.ar(
            sig,
            freq: 2200 * rocking_mod,
            rq: 0.7
        );
        
        // Smoky atmosphere
        // Uses filtered noise to add airy texture
        smoke_filter = LPF.ar(
            PinkNoise.ar(smoke * 0.1),
            freq: 2800
        ) * LFNoise2.kr(0.5).range(0.3, 0.6);
        
        // Warmth enhancement
        // Adds subtle harmonics and low-end presence
        warmth_enhance = (
            LPF.ar(sig, 250) * 0.7 * warmth +
            HPF.ar(sig, 8000) * 0.2 * (1 - warmth)
        );
        
        // Sipping effect
        // Occasional subtle filter dips
        sip_effect = LFPulse.kr(
            freq: 0.15,
            width: 0.1
        ).range(0.7, 1.0);
        
        // Ambient wood creaking and fire crackle
        ambient_crackle = (
            BPF.ar(
                WhiteNoise.ar(0.1),
                freq: LFNoise2.kr(0.8).range(2000, 4000),
                rq: 0.1
            ) * LFNoise2.kr(1).range(0, 0.1)
        );
        
        // Mix all elements
        finalSig = (
            (sig * 0.7) +
            (warmth_enhance * 0.4) +
            (smoke_filter * smoke) +
            ambient_crackle
        ) * sip_effect * gain;
        
        // Gentle compression to keep things smooth
        finalSig = CompanderD.ar(
            finalSig,
            thresh: 0.5,
            ratio: 2,
            attack: 0.1,
            release: 0.3
        );

        // END USER EFFECT CODE

        // MACHINERY FOR SAMPLING THE SIGNAL
        var phase = Phasor.ar(0, 1, 0, ~chunkSize);
        var trig = HPZ1.ar(phase) < 0;
        var partition = PulseCount.ar(trig) % ~numChunks;
        var kr_impulse = Impulse.kr(30);

        // Add FFT Analysis
        var chain_out = FFT(~fft_buffer_out, finalSig, wintype: 1);
        chain_out.do(~fft_buffer_out);

        // Add RMS calculations
        var rms_input = RunningSum.rms(sig, 1024);
        var rms_output = RunningSum.rms(finalSig, 1024);

        // Add RMS outputs
        Out.kr(~rms_bus_input, rms_input);
        Out.kr(~rms_bus_output, rms_output);

        // Add SendReply messages
        SendReply.kr(kr_impulse, '/fft_data');
        SendReply.kr(kr_impulse, '/rms');

        // Write to buffers for waveform data
        BufWr.ar(sig, ~relay_buffer_in.bufnum, phase + (~chunkSize * partition));
        BufWr.ar(finalSig, ~relay_buffer_out.bufnum, phase + (~chunkSize * partition));

        SendReply.kr(kr_impulse, '/buffer_refresh', partition);

        Out.ar(out, [finalSig, finalSig]);
    }).add;
    
    "Porch Vibes SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(\porch_vibes, [
            \in_bus, ~input_bus,
            \warmth, 0.7,
            \smoke, 0.5,
            \rock_speed, 0.3,
            \sip_amount, 0.4
        ], ~effectGroup);
        "New porch_vibes effect synth created".postln;
    };
) 