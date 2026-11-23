# Shader check

Compiles the app's GLSL ES 3.00 shaders (`render/Shaders.kt`) in WebGL2 through headless
Chromium, and renders a few small images through the color shader to check Point Color and
chromatic aberration removal. No Android device or SDK needed.

```sh
python3 tools/shader-check/extract.py app/src/main/java/io/github/steve_linux/photograin/render/Shaders.kt /tmp/shaders.json
node tools/shader-check/check.js /tmp/shaders.json    # compile and link every program
node tools/shader-check/render.js /tmp/shaders.json   # pixel checks, printed as JSON
```

Needs Node.js with Playwright and its Chromium. WebGL2 accepts the same shader language as
OpenGL ES 3.0, so a shader that fails here will also fail on the phone.
