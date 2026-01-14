# Ralph Goal: Generate 10 Unique Audio Effects

## Your Task
Generate ONE creative audio effect for bice-box, update progress, then EXIT.

## First Steps
1. Read `RALPH_PROGRESS.md` to see current status and what effects already exist
2. If `EXIT_SIGNAL: true`, do nothing and exit immediately
3. Review the "Generated Effects" list - your next effect must be DIFFERENT from these
4. Generate ONE new effect that adds variety to the collection

## Generation Guidelines

Use the `@audio-effect` skill to create effects. Ideas to explore:
- Spectral effects (freeze, blur, shift)
- Time-based (delays, reverbs, loopers)
- Modulation (chorus, flanger, phaser)
- Distortion/saturation (tape, tube, bitcrush)
- Granular (stretch, scatter, cloud)
- Hybrid combinations

Save effects to: `audio/candidates/<descriptive_name>.sc`

## After Each Attempt

Update `RALPH_PROGRESS.md`:
1. Increment `Iterations` count
2. Add entry to Iteration Log: "Iteration N: created effect_name" or "Iteration N: failed - reason"
3. If effect succeeded, increment `Effects` count and add to Generated Effects list with a brief description (e.g., "- shimmer_reverb: pitch-shifted delay feedback with long tail")
4. If `Effects` >= 10, set `EXIT_SIGNAL: true`

Then EXIT immediately. One effect per session.

## Quality Bar
- Must compile (test via MCP before saving)
- Must have wet/dry mix parameter
- Must have at least 2 controllable parameters
- Should sound distinct from existing effects

## Lessons Learned
<!-- Add notes here when effects fail - these inform future iterations -->
- MCP create_or_update_audio_effect tool has a race condition: SynthDef.add completes but server hasn't received the SynthDef yet when test runs. All tested effects compile successfully (tape_saturation, ring_modulator, simple_lpf) but fail the instantiation test. This prevents file saving. Need Drew to fix MCP timing issue or provide alternative save method.
