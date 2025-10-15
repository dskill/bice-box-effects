// shader: oscilloscope
(
    var defName = \snow_fall;
    var specs = (
        flake_density: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, ""),
        wind_speed: ControlSpec(0.1, 8.0, 'exp', 0, 1.0, "Hz"),
        crystal_sparkle: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        size_variation: ControlSpec(0.0, 1.0, 'lin', 0, 0.6, ""),
        falling_rate: ControlSpec(0.5, 15.0, 'exp', 0, 3.0, "Hz"),
        silence_depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        ice_texture: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        atmosphere: ControlSpec(100, 1500, 'exp', 0, 400, "Hz"),
        drift: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, ""),
        volume: ControlSpec(0.0, 1.0, 'lin', 0, 0.4, "")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var flake_density = \flake_density.kr(specs[\flake_density].default);
        var wind_speed = \wind_speed.kr(specs[\wind_speed].default);
        var crystal_sparkle = \crystal_sparkle.kr(specs[\crystal_sparkle].default);
        var size_variation = \size_variation.kr(specs[\size_variation].default);
        var falling_rate = \falling_rate.kr(specs[\falling_rate].default);
        var silence_depth = \silence_depth.kr(specs[\silence_depth].default);
        var ice_texture = \ice_texture.kr(specs[\ice_texture].default);
        var atmosphere = \atmosphere.kr(specs[\atmosphere].default);
        var drift = \drift.kr(specs[\drift].default);
        var volume = \volume.kr(specs[\volume].default);

        var snow_layers, wind_whistle, sparkles, ambient_silence, processed, mono_for_analysis;

        // Multiple layers of snow particles with different sizes
        snow_layers = Mix.ar(Array.fill(6, { |i|
            var flake_size, flake_rate, flake_freq, flake_noise, flake_env;
            
            // Different sized flakes
            flake_size = (i + 1) / 6.0;
            flake_rate = falling_rate * (1.0 + (size_variation * (i * 0.3)));
            flake_freq = 1200 + (i * 300) + LFNoise1.kr(0.05).range(-50, 50);
            
            // Create flake impacts using dust and filtered noise
            flake_noise = Dust.ar(flake_rate * flake_density * (8 - i));
            flake_env = Decay.ar(flake_noise, 0.02 + (flake_size * 0.08));
            flake_noise = flake_env * BPF.ar(WhiteNoise.ar, flake_freq, 0.8);
            
            // Vary amplitude by layer
            flake_noise * (0.3 / (i + 1));
        }));

        // Wind movement - subtle whoosh
        wind_whistle = LFNoise1.ar(wind_speed * 2) * LFNoise1.kr(wind_speed).range(0, 1);
        wind_whistle = BPF.ar(wind_whistle, 200 + LFNoise1.kr(0.3).range(-50, 50), 2.0);
        wind_whistle = wind_whistle * drift * 0.15;

        // Crystalline sparkles - tiny high frequency hits
        sparkles = Mix.ar(Array.fill(3, {
            var spark_dust, spark_freq, spark_env;
            spark_dust = Dust.ar(crystal_sparkle * 5);
            spark_freq = TRand.ar(2000, 6000, spark_dust);
            spark_env = Decay.ar(spark_dust, TRand.ar(0.001, 0.01, spark_dust));
            SinOsc.ar(spark_freq) * spark_env * 0.08;
        }));

        // Ambient silence/space - very subtle background
        ambient_silence = LFNoise1.ar(0.1) * (1.0 - silence_depth) * 0.02;
        ambient_silence = LPF.ar(ambient_silence, atmosphere);

        // Add ice texture - crackling
        ice_texture = ClipNoise.ar * ice_texture * 0.1;
        ice_texture = BPF.ar(ice_texture, 800, 0.5) * LFNoise1.kr(2).range(0.5, 1.0);

        // Combine all elements
        processed = snow_layers + wind_whistle + sparkles + ambient_silence + ice_texture;

        // Gentle filtering to create soft, muffled snow sound
        processed = LPF.ar(processed, atmosphere + (silence_depth * 500));
        processed = processed * volume;
        processed = Limiter.ar(processed, 0.9);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'snow_fall' added".postln;

    ~setupEffect.value(defName, specs);
)