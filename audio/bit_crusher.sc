// shader: skull

(
    var defName = \bit_crusher;
    var specs = (
        bits: ControlSpec(1, 16, 'lin', 1, 8, "bits"),
        rate: ControlSpec(0.01, 1.0, 'exp', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%"),
        machine: ControlSpec(100, 20000, 'exp', 0, 2000, "Hz"),
        reverb: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        delay: ControlSpec(0.0, 1.0, 'lin', 0, 0.25, "s"),
        delay_mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var bits = \bits.kr(specs[\bits].default);
        var rate = \rate.kr(specs[\rate].default);
        var mix = \mix.kr(specs[\mix].default);
        var machine = \machine.kr(specs[\machine].default);
        var reverb = \reverb.kr(specs[\reverb].default);
        var delay = \delay.kr(specs[\delay].default);
        var delay_mix = \delay_mix.kr(specs[\delay_mix].default);
        
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
    });
    def.add;
    "Effect SynthDef 'bit_crusher' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
