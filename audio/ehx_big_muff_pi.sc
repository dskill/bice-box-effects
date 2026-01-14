// shader: oscilloscope
// category: Distortion
// description: EHX Big Muff Pi thick, creamy fuzz with violin-like sustain
(
    var defName = \ehx_big_muff_pi;
    var specs = (
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        volume: ControlSpec(0.0, 2.0, 'lin', 0, 1.0, "x"),
        mids: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        fuzz_texture: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var sustain = \sustain.kr(specs[\sustain].default);
        var tone = \tone.kr(specs[\tone].default);
        var volume = \volume.kr(specs[\volume].default);
        var mids = \mids.kr(specs[\mids].default);
        var fuzz_texture = \fuzz_texture.kr(specs[\fuzz_texture].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pre, drive, stage1, stage2, hard, clipped;
        var toneLow, toneHigh, toneBlend, midDb, eq, processed, mono_for_analysis;

        sig = In.ar(in_bus, 1);
        dry = sig;
        pre = HPF.ar(sig, 20);

        drive = sustain.linexp(0, 1, 1, 25);
        stage1 = (pre * (drive * 2)).tanh;
        stage2 = (stage1 * (drive * 0.7 + 1)).softclip;
        hard = (stage1 * (drive * 1.2 + 1)).clip2(0.8);
        clipped = XFade2.ar(stage2, hard, (fuzz_texture * 2) - 1);

        toneLow = LPF.ar(clipped, 700);
        toneHigh = HPF.ar(clipped, 1200);
        toneBlend = (toneLow * (1 - tone)) + (toneHigh * tone);

        midDb = mids.linlin(0, 1, -8, 8);
        eq = BPeakEQ.ar(toneBlend, 1000, 0.6, midDb);
        processed = LeakDC.ar(eq * volume);

        processed = XFade2.ar(dry, processed, (mix * 2) - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'ehx_big_muff_pi' added".postln;

    ~setupEffect.value(defName, specs);
)
