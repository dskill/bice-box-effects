const sketch = function(p) {
    p.waveform0 = []; // Initialize waveform data for channel 0
    p.waveform1 = []; // Initialize waveform data for channel 1
    let decayingWaveform = []; // Local array for decaying waveform
    const decayFactor = 0.95; // Decay factor for exponential decay
    // Add RMS properties
    p.rmsInput = 0;
    p.rmsOutput = 0;
    p.fft1 = [];

    p.colors = {
        cyan: '#00FFFF',
        neonPink: '#FF71CE',
        brightBlue: '#01CDFE',
        hotMagenta: '#FF00FF',
        neonGreen: '#05FFA1',
        brightPurple: '#B967FF',
        darkGray: '#444444',
        darkCyan: '#008B8B',
        darkNeonPink: '#FF1493'
      };
      

    // Add FPS variables
    let fps;
    let fpsArray = [];
    const fpsArraySize = 10;

    const hexToHSB = (hex) => {
        // Remove # if present
        hex = hex.replace(/^#/, '');
        
        // Convert to RGB
        const r = parseInt(hex.slice(0, 2), 16) / 255;
        const g = parseInt(hex.slice(2, 4), 16) / 255;
        const b = parseInt(hex.slice(4, 6), 16) / 255;
        
        const max = Math.max(r, g, b);
        const min = Math.min(r, g, b);
        const delta = max - min;
        
        // Calculate HSB
        let h = 0;
        if (delta === 0) {
            h = 0;
        } else if (max === r) {
            h = 60 * (((g - b) / delta) % 6);
        } else if (max === g) {
            h = 60 * ((b - r) / delta + 2);
        } else {
            h = 60 * ((r - g) / delta + 4);
        }
        
        // Make sure hue is positive
        h = (h + 360) % 360;
        
        const s = max === 0 ? 0 : (delta / max) * 100;
        const v = max * 100;
        
        return { h, s, v };
    };

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
        p.background(0);
        p.angleMode(p.DEGREES); // Use degrees for angle calculations
        // Initialize fps
        fps = p.createP('');
        fps.style('color', p.colors.darkGray); 
        fps.style('font-family', '-apple-system, BlinkMacSystemFont, "Helvetica Neue", Helvetica, Arial, sans-serif');
        fps.style('font-size', '10px');
        fps.style('position', 'fixed');
        fps.style('bottom', '3px');
        fps.style('left', '3px');
        fps.style('margin', '0');
        p.colorMode(p.HSB, 360, 100, 100, 100);
        p.frameRate(60);
        
        // Convert hex colors directly to p5.js color objects
        for (const [key, value] of Object.entries(p.colors)) {
            p.colors[key] = p.color(value);
        }
    }; 

    p.draw = () => {
        p.background(0,0,0,255); 
        p.push();
        p.blendMode(p.ADD);

        // Draw RMS indicator rectangles
        drawRMSIndicators();

        // Draw waveform0 in white at the top with RMS
        drawWaveform(p.waveform0, p.colors['brightBlue'], -p.height / 4, 1, p.rmsInput);
 
        // Draw waveform1 in blue in the middle with RMS
        drawWaveform(p.waveform1, p.colors['neonPink'], p.height / 4, 1, p.rmsOutput);

        // Draw FFT as concentric circles
        drawFFTCircles(p.fft1);

        // Update and draw decaying waveform in red at the bottom
        updateDecayingWaveform();
        drawWaveform(decayingWaveform, p.colors['darkCyan'], p.height / 4, 1, p.rmsOutput);
        drawWaveform(decayingWaveform,  p.colors['darkCyan'], p.height / 4, -1, p.rmsOutput);

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

    const drawFFTCircles = (fftData) => {
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

            for (let bin = 0; bin < fftSize; bin++) {
                const freq = (bin / fftSize) * nyquist;
                if (freq >= minFreq && freq <= maxFreq) {
                    const real = fftData[2 * bin];
                    const imag = fftData[2 * bin + 1];
                    
                    let magnitude = Math.sqrt(real * real + imag * imag);
                    
                    // Calculate color based on magnitude
                    const finalColor = p.lerpColor(
                        p.colors['darkCyan'],  // Cool color (blue) for low intensity
                        p.colors['brightPurple'],  // Warm color (red) for high intensity
                        p.constrain(magnitude*.05, 0, 1)  // Map magnitude to 0-1 range
                    );
                    
                    p.fill(finalColor);

                    // Calculate octave and position within octave
                    const logFreq = Math.log2(freq) - minLog;
                    const octave = Math.floor(logFreq);
                    const octaveFraction = logFreq - octave;

                    // Calculate angle (0 degrees is at the top, moving clockwise)
                    const angle = 270 + (octaveFraction * 360); // This is already in degrees

                    // Calculate radius (inner octaves have smaller radius)
                    // let radius = p.map(octave, 0, totalOctaves, maxRadius * 0.2, maxRadius);
                    let radius = p.map(logFreq, 0, maxLog, maxRadius * 0.0, maxRadius );
                    // Calculate circle size based on magnitude
                    //magnitude = 1;
                    const circleSize =(magnitude*.04 + .1) * (maxRadius / 40);
                    
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
        fps.html('FPS: ' + averageFPS.toFixed(2));
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;