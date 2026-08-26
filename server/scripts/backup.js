#!/usr/bin/env node
'use strict';
/** گرفتن پشتیبان دستی:  node scripts/backup.js [daily|weekly|monthly|manual] */
const backup = require('../src/lib/backup');
const { closeDb } = require('../src/db');

backup.run({ kind: process.argv[2] || 'manual' })
  .then(async (out) => {
    console.log(`پشتیبان ساخته شد: ${out.file} (${Math.round(out.bytes / 1024)} کیلوبایت)${out.encrypted ? ' — رمزشده' : ''}`);
    await closeDb();
  })
  .catch(async (e) => { console.error('✖', e.message); await closeDb(); process.exit(1); });
