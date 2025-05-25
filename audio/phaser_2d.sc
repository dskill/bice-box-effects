(
    SynthDef(\phaser_2d, {
        |out = 0, in_bus = 0, analysis_out_bus, x = 0.5, y = 0.5, mix = 1.0|
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
        1.do({ arg i; // Do this 'numStages' times
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
    }).add;
    "Phaser 2D SynthDef added".postln;

    fork {
        s.sync;

        if(~effect.notNil, { ~effect.free; });

        ~effect = Synth(\phaser_2d, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis,
            \x, 0.5,
            \y, 0.5,
            \mix, 1.0
        ], ~effectGroup);
        ("New phaser_2d synth created with analysis output bus").postln;

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