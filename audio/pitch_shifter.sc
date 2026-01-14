// shader: oscilloscope
// category: Pitch
// description: Pitch shifter with semitone control and mix
(
    var defName = \pitch_shifter;
    var specs = (
        pitchShift: ControlSpec(0.25, 4.0, 'exp', 0, 1.0, "x"),
        wetLevel: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var pitchShift = \pitchShift.kr(specs[\pitchShift].default);
        var wetLevel = \wetLevel.kr(specs[\wetLevel].default);
        
        var sig, shifted, dry, finalSig, mono_for_analysis;
        
        sig = In.ar(in_bus); // Sums stereo to mono
        
        shifted = PitchShift.ar(sig, pitchRatio: pitchShift);
        
        dry = sig * (1 - wetLevel);
        finalSig = dry + (shifted * wetLevel);
        
        // Signal is already mono
        mono_for_analysis = finalSig;

        Out.ar(out, [finalSig, finalSig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'pitch_shifter' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)
