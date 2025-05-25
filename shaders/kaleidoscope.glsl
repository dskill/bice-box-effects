#define PI 3.141592
#define orbs 20.

// uniform sampler2D iChannel0; // Audio texture: FFT (y ~ 0.25), Waveform (y ~ 0.75)
// uniform float iRMSOutput;    // Overall RMS amplitude of the audio output

// Helper function to get a combined FFT value
// Samples low, mid, and high frequency ranges from the FFT data in iChannel0
float getCombinedFFT(sampler2D channel) {
    float fft_low = texture(channel, vec2(0.1, 0.25)).x;  // Sample low frequencies
    float fft_mid = texture(channel, vec2(0.3, 0.25)).x;  // Sample mid frequencies
    float fft_high = texture(channel, vec2(0.6, 0.25)).x; // Sample high frequencies
    // Average them, potentially weight them if desired
    // The FFT data in iChannel0 is often log-scaled amplitude.
    // We can normalize/scale it further if needed.
    // For now, let's assume it's in a somewhat usable range (e.g. 0 to 1 after processing)
    return (fft_low + fft_mid + fft_high) / 3.0;
}




vec2 kale(vec2 uv, vec2 offset, float sides) {
  float angle = atan(uv.y, uv.x);
  angle = ((angle / PI) + 1.0) * 0.5;
  angle = mod(angle, 1.0 / sides) * sides;
  angle = -abs(2.0 * angle - 1.0) + 1.0;
  angle = angle;
  float y = length(uv);
  angle = angle * (y);
  return vec2(angle, y) - offset;
}

vec4 orb(vec2 uv, float size, vec2 position, vec3 color, float contrast) {
  return pow(vec4(size / length(uv + position) * color, 1.), vec4(contrast));
}

mat2 rotate(float angle) {
  return mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
}

void mainImage(out vec4 fragColor, in vec2 fragCoord) {
  vec2 basic_uv = (fragCoord.xy / iResolution.xy) - 0.5;
  vec2 polar_coords = vec2( (atan(basic_uv.y, basic_uv.x) + PI) / (2.0 * PI), length(basic_uv));
  vec2 uv = 23.09 * (2. * fragCoord - iResolution.xy) / iResolution.y;
  float dist = length(uv);
  fragColor = vec4(0.);

  uv.y += 10.0*(texture(iChannel0, vec2( abs(basic_uv.x - 0.5), 0.75)).x - 0.5) * 2.0; // Normalize to -1 to 1


  // Get audio reactivity values
  float combined_fft = getCombinedFFT(iChannel0); // Value typically 0.0 to 1.0 (approx)
  float rms_normalized = clamp(iRMSOutput * 2.0, 0.0, 1.0); // Normalize RMS, assuming iRMSOutput is ~0-0.5

  // Modulate rotation speeds with RMS
  float rotation_speed_factor1 = 1.0 + rms_normalized * 2.0; // Increase speed with RMS
  float rotation_speed_factor2 = 1.0 + rms_normalized * 1.5;
  uv *= rotate(iTime / (20.0 / rotation_speed_factor1) + rms_normalized * 0.05 * iTime);

  // Modulate kaleidoscope sides with FFT
  combined_fft = 1.0; 
  float kale_sides = 3.0 + combined_fft * 7.0; // Modulate between 3 and 10 sides
  uv = kale(uv, vec2(6.97 - combined_fft * 3.0), kale_sides); // Also modulate offset slightly

  uv *= rotate(iTime / (5.0 / rotation_speed_factor2) - rms_normalized * 0.03 * iTime);

  // Modulate displacement factors in the loop with RMS
  float displacement_amount = 0.4 + rms_normalized * 0.6; // Modulate base factors (0.57, 0.63)

  for (float i = 0.; i < orbs; i++) {
    uv.x += (0.57 * displacement_amount) * sin(0.3 * uv.y + iTime + rms_normalized * 2.0);
    uv.y -= (0.63 * displacement_amount) * cos(0.53 * uv.x + iTime - rms_normalized * 1.5);
    float t = i * PI / orbs * 2.;
    float x_pos_factor = 3.0 + combined_fft * 3.0; // Modulate orb positioning
    float y_pos_factor = 3.0 + combined_fft * 3.0;
    float x = x_pos_factor * tan(t + iTime / (10.0 - rms_normalized * 5.0));
    float y = y_pos_factor * cos(t - iTime / (30.0 + rms_normalized * 10.0));
    vec2 position = vec2(x, y);

    // Modulate orb color based on FFT and i (iteration)
    vec3 base_orb_color = cos(vec3(-2.0 + combined_fft * 1.5, 0.0, -1.0 - combined_fft*0.5) * PI * 2. / 3. + PI * (float(i) / (5.37 + combined_fft*2.0))) * 0.5 + 0.5;
    vec3 audio_color_tint = vec3(0.8 + combined_fft * 0.2, 0.8 + rms_normalized * 0.2, 0.8 + (combined_fft + rms_normalized) * 0.1);
    vec3 color = base_orb_color * audio_color_tint;

    // Modulate orb size and contrast with FFT
    float orb_base_size = 1.0 + combined_fft * 1.0; // Modulate base size (1.39)
    float orb_contrast = 1.0 + combined_fft * 0.7;   // Modulate base contrast (1.37)
    fragColor += orb(uv, 1.39 * orb_base_size, position, color, 1.37 * orb_contrast);
  }

  // Modulate final brightness/saturation with RMS
  fragColor.rgb *= (0.6 + rms_normalized * 0.7); // Ensure it doesn't blow out too much
  fragColor.rgb = mix(fragColor.rgb, fragColor.rgb * (0.5 + combined_fft * 0.8), 0.5); // Add some FFT based color boost
  fragColor.a = 1.0;
}