#!/usr/bin/env node
/**
 *  Writes the scene data the app draws (`ui/v3/art/ArtData*.kt`) from the drawing sources in tools/art/scenes.
 *    node tools/art/gen.mjs          (re)write every ArtData file
 *    node tools/art/gen.mjs --check  exit 1 when a written file is stale (CI / precheck)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { sceneText, indexScene } from './engine.mjs';
import { SCENES, FILES } from './scenes/index.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.resolve(here, '../../android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/art');
const check = process.argv.includes('--check');
let stale = 0;

for (const [key, make] of Object.entries(SCENES)) {
  const scene = indexScene(make());
  const text = sceneText(scene);
  if (text.includes('$') || text.includes('"""')) throw new Error(`${key}: text cannot sit in a raw string`);
  if (Buffer.byteLength(text) > 60000) throw new Error(`${key}: ${Buffer.byteLength(text)} bytes — split the scene (JVM constant limit)`);
  const file = path.join(OUT, `ArtData${FILES[key]}.kt`);
  const src = `package ir.vil3ntec.tohid.ui.v3.art

//  نوشتهٔ tools/art/gen.mjs از tools/art/scenes — دست نزنید؛ منبع را عوض کنید و دوباره بسازید.
//  صحنهٔ «${key}»: چرخهٔ ${scene.loop} ثانیه، ژستِ ایستا در ${scene.rest} ثانیه.
internal const val ART_${key.toUpperCase()} = """
${text}
"""
`;
  const old = fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
  if (old === src) continue;
  if (check) { console.error(`stale: ${path.relative(process.cwd(), file)}`); stale++; continue; }
  fs.writeFileSync(file, src);
  console.log(`wrote ${path.relative(process.cwd(), file)} (${text.split('\n').length} lines)`);
}
if (stale) process.exit(1);
