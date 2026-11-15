#!/usr/bin/env node
/**
 *  Preview a scene while drawing (Chromium via Playwright; nothing here ships):
 *    node tools/art/preview.mjs <scene> <outDir> [--times 0,1.5,3] [--gif] [--fps 15] [--badge]
 *  ⇒ <outDir>/<scene>-<light|dark>.png (a strip of poses) and, with --gif, <scene>-<light|dark>.gif (ffmpeg).
 */
import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { createRequire } from 'node:module';
import { svgAt, indexScene } from './engine.mjs';
import { LIGHT, DARK } from './palette.mjs';
import { SCENES } from './scenes/index.mjs';

const require = createRequire(import.meta.url);
const { chromium } = require('playwright');

const [name, outDir, ...rest] = process.argv.slice(2);
const opt = (k, d) => { const i = rest.indexOf(k); return i < 0 ? d : rest[i + 1]; };
const has = (k) => rest.includes(k);
const scene = indexScene(SCENES[name]());
fs.mkdirSync(outDir, { recursive: true });
const times = (opt('--times', '') || [0, 0.25, 0.5, 0.75].map((u) => (u * scene.loop).toFixed(2)).join(',')).split(',').map(Number);

const browser = await chromium.launch();
const page = await browser.newPage({ deviceScaleFactor: 2 });
for (const [mode, pal] of [['light', LIGHT], ['dark', DARK]]) {
  const zoom = opt('--zoom', '');
  const view = zoom ? zoom.split(':').map(Number) : has('--badge') ? [scene.badge[0], scene.badge[1], scene.badge[2], scene.badge[2]] : undefined;
  const cells = times.map((t) => `<div style="display:inline-block;margin:6px;text-align:center;font:11px sans-serif;color:#888">${svgAt(scene, t, pal, { view, px: zoom ? 380 : has('--badge') ? 160 : 560 })}<br>t=${t}</div>`).join('');
  await page.setContent(`<html><body style="margin:0;background:${pal.bg}"><div id="__shot" style="display:inline-block;padding:8px">${cells}</div></body></html>`);
  await page.locator('#__shot').screenshot({ path: path.join(outDir, `${name}${zoom ? '-zoom' : has('--badge') ? '-badge' : ''}-${mode}.png`) });
  if (has('--gif')) {
    const fps = Number(opt('--fps', '15'));
    const n = Math.round(scene.loop * fps);
    const tmp = fs.mkdtempSync(path.join(outDir, '.frames-'));
    for (let i = 0; i < n; i++) {
      await page.setContent(`<html><body style="margin:0;background:${pal.bg}"><div id="__shot" style="display:inline-block">${svgAt(scene, (i / fps) % scene.loop, pal, { px: 480 })}</div></body></html>`);
      await page.locator('#__shot').screenshot({ path: path.join(tmp, `f${String(i).padStart(3, '0')}.png`) });
    }
    const gif = path.join(outDir, `${name}-${mode}.gif`);
    execFileSync('ffmpeg', ['-y', '-loglevel', 'error', '-framerate', String(fps), '-i', path.join(tmp, 'f%03d.png'),
      '-vf', 'split[a][b];[a]palettegen=reserve_transparent=0:stats_mode=full[p];[b][p]paletteuse=dither=none', '-loop', '0', gif]);
    fs.rmSync(tmp, { recursive: true, force: true });
  }
}
await browser.close();
console.log('ok', name, times.join(','));
