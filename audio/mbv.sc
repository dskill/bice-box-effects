(
    SynthDef(\mbv, {
        |out = 0, in_bus = 0, analysis_out_bus,
        gain = 0.5, tone = 0.5, res = 1.37, level = 0.75, reverse_verb = 0.0, mix = 0.5,
        pitch_rate = 0.1, pitch_depth = 0.01|
        
        var sig, processed, mono_for_analysis;
        var reverb_buffer, reverse_sig, reverse_verb_sig;
        var pitch_mod;
        var dry_processed;

        sig = In.ar(in_bus);
        
        // Add slow pitch oscillation
        pitch_mod = SinOsc.kr(pitch_rate).range(1 - pitch_depth, 1 + pitch_depth);
        sig = PitchShift.ar(sig, 0.2, pitch_mod);
        
        // Pre-emphasis filter to boost mids before distortion
        sig = BPF.ar(sig, 800, 2.0, 2.0) + sig;
        
        // Gain stage with asymmetrical soft clipping
        processed = sig * (gain * 400 + 1);
        processed = Select.ar(processed > 0, [
            processed * 0.8,  
            processed       
        ]);
        processed = processed.softclip;
        
        // Tone control using MoogFF
        processed = MoogFF.ar(
            in: processed,
            freq: 100 + (tone * 8000),
            gain: res
        );

        // Additional filtering for character
        processed = BPeakEQ.ar(processed, 1200, 0.5, 3);
        processed = BHiShelf.ar(processed, 3000, 1.0, 2);

        // Store the dry processed signal before reverse reverb
        dry_processed = processed;

        // Create a local buffer for reverse reverb
        reverb_buffer = LocalBuf(SampleRate.ir * 0.75);
        RecordBuf.ar(dry_processed, reverb_buffer, loop: 1);
        reverse_sig = PlayBuf.ar(1, reverb_buffer, rate: -1, loop: 1, startPos: BufFrames.kr(reverb_buffer));
        reverse_verb_sig = FreeVerb.ar(reverse_sig, mix: 1, room: 1.25, damp: 0.5);

        // Mix dry and reverse signals based on reverse_verb parameter
        processed = LinXFade2.ar(dry_processed, reverse_verb_sig, reverse_verb);

        // Level control and final shaping
        processed = processed * level * 0.8;
        processed = LeakDC.ar(processed);

        // Final mix between original input (post-pitch shift and pre-emphasis) and the processed signal
        processed = XFade2.ar(sig, processed, mix*2.0-1.0);

        // Prepare mono signal for analysis
        mono_for_analysis = processed;

        Out.ar(out, [processed, processed]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(\mbv, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \gain, 0.5,
            \tone, 0.5,
            \res, 1.37,
            \level, 0.75,
            \reverse_verb, 0.0,
            \mix, 0.5,
            \pitch_rate, 0.1,
            \pitch_depth, 0.01
        ], ~effectGroup);
        ("New mbv synth created with analysis output bus").postln;
    };
) 