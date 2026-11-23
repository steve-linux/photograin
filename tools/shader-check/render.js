// SPDX-License-Identifier: GPL-3.0-or-later
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const shaders = JSON.parse(fs.readFileSync(process.argv[2]));
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  
  const res = await page.evaluate((S) => {
    const W = 16, H = 16;
    const canvas = document.createElement('canvas'); canvas.width = W; canvas.height = H;
    const gl = canvas.getContext('webgl2');
    const sh = (t, s) => { const x = gl.createShader(t); gl.shaderSource(x, s); gl.compileShader(x); if (!gl.getShaderParameter(x, gl.COMPILE_STATUS)) throw gl.getShaderInfoLog(x); return x; };
    const prog = gl.createProgram(); gl.attachShader(prog, sh(gl.VERTEX_SHADER, S.VERTEX)); gl.attachShader(prog, sh(gl.FRAGMENT_SHADER, S.COLOR)); gl.linkProgram(prog);
    if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) throw gl.getProgramInfoLog(prog);
    gl.useProgram(prog);
    const L = n => gl.getUniformLocation(prog, n);
    function tex2d(unit, pixels) { const t = gl.createTexture(); gl.activeTexture(gl.TEXTURE0 + unit); gl.bindTexture(gl.TEXTURE_2D, t);
      gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, W, H, 0, gl.RGBA, gl.UNSIGNED_BYTE, pixels);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
      gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE); return t; }
    // unused samplers on their own units with right types
    const c = gl.createTexture(); gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, c);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(4));
    const l3 = gl.createTexture(); gl.activeTexture(gl.TEXTURE3); gl.bindTexture(gl.TEXTURE_3D, l3);
    gl.texImage3D(gl.TEXTURE_3D, 0, gl.RGBA8, 1, 1, 1, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(4));
    const ba = gl.createTexture(); gl.activeTexture(gl.TEXTURE4); gl.bindTexture(gl.TEXTURE_2D_ARRAY, ba);
    gl.texImage3D(gl.TEXTURE_2D_ARRAY, 0, gl.R8, 1, 1, 1, 0, gl.RED, gl.UNSIGNED_BYTE, new Uint8Array(1));
    gl.uniform1i(L('uGeo'), 0); gl.uniform1i(L('uBlur'), 1); gl.uniform1i(L('uCurve'), 2); gl.uniform1i(L('uLut'), 3); gl.uniform1i(L('uBrush'), 4);
    gl.uniform3f(L('uWb'), 1, 1, 1); gl.uniform4f(L('uImgMap'), 0, 0, 1, 1); gl.uniform2f(L('uGeoTexel'), 1 / W, 1 / H);
    gl.uniform2f(L('uAspect'), 1, 1); gl.uniform1f(L('uLongEdgePx'), W); gl.uniform3f(L('uLuma'), 0.2126, 0.7152, 0.0722);
    gl.uniform1f(L('uCompare'), -1); gl.uniform1i(L('uCutout'), -1); gl.uniform1i(L('uShowMask'), -1); gl.uniform1i(L('uPointShow'), -1);
    gl.uniform4f(L('uDst'), -1, -1, 1, 1); const vb = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, vb); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([0,0, 1,0, 0,1, 1,1]), gl.STATIC_DRAW); gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 0, 0); gl.uniform4f(L('uSrc'), 0, 0, 1, 1);
    function render(img, setup) {
      tex2d(0, img); tex2d(1, img); setup && setup();
      gl.bindFramebuffer(gl.FRAMEBUFFER, null); gl.viewport(0, 0, W, H); gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      const out = new Uint8Array(W * H * 4); gl.readPixels(0, 0, W, H, gl.RGBA, gl.UNSIGNED_BYTE, out); return out;
    }
    const px = (buf, x, y) => Array.from(buf.slice((y * W + x) * 4, (y * W + x) * 4 + 3));
    function image(f) { const a = new Uint8Array(W * H * 4); for (let y = 0; y < H; y++) for (let x = 0; x < W; x++) { const v = f(x, y); a.set([...v, 255], (y * W + x) * 4); } return a; }
    const err = gl.getError();
    // Left half orange, right half blue.
    const two = image((x) => x < 8 ? [230, 128, 51] : [51, 77, 230]);
    const out = {};
    out.base = [px(render(two), 2, 8), px(render(two), 13, 8)];
    // orange sample hsv: h=(0.5-0.2)/(0.9-0.2)... computed in Kotlin; approximate here
    const r = 0.9, g = 0.5, b = 0.2, mx = 0.9, mn = 0.2, h = ((g - b) / (mx - mn)) / 6;
    const point = () => { gl.uniform1i(L('uPointCount'), 1); gl.uniform4fv(L('uPointSample'), new Float32Array([h, (mx - mn) / mx, mx, 0.5, ...new Array(28).fill(0)]));
      gl.uniform3fv(L('uPointShift'), new Float32Array([0, -1, 0, ...new Array(21).fill(0)])); };
    const pc = render(two, point);
    out.pointDesat = [px(pc, 2, 8), px(pc, 13, 8)];
    const shown = render(two, () => { point(); gl.uniform3fv(L('uPointShift'), new Float32Array(24)); gl.uniform1i(L('uPointShow'), 0); });
    out.showRange = [px(shown, 2, 8), px(shown, 13, 8)];
    gl.uniform1i(L('uPointShow'), -1); gl.uniform1i(L('uPointCount'), 0);
    // Defringe: black | purple column | white, and a flat purple area at the bottom.
    const fr = image((x, y) => y >= 12 ? [140, 60, 200] : (x < 7 ? [0, 0, 0] : x == 7 ? [140, 60, 200] : [255, 255, 255]));
    out.noDefringe = [px(render(fr), 7, 4), px(render(fr), 7, 14)];
    const d = render(fr, () => gl.uniform1i(L('uDefringe'), 1));
    out.defringe = [px(d, 7, 4), px(d, 7, 14)];
    out.glError = gl.getError();
    return out;
  }, shaders);
  console.log(JSON.stringify(res));
  await browser.close();
  const gray = (p) => Math.max(...p) - Math.min(...p) <= 2;
  const same = (a, b) => a.every((v, i) => Math.abs(v - b[i]) <= 2);
  const checks = {
    'point color desaturates the picked color': gray(res.pointDesat[0]),
    'point color leaves other colors': same(res.pointDesat[1], res.base[1]),
    'show range keeps the picked color': same(res.showRange[0], res.base[0]),
    'show range grays the rest': gray(res.showRange[1]),
    'defringe clears a fringe on an edge': gray(res.defringe[0]),
    'defringe keeps flat purple': same(res.defringe[1], res.noDefringe[1]),
    'no GL error': res.glError === 0,
  };
  let failed = 0;
  for (const [name, ok] of Object.entries(checks)) { console.log((ok ? 'ok   ' : 'FAIL ') + name); if (!ok) failed++; }
  process.exit(failed ? 1 : 0);
})();
