# Bice-Box Effects

Audio effects and visualizers for Bice-Box.

## Directory Structure
- `audio/` - SuperCollider audio effects (`.sc`)
- `shaders/` - GLSL visualizers (`.glsl`)
- `visual/` - p5.js visualizers (`.js`)

## File Editing Rules

| File Type | How to Edit |
|-----------|-------------|
| `.sc` (audio) | ⚠️ **MCP tools only** - never write directly |
| `.glsl` (shaders) | ✅ Edit directly with Write tool |
| `.js` (p5.js) | ✅ Edit directly with Write tool |

### Why MCP for `.sc` files?

```
mcp__bice-box__create_or_update_audio_effect(effectName, scCode, makeActive)
mcp__bice-box__test_supercollider_code(scCode)
```

These tools compile the code in SuperCollider and return errors. Direct file writes bypass this feedback loop.

### Visualizers auto-reload on save
Edit `.glsl` and `.js` files directly - hot-reload detects changes automatically.

## Skills - Invoke When Creating/Editing

Skills provide templates, uniforms, and patterns. Invoke them on-demand:

| Task | Invoke Skill |
|------|--------------|
| Audio effects | `@audio-effect` |
| Polyphonic synths | `@polyphonic-synth` |
| GLSL shaders | `@glsl-shader` |

Skills load relevant context (available uniforms like iRMSOutput, templates, common patterns) without permanently bloating CLAUDE.md.

## Critical Rule

**defName MUST exactly match filename** in `.sc` files:
- ✅ `reverb.sc` → `var defName = \reverb;`
- ❌ `my-effect.sc` → `var defName = \my_effect;` (hyphen ≠ underscore)

*If faders don't appear in UI, check defName vs filename first!*

## MCP Tools Reference

| Tool | Purpose |
|------|---------|
| `get_current_effect` | Active effect, visualizer, params |
| `list_effects` | All audio effects |
| `list_visualizers` | All p5.js and GLSL visualizers |
| `set_current_effect(effectName)` | Switch audio effect |
| `set_visualizer(visualizerName)` | Switch visualizer |
| `set_effect_parameters(params)` | Tweak live values (**session only, not saved**) |
| `create_or_update_audio_effect(effectName, scCode, makeActive)` | **Required** for .sc files |
| `test_supercollider_code(scCode)` | Validate SC syntax |
| `read_logs(lines, filter)` | Debug |
