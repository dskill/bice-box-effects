// shader: palpatine
// category: Experimental
(
    var defName = \palpatine;
    var specs = (
        drive: ControlSpec(1.0, 100.0, 'exp', 0, 50, "x"),
        tone: ControlSpec(100, 8000, 'exp', 0, 2000, "Hz"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%"),
        reverb: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        delay: ControlSpec(0.01, 1.0, 'exp', 0, 0.25, "s"),
        delay_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, "%"),
        feedback: ControlSpec(0.0, 0.95, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(specs[\drive].default);
        var tone = \tone.kr(specs[\tone].default);
        var mix = \mix.kr(specs[\mix].default);
        var reverb = \reverb.kr(specs[\reverb].default);
        var delay = \delay.kr(specs[\delay].default);
        var delay_mix = \delay_mix.kr(specs[\delay_mix].default);
        var feedback = \feedback.kr(specs[\feedback].default);
        
        var sig, distorted, filtered, wet, delayed, mono_for_analysis;
        var mod_freq = LFNoise1.kr(0.5).range(4, 8);
        var mod = SinOsc.ar(mod_freq) * 0.3;

        sig = In.ar(in_bus);
         
        distorted = sig * drive;
        distorted = distorted.tanh;
        distorted = distorted * (1 + mod);
        distorted = (distorted * 2).tanh * 0.5;
        distorted = (distorted * 3).clip2(0.7);
        
        filtered = RLPF.ar(distorted, tone, 0.7);
        
        delayed = LocalIn.ar(1) * feedback;
        delayed = delayed + filtered;
        delayed = DelayL.ar(delayed, 1.0, 
            (LFNoise2.kr(10.2)*0.001 + delay)
        );
        LocalOut.ar(delayed);
        
        filtered = (filtered * (1 - delay_mix)) + (delayed * delay_mix);
        
        wet = DelayN.ar(filtered, 0.03, 0.03);
        wet = FreeVerb.ar(wet, reverb, 0.8, 0.2);

        distorted = (wet * mix) + (sig * (1 - mix));

        distorted = LeakDC.ar(distorted);

        mono_for_analysis = distorted;
        
        Out.ar(out, [distorted, distorted]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'palpatine' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
) 