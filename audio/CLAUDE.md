# SuperCollider Audio Effects Quick Reference

## Critical Rules
- **defName MUST match filename** (e.g., `reverb.sc` → `\reverb`)
- **All variables in ONE block** after parameters - NO `var` declarations anywhere else
- **Use specs defaults**: `\param.kr(specs[\param].default)`
- **Mono-first**: Process in mono, output `[processed, processed]`
- **Analysis out**: Always mono signal to `analysis_out_bus`

## Template
```supercollider
// shader: oscilloscope
(
    var defName = \effect_name;
    var specs = (
        param1: ControlSpec(0.1, 10.0, 'exp', 0, 1.0, "x"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var param1 = \param1.kr(specs[\param1].default);
        var mix = \mix.kr(specs[\mix].default);

        // ALL variables declared here!
        var sig, dry, processed, mono_for_analysis;

        // Processing
        sig = In.ar(in_bus);  // Mono input
        dry = sig;
        processed = sig * param1;  // Your effect here
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        // Outputs
        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'effect_name' added".postln;

    ~setupEffect.value(defName, specs);
)
```

## Common ControlSpecs
- Linear 0-1: `ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")`
- Frequency: `ControlSpec(20, 2000, 'exp', 0, 440, "Hz")`
- Gain: `ControlSpec(0.1, 5.0, 'exp', 0, 1.0, "x")`
- Time: `ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s")`