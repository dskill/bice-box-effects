# OSC Data Consolidation Refactor Plan (Attempted)

This document outlines the attempted refactoring steps to consolidate the generation of OSC (Open Sound Control) data, specifically the `/combined_data` message, within the SuperCollider setup for BICE-Box.

## Goal

The primary goal was to centralize all audio analysis (Waveform, FFT, RMS for input and output) and the subsequent OSC message sending into a single "master" analysis SynthDef in `init.sc`. This would simplify individual effect SynthDefs by removing boilerplate analysis and OSC sending code from them. The aim was for effects to only be responsible for their audio processing and outputting a mono signal for analysis.

## Intended Architecture

1.  **`init.sc` - Central Hub:**
    *   **Source Group (`~sourceGroup`):** Contains audio input sources (e.g., `SynthDef(\audioIn)` for live input, `SynthDef(\playGuitarRiff)` for test loop). Output to `~input_bus`.
    *   **Effect Group (`~effectGroup`):** Contains the active audio effect (e.g., `SynthDef(\bypass)`). Reads from `~input_bus`, outputs main audio to speakers, and outputs a dedicated mono signal to `~effect_output_bus_for_analysis`.
    *   **Analysis Group (`~analysisGroup`):** Runs after `~effectGroup`. Contains `SynthDef(\masterAnalyser)`.
    *   **Buses:**
        *   `~input_bus` (Audio, Mono/Stereo): Carries the audio signal going into the effect.
        *   `~effect_output_bus_for_analysis` (Audio, Mono): Carries the mono processed signal from the current effect, specifically for analysis.
        *   `~rms_bus_input` (Control): For pre-effect RMS.
        *   `~rms_bus_output` (Control): For post-effect RMS.
    *   **Buffers:**
        *   `~relay_buffer_out` (Audio): For post-effect waveform data.
        *   `~fft_buffer_out` (Audio): For post-effect FFT data.
    *   **`SynthDef(\masterAnalyser)`:**
        *   Reads `~input_bus` (mono) for input RMS calculation.
        *   Reads `~effect_output_bus_for_analysis` (mono) for all other analysis.
        *   Writes waveform data from `~effect_output_bus_for_analysis` to `~relay_buffer_out`.
        *   Performs FFT on data from `~effect_output_bus_for_analysis`, stores in `~fft_buffer_out`.
        *   Calculates RMS of `~input_bus` -> `~rms_bus_input`.
        *   Calculates RMS of `~effect_output_bus_for_analysis` -> `~rms_bus_output`.
        *   Uses `SendReply.kr` to trigger a single OSC path (e.g., `/combined_data`) at a regular interval (e.g., 60Hz), sending the current buffer partition index.
    *   **`OSCdef(\combinedData)`:**
        *   Triggered by `SynthDef(\masterAnalyser)`.
        *   Reads `~relay_buffer_out`, `~fft_buffer_out`, `~rms_bus_input`, `~rms_bus_output`.
        *   Assembles and sends the single `/combined_data` OSC message to the client application.
    *   Other OSCdefs (like `/tuner_data`) would remain if they serve distinct purposes. Old OSCdefs like `/buffer_refresh`, `/rms`, `/fft_data1` would be removed.

2.  **Effect SynthDefs (e.g., `effects/audio/bypass.sc`):**
    *   Simplified to focus solely on audio processing.
    *   Input arguments: `out` (main audio out), `in_bus` (from `~input_bus`), `analysis_out_bus` (to send mono processed audio to `~effect_output_bus_for_analysis`).
    *   No internal `BufWr`, `FFT`, `RunningSum`, or `SendReply` for `/combined_data` related messages.

## Attempted Incremental Steps

The refactor was attempted incrementally to maintain stability:

**Phase 1: Initial Setup and Correction (Successful)**
*   Corrected a typo in `bypass.sc`: `SumError.ar` was changed to `Mix.ar` for correct stereo-to-mono signal mixing. This was a necessary bug fix independent of the main refactor.

**Phase 2: Revert `init.sc` and Prepare for Incremental `masterAnalyser` Addition (Attempted)**
*   **Goal:** Restore `init.sc` to a known-good state (approximating its structure before large refactoring attempts).
*   **Action:**
    *   Replaced the content of `init.sc` with a version that mirrored its original OSCdef structure (`/buffer_refresh`, `/rms`, `/fft_data` for effect-specific FFTs, and the original `/combined_data` also triggered by effects).
    *   Removed `masterAnalyser`-specific components like `~effect_output_bus_for_analysis`, `~analysisGroup`, and the `masterAnalyser` SynthDef itself from this reverted version.
*   **Status at this point (before next step):** `init.sc` should have been relatively stable, with effects managing their own analysis and OSC triggers as they originally did.

