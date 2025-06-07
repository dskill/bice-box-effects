// shader: outrun
(
    var defName = \mbv;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var gain = \gain.kr(0.5);
        var tone = \tone.kr(0.5);
        var res = \res.kr(1.37);
        var level = \level.kr(0.75);
        var reverse_verb = \reverse_verb.kr(0.0);
        var mix = \mix.kr(0.5);
        var pitch_rate = \pitch_rate.kr(0.1);
        var pitch_depth = \pitch_depth.kr(0.01);
        
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
        mono_for_analysis = Mix.ar(processed);

        Out.ar(out, [processed,processed]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'mbv' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        gain: ControlSpec(0.0, 2.0, 'exp', 0, 0.5, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        res: ControlSpec(0.1, 4.0, 'exp', 0, 1.37, "x"),
        level: ControlSpec(0.0, 2.0, 'lin', 0, 0.75, "x"),
        reverse_verb: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        pitch_rate: ControlSpec(0.01, 1.0, 'exp', 0, 0.1, "Hz"),
        pitch_depth: ControlSpec(0.0, 0.1, 'lin', 0, 0.01, "%")
    ));

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
) 