// resolution: 1.0

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
        // FFT data in iAudioTexture is already log10 scaled, typically in a range like -2 to 1
        fft_mag_log10 = texture(iAudioTexture, vec2(u_tex_coord, 0.25)).x;
    }
    // Normalize the log10 magnitude (e.g., -2 to 1) to a 0-1 range
    return clamp((fft_mag_log10), 0.0, 1.0);
}

float generateRadialFFT(vec2 uv) {
    float min_spiral_radius = 0.05; // Visual minimum radius of the spiral
    float max_spiral_radius = 0.45; // Visual maximum radius of the spiral

    float pixel_angle = atan(uv.y, uv.x); // -PI to PI
    float pixel_radius = length(uv);

    // Map pixel coordinates to spiral parameter t_spiral (0 to 1)
    float t_radius_component = clamp((pixel_radius - min_spiral_radius) / (max_spiral_radius - min_spiral_radius), 0.0, 1.0);
    float t_angle_component = (mod(pixel_angle + PI * 0.5 + PI * 2.0, PI * 2.0)) / (PI * 2.0);
    float t_spiral = t_radius_component + (t_angle_component / NUM_OCTAVES_DISPLAY);

    // Find best t_spiral for current pixel
    float estimated_t = -1.0;
    float smallest_dist_sq = 1e10;

    for(float octave = 0.0; octave < NUM_OCTAVES_DISPLAY; octave += 1.0) {
        float angle_offset_for_octave = octave * 2.0 * PI;
        float target_total_angle = mod(pixel_angle + PI*0.5 + PI*2.0, PI*2.0) + angle_offset_for_octave;
        
        float t_candidate = target_total_angle / (NUM_OCTAVES_DISPLAY * 2.0 * PI);
        t_candidate = clamp(t_candidate, 0.0, 1.0);

        float ideal_radius_for_t_candidate = mix(min_spiral_radius, max_spiral_radius, t_candidate);
        float dist_sq = pow(pixel_radius - ideal_radius_for_t_candidate, 2.0);
        
        if(dist_sq < smallest_dist_sq) {
            smallest_dist_sq = dist_sq;
            estimated_t = t_candidate;
        }
    }
    
    if (estimated_t >= 0.0) {
        float current_midi_note = START_MIDI_NOTE_DISPLAY + estimated_t * (NUM_OCTAVES_DISPLAY * NUM_SEMITONES - 1.0);
        float sc_bin_freq_step = SAMPLERATE / FFT_ANALYSIS_SIZE_SC;
        float fft_intensity = getNormalizedFFTForMidiNote(current_midi_note, sc_bin_freq_step);

        float ideal_radius_at_t = mix(min_spiral_radius, max_spiral_radius, estimated_t);
        float displacement_scale = 0.03;
        float perturbed_radius = ideal_radius_at_t + (fft_intensity - 0.5) * 2.0 * displacement_scale;

        float line_thickness = 0.006 + fft_intensity * 0.01;
        float radial_distance_to_line = abs(pixel_radius - perturbed_radius);
        float line_sdf_value = smoothstep(line_thickness, line_thickness * 0.3, radial_distance_to_line);

        if (line_sdf_value > 0.0) {
            return line_sdf_value * (0.7 + fft_intensity * 0.3);
        }
    }
    
    return 0.0;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    vec2 texel = 1.0 / iResolution.xy;
    texel *= 2.0;
    
    // Center coordinates for zoom effect
    vec2 center = vec2(0.5, 0.5);
    vec2 centered_uv = uv - center;
    
    // Zoom factor - slowly zoom into the center
    float zoom_speed = 0.997; // Slightly less than 1.0 for inward zoom
    vec2 zoomed_uv = centered_uv * zoom_speed + center;
    
    // Sample previous frame with zoom effect
    vec4 prev = texture(iChannel0, zoomed_uv);
    
    // Blur/diffusion sampling from neighbors
    vec4 prevUp = texture(iChannel0, zoomed_uv - vec2(0.0, texel.y * 2.0));
    vec4 prevDown = texture(iChannel0, zoomed_uv + vec2(0.0, texel.y * 2.0));
    vec4 prevLeft = texture(iChannel0, zoomed_uv - vec2(texel.x * 2.0, 0.0));
    vec4 prevRight = texture(iChannel0, zoomed_uv + vec2(texel.x * 2.0, 0.0));
    
    // Accumulation with blur
    vec4 accumulation = (prevUp + prevDown + prevLeft + prevRight) * 0.0 + prev * .98;
    accumulation *= 0.995; // Slight decay to prevent infinite buildup
    
    // Generate new FFT radial content
    vec2 uv_center_offset = fragCoord.xy - 0.5 * iResolution.xy;
    vec2 uv_centered = uv_center_offset / iResolution.y;
    
    float fft_line = generateRadialFFT(uv_centered);
    
    // Color the FFT line
    vec3 fft_color = mix(DARK_CYAN, BRIGHT_PURPLE, fft_line) * fft_line;
    
    // Combine accumulation with new content
    vec3 final_color = accumulation.rgb + fft_color;
    
    // Add subtle vignette to enhance tunnel effect
    //vec2 vignette_uv = abs(uv - 0.5);
    //float vignette = 1.0 - smoothstep(0.3, 0.7, length(vignette_uv));
    //final_color *= (0.6 + 0.4 * vignette);

    // Apply tanh tone mapping to prevent overexposure
    
    fragColor = vec4(final_color, 1.0);
} 