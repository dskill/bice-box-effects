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
        p.background(0, 0, 0, 255);

        // Draw RMS indicator rectangles
        drawRMSIndicators();

        // Draw 6 waveforms representing guitar strings
        drawGuitarStrings();

        // Add debug text for tuner data
        //drawTunerDebugText();
    };

    const drawGuitarStrings = () => {
        if (!p.tunerData || !p.waveform1 || p.waveform1.length === 0) return;

        const stringSpacing = p.height / 7; // Divide height into 7 parts for 6 strings
        const stringNames = ['E', 'A', 'D', 'G', 'B', 'e']; // Standard guitar tuning

        for (let i = 0; i < 6; i++) {
            const yOffset = (i + 1) * stringSpacing;
            const amplitude = p.tunerData.amplitudes[i] || 0;
            const difference = p.tunerData.differences[i] || 0;
            difference_01 = calculateBrightness(difference);

            // Draw string label and info
            drawStringInfo(stringNames[i], amplitude, difference, yOffset);

            // Draw waveform
            drawWaveform(p.waveform1, difference_01, yOffset, amplitude);
        }
    };

    const calculateBrightness = (difference) => {
        const maxDifference = 50;
        const t = p.constrain(Math.abs(difference) / maxDifference, 0, 1);
        return p.lerp(1, 0, t);
    };

    const drawStringInfo = (stringName, amplitude, difference, yOffset) => {
        p.fill(255);
        p.textSize(14);
        p.textAlign(p.LEFT, p.BOTTOM);
        const info = `${stringName}: Amp ${amplitude.toFixed(4)}, Diff ${difference.toFixed(2)} cents`;
        p.text(info, 10, yOffset - 5);
    };

    const drawWaveform = (waveform, difference_01, yOffset, amplitude) => {
        if (waveform && waveform.length > 0) {
            p.stroke( difference_01 * 100 + 10);
            p.strokeWeight(1)
            //p.strokeWeight(1 + amplitude * 10); // Adjust stroke weight based on amplitude
            p.noFill();
            p.beginShape();

            for (let i = 0; i < waveform.length; i++) {
                let x = p.map(i, 0, waveform.length, 0, p.width);
                let y = yOffset + waveform[i] * p.height / 16 * difference_01;
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
        if (!p.tunerData || 
            !p.tunerData.freq || 
            p.tunerData.hasFreq === undefined || 
            !p.tunerData.differences || 
            !p.tunerData.amplitudes) {
            p.fill(255);
            p.textSize(24);
            p.textAlign(p.CENTER, p.CENTER);
            p.text("No signal", p.width / 2, p.height / 2);
            return;
        }

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
    
    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;