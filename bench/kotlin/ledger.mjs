#!/usr/bin/env node
/**
 * دادهٔ مرجعِ قطعیِ `bench/datagen.mjs` را برای ریزسنجشِ JVM در یک فایل می‌نویسد.
 *   node bench/kotlin/ledger.mjs <out.json> [scale]
 * ⛔ هیچ دادهٔ واقعی‌ای نیست؛ همان seed ثابتِ سنجه‌های وب و سرور.
 */
import fs from 'node:fs';
import { makeReference, REFERENCE } from '../datagen.mjs';

const out = process.argv[2];
if (!out) { console.error('مسیرِ خروجی لازم است'); process.exit(2); }
const scale = Number(process.argv[3] || 1);
const scaled = Object.fromEntries(Object.entries(REFERENCE).map(([k, v]) => [k, k === 'days' ? v : Math.max(1, Math.round(v * scale))]));
fs.writeFileSync(out, JSON.stringify(makeReference(scaled)));
console.log(`${out}: ${(fs.statSync(out).size / 1e6).toFixed(1)}MB`, JSON.stringify(scaled));
