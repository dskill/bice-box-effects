#!/bin/bash

# claunch - Claude CLI session manager with tmux
# Version: 0.0.1
# License: MIT
# Repository: https://github.com/0xkaz/claunch

VERSION="0.0.4"

# === Parse flags ===
USE_TMUX=false  # Default to direct mode
ARGS=()

while [[ $# -gt 0 ]]; do
  case $1 in
    --version|-v)
      echo "claunch v$VERSION"
      exit 0
      ;;
    --tmux)
      USE_TMUX=true
      shift
      ;;
    --help|-h)
      echo "claunch v$VERSION - Claude CLI session manager"
      echo ""
      echo "Usage:"
      echo "  claunch [options]           Start or resume Claude session"
      echo "  claunch list               List all sessions"
      echo "  claunch clean              Clean up orphaned session files"
      echo ""
      echo "Options:"
      echo "  --tmux                     Run with tmux (persistent sessions)"
      echo "  --version, -v              Show version"
      echo "  --help, -h                 Show this help"
      echo ""
      echo "Examples:"
      echo "  claunch                    Start direct Claude session (default)"
      echo "  claunch --tmux             Start tmux-based session"
      echo "  claunch list               List active sessions"
      exit 0
      ;;
    *)
      ARGS+=("$1")
      shift
      ;;
  esac
done

# Restore positional parameters
set -- "${ARGS[@]}"

# === Check for Claude CLI ===
# Check common locations for claude
CLAUDE_CMD=""
if command -v claude &> /dev/null; then
  CLAUDE_CMD="claude"
elif [ -f "$HOME/.claude/local/claude" ]; then
  CLAUDE_CMD="$HOME/.claude/local/claude"
elif [ -f "/usr/local/bin/claude" ]; then
  CLAUDE_CMD="/usr/local/bin/claude"
else
  echo "❌ Claude CLI not found. Please install it first."
  echo "   Visit: https://docs.anthropic.com/en/docs/claude-code" 
  exit 1
fi

# === Function to create tmux config ===
setup_tmux_config() {
  local config_file="$1"
  cat > "$config_file" << 'EOF'
# Enable mouse support
set -g mouse on

# Fix mouse cursor display issues - comprehensive settings
set -ga terminal-overrides ',*:Ss=\E[%p1%d q:Se=\E[2 q'
set -ga terminal-overrides ',xterm*:Cr=\E]12;gray\007'
set -ga terminal-overrides ',screen*:Cr=\E]12;gray\007'
set -ga terminal-overrides ',tmux*:Cr=\E]12;gray\007'

# Additional cursor fixes
set -ga terminal-overrides ',*:Cs=\E[4 q:Ce=\E[2 q'
set -as terminal-overrides ',*:Smulx=\E[4::%p1%dm'

# Set proper TERM and ensure 256 colors
set -g default-terminal "screen-256color"
set -ga terminal-features "*:RGB"

# Ensure proper cursor handling
set -g focus-events on
EOF
}

# === Handle commands ===
case "$1" in
  list)
    echo "📋 Active Claude sessions:"
    for session_file in ~/.claude_session_*; do
      if [[ -f "$session_file" ]]; then
        project=$(basename "$session_file" | sed 's/\.claude_session_//')
        session_id=$(cat "$session_file" 2>/dev/null || echo "(empty)")
        tmux_status=""
        if tmux has-session -t "claude-$project" 2>/dev/null; then
          tmux_status=" ✅ (tmux active)"
        else
          tmux_status=" ❌ (tmux inactive)"
        fi
        echo "  - $project: $session_id$tmux_status"
      fi
    done
    exit 0
    ;;
  clean)
    echo "🧹 Cleaning up old session files..."
    removed_count=0
    for session_file in ~/.claude_session_*; do
      if [[ -f "$session_file" ]]; then
        project=$(basename "$session_file" | sed 's/\.claude_session_//')
        if ! tmux has-session -t "claude-$project" 2>/dev/null; then
          echo "  Removing: $session_file"
          rm -f "$session_file"
          ((removed_count++))
        fi
      fi
    done
    echo "✅ Removed $removed_count session file(s)"
    exit 0
    ;;
esac

