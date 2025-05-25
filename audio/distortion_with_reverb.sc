(
    SynthDef(\distortion_with_reverb, {
        |out = 0, in_bus = 0, analysis_out_bus, drive = 0.5, tone = 0.5, decay = 1, roomSize = 0.7, wetLevelDist = 0.5, wetLevelRev = 0.5|
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
    }).add;
    "Effect SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(\distortion_with_reverb, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \drive, 0.5,
            \tone, 0.5,
            \decay, 1,
            \roomSize, 0.7,
            \wetLevelDist, 0.5,
            \wetLevelRev, 0.5
        ], ~effectGroup);
        ("New distortion_with_reverb synth created with analysis output bus").postln;
    };
)