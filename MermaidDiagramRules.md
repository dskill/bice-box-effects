Analyze the provided SuperCollider SynthDef file (`<filename>.sc`) and generate a Mermaid flowchart diagram (`flowchart TD`) that visually represents its audio signal flow and parameter dependencies.

**Target Style Guidelines (based on the provided `flames.sc` example and subsequent refinement):**

1.  **Layout:** Use a top-down flowchart (`flowchart TD`).
2.  **Theme:** Include a config block for the theme:
    ```mermaid
    ---
    config:
      theme: redux-dark
    ---
    ```
3.  **Nodes:**
    *   Use meaningful IDs for nodes (e.g., `In`, `Filter`, `Mixer`).
    *   Enclose node text in double quotes (`""`) if it contains spaces, special characters (`()`, `+`, `=`, etc.), or describes a process/component.
    *   Clearly indicate relevant parameters influencing a node within its text, usually in parentheses (e.g., `"Gain Stage (gain)"`, `"MoogFF Filter (tone, res)"`).
    *   Use standard shapes where appropriate:
        *   `["..."]` for general processing stages or descriptions.
        *   `{"..."}` for decision points or filters/modulators.
        *   `(["..."])` for inputs/outputs or distinct sound sources.
4.  **Edges:**
    *   Use `-->` for direct signal flow.
    *   Use `&` on a single line to show a signal splitting to multiple destinations simultaneously from one source (e.g., `Source --> Dest1 & Dest2 & Dest3`).
    *   Use labeled arrows (`-- "Label text" -->`) for important connections like dry signal paths.
5.  **Structure:**
    *   Trace the main signal path from the input (`In.ar`) to the final audio output (`Out.ar`).
    *   Identify and diagram any parallel signal paths (e.g., sidechain processing, effect generation like sparkles/shimmer, analysis chains). Use subgraphs (`subgraph SubgraphName ... end`) to group related parallel processes logically (e.g., `subgraph SparkleGeneration`).
    *   Clearly show where signals are mixed or combined (e.g., using `XFade2`, `Mix.fill`, `+`).
    *   Do not include monitoring signals (`BufWr`, `SendReply`, `Out.kr`, `RunningSum.rms`, etc.) unless they directly feed back into the main audio signal path. Focus only on the audible signal flow.

**Task:**

Apply these guidelines to the file `<filename>.sc` and generate the corresponding Mermaid diagram.