# === Show security warning on first use ===
if [[ ! -f ~/.claunch_warning_acknowledged ]]; then
  echo "⚠️  SECURITY WARNING"
  echo ""
  echo "claunch runs Claude with --dangerously-skip-permissions flag."
  echo "This gives Claude FULL access to your file system without confirmation."
  echo ""
  echo "Only use in trusted project directories."
  echo "Continue? (y/N): "
  read -r response
  if [[ "$response" != "y" && "$response" != "Y" ]]; then
    echo "Aborted."
    exit 1
  fi
  touch ~/.claunch_warning_acknowledged
  echo ""
fi

# === [0] check for tmux (only if needed) ===
if $USE_TMUX && ! command -v tmux &> /dev/null; then
  echo "🛠 tmux not found. Attempting to install..."

  if [[ "$OSTYPE" == "darwin"* ]]; then
    if command -v brew &> /dev/null; then
      echo "🍺 Installing tmux via Homebrew..."
      brew install tmux || { echo "❌ Failed to install via Homebrew"; exit 1; }
    else
      echo "❌ Homebrew is not installed. Please install brew first."
      echo "Official page: https://brew.sh/"
      exit 1
    fi

  elif [[ -f /etc/debian_version ]]; then
    echo "📦 Installing tmux via apt..."
    sudo apt update && sudo apt install -y tmux || { echo "❌ Failed to install via apt"; exit 1; }

  else
    echo "❌ Unable to auto-install tmux on your system."
    echo "Please install manually: https://github.com/tmux/tmux"
    exit 1
  fi
fi

# === [1] define project/session ===
PROJECT=$(basename "$PWD")
SESSION_FILE="$HOME/.claude_session_${PROJECT}"

# === [2] check for session file ===
if [ ! -f "$SESSION_FILE" ]; then
  echo "📁 Session ID file not found: $SESSION_FILE"
  echo "🆕 Starting new Claude session (using --dangerously-skip-permissions)"
  
  if $USE_TMUX; then
    echo "   Find the session ID in Claude's output and save it with:"
    echo ""
    echo "   echo \"sess-xxxxxxxx\" > $SESSION_FILE"
    echo ""

    # Create temporary tmux config for new sessions too
    TMUX_CONF=$(mktemp)
    setup_tmux_config "$TMUX_CONF"

    tmux -f "$TMUX_CONF" new-session -As "claude-$PROJECT" \
      "$CLAUDE_CMD code --dangerously-skip-permissions"
    
    rm -f "$TMUX_CONF"
  else
    echo "   Running Claude directly."
    echo "   Find the session ID in Claude's output and save it with:"
    echo ""
    echo "   echo \"sess-xxxxxxxx\" > $SESSION_FILE"
    echo ""
    exec "$CLAUDE_CMD" code --dangerously-skip-permissions
  fi
  exit 0
fi

# === [3] resume with session ID ===
SESSION_ID=$(cat "$SESSION_FILE" 2>/dev/null || echo "")

# Validate session ID format
if [[ -z "$SESSION_ID" ]]; then
  echo "❌ Session file is empty: $SESSION_FILE"
  echo "   Please save a valid session ID or delete the file to start fresh."
  exit 1
fi

if [[ ! "$SESSION_ID" =~ ^sess-[a-zA-Z0-9]+$ ]]; then
  echo "❌ Invalid session ID format: $SESSION_ID"
  echo "   Expected format: sess-xxxxxxxx"
  echo "   Delete $SESSION_FILE to start a new session."
  exit 1
fi

# === Start Claude with or without tmux ===
if $USE_TMUX; then
  # === Configure tmux for better mouse support ===
  # Create temporary tmux config
  TMUX_CONF=$(mktemp)
  setup_tmux_config "$TMUX_CONF"

  # Start tmux with custom config
  tmux -f "$TMUX_CONF" new-session -As "claude-$PROJECT" \
    "$CLAUDE_CMD code --resume $SESSION_ID --dangerously-skip-permissions"

  # Clean up temp file
  rm -f "$TMUX_CONF"
else
  echo "🔄 Resuming Claude session: $SESSION_ID"
  exec "$CLAUDE_CMD" code --resume "$SESSION_ID" --dangerously-skip-permissions
fi