**Phase 3: Add `masterAnalyser` Coexisting with Original System (Attempted, led to issues)**
*   **Goal:** Introduce the `masterAnalyser` SynthDef and its necessary components into the reverted `init.sc`, allowing it to run alongside the existing system for testing, without initially modifying effects or the primary OSCdefs.
*   **Actions Performed on `init.sc` (added to the reverted version):**
    1.  **Defined New Bus:**
        ```supercollider
        ~effect_output_bus_for_analysis = Bus.audio(s, 1);
        ```
    2.  **Defined New Group:** (to run after `~effectGroup`)
        ```supercollider
        ~analysisGroup = Group.new(~effectGroup, \addAfter);
        ```
    3.  **Defined `SynthDef(\masterAnalyser)`:**
        *   Read from `~input_bus` (for input RMS) and `~effect_output_bus_for_analysis` (for output waveform, FFT, RMS - expected to be silent initially).
        *   Wrote analysis data to the *existing* `~relay_buffer_out`, `~fft_buffer_out`, `~rms_bus_input`, `~rms_bus_output`. This was a point of contention/overwrite with effects still performing their own analysis to these same buffers/buses.
        *   Used `SendReply.kr` to send a trigger to a *new, temporary OSC path*: `'/master_combined_data_trigger'`, to avoid immediate conflict with the effects triggering the original `'/combined_data'` path.
        ```supercollider
        SynthDef(\masterAnalyser, {
            // ... (declarations)
            var effect_input_sig = In.ar(~input_bus, 1);
            var effect_output_sig = In.ar(~effect_output_bus_for_analysis, 1);
            // ... (phasor, partition logic) ...

            BufWr.ar(ReplaceNaN.ar(effect_output_sig, 0), ~relay_buffer_out.bufnum, phase_for_bufwr + (~chunkSize * partition_for_bufwr));
            FFT(~fft_buffer_out, ReplaceNaN.ar(effect_output_sig, 0), hop: 0.5, wintype: 1);

            rms_input_val = RunningSum.rms(ReplaceNaN.ar(effect_input_sig, 0), 1024);
            rms_output_val = RunningSum.rms(ReplaceNaN.ar(effect_output_sig, 0), 1024);

            Out.kr(~rms_bus_input, rms_input_val);
            Out.kr(~rms_bus_output, rms_output_val);

            // ... (latch partition) ...
            SendReply.kr(kr_impulse_for_sendreply, \'/master_combined_data_trigger\', latched_partition);
            Silent.ar(1);
        }).add;
        ```
    4.  **Added Temporary Monitoring `OSCdef`:**
        ```supercollider
        OSCdef(\masterCombinedDataTriggerTest, { |msg|
            var partition = msg[1].asInteger;
            ("Master Analyser triggered with partition: " ++ partition).postln;
        }, \'/master_combined_data_trigger\');
        ```
    5.  **Instantiated `masterAnalyserSynth`:**
        ```supercollider
        ~masterAnalyserSynth = Synth(\masterAnalyser, target: ~analysisGroup);
        ```
*   **Expected Outcome of This Phase:**
    *   The `masterAnalyser` would run.
    *   "Master Analyser triggered..." messages would appear in the SuperCollider console.
    *   The original effects and OSC data flow would remain largely functional (though `masterAnalyser` would be overwriting the shared buffers, potentially leading to mixed data in the original `/combined_data` message).
*   **Actual Outcome:** User reported "things aren't working," prompting a revert.

## Next Steps (If Re-attempting)

1.  **Stabilize `init.sc`:** Ensure `init.sc` and `bypass.sc` (with the `Mix.ar` fix) are in a reliably working state, similar to before the refactor attempt (Phase 2 described above).
2.  **Isolate `masterAnalyser` Buffers/Buses:**
    *   When re-introducing `SynthDef(\masterAnalyser)` (Phase 3), have it write to its *own dedicated set* of buffers and control buses:
        *   `~master_relay_buffer_out`
        *   `~master_fft_buffer_out`
        *   `~master_rms_bus_input`
        *   `~master_rms_bus_output`
    *   This prevents it from interfering with the data being written by the original effects to `~relay_buffer_out`, `~fft_buffer_out`, `~rms_bus_input`, `~rms_bus_output`.
3.  **Test `masterAnalyser` Trigger:**
    *   Use the `SendReply.kr` in `masterAnalyser` to `'/master_combined_data_trigger'` and monitor with `OSCdef(\masterCombinedDataTriggerTest)`.
    *   The `OSCdef(\masterCombinedDataTriggerTest)` can then be expanded to actually read from `masterAnalyser`'s dedicated buffers/buses and print/send this data to verify `masterAnalyser`'s internal logic.
4.  **Modify One Effect:**
    *   Once `masterAnalyser` is confirmed to be populating its own buffers correctly, modify a single effect (e.g., `bypass.sc`):
        *   Add the `analysis_out_bus` argument.
        *   Make it output its processed mono signal to this bus: `Out.ar(analysis_out_bus, Mix.ar(stereo_sig));`
        *   Remove its internal `BufWr`, `FFT`, `RunningSum`, and `SendReply` calls related to the old OSC messages (`/buffer_refresh`, `/rms`, `/fft_data`, `/combined_data`).
5.  **Switch `OSCdef(\combinedData)` Source:**
    *   Modify the main `OSCdef(\combinedData)` in `init.sc` to:
        *   Listen to the trigger from `masterAnalyser` (e.g., change its path from what effects were sending to what `masterAnalyser` is sending, or make `masterAnalyser` send to the original `/combined_data` path once effects no longer do).
        *   Read data from `masterAnalyser`'s dedicated buffers/buses (or the main ones if `masterAnalyser` is now solely responsible for them).
6.  **Cleanup:**
    *   Remove the old `OSCdef`s (`/buffer_refresh`, `/rms`, `/fft_data1`) from `init.sc` once they are no longer needed.
    *   Remove the temporary `OSCdef(\masterCombinedDataTriggerTest)`.
    *   If dedicated buffers were used for `masterAnalyser` during testing, decide if they should become the main buffers or if `masterAnalyser` should take over the original `~relay_buffer_out`, etc.

This methodical, isolated approach should help in identifying where issues arise during the refactoring process. 