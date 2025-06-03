(
    var defName = \phaser_2d;
    var def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var x = \x.kr(0.5);
        var y = \y.kr(0.5);
        var mix = \mix.kr(1.0);
        
        var sig, dry, processed, mono_for_analysis;
        var numStages, freq, feedback;

        sig = In.ar(in_bus);
        dry = sig;

        // Map x to number of stages (2 to 12)
        numStages = x.linlin(0, 1, 2, 12).round;

        // Map y to LFO frequency (0.1 Hz to 5 Hz)
        freq = y.linexp(0, 1, 0.1, 5);

        // Create the phaser effect
        processed = sig; // Start with dry signal
        numStages.do({ arg i; // Do this 'numStages' times
            processed = AllpassN.ar(
                in: processed,
                delaytime: LFNoise1.kr(freq / (i + 1), 0.01, 0.02), // Varying delay times for each stage
                decaytime: 0.1, // Constant decay time
                mul: 0.99 // Prevent runaway feedback
            );
        });

        // Mix the dry and processed signals
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        // Prepare mono signal for analysis
        if (processed.isArray) {
            mono_for_analysis = Mix.ar(processed);
        } {
            mono_for_analysis = processed;
        };

        Out.ar(out, [processed, processed]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'phaser_2d' added".postln;

    // Register parameter specifications using the helper function
    ~registerEffectSpecs.value(defName, (
        x: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "stages"),
        y: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "freq"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 1.0, "%")
    ));

    fork {
        s.sync;

        if(~effect.notNil, { ~effect.free; });

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;

        // OSC responder for phaser parameters
        OSCdef.new(
            \phaserParamsListener, // A unique key for this OSCdef
            { |msg, time, addr, recvPort|
                var x_val = msg[1];
                var y_val = msg[2];
                // ("Received /phaser/params: X=" ++ x_val ++ ", Y=" ++ y_val).postln; // For debugging
                if(~effect.notNil, {
                    ~effect.set(\x, x_val, \y, y_val);
                });
            },
            '/params', // The OSC address to listen to
            nil // Listen on any client address
        );
    };
)