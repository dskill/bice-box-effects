const sketch = function(p) {
    p.waveform0 = []; // Initialize waveform data for channel 0
    p.waveform1 = []; // Initialize waveform data for channel 1
    let decayingWaveform = []; // Local array for decaying waveform
    const decayFactor = 0.95; // Decay factor for exponential decay
    // Add RMS properties
    p.rmsInput = 0;
    p.rmsOutput = 0;

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
        p.background(0);
    };

    p.draw = () => {
        p.background(0,0,0,100);

        // Draw waveform0 in white at the top with RMS
        drawWaveform(p.waveform0, p.color(255, 100, 0), -p.height / 4, 1, p.rmsInput);

        // Draw waveform1 in blue in the middle with RMS
        drawWaveform(p.waveform1, p.color(0, 100, 255), p.height / 4, 1, p.rmsOutput);

        // Update and draw decaying waveform in red at the bottom
        updateDecayingWaveform();
        drawWaveform(decayingWaveform, p.color(255, 0, 0), p.height / 4, 1, p.rmsOutput);
        drawWaveform(decayingWaveform, p.color(255, 0, 0), p.height / 4, -1, p.rmsOutput);

        // Draw the wiggling triangle
        drawWigglingTriangle(p.waveform1, p.color(255, 255, 0, 150)); // Yellow with some transparency
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

    const drawWigglingTriangle = (waveform, color) => {
        if (waveform && waveform.length > 0) {
            p.fill(color);
            p.noStroke();
            p.beginShape();

            // Top vertex of the triangle
            let topX = p.width / 2;
            let topY = p.height / 4;
            p.vertex(topX, topY);

            // Bottom-left vertex
            let leftX = p.width / 4;
            let leftY = p.height * 3 / 4;
            p.vertex(leftX, leftY);

            // Wiggle the bottom edge
            for (let i = 0; i < waveform.length; i++) {
                let x = p.map(i, 0, waveform.length, leftX, p.width * 3 / 4);
                let y = leftY + waveform[i] * p.height / 16; // Adjust the wiggle amplitude
                p.vertex(x, y);
            }

            // Bottom-right vertex
            let rightX = p.width * 3 / 4;
            let rightY = p.height * 3 / 4;
            p.vertex(rightX, rightY);

            p.endShape(p.CLOSE);
        }
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;