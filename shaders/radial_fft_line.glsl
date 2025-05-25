// Radial FFT Line Visualizer GLSL Shader
// Displays FFT data as a single spiraling line,
// where the line's displacement is modulated by FFT magnitude.

#define PI 3.14159265359
#define SAMPLERATE 48000.0
#define FFT_ANALYSIS_SIZE_SC 2048.0
#define NUM_FFT_BINS_IN_TEXTURE 512.0

#define A4_FREQ 440.0
#define A4_MIDI_NOTE 69.0
#define NUM_SEMITONES 12.0

#define START_MIDI_NOTE_DISPLAY 28.0 // E1
#define NUM_OCTAVES_DISPLAY 6.0 // Total octaves the spiral will represent

// Colors
const vec3 DARK_CYAN = vec3(0.0, 0.145, 0.545);
const vec3 BRIGHT_PURPLE = vec3(1.025, 0.704, 1.0);
const vec3 BACKGROUND_COLOR = vec3(0.02, 0.02, 0.03);

float midiToFreq(float midiNote) {
    return A4_FREQ * pow(2.0, (midiNote - A4_MIDI_NOTE) / NUM_SEMITONES);
}

// Helper to get normalized (0-1) FFT magnitude for a given MIDI note
float getNormalizedFFTForMidiNote(float midiNote, float sc_bin_freq_step) {
    float freq = midiToFreq(midiNote);
    float target_sc_bin_float = freq / sc_bin_freq_step;
    float fft_mag_log10 = 0.0; // Default to silence

    if (target_sc_bin_float >= 0.0 && target_sc_bin_float < NUM_FFT_BINS_IN_TEXTURE) {
        float u_tex_coord = target_sc_bin_float / NUM_FFT_BINS_IN_TEXTURE;
        // FFT data in iChannel0 is already log10 scaled, typically in a range like -2 to 1
        fft_mag_log10 = texture(iChannel0, vec2(u_tex_coord, 0.25)).x;
    }
    // Normalize the log10 magnitude (e.g., -2 to 1) to a 0-1 range
    return clamp((fft_mag_log10), 0.0, 1.0);
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv_center_offset = fragCoord.xy - 0.5 * iResolution.xy;
    // Use iResolution.y for normalization to keep aspect ratio correct for radial calculations
    vec2 uv = uv_center_offset / iResolution.y;

    vec3 finalPixelColor = vec3(0.0);

    float min_spiral_radius = 0.05; // Visual minimum radius of the spiral
    float max_spiral_radius = 0.45; // Visual maximum radius of the spiral

    float pixel_angle = atan(uv.y, uv.x); // -PI to PI
    float pixel_radius = length(uv);

    // --- Map pixel coordinates (uv) to a single parameter `t_spiral` (0 to 1) ---
    // `t_spiral` represents normalized progress along the entire spiral (all octaves).
    // This mapping determines how frequencies are laid out.

    // A simple approach: `t_spiral` determined by radius, angle fine-tunes within an octave.
    // Normalize pixel_radius to a [0,1] range based on min/max spiral radius.
    float t_radius_component = clamp((pixel_radius - min_spiral_radius) / (max_spiral_radius - min_spiral_radius), 0.0, 1.0);
    
    // Normalize angle to [0,1] for one rotation. Let spiral start at +Y axis (top).
    float t_angle_component = (mod(pixel_angle + PI * 0.5 + PI * 2.0, PI * 2.0)) / (PI * 2.0); // 0 at top, progresses clockwise

    // Combine: t_radius primarily dictates the octave. t_angle dictates note within that octave span.
    // Each octave segment of the radius gets one full t_angle sweep.
    float t_spiral = t_radius_component + (t_angle_component / NUM_OCTAVES_DISPLAY);
    // Normalize t_spiral to ensure it effectively stays within [0,1] range that maps to total notes.
    // This mapping is crucial and might need tweaking for good visual distribution.
    // A more robust way for an Archimedean spiral (r = a*theta_total):
    // theta_total = t_spiral * NUM_OCTAVES_DISPLAY * 2.0 * PI.
    // r_ideal = mix(min_spiral_radius, max_spiral_radius, t_spiral);
    // We need to find `t_spiral` such that `pixel_angle` and `pixel_radius` are "on" this conceptual spiral.

    // Let's iterate a small number of turns to find the best `t_spiral` for the current pixel.
    // This is a compromise between full SDF and the many-dot approach.
    float estimated_t = -1.0;
    float smallest_dist_sq = 1e10;

    for(float octave = 0.0; octave < NUM_OCTAVES_DISPLAY; octave += 1.0) {
        // Calculate a `t` value if the current pixel lines up with this octave's turn at `pixel_angle`.
        float angle_offset_for_octave = octave * 2.0 * PI;
        // Target total angle for this crossing:
        float target_total_angle = mod(pixel_angle + PI*0.5 + PI*2.0, PI*2.0) + angle_offset_for_octave;
        
        float t_candidate = target_total_angle / (NUM_OCTAVES_DISPLAY * 2.0 * PI);
        t_candidate = clamp(t_candidate, 0.0, 1.0); // Ensure t_candidate is valid

        float ideal_radius_for_t_candidate = mix(min_spiral_radius, max_spiral_radius, t_candidate);
        
        float dist_sq = pow(pixel_radius - ideal_radius_for_t_candidate, 2.0);
        // Could also add an angular distance component if pixel_angle doesn't perfectly match,
        // but for a dense spiral, radial distance is key.
        
        if(dist_sq < smallest_dist_sq) {
            smallest_dist_sq = dist_sq;
            estimated_t = t_candidate;
        }
    }
    
    // --- End mapping ---
    
    if (estimated_t >= 0.0) {
        float current_midi_note = START_MIDI_NOTE_DISPLAY + estimated_t * (NUM_OCTAVES_DISPLAY * NUM_SEMITONES - 1.0);
        float sc_bin_freq_step = SAMPLERATE / FFT_ANALYSIS_SIZE_SC;
        float fft_intensity = getNormalizedFFTForMidiNote(current_midi_note, sc_bin_freq_step);

        // Define the ideal (unperturbed) radius of the spiral at `estimated_t`
        float ideal_radius_at_t = mix(min_spiral_radius, max_spiral_radius, estimated_t);

        // Perturb the radius based on FFT intensity
        float displacement_scale = 0.03; // How much the FFT displaces the line
        float perturbed_radius = ideal_radius_at_t + (fft_intensity - 0.5) * 2.0 * displacement_scale;
                                         // (fft_intensity - 0.5)*2 makes range -1 to 1 from 0 to 1

        float line_thickness = 0.006 + fft_intensity * 0.01; // Line gets thicker with more intensity
        
        // Calculate distance from pixel's radius to the perturbed spiral radius
        float radial_distance_to_line = abs(pixel_radius - perturbed_radius);

        // Draw the line using smoothstep
        float line_sdf_value = smoothstep(line_thickness, line_thickness * 0.3, radial_distance_to_line);

        if (line_sdf_value > 0.0) {
            vec3 base_color = mix(DARK_CYAN, BRIGHT_PURPLE, fft_intensity);
            // Modulate brightness also by closeness to center of the line
            finalPixelColor = base_color * line_sdf_value * (0.7 + fft_intensity * 0.3) ;
        }
    }

    finalPixelColor = mix(BACKGROUND_COLOR, finalPixelColor, clamp(length(finalPixelColor) / length(vec3(0.7)), 0.0, 1.0) ); // Blend with bg
    fragColor = vec4(finalPixelColor, 1.0);
} 