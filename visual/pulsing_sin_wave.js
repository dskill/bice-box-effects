const sketch = function(p) {
    p.waveformData = []; // Initialize waveform data

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
    };

    p.draw = () => {
        
        p.background(0);

        // Check if waveform data is available
        if (p.waveformData && p.waveformData.length > 0) {
            p.stroke(0, 255, 0);
            p.noFill();
            p.beginShape();

            // log the waveform data
            //console.log("P5: ", p.waveformData[0]);

            // Use the waveform data for visualization
            for (let i = 0; i < p.waveformData.length; i++) {
                let x = p.map(i, 0, p.waveformData.length, 0, p.width);
                let y = p.height / 2 + p.waveformData[i] * p.height / 2;
                p.vertex(x, y);
            }

            p.endShape();
        }
    };

    p.windowResized = () => {
        p.resizeCanvas(p.windowWidth, p.windowHeight);
    };
};

module.exports = sketch;