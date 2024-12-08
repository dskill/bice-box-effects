const sketch = function(p) {
  let snowflakes = [];
  p.waveform0 = [];
  p.waveform1 = [];

  p.setup = function() {
    p.createCanvas(p.windowWidth, p.windowHeight);
    
    // Create initial snowflakes
    for (let i = 0; i < 500; i++) {
      snowflakes.push(new Snowflake());
    }
  }

  p.draw = function() {
    p.background(0, 20, 40); // Dark blue night sky
    
    // Update and display snowflakes
    for (let flake of snowflakes) {
      flake.update(); // Amplify the effect
      flake.display();
    }
  }

  class Snowflake {
    constructor() {
      this.reset();
      this.y = p.random(-50, p.height);
    }
    
    reset() {
      this.x = p.random(p.width);
      this.y = p.random(-50, -10);
      this.size = p.random(3, 10);
      this.speed = p.random(.2, .6);
      this.wobble = p.random(0, p.PI * 2);
    }
    
    update() {
      let myAudioLevel = 0;
      this.waveformIndex = Math.floor(p.map(this.x, 0, p.width, 0, p.waveform1.length));
      
      if (p.waveform1.length > 0) {
        myAudioLevel = p.waveform1[this.waveformIndex];
      }
      
      this.y += this.speed + (myAudioLevel * 10);
      this.x += p.sin(this.wobble) * 0.5;
      this.wobble += 0.05;
      
      if (this.y > p.height + 20) {
        this.reset();
      }
    }
    
    display() {
      p.fill(255, 250);
      p.stroke(255, 250);
      p.strokeWeight(1);
      
      p.push();
      p.translate(this.x, this.y);
      p.rotate(this.wobble);
      for (let i = 0; i < 2; i++) {
        p.rotate(p.PI/3);
        p.line(0, 0, this.size, 0);
        //p.ellipse(this.size * 0.7, 0, this.size * 0.3);
      }
      p.pop();
    }
  }

  p.windowResized = function() {
    p.resizeCanvas(p.windowWidth, p.windowHeight);
  }
}

module.exports = sketch;