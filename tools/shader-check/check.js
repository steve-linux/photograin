// SPDX-License-Identifier: GPL-3.0-or-later
const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const shaders = JSON.parse(fs.readFileSync(process.argv[2]));
  const browser = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const page = await browser.newPage();
  const res = await page.evaluate((shaders) => {
    const gl = document.createElement('canvas').getContext('webgl2');
    if (!gl) return { error: 'no webgl2' };
    const out = {};
    const compile = (type, src) => { const sh = gl.createShader(type); gl.shaderSource(sh, src); gl.compileShader(sh);
      return gl.getShaderParameter(sh, gl.COMPILE_STATUS) ? sh : { log: gl.getShaderInfoLog(sh) }; };
    const vs = compile(gl.VERTEX_SHADER, shaders.VERTEX);
    if (vs.log) return { VERTEX: vs.log };
    for (const k of ['GEOMETRY', 'BLUR', 'COLOR']) {
      const fs = compile(gl.FRAGMENT_SHADER, shaders[k]);
      if (fs.log) { out[k] = fs.log; continue; }
      const p = gl.createProgram(); gl.attachShader(p, vs); gl.attachShader(p, fs); gl.linkProgram(p);
      out[k] = gl.getProgramParameter(p, gl.LINK_STATUS) ? 'ok' : gl.getProgramInfoLog(p);
    }
    return out;
  }, shaders);
  console.log(JSON.stringify(res, null, 1));
  await browser.close();
})();
