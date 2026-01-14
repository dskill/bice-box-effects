// shader: oscilloscope
// category: Modulation
// description: Boss CE-2 Chorus warm BBD swirl with smooth rate/depth shimmer
(
    var defName = \boss_ce2_chorus;
    var specs = (
        rate: ControlSpec(0.05, 8.0, 'exp', 0, 0.8, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.55, "x"),
        tone: ControlSpec(400, 8000, 'exp', 0, 2500, "Hz"),
        pre_delay: ControlSpec(0.001, 0.02, 'exp', 0, 0.006, "s"),
        wow_flutter: ControlSpec(0.0, 1.0, 'lin', 0, 0.25, "x"),
        noise: ControlSpec(0.0, 1.0, 'lin', 0, 0.08, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var tone = \tone.kr(specs[\tone].default);
        var pre_delay = \pre_delay.kr(specs[\pre_delay].default);
        var wow_flutter = \wow_flutter.kr(specs[\wow_flutter].default);
        var noise = \noise.kr(specs[\noise].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, lfo, flutter, mod, delayTime, chorus, noiseSig, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;

        lfo = SinOsc.kr(rate).range(-1, 1);
        flutter = LFNoise1.kr((rate * 4).clip(0.1, 12)).range(-1, 1) * wow_flutter;
        mod = (lfo + flutter) * depth;

        delayTime = (pre_delay + 0.005) + (mod * 0.01);
        delayTime = delayTime.clip(0.001, 0.03);

        chorus = DelayC.ar(sig, 0.05, delayTime);
        chorus = LPF.ar(chorus, tone);
        chorus = (chorus * 1.2).tanh;

        noiseSig = WhiteNoise.ar * noise * 0.02;
        processed = chorus + noiseSig;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'boss_ce2_chorus' added".postln;

    ~setupEffect.value(defName, specs);
)
