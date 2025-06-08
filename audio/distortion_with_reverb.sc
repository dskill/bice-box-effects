// shader: skull

(
    var defName = \distortion_with_reverb;
    var specs = (
        drive: ControlSpec(0.1, 10.0, 'exp', 0, 0.5, "x"),
        tone: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        decay: ControlSpec(0.0, 2.0, 'lin', 0, 1.0, "s"),
        roomSize: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        wetLevelDist: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        wetLevelRev: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var drive = \drive.kr(specs[\drive].default);
        var tone = \tone.kr(specs[\tone].default);
        var decay = \decay.kr(specs[\decay].default);
        var roomSize = \roomSize.kr(specs[\roomSize].default);
        var wetLevelDist = \wetLevelDist.kr(specs[\wetLevelDist].default);
        var wetLevelRev = \wetLevelRev.kr(specs[\wetLevelRev].default);
        
        var sig, distorted, verb, dryDist, dryRev, mono_for_analysis;
        
        sig = In.ar(in_bus);
        
        // Distortion effect
        distorted = (sig * drive).tanh();
        distorted = LPF.ar(distorted, tone.linexp(0, 1, 100, 20000));
        dryDist = sig * (1 - wetLevelDist);
        sig = dryDist + (distorted * wetLevelDist);
        
        // Reverb effect
        verb = FreeVerb.ar(sig, mul: decay, room: roomSize);
        dryRev = sig * (1 - wetLevelRev);
        sig = dryRev + (verb * wetLevelRev);
        
        // Prepare mono signal for analysis
        mono_for_analysis = sig;

        Out.ar(out, [sig,sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'distortion_with_reverb' added".postln;

    // Register specs and create the synth
    ~setupEffect.value(defName, specs);
)