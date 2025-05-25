// Radial FFT Spiral Visualizer GLSL Shader
// Displays FFT data as a spiral of 12 semitones per octave.

#define PI 3.14159265359
#define SAMPLERATE 48000.0
#define FFT_ANALYSIS_SIZE_SC 2048.0 // FFT size used in SuperCollider for analysis
#define NUM_FFT_BINS_IN_TEXTURE 512.0 // Number of FFT bins available in the iChannel0 texture

// Musical constants for frequency calculations
#define A4_FREQ 440.0
#define A4_MIDI_NOTE 69.0
#define NUM_SEMITONES 12.0

// Display range configuration (approximately E1 to D#7, covering about 6 octaves)
#define START_MIDI_NOTE_DISPLAY 28.0 // MIDI note for E1 (lowest note to display)
#define NUM_OCTAVES_DISPLAY 6.0      // Number of octaves to display from the start note

// Colors for visualization (matches JS example)
const vec3 DARK_CYAN = vec3(0.0, 0.545, 0.545);    // #008B8B
const vec3 BRIGHT_PURPLE = vec3(0.725, 0.404, 1.0); // #B967FF

// Helper function to convert MIDI note number to frequency in Hz
float midiToFreq(float midiNote) {
    return A4_FREQ * pow(2.0, (midiNote - A4_MIDI_NOTE) / NUM_SEMITONES);
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    // Normalize coordinates: (0,0) at center, y-axis preserves aspect ratio, range approx -0.5*aspect to 0.5*aspect
    vec2 uv = (fragCoord.xy - 0.5 * iResolution.xy) / iResolution.y;
    vec3 finalColor = vec3(0.0); // Initialize final pixel color

    // Visualization parameters
    float max_visual_radius = 0.45; // Maximum radius of the spiral on screen (screen units)
    float min_visual_radius = 0.05; // Minimum radius (center of the spiral)

    // Calculate the frequency represented by each bin step in the SuperCollider FFT analysis
    float sc_bin_freq_step = SAMPLERATE / FFT_ANALYSIS_SIZE_SC;

    // Loop through the specified number of octaves
    for (float octave_index = 0.0; octave_index < NUM_OCTAVES_DISPLAY; octave_index += 1.0) {
        // Loop through the 12 semitones in the current octave
        for (float semitone_index = 0.0; semitone_index < NUM_SEMITONES; semitone_index += 1.0) {
            // Calculate the MIDI note number for the current semitone and octave
            float current_midi_note = START_MIDI_NOTE_DISPLAY + octave_index * NUM_SEMITONES + semitone_index;
            float target_freq_hz = midiToFreq(current_midi_note);

            // Determine which FFT bin from SuperCollider's analysis corresponds to this frequency
            float target_sc_bin_float = target_freq_hz / sc_bin_freq_step;

            // Check if this frequency falls within the range covered by our texture (0 to NUM_FFT_BINS_IN_TEXTURE-1)
            if (target_sc_bin_float < 0.0 || target_sc_bin_float >= NUM_FFT_BINS_IN_TEXTURE) {
                continue; // This frequency is outside our available FFT data
            }
            
            // Normalized texture coordinate (u) to sample the FFT data in iChannel0
            // target_sc_bin_float is the conceptual index into the 512 bins
            float u_tex_coord = target_sc_bin_float / NUM_FFT_BINS_IN_TEXTURE;

            // Fetch the log10-scaled FFT magnitude from the texture (first row, y-coordinate approx 0.25)
            float log10_fft_magnitude = texture(iChannel0, vec2(u_tex_coord, 0.25)).x;

            // --- Calculate position for the dot on the spiral ---
            // Angle is determined by the semitone (0-11). 0 degrees = top (+Y).
            float angle_rad = (semitone_index / NUM_SEMITONES) * 2.0 * PI - (PI / 2.0);

            // Radius is based on the overall progression through total semitones displayed
            float total_semitones_from_display_start = octave_index * NUM_SEMITONES + semitone_index;
            float radius_normalized_overall = total_semitones_from_display_start / (NUM_OCTAVES_DISPLAY * NUM_SEMITONES -1.0); // 0-1 over all displayed points
            float radius = mix(min_visual_radius, max_visual_radius, radius_normalized_overall);
            
            vec2 dot_center_pos = vec2(cos(angle_rad) * radius, sin(angle_rad) * radius);

            // --- Determine color and size of the dot based on FFT magnitude ---
            // Normalize the log10_fft_magnitude (typically -2 to +1) to a 0-1 range for intensity
            float normalized_intensity = clamp((log10_fft_magnitude + 2.0) / 3.0, 0.0, 1.0); // Maps approx [-2, 1] to [0, 1]
            
            vec3 dot_color = mix(DARK_CYAN, BRIGHT_PURPLE, normalized_intensity);
            
            // Size of the dot, increasing with intensity
            float dot_screen_size = 0.004 + normalized_intensity * 0.020;

            // Draw the dot using a smoothstep for anti-aliasing
            float distance_to_dot_center = length(uv - dot_center_pos);
            float dot_sdf = smoothstep(dot_screen_size, dot_screen_size - 0.0025, distance_to_dot_center); // Soft falloff

            // Add the dot's color to the final pixel color, modulated by its intensity
            finalColor += dot_sdf * dot_color * (0.3 + normalized_intensity * 0.7);
        }
    }

    // Apply a dark background, allowing existing color to show through
    finalColor = mix(vec3(0.02, 0.02, 0.03), finalColor, clamp(length(finalColor) * 1.5, 0.0, 1.0));

    fragColor = vec4(finalColor, 1.0);
} 