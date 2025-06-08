// shader: skull

(
    var defName = \overdrive;
    var specs = (
        drive: ControlSpec(1.0, 50.0, 'exp', 0, 10, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(specs[\drive].default);
        var tone = \tone.kr(specs[\tone].default);
        var mix = \mix.kr(specs[\mix].default);
        
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
        // Assuming 'distorted' is mono here
        mono_for_analysis = distorted;

        Out.ar(out, [distorted, distorted]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'overdrive' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
