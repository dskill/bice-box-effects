(
    SynthDef(\bit_crusher, {
        |out = 0, in_bus = 0, analysis_out_bus, bits = 8, rate = 0.5, mix = 1.0, 
        machine = 2000, reverb = 0.3, delay = 0.25, delay_mix = 0.2|
        var sig, crushed, filtered, wet, delayed, mono_for_analysis;

        sig = In.ar(in_bus);
         
        // Bitcrusher effect
        crushed = sig;
        crushed = crushed.round(2.pow(bits).reciprocal);
        crushed = Latch.ar(crushed, Impulse.ar(rate * SampleRate.ir * 0.5));

        // Low pass filter
        filtered = LPF.ar(crushed, machine);
        
        // Delay
        delayed = DelayL.ar(filtered, 1.0, delay);
        filtered = (filtered * (1 - delay_mix)) + (delayed * delay_mix);
        
        // Reverb
        wet = FreeVerb.ar(filtered, reverb, 0.8, 0.5);

        // Blend original and processed signals
        crushed = (wet * mix) + (sig * (1 - mix));

        // Remove DC offset
        crushed = LeakDC.ar(crushed);
        
        // Prepare mono signal for analysis
        if (crushed.isArray) {
            mono_for_analysis = Mix.ar(crushed);
        } {
            mono_for_analysis = crushed;
        };

        Out.ar(out, [crushed, crushed]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    }).add;

    "Effect SynthDef added".postln;

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(\bit_crusher, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \bits, 8, 
            \rate, 0.5, 
            \mix, 1.0, 
            \machine, 2000, 
            \reverb, 0.3, 
            \delay, 0.25, 
            \delay_mix, 0.2
        ], ~effectGroup);
        ("New bit_crusher synth created with analysis output bus").postln;
    };
)
