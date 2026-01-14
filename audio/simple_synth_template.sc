// shader: oscilloscope
// category: MIDI
// description: Polyphonic synth template with ADSR and filter shaping
// Simple polyphonic synthesizer template
// This demonstrates how easy it is to create new polyphonic effects

(
    var defName = \simple_synth_template;
    var numVoices = 16; // Polyphonic synth
    var specs = (
        // Define your effect parameters here
        amp: ControlSpec(0, 1, 'lin', 0, 0.5, ""),
        filter_freq: ControlSpec(100, 8000, 'exp', 0, 2000, "Hz"),
        wave_type: ControlSpec(0, 2, 'lin', 1, 0, ""), // 0=sine, 1=saw, 2=square
        // ADSR envelope parameters
        attack: ControlSpec(0.001, 2.0, 'exp', 0, 0.01, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, ""),
        release: ControlSpec(0.001, 4.0, 'exp', 0, 0.2, "s")
    );
 
    var def = SynthDef(defName, {
        // ALL VARIABLE DECLARATIONS MUST BE AT THE TOP
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        var amp = \amp.kr(specs[\amp].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var wave_type = \wave_type.kr(specs[\wave_type].default);
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        
        var sig, env, gate, freq, vel, amp_vel;
        var mono_for_analysis;
        var voice, voices, out_sig;
        
        // MIDI setup
        var note = \note.kr(60);  // MIDI note number
        var velocity = \velocity.kr(64);  // MIDI velocity (0-127)
        var gateTrig = \gate.kr(0);  // Gate signal (0 or 1)

        // Convert MIDI note to frequency
        freq = note.midicps;
        amp_vel = velocity / 127;

        // ADSR envelope
        env = EnvGen.kr(
            Env.adsr(attack, decay, sustain, release),
            gateTrig,
            doneAction: 0
        );

        // Oscillator based on wave type
        sig = Select.ar(wave_type, [
            SinOsc.ar(freq),
            Saw.ar(freq),
            Pulse.ar(freq, 0.5)
        ]);

        // Apply filter
        sig = LPF.ar(sig, filter_freq);

        // Apply envelope and amplitude
        sig = sig * env * amp * amp_vel;

        // Output
        out_sig = sig;  // Mono output
        Out.ar(out, [out_sig, out_sig]);
        
        // Analysis output (mono)
        mono_for_analysis = out_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
    });

    def.add;
    "Effect SynthDef 'simple_synth_template' added".postln;

    // Create polyphonic synth using setupPolyEffect
    ~setupPolyEffect.value(defName, specs, numVoices);
)
