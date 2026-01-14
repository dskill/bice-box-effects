// shader: oscilloscope
// category: Distortion
// description: Boss DS-1 Distortion emulation with tight hard-edge bite and tone filter
(
    var defName = \boss_ds1;
    var specs = (
        distortion: ControlSpec(0.2, 20.0, 'exp', 0, 4.0, "x"),
        tone: ControlSpec(200, 6000, 'exp', 0, 2500, "Hz"),
        level: ControlSpec(0.1, 3.0, 'exp', 0, 1.0, "x"),
        bite: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        low_end: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        clipping: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        noise_gate: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var distortion = \distortion.kr(specs[\distortion].default);
        var tone = \tone.kr(specs[\tone].default);
        var level = \level.kr(specs[\level].default);
        var bite = \bite.kr(specs[\bite].default);
        var low_end = \low_end.kr(specs[\low_end].default);
        var clipping = \clipping.kr(specs[\clipping].default);
        var noise_gate = \noise_gate.kr(specs[\noise_gate].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, pre, bite_db, drive_sig, soft_clip, hard_clip, clipped, filtered;
        var gate_amt, amp, thresh, gate, processed, mono_for_analysis;

        sig = In.ar(in_bus, 1);
        dry = sig;

        pre = HPF.ar(sig, low_end.linexp(0.0, 1.0, 30, 200));
        bite_db = bite.linlin(0.0, 1.0, 0.0, 6.0);
        pre = BHiShelf.ar(pre, 2000, 1.0, bite_db);

        drive_sig = pre * distortion;
        soft_clip = drive_sig.tanh;
        hard_clip = drive_sig.clip2(clipping.linlin(0.0, 1.0, 0.4, 0.9));
        clipped = XFade2.ar(soft_clip, hard_clip, (clipping * 2) - 1);

        filtered = RLPF.ar(clipped, tone, 0.6);
        processed = filtered * level;

        amp = Amplitude.kr(processed, 0.01, 0.1);
        thresh = noise_gate.linexp(0.0, 1.0, 0.00001, 0.02);
        gate = (amp > thresh).lag(0.03);
        gate_amt = noise_gate;
        processed = processed * (1 - gate_amt + (gate_amt * gate));

        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'boss_ds1' added".postln;

    ~setupEffect.value(defName, specs);
)
