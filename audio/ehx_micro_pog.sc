// shader: oscilloscope
// category: Pitch
// description: Electro-Harmonix Micro POG fast poly octave up/down with organ blend
(
    var defName = \ehx_micro_pog;
    var specs = (
        dry: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, ""),
        octave_up: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        octave_down: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        tone: ControlSpec(200, 8000, 'exp', 0, 5000, "Hz"),
        attack: ControlSpec(0.001, 0.2, 'exp', 0, 0.01, "s"),
        organ_blend: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        sub_filter: ControlSpec(40, 200, 'exp', 0, 60, "Hz"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var dry = \dry.kr(specs[\dry].default);
        var octave_up = \octave_up.kr(specs[\octave_up].default);
        var octave_down = \octave_down.kr(specs[\octave_down].default);
        var tone = \tone.kr(specs[\tone].default);
        var attack = \attack.kr(specs[\attack].default);
        var organ_blend = \organ_blend.kr(specs[\organ_blend].default);
        var sub_filter = \sub_filter.kr(specs[\sub_filter].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry_sig, oct_up, oct_down, oct_up_det, oct_down_det;
        var organ, wet, internal, processed, mono_for_analysis;

        sig = In.ar(in_bus, 1);
        dry_sig = sig;

        oct_up = PitchShift.ar(sig, 0.03, 2.0, 0.0, 0.004) * octave_up;
        oct_down = PitchShift.ar(sig, 0.05, 0.5, 0.0, 0.004) * octave_down;
        oct_up_det = PitchShift.ar(sig, 0.03, 2.01, 0.0, 0.004);
        oct_down_det = PitchShift.ar(sig, 0.05, 0.503, 0.0, 0.004);
        organ = (oct_up_det + oct_down_det) * 0.5 * organ_blend;

        wet = oct_up + oct_down + organ;
        wet = HPF.ar(wet, sub_filter);
        wet = LPF.ar(wet, tone);
        wet = Lag.ar(wet, attack);

        internal = (dry_sig * dry) + wet;
        processed = XFade2.ar(dry_sig, internal, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'ehx_micro_pog' added".postln;

    ~setupEffect.value(defName, specs);
)
