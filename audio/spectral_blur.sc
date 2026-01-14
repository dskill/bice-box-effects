// shader: oscilloscope
// category: Spectral
// description: FFT spectral mangler with freeze and blur
(
    var defName = \spectral_blur;
    var specs = (
        blur: ControlSpec(0.0, 1.0, 'lin', 0, 0.3, "%"),
        freeze: ControlSpec(0.0, 1.0, 'lin', 0, 0.0, "%"),
        shift: ControlSpec(-12, 12, 'lin', 0, 0, "st"),
        brightness: ControlSpec(0.1, 4.0, 'exp', 0, 1.0, "x"),
        smear: ControlSpec(0.0, 1.0, 'lin', 0, 0.2, "%"),
        mix: ControlSpec(0.0, 1.0, 'lin', 0, 0.5, "%")
    );

    var def = SynthDef(defName, {
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var blur = \blur.kr(specs[\blur].default);
        var freeze = \freeze.kr(specs[\freeze].default);
        var shift = \shift.kr(specs[\shift].default);
        var brightness = \brightness.kr(specs[\brightness].default);
        var smear = \smear.kr(specs[\smear].default);
        var mix = \mix.kr(specs[\mix].default);

        var sig, dry, chain, processed, mono_for_analysis;
        var freezeTrig, frozenChain, liveChain, blendChain;
        var shiftRatio;

        sig = In.ar(in_bus);
        dry = sig;

        // FFT processing
        chain = FFT(LocalBuf(2048), sig);
        
        // Frozen spectral frame
        freezeTrig = Impulse.kr(10);
        frozenChain = PV_Freeze(chain, freeze);
        
        // Spectral blur (randomize phases)
        chain = PV_MagSmear(frozenChain, smear.linlin(0, 1, 0, 100).round(1));
        
        // Spectral shift (frequency shift in spectral domain)
        shiftRatio = shift.midiratio;
        chain = PV_BinShift(chain, shiftRatio, 0, blur);
        
        // Reconstruct audio
        processed = IFFT(chain);
        
        // Brightness control (tilt filter)
        processed = BHiShelf.ar(processed, 2000, 1.0, brightness.ampdb * 6);
        
        // Mix
        processed = XFade2.ar(dry, processed, mix * 2 - 1);

        mono_for_analysis = processed;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [processed, processed]);
    });
    def.add;
    "Effect SynthDef 'spectral_blur' added".postln;

    ~setupEffect.value(defName, specs);
)