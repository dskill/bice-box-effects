// shader: outrun
(
    var defName = \mbv;
    var specs = (
        gain: ControlSpec(0.0, 2.0, 'exp', 0, 0.5, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        res: ControlSpec(0.1, 4.0, 'exp', 0, 1.37, "x"),
        level: ControlSpec(0.0, 2.0, 'lin', 0, 0.75, "x"),
        reverse_verb: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        pitch_rate: ControlSpec(0.01, 1.0, 'exp', 0, 0.1, "Hz"),
        pitch_depth: ControlSpec(0.0, 0.1, 'lin', 0, 0.01, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var gain = \gain.kr(specs[\gain].default);
        var tone = \tone.kr(specs[\tone].default);
        var res = \res.kr(specs[\res].default);
        var level = \level.kr(specs[\level].default);
        var reverse_verb = \reverse_verb.kr(specs[\reverse_verb].default);
        var mix = \mix.kr(specs[\mix].default);
        var pitch_rate = \pitch_rate.kr(specs[\pitch_rate].default);
        var pitch_depth = \pitch_depth.kr(specs[\pitch_depth].default);
        
        var sig, processed, mono_for_analysis;
        var reverb_buffer, reverse_sig, reverse_verb_sig;
        var pitch_mod;
        var dry_processed;

        sig = In.ar(in_bus); // Sums stereo to mono
        
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
        // LinXFade2 will correctly fade between the mono dry_processed and stereo reverse_verb_sig
        processed = LinXFade2.ar(dry_processed, reverse_verb_sig, reverse_verb);

        // Level control and final shaping
        processed = processed * level * 0.8;
        processed = LeakDC.ar(processed);

        // Final mix between original input and the processed signal
        // XFade2 will correctly fade between the mono sig and stereo processed signal
        processed = XFade2.ar(sig, processed, mix*2.0-1.0);

        // Prepare mono signal for analysis
        mono_for_analysis = Mix.ar(processed);

        Out.ar(out, processed); // 'processed' is now stereo
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'mbv' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
) 