# P5.js Shader Authoring & Debugging Guidelines

This document summarizes key learnings and best practices for creating and troubleshooting WebGL shaders within a p5.js environment, based on common issues encountered.

## 1. Shader Initialization

The most critical step is to ensure your shader is created *after* the WebGL rendering context is available.

- **ALWAYS call `p.createShader(vertexShader, fragmentShader)` inside the `setup()` function.**
- **This MUST be done AFTER `p.createCanvas(width, height, p.WEBGL);` has been called.**

Failure to do so will result in the shader object not having access to the WebGL renderer, leading to failed compilations (often indicated by `shader._renderer` being undefined or `shader._vertShader` / `shader._fragShader` being `-1`).

**Example:**
```javascript
// In your p5.js sketch (e.g., zippyZaps.js)

let shader;
const vertexShaderSource = `...`; // Your vertex GLSL
const fragmentShaderSource = `...`; // Your fragment GLSL

function setup() {
  createCanvas(windowWidth, windowHeight, WEBGL); // Initialize WebGL context FIRST
  
  // THEN create the shader
  shader = createShader(vertexShaderSource, fragmentShaderSource); 
  
  // Proceed with shader debugging logs (see section 2)
  // ...
}
```

## 2. Debugging GLSL Errors

When shaders fail to compile or link, p5.js might not always surface the errors clearly without direct intervention.

### 2.1. Browser Developer Console
- **Your first stop should always be the browser's developer console.**
- Clear the console before running your sketch to easily spot the earliest error messages.
- GLSL compilation errors typically look like: `ERROR: 0:LINE_NUMBER: 'description of error'`.

### 2.2. Detailed In-Sketch Logging (Recommended)
To get precise error messages and line numbers directly within your JavaScript console, you can access the underlying WebGL context and query shader/program status.

Place the following logging code in your `setup()` function, immediately after `shader = p.createShader(...)`:

```javascript
// In setup(), after shader = p.createShader(...)

console.log('[DEBUG] Shader object immediately after p.createShader:', shader);

if (shader && shader._renderer && shader._renderer.GL) {
    const gl = shader._renderer.GL;
    let shadersCompiled = true;

    // Check Vertex Shader
    // Ensure _vertShader is a WebGLShader object (not -1 or null)
    if (shader._vertShader && typeof shader._vertShader === 'object' && shader._vertShader !== -1) {
        console.log('[DEBUG] Vertex shader object appears valid:', shader._vertShader);
        if (!gl.getShaderParameter(shader._vertShader, gl.COMPILE_STATUS)) {
            shadersCompiled = false;
            console.error('Vertex Shader Compilation Error:', gl.getShaderInfoLog(shader._vertShader));
        } else {
            console.log('[DEBUG] Vertex shader compiled successfully.');
            let vsLog = gl.getShaderInfoLog(shader._vertShader);
            if (vsLog) console.warn('Vertex Shader Log (may contain warnings/info):', vsLog);
        }
    } else {
        shadersCompiled = false;
        console.error('[DEBUG] Vertex shader object (shader._vertShader) is invalid. Value:', shader._vertShader);
    }

    // Check Fragment Shader
    // Ensure _fragShader is a WebGLShader object (not -1 or null)
    if (shader._fragShader && typeof shader._fragShader === 'object' && shader._fragShader !== -1) {
        console.log('[DEBUG] Fragment shader object appears valid:', shader._fragShader);
        if (!gl.getShaderParameter(shader._fragShader, gl.COMPILE_STATUS)) {
            shadersCompiled = false;
            console.error('Fragment Shader Compilation Error:', gl.getShaderInfoLog(shader._fragShader));
        } else {
            console.log('[DEBUG] Fragment shader compiled successfully.');
            let fsLog = gl.getShaderInfoLog(shader._fragShader);
            if (fsLog) console.warn('Fragment Shader Log (may contain warnings/info):', fsLog);
        }
    } else {
        shadersCompiled = false;
        console.error('[DEBUG] Fragment shader object (shader._fragShader) is invalid. Value:', shader._fragShader);
    }

    // Check Program Linking (only if individual shaders compiled)
    // Ensure shader.program is a WebGLProgram object (not 0 or null)
    if (shadersCompiled && shader.program && typeof shader.program === 'object' && shader.program !== 0) {
        console.log('[DEBUG] Shader program object appears valid:', shader.program);
        if (!gl.getProgramParameter(shader.program, gl.LINK_STATUS)) {
            console.error('Shader Program Linking Error:', gl.getProgramInfoLog(shader.program));
        } else {
            console.log('[DEBUG] Shader program linked successfully.');
            let programLog = gl.getProgramInfoLog(shader.program);
            if (programLog) { // Log even if empty, as it might contain warnings
                console.warn('Program Link Log (may contain warnings/info):', programLog);
            }
        }
    } else if (shadersCompiled) { 
         console.error('[DEBUG] Shader program object (shader.program) is invalid despite individual shaders potentially compiling. Value:', shader.program);
    } else { 
        console.error('[DEBUG] Shader program linking not attempted or failed due to compilation errors in vertex or fragment shaders.');
    }

} else {
    console.error('[DEBUG] Shader object, its renderer, or GL context is not available. Cannot check compilation/linking. Shader object:', shader);
}
```
This logging was crucial in identifying the loop initialization error.

## 3. GLSL ES 1.0 Considerations & Common Errors

WebGL, especially in simpler p5.js setups, often targets GLSL ES 1.0, which has stricter rules than desktop GLSL or later ES versions.

### 3.1. Loop Initialization
- **Error:** `Loop index cannot be initialized with non-constant expression`
- **Cause:** Initializing a `for` loop counter (e.g., `float i = n;`) where `n` is a variable (even if seemingly constant like `float n = 20.0;`). GLSL ES 1.0 requires loop initializers to be compile-time constant expressions or use `int` iterators.
- **Solution:**
    - Use an `int` for the loop iterator with constant bounds.
    - If you need a `float` value that changes like your original loop variable, calculate it inside the loop.

**Example Fix:**
```glsl
// Problematic GLSL:
// float n = 20.0;
// for (float i = n; i > 0.0; i--) { /* ... use i and n ... */ }

// Corrected GLSL:
const float n_val_for_calc = 20.0;   // Use for calculations involving 'n'
const int num_loop_iterations = 20;  // Must be a const int for loop bounds

for (int iter = 0; iter < num_loop_iterations; ++iter) {
    float i_loop_val = n_val_for_calc - float(iter); // Reconstructs 'i' (20.0, 19.0, ..., 1.0)
    
    // ... use i_loop_val and n_val_for_calc ...
    // Example: float io = A * 2. * pi * i_loop_val / n_val_for_calc; 
}
```

## 4. Varying Variables

- **Declaration:** Varyings must be declared in both vertex and fragment shaders with the same name and type if they are to pass data.
- **Usage:** If a varying is declared but not used in the fragment shader, it might be optimized away. While not always an error, it can sometimes lead to linking warnings or unexpected behavior in some drivers.
- **Best Practice:** If `vTexCoord` (or any other varying) is not used in the fragment shader, it's cleaner to comment it out or remove its declaration and assignment from both shaders to avoid potential confusion or subtle issues.

By following these guidelines, you can streamline your p5.js shader development and more effectively debug issues when they arise. 