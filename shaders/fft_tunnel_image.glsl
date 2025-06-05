// resolution: 1.0
precision highp float;

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
const vec3 TUNNEL_GLOW = vec3(0.2, 0.6, 1.2);

const float TANH_AMOUNT = .3;

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

// Generate fresh FFT radial lines for overlay
float generateFreshFFT(vec2 uv) {
    float min_spiral_radius = 0.05;
    float max_spiral_radius = 0.45;

    float pixel_angle = atan(uv.y, uv.x);
    float pixel_radius = length(uv);

    float t_radius_component = clamp((pixel_radius - min_spiral_radius) / (max_spiral_radius - min_spiral_radius), 0.0, 1.0);
    float t_angle_component = (mod(pixel_angle + PI * 0.5 + PI * 2.0, PI * 2.0)) / (PI * 2.0);

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
        float displacement_scale = 0.04; // Slightly more displacement for fresh overlay
        float perturbed_radius = ideal_radius_at_t + (fft_intensity - 0.5) * 2.0 * displacement_scale;

        float line_thickness = 0.004 + fft_intensity * 0.008; // Thinner lines for overlay
        float radial_distance_to_line = abs(pixel_radius - perturbed_radius);
        float line_sdf_value = smoothstep(line_thickness, line_thickness * 0.2, radial_distance_to_line);

        if (line_sdf_value > 0.0) {
            return line_sdf_value * fft_intensity;
        }
    }
    
    return 0.0;
}

void mainImage( out vec4 fragColor, in vec2 fragCoord ) {
    vec2 uv = fragCoord.xy / iResolution.xy;
    
    // Sample the accumulated buffer
    vec3 buffer = texture(iChannel0, uv).rgb;
    buffer = clamp(tanh(buffer * TANH_AMOUNT), 0.0, 1.0);

    
    // Generate fresh FFT overlay
    vec2 uv_center_offset = fragCoord.xy - 0.5 * iResolution.xy;
    vec2 uv_centered = uv_center_offset / iResolution.y;
    
    float fresh_fft = generateFreshFFT(uv_centered);
    
    // Create bright overlay for fresh FFT
    vec3 fresh_color = mix(BRIGHT_PURPLE, TUNNEL_GLOW, fresh_fft) * fresh_fft * 1.5;
    
    // Add tunnel depth effect based on distance from center
    float center_distance = length(uv_centered);
    float depth_glow = exp(-center_distance * 2.0) * .3;
    vec3 depth_color = TUNNEL_GLOW * depth_glow;
    
    // Combine buffer, fresh overlay, and depth glow
    vec3 final_color = buffer * .1 + fresh_color + depth_color;
    
    // Add chromatic aberration for tunnel effect
    float aberration_strength = center_distance * 0.1;
    vec2 red_offset = vec2(aberration_strength, 0.0);
    vec2 blue_offset = vec2(-aberration_strength, 0.0);
    
    float red_channel = texture(iChannel0, uv + red_offset).r;
    red_channel = clamp(tanh(red_channel * TANH_AMOUNT), 0.0, 1.0);
    float blue_channel = texture(iChannel0, uv + blue_offset).b;
    blue_channel = clamp(tanh(blue_channel * TANH_AMOUNT), 0.0, 1.0);
    
    final_color.r = mix(final_color.r, red_channel, 0.3);
    final_color.b = mix(final_color.b, blue_channel, 0.3);
    
    // Final contrast and brightness adjustment
    final_color = pow(final_color, vec3(0.9)); // Slight contrast boost
    
    fragColor = vec4(final_color, 1.0);
} 