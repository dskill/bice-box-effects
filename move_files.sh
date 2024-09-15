#!/bin/bash

# Create directories if they don't exist
mkdir -p audio_effect visual_effect

# Move .sc files to audio_effect
mv *.sc audio_effect/

# Move .js files to visual_effect
mv *.js visual_effect/

echo "Files have been moved successfully."