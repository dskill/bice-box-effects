const sketch = function(p) {
    p.waveform0 = []; // Initialize waveform data for channel 0
    p.waveform1 = []; // Initialize waveform data for channel 1
    let decayingWaveform = []; // Local array for decaying waveform
    const decayFactor = 0.95; // Decay factor for exponential decay
    // Add RMS properties
    p.rmsInput = 0;
    p.rmsOutput = 0;

    /*
    p.tunerData = {
        freq: 0,
        hasFreq: false
    };*/

    // Add properties for tuner data
   // p.tunerData.freq = 0.0;
   // p.tunerData.hasFreq = false;
    //.difference = 0;

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
        p.background(0);
    };

    p.draw = () => {
        p.background(0,0,0,255);

        // Draw RMS indicator rectangles
        drawRMSIndicators();

        // Draw waveform0 in white at the top with RMS
        drawWaveform(p.waveform0, p.color(255, 100, 0), -p.height / 4, 1, p.rmsInput);

        // Draw waveform1 in blue in the middle with RMS
        drawWaveform(p.waveform1, p.color(0, 100, 255), p.height / 4, 1, p.rmsOutput);

        // Update and draw decaying waveform in red at the bottom
        updateDecayingWaveform();
        drawWaveform(decayingWaveform, p.color(100, 100, 0), p.height / 4, 1, p.rmsOutput);
        drawWaveform(decayingWaveform, p.color(100, 100, 0), p.height / 4, -1, p.rmsOutput);

        // Add debug text for tuner data
        drawTunerDebugText();
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
        p.fill(128, 128, 128, 15); // 51 is 20% of 255
        p.noStroke();

        // Draw input RMS indicator
        const inputRectWidth = p.map(p.rmsInput, 0, 1, 0, maxWidth);
        p.rect(0, p.height - rectHeight * 2, inputRectWidth, rectHeight);

        // Draw output RMS indicator
        const outputRectWidth = p.map(p.rmsOutput, 0, 1, 0, maxWidth);
        p.rect(0, p.height - rectHeight, outputRectWidth, rectHeight);
    };

    const drawTunerDebugText = () => {
        p.fill(255);
        p.textSize(16);
        p.textAlign(p.LEFT, p.TOP);
    
        let debugText = `Frequency: ${p.tunerData.freq.toFixed(2)} Hz\n`;
        debugText += `Has Frequency: ${p.tunerData.hasFreq}\n`;
        
        // Log 6 channels of differences
        debugText += "Differences:\n";
        for (let i = 0; i < 6; i++) {
            debugText += `  Channel ${i + 1}: ${p.tunerData.differences[i].toFixed(2)} cents\n`;
        }
        
        // Log 6 channels of amplitudes
        debugText += "Amplitudes:\n";
        for (let i = 0; i < 6; i++) {
            debugText += `  Channel ${i + 1}: ${p.tunerData.amplitudes[i].toFixed(4)}\n`;
        }
    
        p.text(debugText, 10, 10);
    };
    
    // ... existing code ...

    // Add a method to update tuner data
    p.updateTunerData = (data) => {
        p.freq = data.freq;
        p.hasFreq = data.hasFreq;
        p.difference = data.difference;
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;