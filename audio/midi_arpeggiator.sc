// shader: oscilloscope
// category: Synth
(
    var defName = \midi_arpeggiator;
    var numVoices = 8; // Maximum polyphony for key tracking
    var specs = (
        // Arpeggiator parameters
        arp_rate: ControlSpec(0.5, 8.0, 'exp', 0, 2.0, "Hz"),
        arp_pattern: ControlSpec(0, 3, 'lin', 1, 0, ""), // 0=up, 1=down, 2=up/down, 3=random
        gate_length: ControlSpec(0.1, 0.95, 'lin', 0, 0.8, ""),
        
        // Oscillator parameters
        wave_type: ControlSpec(0, 2, 'lin', 1, 1, ""), // 0=sine, 1=saw, 2=square
        filter_freq: ControlSpec(100, 8000, 'exp', 0, 2000, "Hz"),
        filter_res: ControlSpec(0.1, 0.9, 'lin', 0, 0.3, ""),
        
        // Envelope parameters
        attack: ControlSpec(0.001, 0.5, 'exp', 0, 0.01, "s"),
        decay: ControlSpec(0.001, 2.0, 'exp', 0, 0.1, "s"),
        sustain: ControlSpec(0.0, 1.0, 'lin', 0, 0.8, ""),
        release: ControlSpec(0.001, 1.0, 'exp', 0, 0.2, "s"),
        
        // Output
        amp: ControlSpec(0, 1, 'lin', 0, 0.5, "")
    );

    var def = SynthDef(defName, {
        // Standard parameters
        var out = \out.kr(0);
        var in_bus = \in_bus.kr(0);
        var analysis_out_bus = \analysis_out_bus.kr;
        
        // Arpeggiator parameters
        var arp_rate = \arp_rate.kr(specs[\arp_rate].default);
        var arp_pattern = \arp_pattern.kr(specs[\arp_pattern].default);
        var gate_length = \gate_length.kr(specs[\gate_length].default);
        
        // Oscillator parameters
        var wave_type = \wave_type.kr(specs[\wave_type].default);
        var filter_freq = \filter_freq.kr(specs[\filter_freq].default);
        var filter_res = \filter_res.kr(specs[\filter_res].default);
        
        // Envelope parameters
        var attack = \attack.kr(specs[\attack].default);
        var decay = \decay.kr(specs[\decay].default);
        var sustain = \sustain.kr(specs[\sustain].default);
        var release = \release.kr(specs[\release].default);
        
        // Output
        var amp = \amp.kr(specs[\amp].default);
        
        // Voice arrays - REQUIRED for polyphonic synths
        var voice_freqs = \voice_freqs.kr(Array.fill(numVoices, 440));
        var voice_gates = \voice_gates.kr(Array.fill(numVoices, 0));
        var voice_amps = \voice_amps.kr(Array.fill(numVoices, 0));
        
        // ALL other variables declared here!
        var held_notes, num_held, arp_clock, arp_index, current_freq, arp_gate;
        var wave, filtered, env, final_sig, mono_for_analysis;
        
        // Collect currently held notes (non-zero gates)
        held_notes = Array.fill(numVoices, { |i|
            var freq, gate;
            if(numVoices > 1) {
                freq = voice_freqs[i];
                gate = voice_gates[i];
            } {
                freq = voice_freqs;
                gate = voice_gates;
            };
            freq * gate; // Only include frequency if gate is active
        });
        
        // Count number of held notes
        num_held = held_notes.collect({ |freq| (freq > 0).asInteger }).sum;
        
        // Arpeggiator clock
        arp_clock = Impulse.kr(arp_rate);
        
        // Arpeggiator index counter
        arp_index = PulseCount.kr(arp_clock) % max(1, num_held);
        
        // Select current note based on pattern
        current_freq = Select.kr(arp_pattern, [
            // Pattern 0: Up
            Select.kr(arp_index, held_notes),
            // Pattern 1: Down  
            Select.kr((num_held - 1 - arp_index) % max(1, num_held), held_notes),
            // Pattern 2: Up/Down (ping-pong)
            Select.kr(
                Select.kr((arp_index / max(1, num_held - 1)).asInteger % 2, [
                    arp_index % max(1, num_held), // up
                    (num_held - 1 - (arp_index % max(1, num_held))) % max(1, num_held) // down
                ]), 
                held_notes
            ),
            // Pattern 3: Random
            Select.kr(LFNoise0.kr(arp_rate).range(0, max(1, num_held - 1)).round, held_notes)
        ]);
        
        // Only play when we have held notes
        current_freq = current_freq * (num_held > 0);
        
        // Generate arp gate with specified length
        arp_gate = Trig.kr(arp_clock, gate_length / arp_rate) * (current_freq > 0);
        
        // Generate oscillator
        wave = Select.ar(wave_type, [
            SinOsc.ar(current_freq),      // 0 = sine
            Saw.ar(current_freq),         // 1 = saw  
            Pulse.ar(current_freq, 0.5)   // 2 = square
        ]);
        
        // Apply filter
        filtered = RLPF.ar(wave, filter_freq, 1 - filter_res);
        
        // Apply envelope
        env = EnvGen.ar(Env.adsr(attack, decay, sustain, release), arp_gate);
        
        // Final signal
        final_sig = filtered * env * amp;
        
        // Outputs
        mono_for_analysis = final_sig;
        Out.ar(analysis_out_bus, mono_for_analysis);
        Out.ar(out, [final_sig, final_sig]);
    });
    def.add;
    "Effect SynthDef 'midi_arpeggiator' (polyphonic) added".postln;

    // CRITICAL: Pass numVoices to ~setupEffect to enable MIDI control
    ~setupEffect.value(defName, specs, [], numVoices);
)