# Bice-Box Effects Development

Quick reference for creating audio effects and visualizers for Bice-Box.

## 🚨 CRITICAL: Effect File Editing Policy 🚨

**ALL effect file modifications MUST use the designated skill + MCP tool workflow:**

- **Audio effects (`.sc` files in `audio/`)** → MUST use `audio-effect` skill + MCP tools
- **Synths (`.sc` files in `audio/`)** → MUST use `polyphonic-synth` skill + MCP tools
- **GLSL shaders (`.glsl` files)** → MUST use `glsl-shader` skill + MCP tools
- **p5.js visuals (`.js` files in `visual/`)** → Use Write tool directly

**This applies to:**
- ✅ Creating new effects
- ✅ Updating/modifying existing effects
- ✅ Changing default parameter values
- ✅ Fixing bugs in effects
- ✅ ANY modification to effect files

**NEVER directly edit effect files using Write, Read+Write, or Search_Replace tools!**

**Why?** The MCP workflow includes validation, syntax testing, and safety checks that prevent breaking the live system.

## Quick Start

**Working with audio effects?** → Use the `audio-effect` skill
**Working with synths/instruments?** → Use the `polyphonic-synth` skill
**Working with visualizers?** → Use the `glsl-shader` skill

## MCP Tools Quick Reference

All tools are available via the `mcp__bice-box__` prefix:

### Querying
- **get_current_effect** - Get active effect name, visualizer, and parameters
- **list_effects** - List all available audio effects
- **list_visualizers** - List all p5.js sketches and GLSL shaders

### Switching
- **set_current_effect** - Switch audio effect by name
  - `effectName` (string, required)
- **set_visualizer** - Switch visualizer by name
  - `visualizerName` (string, required)

### Live Control
- **set_effect_parameters** - Update live parameter values (session only, doesn't change defaults)
  - `params` (object, required) - key/value pairs like `{mix: 0.7, delay: 0.5}`

### Creating/Updating Effects (Required Methods)
- **create_or_update_audio_effect** - REQUIRED for all `.sc` file modifications (validates before saving)
  - `effectName` (string, required) - name without .sc extension
  - `scCode` (string, required) - SuperCollider code (authored using appropriate skill)
  - `makeActive` (boolean, optional) - load effect immediately after creation/update
- **test_supercollider_code** - Test syntax without saving files
  - `scCode` (string, required) - SuperCollider code to validate

### Debugging
- **read_logs** - Read recent application logs
  - `lines` (number, optional, default 100, max 1000)
  - `filter` (string, optional) - search string

## Critical Universal Rules

⚠️ **FILENAME/DEFNAME MATCHING IS CRITICAL** ⚠️
- **defName MUST EXACTLY match filename** (character for character!)
  - ✅ CORRECT: `reverb.sc` → `var defName = \reverb;`
  - ✅ CORRECT: `ping_pong_delay.sc` → `var defName = \ping_pong_delay;`
  - ❌ WRONG: `happy-synth.sc` → `var defName = \happy_synth;` (hyphen vs underscore!)
- If faders don't appear in UI, check filename vs defName first!

**File Management**
- Files auto-reload on save via hot-reload system
- **SuperCollider effects (`.sc`)**: MUST use `create_or_update_audio_effect` MCP tool (validates before saving)
- **GLSL shaders (`.glsl`)**: MUST use `glsl-shader` skill + Write tool
- **p5.js visuals (`.js`)**: Can use Write tool directly
- Maximum 12 faders fit on screen - design parameters accordingly

## Directory Structure

- `audio/` - SuperCollider audio effects (.sc files)
- `shaders/` - GLSL visual effects (.glsl files)
- `visual/` - p5.js visual effects (.js files)

## Typical Workflow

1. Choose appropriate skill based on what you're creating
2. Use `test_supercollider_code` to validate syntax during development (for audio effects/synths)
3. Use `create_or_update_audio_effect` to safely create/update effects
4. Use `set_current_effect` or `set_visualizer` to activate and test
5. Use `set_effect_parameters` to tweak values live
6. Use `read_logs` if errors occur

## Notes

- **Parameter updates via MCP** (`set_effect_parameters`) only affect live values for the current session
- **To change default values**: Use the appropriate skill to author the updated code, then use `create_or_update_audio_effect` to save it
- Effect/visualizer changes via MCP automatically update the UI
- Hot-reload detects file changes and reloads active effect/visualizer

## Workflow Summary

1. **Read existing effect** (if updating) → Use Read tool on `.sc` or `.glsl` file
2. **Author/update code** → Use appropriate skill (`audio-effect`, `polyphonic-synth`, or `glsl-shader`)
3. **Test syntax** (for SC only) → `test_supercollider_code`
4. **Save effect** → `create_or_update_audio_effect` (for SC) or Write tool (for GLSL)
5. **Activate** → `set_current_effect` or `set_visualizer`
6. **Tweak parameters** → `set_effect_parameters` for live testing
