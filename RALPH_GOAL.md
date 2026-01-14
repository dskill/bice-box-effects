# Ralph Goal: Replicate 20 Famous Guitar Pedals (High-Detail Sonic Emulation)

## Your Task
Generate **ONE** creative audio effect for **bice-box** that **replicates (in high sonic detail) a specific famous guitar pedal**, update progress, then **EXIT**.

- **Sonic characteristics only** (not UI, enclosure, or exact circuit cloning).
- The **real pedal name must appear in the effect description**, along with a concise summary of what makes its sound iconic.
- Leave implementation decisions to the LLM, but **target the hallmark behaviors** described in the pedal specs below.

---

## First Steps
1. Read `RALPH_PROGRESS.md` to see current status and what effects already exist.
2. If `EXIT_SIGNAL: true`, do nothing and exit immediately.
3. Review BOTH:
   - The “Generated Effects” list in `RALPH_PROGRESS.md`
   - The “Existing Effects” list below
4. Choose ONE pedal from **Famous Guitar Pedals to Replicate** that has not been implemented yet.
5. Implement ONE effect (named after the pedal) that emulates the pedal’s sound behavior.
6. Save via MCP `create_or_update_audio_effect`, then update `RALPH_PROGRESS.md`, then EXIT.

---

## Existing Effects (DO NOT recreate these)
analog_chorus, autowah, bands, baxandall_distortion, bit_crusher, bypass, crackle_reverb, distortion_with_reverb, electremolo, env_gate, flames, flanger, freq_shifter, garfunkel, grain_shifter, granular_delay, grunge_distortion, guitar_arpeggiator, guitar_harmonizer, guitar_harmony_synth, guitar_to_midi, harmonic_tremolo, hyperdrive, jet_flanger, kaleidoscope, layla_backing, mbv, midi_arpeggiator, multiband_comp, neon_geometrics, neon_love, overdrive, palpatine, phaser_2d, ping_pong_delay, pitch_resonance, pitch_shifter, resonator, satanic_organ, simple_synth_template, spectral_blur, spectral_convolve, spectral_freezing_delay, synthtoy, tape_warble, test_sin_wave, tremolo, triangle_distortion, tuner, vocoder_envelope, vocoder_wah, vocorotator, zeeks_pizza

---

## Valid Visualizers (only use these names if pairing)
**Shaders:** ascii, claude spectrum, cloud diving, curling smoke, fft tunnel, fire3d, fireflow, flames, fractal waves, grunge, kaleidoscope, liquid flame, metaballs, moebius, neon geometrics, neon love, oscilloscope, outrun, palpatine, plasma globe, radial fft line, scope, silexar ascii, skull  
**p5.js:** bands, electremolo, flames fluid, hyperdrive, outrun waves, palpatine lightning, phaser 2d, ping pong, ripples, simplex, singularity, tuner, waveform pingpong, zeeks pizza

---

## Generation Guidelines
**NOTE:** The Skill tool doesn't work in `-p` mode. Read the skill file directly:  
`.claude/skills/audio-effect/SKILL.md`

- Push into **faithful emulation** territory: nonlinearities, dynamic response, tone-shaping, modulation idiosyncrasies, and “quirks.”
- Every effect MUST include:
  - Wet/dry mix parameter
  - At least 2 other controllable parameters
  - (Up to **8 knobs** available—use what is appropriate)
- Name the effect **after the real pedal**, using a consistent snake_case naming convention (examples below).
- The effect description MUST:
  - Include the **exact pedal name**
  - Describe the pedal’s hallmark sonic traits (one short paragraph)

---

## Famous Guitar Pedals to Replicate (Specs You Must Target)

Each entry includes required sonic behaviors to emulate. Use these as the **acceptance criteria** for your implementation.

### 1) ibanez_ts808_tube_screamer (Overdrive)
**Target sound:** Smooth overdrive with a pronounced “mid-hump,” commonly used to tighten bass and push an amp. Ibanez highlights its “smooth and full tone” and the classic 3-control format (Overdrive/Level/Tone). :contentReference[oaicite:0]{index=0}

