(
    SynthDef(\palpatine, {
        |out = 0, in_bus = 0, analysis_out_bus, drive = 50, tone = 2000, mix = 1.0, 
        reverb = 0.3, delay = 0.25, delay_mix = 0.2, feedback = 0.5|
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
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\palpatine, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \drive, 50,
            \tone, 2000,
            \mix, 1.0,
            \reverb, 0.3,
            \delay, 0.25,
            \delay_mix, 0.2,
            \feedback, 0.5
        ], ~effectGroup);
        ("New palpatine synth created with analysis output bus").postln;
    };
) 