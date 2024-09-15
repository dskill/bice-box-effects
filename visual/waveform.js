const sketch = function(p) {
    p.waveform0 = []; // Initialize waveform data for channel 0
    p.waveform1 = []; // Initialize waveform data for channel 1
    let decayingWaveform = []; // Local array for decaying waveform
    const decayFactor = 0.95; // Decay factor for exponential decay

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
    };

    p.draw = () => {
        p.background(0);

        // Draw waveform0 in green
        drawWaveform(p.waveform0, p.color(0, 255, 0));

        // Draw waveform1 in blue
        drawWaveform(p.waveform1, p.color(0, 0, 255));

        // Update and draw decaying waveform in red
        updateDecayingWaveform();
        drawWaveform(decayingWaveform, p.color(255, 0, 0));
    };

    const drawWaveform = (waveform, color) => {
        if (waveform && waveform.length > 0) {
            p.stroke(color);
            p.noFill();
            p.beginShape();

            for (let i = 0; i < waveform.length; i++) {
                let x = p.map(i, 0, waveform.length, 0, p.width);
                let y = p.height / 2 + waveform[i] * p.height / 2;
                p.vertex(x, y);
            }

            p.endShape();
        }
    };

    const updateDecayingWaveform = () => {
        // Update decayingWaveform with new data from waveform0
        if (p.waveform1.length > 0) {
            for (let i = 0; i < p.waveform0.length; i++) {
                // If decayingWaveform[i] exists, decay it and take the max with the new value
                // Otherwise, just use the new value
                decayingWaveform[i] = decayingWaveform[i] 
                    ? Math.max(decayingWaveform[i] * decayFactor, Math.abs(p.waveform0[i]))
                    : Math.abs(p.waveform0[i]);
            }
        }
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;