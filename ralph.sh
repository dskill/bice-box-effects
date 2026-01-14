#!/bin/bash
# Ralph loop for effect generation
# Usage: ./ralph.sh [max_iterations]

MAX_ITERATIONS=${1:-50}
STAGNATION_LIMIT=3
PROGRESS_FILE="RALPH_PROGRESS.md"
OUTPUT_LOG="ralph-output.log"

# Clear previous log
> "$OUTPUT_LOG"

iteration=0
last_effect_count=0
stagnation=0

echo "Starting Ralph loop (max $MAX_ITERATIONS iterations)"
echo "Output log: $OUTPUT_LOG"
echo "Monitor with: tail -f $OUTPUT_LOG"
echo "Press Ctrl+C to stop"
echo "---"

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

    # Run Claude
    iteration=$((iteration + 1))
    echo "=== Iteration $iteration ===" | tee -a "$OUTPUT_LOG"
    claude --max-turns 20 --verbose -p "$(cat RALPH_GOAL.md)" --output-format stream-json 2>&1 | \
        tee -a "$OUTPUT_LOG" | \
        jq -r --unbuffered 'select(.type == "assistant") | .message.content[]? | select(.type == "text") | .text // empty' 2>/dev/null

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
