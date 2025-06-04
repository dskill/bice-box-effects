// shader: moebius

(
    var defName = \bands;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(10);
        var tone = \tone.kr(0.5);
        var mix = \mix.kr(1.0);
        
        var sig, distorted, mono_for_analysis;

        sig = In.ar(in_bus);
        
        // Apply soft clipping and add some even harmonics
        // Adjust drive scaling if needed for more subtle or aggressive distortion
        distorted = (sig * drive).clip2(0.8);  
        distorted = (distorted * 0.8) + (distorted.squared * 0.2);

        // Add tone shaping: low shelf, mid peak, and high shelf adjustments
        // These ranges and amounts can be tweaked to taste.
        distorted = BLowShelf.ar(distorted, 400, 1.0, tone * -24);   // Bass attenuation/boost
        distorted = BPeakEQ.ar(distorted, 1200, 0.5, tone * 12);     // Mid presence
        distorted = BHiShelf.ar(distorted, 3200, 1.0, tone * -6);    // High attenuation/boost

        // Blend original and distorted signals
        distorted = (distorted * mix) + (sig * (1 - mix));

        // Remove DC offset that might be introduced
        distorted = LeakDC.ar(distorted);

        // Prepare mono signal for analysis
        mono_for_analysis = distorted;

        Out.ar(out, [distorted, distorted]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'bands' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        drive: ControlSpec(1.0, 50.0, 'exp', 0, 10, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    ));

    fork {
        s.sync;

        // Free existing synth if it exists
        if(~effect.notNil) {
            "Freeing existing effect synth".postln;
            ~effect.free;
        };

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
) 