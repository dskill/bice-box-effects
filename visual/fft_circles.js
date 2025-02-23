const sketch = function(p) {
    p.waveform0 = []; // Initialize waveform data for channel 0
    p.waveform1 = []; // Initialize waveform data for channel 1
    let decayingWaveform = []; // Local array for decaying waveform
    const decayFactor = 0.95; // Decay factor for exponential decay
    // Add RMS properties
    p.rmsInput = 0;
    p.rmsOutput = 0;
    p.fft1 = [];

    // Add FPS variables
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
        p.background(0);
        p.angleMode(p.DEGREES); // Use degrees for angle calculations
        // Initialize fps
        fps = p.createP('');
        fps.position(10, 10);
        fps.style('color', 'white');
        p.colorMode(p.HSB, 360, 100, 100, 100);
        p.frameRate(60);
    };

    p.draw = () => {
        p.background(0,0,0,255); 
        p.push();
        p.blendMode(p.ADD);

        // Draw RMS indicator rectangles
        drawRMSIndicators();

        // Draw waveform0 in white at the top with RMS
        drawWaveform(p.waveform0, p.color(255, 100, 100), -p.height / 4, 1, p.rmsInput);
 
        // Draw waveform1 in blue in the middle with RMS
        drawWaveform(p.waveform1, p.color(0, 100, 100), p.height / 4, 1, p.rmsOutput);

        // Draw FFT as concentric circles
        drawFFTCircles(p.fft1, p.color(255, 100, 100));

        // Update and draw decaying waveform in red at the bottom
        updateDecayingWaveform();
        drawWaveform(decayingWaveform, p.color(200, 40, 40), p.height / 4, 1, p.rmsOutput);
        drawWaveform(decayingWaveform, p.color(200, 40, 40), p.height / 4, -1, p.rmsOutput);

        //drawFFT(p.fft1, p.color(255, 0, 200));


        // remove additive blend mode
        p.pop();

        // Update FPS counter
        updateFPS();
        
    };

    const drawFFT = (fftData, color) => {
        if (fftData && fftData.length > 0) {
            p.push();
            p.stroke(color);
            p.fill(color);
            
            const fftSize = 200; // fftData.length / 2;
            for (let i = 0; i < fftSize; i++) {
                const real = fftData[2 * i];
                const imag = fftData[2 * i + 1];
                let magnitude = Math.sqrt(real * real + imag * imag);
                magnitude = Math.log(magnitude + 1) / Math.log(10); // Apply logarithmic scaling
                const x = p.map(i, 0, fftSize, 0, p.width);
                const y = p.map(magnitude, 0, 1, p.height, p.height - 100);
                
                // Draw a dot for each bin
                p.ellipse(x, y, 4, 4);
            }

            p.pop();
        }
    };

    const drawWaveform = (waveform, color, yOffset, yMult, rms) => {
        if (waveform && waveform.length > 0) {
            //color.setRed(rms * 1000);
            p.stroke(color);
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
        p.fill(128, 20, 10); // 51 is 20% of 255
        p.noStroke();

        // Draw input RMS indicator
        const inputRectWidth = p.map(p.rmsInput, 0, 1, 0, maxWidth);
        p.rect(0, p.height - rectHeight * 2, inputRectWidth, rectHeight);

        // Draw output RMS indicator
        const outputRectWidth = p.map(p.rmsOutput, 0, 1, 0, maxWidth);
        p.rect(0, p.height - rectHeight, outputRectWidth, rectHeight);
    };

    const drawFFTCircles = (fftData, baseColor) => {
        if (fftData && fftData.length > 0) {
            p.push();
            p.translate(p.width / 2, p.height / 2);
            p.noStroke();

            const maxRadius = Math.min(p.width, p.height) * 0.75;
            const fftSize = fftData.length / 2;
            const sampleRate = 48000;
            const nyquist = sampleRate / 2;

            const minFreq = 82.41/2.0; // Frequency of the low E string (E2) on a standard guitar
            const maxFreq = 2318.51; // Approximately the frequency of the highest E (E6) on a standard guitar
            const minLog = Math.log2(minFreq);
            const maxLog = Math.log2(maxFreq);
            const totalOctaves = Math.ceil(maxLog - minLog);

            for (let bin = 0; bin < fftSize; bin++) {
                const freq = (bin / fftSize) * nyquist;
                if (freq >= minFreq && freq <= maxFreq) {
                    const real = fftData[2 * bin];
                    const imag = fftData[2 * bin + 1];
                    
                    let magnitude = Math.sqrt(real * real + imag * imag);
                    //magnitude *= 0.02;
                    //magnitude = Math.log2(magnitude);
                    
                    // Calculate color based on magnitude
                    const intensityColor = p.lerpColor(
                        p.color(0, 100, 10),  // Cool color (blue) for low intensity
                        p.color(200, 80, 100),  // Warm color (red) for high intensity
                        p.constrain(magnitude*.05, 0, 1)  // Map magnitude to 0-1 range
                    );
                    
                    // Blend the intensity color with the base color
                    const finalColor = p.lerpColor(baseColor, intensityColor, 1.0);
                    p.fill(finalColor);

                    // Calculate octave and position within octave
                    const logFreq = Math.log2(freq) - minLog;
                    const octave = Math.floor(logFreq);
                    const octaveFraction = logFreq - octave;

                    // Calculate angle (0 degrees is at the top, moving clockwise)
                    const angle = 270 + (octaveFraction * 360); // This is already in degrees

                    // Calculate radius (inner octaves have smaller radius)
                    // let radius = p.map(octave, 0, totalOctaves, maxRadius * 0.2, maxRadius);
                    let radius = p.map(logFreq, 0, maxLog, maxRadius * 0.0, maxRadius * (p.rmsOutput + .5));
                    // Calculate circle size based on magnitude
                    //magnitude = 1;
                    const circleSize = Math.log2(magnitude * .5) * (maxRadius / 40);
                    
                    // Use p.cos and p.sin directly with the angle in degrees
                    const x = radius * p.cos(angle);
                    const y = radius * p.sin(angle);
                    
                    p.ellipse(x, y, circleSize);

                     // Calculate triangle points
                     /*
                    const x1 = radius * p.cos(angle);
                    const y1 = radius * p.sin(angle);
                    const x2 = (radius + circleSize) * p.cos(angle - 5);
                    const y2 = (radius + circleSize) * p.sin(angle - 5);
                    const x3 = (radius + circleSize) * p.cos(angle + 5);
                    const y3 = (radius + circleSize) * p.sin(angle + 5);
                    

                    // Draw triangle pointing out from the center
                    p.triangle(x1, y1, x2, y2, x3, y3);
                    */
                }
            }
            p.pop();
        }
    };

    const updateFPS = () => {
        fpsArray.push(p.frameRate());
        if (fpsArray.length > fpsArraySize) {
            fpsArray.shift();
        }
        const averageFPS = fpsArray.reduce((sum, value) => sum + value, 0) / fpsArray.length;
        fps.html('Avg FPS: ' + averageFPS.toFixed(2));
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;