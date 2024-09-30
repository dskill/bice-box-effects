#!/bin/bash

# Configuration
LOCAL_DIR="$HOME/bice-box-effects"
REMOTE_USER="patch"
REMOTE_HOST="192.168.50.252"
REMOTE_DIR="bice-box-effects"

# Ensure fswatch is installed
if ! command -v fswatch &> /dev/null; then
    echo "fswatch is not installed. Please install it using Homebrew:"
    echo "brew install fswatch"
    exit 1
fi

# Function to check remote directory
check_remote_dir() {
    if ! ssh "$REMOTE_USER@$REMOTE_HOST" "[ -d $REMOTE_DIR ]"; then
        echo "Error: Remote directory $REMOTE_DIR does not exist on $REMOTE_HOST."
        exit 1
    fi
}

# Function to sync changes
sync_changes() {
    rsync -avz --delete "$LOCAL_DIR/" "$REMOTE_USER@$REMOTE_HOST:$REMOTE_DIR"
}

# Check if LOCAL_DIR exists
if [ ! -d "$LOCAL_DIR" ]; then
    echo "Error: Local directory $LOCAL_DIR does not exist."
    exit 1
fi

# Check if REMOTE_DIR exists
check_remote_dir

# Initial sync
echo "Performing initial sync..."
sync_changes

# Monitor and sync changes
echo "Monitoring $LOCAL_DIR for changes..."
fswatch -o "$LOCAL_DIR" | while read f; do
    echo "Change detected, syncing..."
    sync_changes
done