#!/usr/bin/env node
'use strict';
/**
 * همان آزمون‌ها، روی راه‌اندازِ درون‌فرآیندی (PGlite) — بی هیچ PostgreSQLی.
 *
 *   npm run test:pglite
 *
 * دو آزمون که خودِ pg_dump/psql را صدا می‌زنند (backup و pump-isolation)
 * این‌جا معنایی ندارند و رد می‌شوند؛ پشتیبانِ PGlite جداگانه سنجیده می‌شود
 * (pglite-driver.test.js).
 */
const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const dir = path.join(__dirname, '..', 'test');
const skip = new Set(['backup.test.js', 'pump-isolation.test.js']);
const files = fs.readdirSync(dir).filter((f) => f.endsWith('.test.js') && !skip.has(f)).sort()
  .map((f) => path.join('test', f));

const r = spawnSync(process.execPath, ['--test', '--test-concurrency=1', ...files], {
  stdio: 'inherit',
  env: { ...process.env, TEST_DATABASE_URL: 'pglite:memory' },
});
process.exit(r.status ?? 1);
