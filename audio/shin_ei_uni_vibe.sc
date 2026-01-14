// shader: oscilloscope
// category: Modulation
// description: Shin-Ei Uni-Vibe asymmetrical swirl with chorus/vibrato throb
(
    var defName = \shin_ei_uni_vibe;
    var specs = (
        speed: ControlSpec(0.05, 8.0, 'exp', 0, 1.2, "Hz"),
        intensity: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        chorus_vibrato: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, ""),
        lamp_wobble: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        asymmetry: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        throb: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var speed = \speed.kr(specs[\speed].default);
        var intensity = \intensity.kr(specs[\intensity].default);
        var chorus_vibrato = \chorus_vibrato.kr(specs[\chorus_vibrato].default);
        var lamp_wobble = \lamp_wobble.kr(specs[\lamp_wobble].default);
        var asymmetry = \asymmetry.kr(specs[\asymmetry].default);
        var throb = \throb.kr(specs[\throb].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, lfo, skew, wobble, modPhase, modPos, baseDelay, depth;
        var d1, d2, d3, d4, wet, ampMod, modeSignal, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        skew = LFSaw.kr(speed, 1);
        lfo = (SinOsc.kr(speed) * (1 - asymmetry)) + (skew * asymmetry);
        wobble = LFNoise1.kr(speed * 2).range(-1, 1) * lamp_wobble;
        modPhase = (lfo + wobble).tanh;
        modPos = modPhase.linlin(-1, 1, 0, 1);

        baseDelay = 0.0008;
        depth = intensity.linlin(0, 1, 0.0001, 0.0035);

        d1 = AllpassC.ar(sig, 0.02, baseDelay + (modPos * depth), 0.6);
        d2 = AllpassC.ar(d1, 0.02, baseDelay + (modPos * (depth * 1.1)), 0.6);
        d3 = AllpassC.ar(d2, 0.02, baseDelay + (modPos * (depth * 0.9)), 0.6);
        d4 = AllpassC.ar(d3, 0.02, baseDelay + (modPos * (depth * 1.2)), 0.6);

        ampMod = 1 + (SinOsc.kr(speed).range(-1, 1) * throb * 0.35);
        wet = d4 * ampMod;

        modeSignal = wet + (dry * (1 - chorus_vibrato));
        wet = XFade2.ar(dry, modeSignal, mix * 2 - 1);

        mono_for_analysis = wet;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [wet, wet]);
    });
    def.add;
    "Effect SynthDef 'shin_ei_uni_vibe' added".postln;

    ~setupEffect.value(defName, specs);
)
