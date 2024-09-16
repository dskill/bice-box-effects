const sketch = function(p) {
    p.waveform0 = [];
    p.waveform1 = [];
    let particles = [];
    const numParticles = 10;
    const maxSpeed = 2;
    const trailLength = 20;

    p.setup = () => {
        p.createCanvas(p.windowWidth, p.windowHeight);
        p.background(0);
        for (let i = 0; i < numParticles; i++) {
            particles.push(new Particle());
        }
    };

    p.draw = () => {
        p.background(0,0,0);

        // Draw connection lines
        p.stroke(255, 30);
        p.strokeWeight(0.5);
        for (let i = 0; i < particles.length; i++) {
            for (let j = i + 1; j < particles.length; j++) {
                let d = p.dist(particles[i].pos.x, particles[i].pos.y, particles[j].pos.x, particles[j].pos.y);
                if (d < 100) {
                    p.line(particles[i].pos.x, particles[i].pos.y, particles[j].pos.x, particles[j].pos.y);
                }
            }
        }

        // Update and display particles
        particles.forEach(particle => {
            particle.update();
            particle.display();
        });

        // Draw waveforms with RMS values
        drawWaveform(p.waveform0, p.color(255, 100, 0, 150), -p.height / 4, p.rmsInput);
        drawWaveform(p.waveform1, p.color(0, 100, 255, 150), p.height / 4, p.rmsOutput);
    };

    class Particle {
        constructor() {
            this.pos = p.createVector(p.random(p.width), p.random(p.height));
            this.vel = p.createVector(p.random(-1, 1), p.random(-1, 1)).mult(p.random(1, maxSpeed));
            this.acc = p.createVector();
            this.trail = [];
        }

        update() {
            let waveIndex = Math.floor(p.map(this.pos.x, 0, p.width, 0, p.waveform1.length - 1));
            let waveValue = p.waveform1[waveIndex] || 0;
            
            this.acc = p.createVector(p.random(-1, 1), p.random(-1, 1)).normalize().mult(waveValue * 10);
            this.vel.add(this.acc);
            this.vel.limit(maxSpeed);
            this.pos.add(this.vel);

            if (this.pos.x < 0 || this.pos.x > p.width) this.vel.x *= -1;
            if (this.pos.y < 0 || this.pos.y > p.height) this.vel.y *= -1;

            this.trail.unshift(this.pos.copy());
            if (this.trail.length > trailLength) {
                this.trail.pop();
            }
        }

        display() {
            p.noStroke();
            for (let i = 0; i < this.trail.length; i++) {
                let alpha = p.map(i, 0, this.trail.length, 255, 0);
                p.fill(255, 100, 0, alpha);
                p.ellipse(this.trail[i].x, this.trail[i].y, 4, 4);
            }
        }
    }

    const drawWaveform = (waveform, color, yOffset, rms) => {
        if (waveform && waveform.length > 0) {
            p.stroke(color);
            p.strokeWeight(1.0 + Math.max(rms, 0.002) * 100.0); // Ensure RMS is at least 0.05
            p.noFill();
            p.beginShape();
            for (let i = 0; i < waveform.length; i++) {
                let x = p.map(i, 0, waveform.length, 0, p.width);
                let y = p.height / 2 + yOffset + waveform[i] * p.height / 4;
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