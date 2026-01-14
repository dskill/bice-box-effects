// shader: oscilloscope
// category: Modulation
(
    var defName = \tape_warble;
    var specs = (
        warble_rate: ControlSpec(0.1, 8.0, 'exp', 0, 1.5, "Hz"),
        warble_depth: ControlSpec(0.0, 0.05, 'lin', 0, 0.01, "semi"),
        flutter_rate: ControlSpec(5.0, 30.0, 'exp', 0, 12.0, "Hz"),
        flutter_depth: ControlSpec(0.0, 0.02, 'lin', 0, 0.003, "semi"),
        noise_amount: ControlSpec(0.0, 0.3, 'lin', 0, 0.05, "%"),
        filter_amount: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var warble_rate = \warble_rate.kr(specs[\warble_rate].default);
        var warble_depth = \warble_depth.kr(specs[\warble_depth].default);
        var flutter_rate = \flutter_rate.kr(specs[\flutter_rate].default);
        var flutter_depth = \flutter_depth.kr(specs[\flutter_depth].default);
        var noise_amount = \noise_amount.kr(specs[\noise_amount].default);
        var filter_amount = \filter_amount.kr(specs[\filter_amount].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here
        var sig, dry, warble_lfo, flutter_lfo, total_pitch_shift, pitch_ratio;
        var delayed_sig, tape_noise, filtered, processed, mono_for_analysis;

        // Processing
        sig = In.ar(in_bus);
        dry = sig;

        // Slow warble (tape speed variation)
        warble_lfo = SinOsc.kr(warble_rate);
        
        // Fast flutter (mechanical imperfections)
        flutter_lfo = LFNoise1.kr(flutter_rate);
        
        // Combine pitch modulations (in semitones)
        total_pitch_shift = (warble_lfo * warble_depth) + (flutter_lfo * flutter_depth);
        pitch_ratio = total_pitch_shift.midiratio;
        
        // Pitch shift using delay-based modulation
        delayed_sig = PitchShift.ar(sig, 0.2, pitch_ratio, 0.01, 0.01);
        
        // Add tape noise
        tape_noise = LPF.ar(PinkNoise.ar(noise_amount), 4000);
        delayed_sig = delayed_sig + tape_noise;
        
        // Age the sound with filtering
        filtered = LPF.ar(delayed_sig, 8000 - (filter_amount * 4000));
        filtered = HPF.ar(filtered, 20 + (filter_amount * 80));
        
        // Mix dry/wet
        processed = XFade2.ar(dry, filtered, mix * 2 - 1);

        // Outputs
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'tape_warble' added".postln;

    ~setupEffect.value(defName, specs);
)