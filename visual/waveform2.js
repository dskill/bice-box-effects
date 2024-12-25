const sketch = function(p) {
    p.waveform0 = []; // Initialize waveform data for channel 0
    p.waveform1 = []; // Initialize waveform data for channel 1
    let decayingWaveform = []; // Local array for decaying waveform
    const decayFactor = 0.95; // Decay factor for exponential decay
    // Add RMS properties
    p.rmsInput = 0;
    p.rmsOutput = 0;
    p.fft1 = [];

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
        p.background(0);
        p.colorMode(p.HSB, 360, 100, 100, 100);

    };

    p.draw = () => {
        p.background(0,0,0,100);
        p.push();
        p.blendMode(p.ADD);

        // Draw RMS indicator rectangles
        drawRMSIndicators();

        // Draw waveform0 in white at the top with RMS
        drawWaveform(p.waveform0, p.color(0, 0, 0), -p.height / 4, 1, p.rmsInput);

        // Draw waveform1 in blue in the middle with RMS
        drawWaveform(p.waveform1, p.color(100, 0, 0), p.height / 4, 1, p.rmsOutput);

        // Update and draw decaying waveform in red at the bottom
        updateDecayingWaveform();
        drawWaveform(decayingWaveform, p.color(0, 0, 0), p.height / 4, 1, p.rmsOutput);
        drawWaveform(decayingWaveform, p.color(0, 0, 0), p.height / 4, -1, p.rmsOutput);

        // Draw FFT as concentric circles
        drawFFTCircles(p.fft1, p.color(20,100,10));

        p.pop();
    };

    const drawWaveform = (waveform, color, yOffset, yMult, rms) => {
        if (waveform && waveform.length > 0) {
            // Get the hue of the original color
            let hue = p.hue(color) + rms * 1000 + p.frameCount;
            // Ensure hue stays within 0-360 range using modulo
            hue = hue % 360;
            let scaledColor = p.color(hue, 100, rms * 100 + 50);
            let finalColor = scaledColor;
           // color.setRed(rms);
            p.stroke(finalColor);
            //p.strokeWeight(1.0 + Math.max(rms, 0.002) * 10.0); // Adjust stroke weight based on RMS
            p.noFill();
            p.beginShape();

            for (let i = 0; i < waveform.length; i++) {
                let x = p.map(i, 0, waveform.length, 0, p.width);
                let y = p.height / 2 + yOffset + waveform[i] * p.height / 8 * yMult;
                p.vertex(x, y);
            }

            p.endShape();
        }
    };

    const updateDecayingWaveform = () => {
        // Update decayingWaveform with new data from waveform0
        if (p.waveform1.length > 0) {
            for (let i = 0; i < p.waveform1.length; i++) {
                // If decayingWaveform[i] exists, decay it and take the max with the new value
                // Otherwise, just use the new value
                decayingWaveform[i] = decayingWaveform[i] 
                    ? -1.0 * Math.max( Math.abs(decayingWaveform[i]) * decayFactor, Math.abs(p.waveform1[i]))
                    : Math.abs(p.waveform1[i]);
            }
        }
    };

    const drawRMSIndicators = () => {
        const rectHeight = p.height * 0.5; // 5% of the canvas height
        const maxWidth = p.width;

        // Set fill color to 20% opaque gray
        p.fill(100, 20, 40, 20); // 51 is 20% of 255
        p.noStroke();

        // Draw input RMS indicator
        const inputRectWidth = p.map(p.rmsInput, 0, 1, 0, maxWidth);
        p.rect(0, p.height - rectHeight * 2, inputRectWidth, rectHeight);

        // Draw output RMS indicator
        const outputRectWidth = p.map(p.rmsOutput, 0, 1, 0, maxWidth);
        p.rect(0, p.height - rectHeight, outputRectWidth, rectHeight);
    };

    const drawFFTCircles = (fftData, color) => {
        if (fftData && fftData.length > 0) {
            p.push();
            p.translate(p.width / 2, p.height / 2);
            p.noFill();
            p.stroke(color);

            const maxRadius = Math.min(p.width, p.height) * 0.4;
            const fftSize = fftData.length / 2; // Since fftData contains both real and imaginary parts
            const sampleRate = 48000; // Adjust this to match your actual sample rate
            const nyquist = sampleRate / 2;
            const binFrequency = nyquist / fftSize;

            const minFreq = 20; // Lowest frequency to display (in Hz)
            const maxFreq = 20000; // Highest frequency to display (in Hz)
            const numOctaves = Math.log2(maxFreq / minFreq);

            for (let octave = 0; octave < numOctaves; octave++) {
                p.beginShape();
                for (let angle = 0; angle <= 360; angle+=1) {
                    const baseRadius = (octave + 1) * (maxRadius / numOctaves);
                    
                    // Calculate frequency for this angle and octave
                    const freq = minFreq * Math.pow(2, octave + angle / 360);
                    
                    // Find the corresponding FFT bin
                    const bin = Math.floor(freq / binFrequency);
                    
                    // Get the real and imaginary parts for this bin
                    const real = fftData[2 * bin];
                    const imag = fftData[2 * bin + 1];
                    
                    // Calculate the magnitude
                    let magnitude = Math.sqrt(real * real + imag * imag);
                    //p.stroke(p.color(magnitude,80,100,magnitude * .4 + 40));
                    magnitude = Math.pow(magnitude, 0.5);

                    // Calculate the deformed radius using a logarithmic scale for amplitude
                    const logAmplitude = magnitude > 0 ? Math.log10(magnitude + 1) : 0; // Adding 1 to avoid log(0)
                    const deformedRadius = baseRadius + magnitude * (maxRadius / numOctaves);
                    
                    const x = deformedRadius * p.cos(angle);
                    const y = deformedRadius * p.sin(angle);
                    p.vertex(x, y);
                }
                p.endShape(p.CLOSE);
            }
            p.pop();
        }
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;