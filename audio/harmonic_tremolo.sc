// shader: kaleidoscope
// category: Modulation
(
    var defName = \harmonic_tremolo;
    var specs = (
        rate: ControlSpec(0.1, 20.0, 'exp', 0, 4.0, "Hz"),
        depth: ControlSpec(0.0, 1.0, 'lin', 0, 0.7, "%"),
        phase_spread: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        crossover: ControlSpec(200, 4000, 'exp', 0, 1000, "Hz"),
        resonance: ControlSpec(0.3, 0.9, 'lin', 0, 0.6, ""),
        stereo_phase: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(specs[\rate].default);
        var depth = \depth.kr(specs[\depth].default);
        var phase_spread = \phase_spread.kr(specs[\phase_spread].default);
        var crossover = \crossover.kr(specs[\crossover].default);
        var resonance = \resonance.kr(specs[\resonance].default);
        var stereo_phase = \stereo_phase.kr(specs[\stereo_phase].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, processed, mono_for_analysis;
        var low_band, mid_band1, mid_band2, mid_band3, high_band;
        var lfo1, lfo2, lfo3, lfo4, lfo5;
        var mod1, mod2, mod3, mod4, mod5;
        var left, right;

        sig = In.ar(in_bus);
        dry = sig;

        // Create 5 frequency bands using resonant bandpass filters
        low_band = RLPF.ar(sig, crossover * 0.4, resonance);
        mid_band1 = BPF.ar(sig, crossover * 0.7, resonance);
        mid_band2 = BPF.ar(sig, crossover * 1.0, resonance);
        mid_band3 = BPF.ar(sig, crossover * 1.5, resonance);
        high_band = RHPF.ar(sig, crossover * 2.5, resonance);

        // Create phase-shifted LFOs for each band
        lfo1 = SinOsc.kr(rate, 0);
        lfo2 = SinOsc.kr(rate, phase_spread * 2pi * 0.25);
        lfo3 = SinOsc.kr(rate, phase_spread * 2pi * 0.5);
        lfo4 = SinOsc.kr(rate, phase_spread * 2pi * 0.75);
        lfo5 = SinOsc.kr(rate, phase_spread * 2pi);

        // Apply tremolo modulation to each band
        mod1 = 1 - (depth * 0.5) + (lfo1 * depth * 0.5);
        mod2 = 1 - (depth * 0.5) + (lfo2 * depth * 0.5);
        mod3 = 1 - (depth * 0.5) + (lfo3 * depth * 0.5);
        mod4 = 1 - (depth * 0.5) + (lfo4 * depth * 0.5);
        mod5 = 1 - (depth * 0.5) + (lfo5 * depth * 0.5);

        // Mix all modulated bands
        left = (low_band * mod1) + (mid_band1 * mod2) + (mid_band2 * mod3) + 
               (mid_band3 * mod4) + (high_band * mod5);

        // Create stereo version with phase offset
        lfo1 = SinOsc.kr(rate, stereo_phase * 2pi);
        lfo2 = SinOsc.kr(rate, (phase_spread * 0.25 + stereo_phase) * 2pi);
        lfo3 = SinOsc.kr(rate, (phase_spread * 0.5 + stereo_phase) * 2pi);
        lfo4 = SinOsc.kr(rate, (phase_spread * 0.75 + stereo_phase) * 2pi);
        lfo5 = SinOsc.kr(rate, (phase_spread + stereo_phase) * 2pi);

        mod1 = 1 - (depth * 0.5) + (lfo1 * depth * 0.5);
        mod2 = 1 - (depth * 0.5) + (lfo2 * depth * 0.5);
        mod3 = 1 - (depth * 0.5) + (lfo3 * depth * 0.5);
        mod4 = 1 - (depth * 0.5) + (lfo4 * depth * 0.5);
        mod5 = 1 - (depth * 0.5) + (lfo5 * depth * 0.5);

        right = (low_band * mod1) + (mid_band1 * mod2) + (mid_band2 * mod3) + 
                (mid_band3 * mod4) + (high_band * mod5);

        processed = XFade2.ar([dry, dry], [left, right], mix * 2 - 1);

        mono_for_analysis = processed[0];
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, processed);
    });
    def.add;
    "Effect SynthDef 'harmonic_tremolo' added".postln;

    ~setupEffect.value(defName, specs);
)
