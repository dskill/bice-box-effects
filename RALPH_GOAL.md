# Ralph Goal: Generate 10 Unique Audio Effects

## Your Task
Generate ONE creative audio effect for bice-box, update progress, then EXIT.

## First Steps
1. Read `RALPH_PROGRESS.md` to see current status and what effects already exist
2. If `EXIT_SIGNAL: true`, do nothing and exit immediately
3. Review BOTH the "Generated Effects" list AND the "Existing Effects" below
4. Generate ONE new effect that is DIFFERENT from all of these

## Existing Effects (DO NOT recreate these)
bypass, analog_chorus, autowah, bands, baxandall_distortion, bit_crusher, bitcrusher, chorus, crackle_reverb, distortion_with_reverb, electremolo, flames, flanger, garfunkel, granular_delay, grunge_distortion, guitar_arpeggiator, guitar_harmonizer, guitar_harmony_synth, guitar_to_midi, hyperdrive, jet_flanger, kaleidoscope, layla_backing, mbv, midi_arpeggiator, neon_geometrics, neon_love, overdrive, palpatine, phaser, phaser_2d, ping_pong_delay, pitch_shifter, ring_mod, satanic_organ, shimmer_reverb, simple_synth_template, spectral_freeze, spectral_freezing_delay, stereo_widener, synthtoy, test_sin_wave, tremolo, triangle_distortion, tuner, vocoder_envelope, vocoder_wah, vocorotator, zeeks_pizza

## Valid Visualizers (only use these names if pairing)
**Shaders:** ascii, claude spectrum, cloud diving, curling smoke, fft tunnel, fire3d, fireflow, flames, fractal waves, grunge, kaleidoscope, liquid flame, metaballs, moebius, neon geometrics, neon love, oscilloscope, outrun, palpatine, plasma globe, radial fft line, scope, silexar ascii, skull
**p5.js:** bands, electremolo, flames fluid, hyperdrive, outrun waves, palpatine lightning, phaser 2d, ping pong, ripples, simplex, singularity, tuner, waveform pingpong, zeeks pizza

## Generation Guidelines

**NOTE:** The Skill tool doesn't work in `-p` mode. Read the skill file directly:
`.claude/skills/audio-effect/SKILL.md`

Push into NEW territory - ideas:
- Spectral mangling (convolution, vocoding, spectral delay)
- Unusual modulation sources (chaos, envelope followers, pitch tracking)
- Textural effects (noise gating, resonator banks, comb filters)
- Lo-fi/degradation (tape warble, vinyl crackle, radio tuning)
- Pitch effects (harmonizers, octavers, whammy-style)
- Dynamics (multiband compression, expanders, ducking)

## Saving Effects

Use MCP `create_or_update_audio_effect` to save effects. It will test compilation automatically.

## After Each Attempt

Update `RALPH_PROGRESS.md`:
1. Increment `Iterations` count
2. Add entry to Iteration Log: "Iteration N: created effect_name" or "Iteration N: failed - reason"
3. If effect succeeded, increment `Effects` count and add to Generated Effects list with a brief description
4. If `Effects` >= 10, set `EXIT_SIGNAL: true`

Then EXIT immediately. One effect per session.

## Quality Bar
- Must compile (test via MCP before saving)
- Must have wet/dry mix parameter
- Must have at least 2 controllable parameters
- Must be DIFFERENT from existing effects listed above

## Lessons Learned
<!-- Add notes here when effects fail - these inform future iterations -->
- (none yet)
