// shader: oscilloscope
(
    var defName = \black_atlass_voice;
    var specs = (
        vibrato_rate: ControlSpec(3.0, 8.0, 'lin', 0, 5.5, "Hz"),
        vibrato_depth: ControlSpec(0.0, 0.8, 'lin', 0, 0.4, ""),
        warble_rate: ControlSpec(0.1, 2.0, 'exp', 0, 0.6, "Hz"),
        warble_depth: ControlSpec(0.0, 0.5, 'lin', 0, 0.3, ""),
        chorus_rate: ControlSpec(0.2, 4.0, 'exp', 0, 1.2, "Hz"),
        chorus_depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, ""),
        breathiness: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, ""),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var vibrato_rate = \vibrato_rate.kr(specs[\vibrato_rate].default);
        var vibrato_depth = \vibrato_depth.kr(specs[\vibrato_depth].default);
        var warble_rate = \warble_rate.kr(specs[\warble_rate].default);
        var warble_depth = \warble_depth.kr(specs[\warble_depth].default);
        var chorus_rate = \chorus_rate.kr(specs[\chorus_rate].default);
        var chorus_depth = \chorus_depth.kr(specs[\chorus_depth].default);
        var breathiness = \breathiness.kr(specs[\breathiness].default);
        var mix = \mix.kr(specs[\mix].default);
        
        var sig, dry, vibrato_delay, warble_delay, chorused, breathy, processed, mono_for_analysis;

        sig = In.ar(in_bus);
        dry = sig;
        
        // Vibrato using modulated delay
        vibrato_delay = DelayC.ar(sig, 0.02, 0.005 + (SinOsc.kr(vibrato_rate) * vibrato_depth * 0.003));
        
        // Warble - slower, irregular modulation 
        warble_delay = DelayC.ar(vibrato_delay, 0.03, 0.008 + (SinOsc.kr(warble_rate, SinOsc.kr(warble_rate * 0.3) * 0.5) * warble_depth * 0.005));
        
        // Chorus for that rich, layered vocal sound
        chorused = warble_delay + DelayC.ar(warble_delay, 0.04, 0.015 + (SinOsc.kr(chorus_rate) * chorus_depth * 0.008)) * 0.6;
        
        // Breathiness - add filtered noise
        breathy = chorused + (LPF.ar(PinkNoise.ar(breathiness * 0.03), 2000) * EnvFollow.ar(chorused, 0.01, 0.1));
        
        processed = XFade2.ar(dry, breathy, mix * 2 - 1);
        
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'black_atlass_voice' added".postln;

    ~setupEffect.value(defName, specs);
)