#!/bin/bash
# Ralph loop for effect generation
# Usage: ./ralph.sh [options]
#   -c, --cli <claude|codex>  Choose CLI (default: claude)
#   -n, --max-iterations <N>  Max iterations (default: 50)
#   -h, --help                Show help

CLI="claude"
MAX_ITERATIONS=50
STAGNATION_LIMIT=3
PROGRESS_FILE="RALPH_PROGRESS.md"
OUTPUT_LOG="ralph-output.log"

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -c|--cli)
            CLI="$2"
            shift 2
            ;;
        -n|--max-iterations)
            MAX_ITERATIONS="$2"
            shift 2
            ;;
        -h|--help)
            echo "Usage: ./ralph.sh [options]"
            echo "  -c, --cli <claude|codex>  Choose CLI (default: claude)"
            echo "  -n, --max-iterations <N>  Max iterations (default: 50)"
            echo "  -h, --help                Show help"
            exit 0
            ;;
        *)
            # Legacy: first positional arg is max_iterations
            MAX_ITERATIONS="$1"
            shift
            ;;
    esac
done

# Validate CLI choice
if [[ "$CLI" != "claude" && "$CLI" != "codex" ]]; then
    echo "Error: CLI must be 'claude' or 'codex'"
    exit 1
fi

# Clear previous log and write run params
> "$OUTPUT_LOG"
{
    echo "=== Ralph Run Parameters ==="
    echo "CLI: $CLI"
    echo "Max Iterations: $MAX_ITERATIONS"
    echo "Stagnation Limit: $STAGNATION_LIMIT"
    echo "Started: $(date)"
    echo "==========================="
    echo ""
} | tee "$OUTPUT_LOG"

iteration=0
last_effect_count=0
stagnation=0

echo "Starting Ralph loop with $CLI (max $MAX_ITERATIONS iterations)"
echo "Output log: $OUTPUT_LOG"
echo "Monitor with: tail -f $OUTPUT_LOG"
echo "Press Ctrl+C to stop"
echo "---"

# Function to run the appropriate CLI
run_cli() {
    local prompt="$1"

    if [[ "$CLI" == "claude" ]]; then
        claude --max-turns 20 --verbose -p "$prompt" --output-format stream-json 2>&1 | \
            tee -a "$OUTPUT_LOG" | \
            jq -r --unbuffered 'select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text // empty | . + "\n"' 2>/dev/null
    else
        # Codex CLI
        codex exec "$prompt" --json --full-auto 2>&1 | \
            tee -a "$OUTPUT_LOG" | \
            jq -r --unbuffered 'select(.type == "message" and .role == "assistant") | .content // empty' 2>/dev/null
    fi
}

while true; do
    # Check EXIT_SIGNAL
    if grep -q "EXIT_SIGNAL: true" "$PROGRESS_FILE" 2>/dev/null; then
        echo "EXIT_SIGNAL detected. Goal complete!"
        break
    fi

    # Check max iterations
    if [ $iteration -ge $MAX_ITERATIONS ]; then
        echo "Max iterations ($MAX_ITERATIONS) reached. Stopping."
        break
    fi

    # Run CLI
    iteration=$((iteration + 1))
    echo "=== Iteration $iteration ($CLI) ===" | tee -a "$OUTPUT_LOG"
    run_cli "$(cat RALPH_GOAL.md)"

    # Check for stagnation (no new effects after N iterations)
    current_effect_count=$(grep "Effects:" "$PROGRESS_FILE" 2>/dev/null | sed 's/Effects: \([0-9]*\).*/\1/' || echo 0)
    if [ "$current_effect_count" -eq "$last_effect_count" ]; then
        stagnation=$((stagnation + 1))
        echo "No progress. Stagnation count: $stagnation/$STAGNATION_LIMIT"
        if [ $stagnation -ge $STAGNATION_LIMIT ]; then
            echo "Stagnation limit reached. Check RALPH_GOAL.md lessons learned."
            break
        fi
    else
        stagnation=0
        last_effect_count=$current_effect_count
    fi

    # Show progress
    echo "--- Progress: $(grep -E '^Effects:' "$PROGRESS_FILE") | Iterations: $iteration ---"
    echo ""
done

echo ""
echo "=== Final Status ==="
grep -E "^(Effects|Iterations|EXIT_SIGNAL):" "$PROGRESS_FILE"