**Knob mapping suggestion (up to 8):** drive, tone, level, mid_hump, bass_cut, clipping_symmetry, noise_gate, mix

---

### 2) klon_centaur (Overdrive/Boost)
**Target sound:** “Transparent overdrive” style—adds gain with minimal coloration, often used to add thickness and a slight mid emphasis while preserving the base tone. :contentReference[oaicite:1]{index=1}

**Knob mapping suggestion:** gain, treble, output, clean_blend, mid_emphasis, headroom, saturation, mix

---

### 3) proco_rat (Distortion)
**Target sound:** LM308-driven distortion with diode clipping and a post-distortion tone filter (“Filter” control). Emulate the strong character change as the filter rolls off highs. :contentReference[oaicite:2]{index=2}

**Knob mapping suggestion:** distortion, filter, level, low_cut, presence, clipping_hardness, sag, mix

---

### 4) boss_ds1 (Distortion)
**Target sound:** Tight, hard-edged gain with rich harmonics; designed to avoid harsh buzzy muddiness at high gain and to retain guitar characteristics. :contentReference[oaicite:3]{index=3}

**Knob mapping suggestion:** distortion, tone, level, bite, low_end, clipping, noise_gate, mix

---

### 5) ehx_big_muff_pi (Fuzz/Distortion/Sustainer)
**Target sound:** Thick, creamy fuzz with “violin-like sustain,” singing sustain and crushing distortion; classic tone stack behavior. :contentReference[oaicite:4]{index=4}

**Knob mapping suggestion:** sustain, tone, volume, mids, bass, treble, fuzz_texture, mix

---

### 6) dallas_arbiter_fuzz_face (Fuzz)
**Target sound:** Highly pickup/volume-sensitive fuzz; rolling back guitar volume cleans up to clean/crunch while full volume gives classic fuzz saturation. :contentReference[oaicite:5]{index=5}

**Knob mapping suggestion:** fuzz, volume, input_impedance, cleanup_amount, bias, warmth, gating, mix

---

### 7) zvex_fuzz_factory (Fuzz/Chaos)
**Target sound:** Wide range from gated “velcro” fuzz to intermodulating oscillations, shortwave/radio artifacts, and octave-like fuzz. :contentReference[oaicite:6]{index=6}

**Knob mapping suggestion:** gate, comp, drive, stab, bias, chaos, oscillation_amt, mix

---

### 8) dunlop_cry_baby_wah (Wah)
**Target sound:** Expressive vocal-like sweeping midrange; wah as a dynamic filter sweep (and can be “parked” as a fixed mid boost). :contentReference[oaicite:7]{index=7}

**Knob mapping suggestion:** wah_position (auto/virtual), q, range, resonance, drive, pre_emphasis, noise, mix

---

### 9) mxr_phase_90 (Phaser)
**Target sound:** One-knob classic phaser; ranges from subtle shimmer to pronounced swooshing sweep. :contentReference[oaicite:8]{index=8}

**Knob mapping suggestion:** rate, depth, feedback, stage_spread, sweep_center, stereo_width, waveform, mix

---

### 10) shin_ei_uni_vibe (Chorus/Vibrato/Phaser)
**Target sound:** Asymmetrical phase shifting associated with Leslie-like swirl; chorus mode mixes dry + phase-shifted, vibrato mode removes dry for pitch-bend throb. :contentReference[oaicite:9]{index=9}

**Knob mapping suggestion:** speed, intensity, chorus_vibrato, lamp_wobble, asymmetry, throb, stereo, mix

---

### 11) boss_ce2_chorus (Analog Chorus)
**Target sound:** Classic analog chorus lineage (CE-2 era), widely recognized warm modulation character; model should feel like an analog BBD chorus voice. :contentReference[oaicite:10]{index=10}

**Knob mapping suggestion:** rate, depth, tone, pre_delay, stereo_phase, noise, wow_flutter, mix

---

### 12) ehx_electric_mistress (Flanger)
**Target sound:** Lush flanging/chorus textures; includes “Filter Matrix” concept to freeze the sweep into fixed comb-filter tonality. :contentReference[oaicite:11]{index=11}

