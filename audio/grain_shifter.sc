// shader: oscilloscope
// category: Pitch
// description: Granular pitch shifter with textural glitch
(
    var defName = \grain_shifter;
    var specs = (
        pitch: ControlSpec(0.25, 4.0, 'exp', 0, 1.0, "x"),
        grain_size: ControlSpec(0.01, 0.2, 'exp', 0, 0.05, "s"),
        density: ControlSpec(1, 100, 'exp', 0, 20, "grains/s"),
        randomness: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var pitch = \pitch.kr(specs[\pitch].default);
        var grain_size = \grain_size.kr(specs[\grain_size].default);
        var density = \density.kr(specs[\density].default);
        var randomness = \randomness.kr(specs[\randomness].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here!
        var sig, dry, processed, mono_for_analysis;
        var buf_length, buffer, write_pos, read_pos;
        var trigger, grain_dur, pos_rand, pitch_rand;
        var grains;

        // Processing
        sig = In.ar(in_bus);
        dry = sig;

        // Create a buffer to hold recent audio (1 second)
        buf_length = 1.0;
        buffer = LocalBuf(SampleRate.ir * buf_length);
        
        // Write input to circular buffer
        write_pos = Phasor.ar(0, 1, 0, BufFrames.kr(buffer));
        BufWr.ar(sig, buffer, write_pos);

        // Grain trigger
        trigger = Impulse.ar(density);
        grain_dur = grain_size;
        
        // Randomize read position and pitch
        pos_rand = TRand.ar(0, randomness * 0.1, trigger);
        pitch_rand = TRand.ar(1 - (randomness * 0.1), 1 + (randomness * 0.1), trigger);
        
        // Read position slightly behind write position with randomness
        read_pos = (write_pos - (SampleRate.ir * 0.1) + (pos_rand * SampleRate.ir)).wrap(0, BufFrames.kr(buffer));

        // Granular synthesis with pitch shifting
        grains = GrainBuf.ar(
            2,                      // num channels
            trigger,                // trigger
            grain_dur,              // grain duration
            buffer,                 // buffer
            pitch * pitch_rand,     // pitch ratio with randomness
            read_pos / BufFrames.kr(buffer),  // position (0-1)
            2,                      // interpolation
            0,                      // pan
            -1                      // envelope (-1 = default Hann)
        );

        // Reduce volume to prevent clipping with multiple grains
        processed = grains.sum * 0.5;
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        // Outputs
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'grain_shifter' added".postln;

    ~setupEffect.value(defName, specs);
)