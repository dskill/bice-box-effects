(
    var defName = \flanger;
    var def, specsForJSON, jsonString, paramCount;
    
    def = SynthDef(defName, {
        // Use NamedControl style instead of traditional arguments
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var rate = \rate.kr(0.5);
        var depth = \depth.kr(0.002);
        var feedback = \feedback.kr(0.5);
        var center = \center.kr(0.005);
        var mix = \mix.kr(0.5);
        
        var sig, flange, mod, wet, final_sig, mono_for_analysis;

        sig = In.ar(in_bus);
        mod = SinOsc.kr(rate).range(center - depth, center + depth);
        
        flange = DelayC.ar(sig + (LocalIn.ar(1) * feedback), 0.02, mod);
        LocalOut.ar(flange);
        
        wet = flange;
        final_sig = XFade2.ar(sig, wet, mix * 2 - 1);

        mono_for_analysis = final_sig;
                
        Out.ar(out, [final_sig,final_sig]);
        Out.ar(analysis_out_bus, mono_for_analysis);
    });
    def.add;
    "Effect SynthDef 'flanger' added".postln;
    
    // Ensure ~effectParameterSpecs is an IdentityDictionary
    ~effectParameterSpecs ?? { ~effectParameterSpecs = IdentityDictionary.new; };
    if(~effectParameterSpecs.isKindOf(IdentityDictionary).not) {
        "CRITICAL WARNING: ~effectParameterSpecs is not an IdentityDictionary! Re-initializing.".postln;
        ~effectParameterSpecs = IdentityDictionary.new;
    };

    // Register parameter specifications for this SynthDef
    // With NamedControl style, we can directly define the specs based on our knowledge of the parameters
    ~effectParameterSpecs.put(defName, (
        rate: ControlSpec(0.01, 10, 'exp', 0, 0.5, "Hz"),
        depth: ControlSpec(0.0001, 0.01, 'lin', 0, 0.002, "s"),
        feedback: ControlSpec(0.0, 0.99, 'lin', 0, 0.5, "%"),
        center: ControlSpec(0.0001, 0.02, 'lin', 0, 0.005, "s"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    ));
    
    // Debug: Check what was stored
    ("DEBUG: ~effectParameterSpecs[%] = %").format(defName, ~effectParameterSpecs[defName]).postln;
    ("DEBUG: ~effectParameterSpecs[%] class = %").format(defName, ~effectParameterSpecs[defName].class).postln;
    
    // Convert ControlSpecs to JSON-serializable format
    specsForJSON = ();
    ("DEBUG: specsForJSON initialized as: %").format(specsForJSON).postln;
    
    ~effectParameterSpecs[defName].keysValuesDo({ |key, spec|
        ("DEBUG: Processing key: %, spec: %, spec.class: %").format(key, spec, spec.class).postln;
        ("DEBUG: spec.minval: %, spec.maxval: %, spec.default: %").format(spec.minval, spec.maxval, spec.default).postln;
        
        specsForJSON.put(key, (
            minval: spec.minval,
            maxval: spec.maxval,
            warp: spec.warp.asSpecifier.asString,
            step: spec.step,
            default: spec.default,
            units: spec.units.asString
        ));
        ("DEBUG: specsForJSON after adding %: %").format(key, specsForJSON).postln;
    });
    
    ("DEBUG: Final specsForJSON: %").format(specsForJSON).postln;
    
    // Manually build JSON string since asJSON doesn't work reliably with nested Events
    jsonString = "{";
    paramCount = 0;
    specsForJSON.keysValuesDo({ |key, paramData|
        if(paramCount > 0, { jsonString = jsonString ++ "," });
        jsonString = jsonString ++ "\"" ++ key.asString ++ "\":{";
        jsonString = jsonString ++ "\"minval\":" ++ paramData.minval.asString;
        jsonString = jsonString ++ ",\"maxval\":" ++ paramData.maxval.asString;
        jsonString = jsonString ++ ",\"warp\":\"" ++ paramData.warp.asString ++ "\"";
        jsonString = jsonString ++ ",\"step\":" ++ paramData.step.asString;
        jsonString = jsonString ++ ",\"default\":" ++ paramData.default.asString;
        jsonString = jsonString ++ ",\"units\":\"" ++ paramData.units.asString ++ "\"";
        jsonString = jsonString ++ "}";
        paramCount = paramCount + 1;
    });
    jsonString = jsonString ++ "}";
    
    ("DEBUG: Manual JSON string: %").format(jsonString).postln;
    ("Parameter specs for % registered: %").format(defName, jsonString).postln;

    // Existing logic to create the synth instance
    fork {
        s.sync;

        if(~effect.notNil, {
            "Freeing existing effect synth".postln;
            ~effect.free;
        });

        ~effect = Synth(defName, [
            \in_bus, ~input_bus,
            \analysis_out_bus, ~effect_output_bus_for_analysis
        ], ~effectGroup);
        ("New % synth created with analysis output bus").format(defName).postln;
    };
)