**Knob mapping suggestion:** rate, depth, feedback, manual, filter_matrix, stereo, hi_cut, mix

---

### 13) ehx_deluxe_memory_man (Analog Delay + Mod)
**Target sound:** “Super organic” analog delay with blend/feedback/time plus selectable chorus/vibrato modulation on repeats. :contentReference[oaicite:12]{index=12}

**Knob mapping suggestion:** delay_time, feedback, blend, mod_rate, mod_depth, saturation, hi_cut, mix

---

### 14) line6_dl4_delay_modeler (Delay + Looper style)
**Target sound:** Multi-mode delay modeler concept; emulate “model switching” feel and looper-like behaviors (within bice-box constraints). Line 6 positions it as including the original DL4 delays and looper lineage. :contentReference[oaicite:13]{index=13}

**Knob mapping suggestion:** mode_select, time, repeats, tweak, tweez, modulation, smear, mix

---

### 15) digitech_whammy (Pitch Bend/Interval)
**Target sound:** Expression-style pitch shifting: dive bombs, pitch bends, fast harmony shifts; include poly/chordal behavior where possible. :contentReference[oaicite:14]{index=14}

**Knob mapping suggestion:** interval, expression_pos, tracking_smooth, glide_time, harmony_mix, detune, formant, mix

---

### 16) ehx_micro_pog (Polyphonic Octave)
**Target sound:** Fast polyphonic tracking for chords/arpeggios with minimal glitching; octave up/down + dry balance; capable of 12-string and organ-like tones. :contentReference[oaicite:15]{index=15}

**Knob mapping suggestion:** dry, octave_up, octave_down, tone, attack, organ_blend, sub_filter, mix

---

### 17) strymon_bigsky (Reverb Workstation)
**Target sound:** 12 “reverb machines,” including Cloud and Shimmer; should cover realistic room/hall/plate and huge ambient harmonic tails. :contentReference[oaicite:16]{index=16}

**Knob mapping suggestion:** decay, pre_delay, tone, mod, param1, param2, shimmer_amt, mix

---

### 18) mxr_dyna_comp (Compressor)
**Target sound:** Tightens signal, adds rich sustain, and can produce the “percussive and clicky” compressed attack heard on many recordings. :contentReference[oaicite:17]{index=17}

**Knob mapping suggestion:** sensitivity, output, attack, release, knee, makeup_gain, color, mix

---

### 19) boss_vb2_vibrato (Vibrato)
**Target sound:** True pitch-shift vibrato (no chorus dry blend), BBD-style; include controllable onset (“rise time”) behavior. :contentReference[oaicite:18]{index=18}

**Knob mapping suggestion:** rate, depth, rise_time, waveform, hi_cut, instability, stereo, mix

---

### 20) boss_sg1_slow_gear (Auto Swell)
**Target sound:** Automatic volume swell that removes pick attack and fades in based on sensitivity/threshold and attack speed—like an auto volume pedal. :contentReference[oaicite:19]{index=19}

**Knob mapping suggestion:** sensitivity, attack, release, swell_curve, gate, sustain, noise, mix

---

## Saving Effects
Use MCP `create_or_update_audio_effect` to save effects. It will test compilation automatically.

---

## After Each Attempt (Update `RALPH_PROGRESS.md`)
1. Increment `Iterations` count.
2. Add entry to Iteration Log:  
   - `Iteration N: created effect_name` OR  
   - `Iteration N: failed - reason`
3. If succeeded:
   - Increment `Effects` count.
   - Add to Generated Effects list with a brief description (must mention which real pedal it emulates).
4. If `Effects >= 20`, set `EXIT_SIGNAL: true`.

Then EXIT immediately. One effect per session.

---

## Quality Bar
- Must compile (test via MCP before saving).
- Must have wet/dry mix parameter.
- Must have at least 2 controllable parameters.
- Must be DIFFERENT from existing effects listed above.
- Description MUST include the referenced guitar pedal name and its hallmark sonic qualities.

---

## Lessons Learned
<!-- Add notes here when effects fail - these inform future iterations -->
- (none yet)
