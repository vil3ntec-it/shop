#!/usr/bin/env node
'use strict';
/**
 * POS-04 و WEB-05 — پیش‌نویسِ فهرستِ مترادف (شرح: `src/lib/search-synonyms-draft.js`، سند: `docs/SYNONYMS-draft-fa.md`).
 *
 *   node scripts/synonyms-draft.js                                   پیش‌نمایش: چند گروه افزوده و کدام کنار می‌رود (هیچ نوشتنی)
 *   node scripts/synonyms-draft.js --apply --reason "بازبینی شد"     افزودن به فهرستِ مرکزی (فهرستِ کنونیِ پنل دست نمی‌خورد)
 *   … [--file <پرونده>]                                              پرونده‌ای جز پیش‌نویسِ همراهِ سرور (همان شکل)
 */
const args = process.argv.slice(2);
const at = (k) => { const i = args.indexOf(k); return i >= 0 ? args[i + 1] : undefined; };

const draft = require('../src/lib/search-synonyms-draft');
const { closeDb } = require('../src/db');

draft.load({ apply: args.includes('--apply'), reason: at('--reason') || '', file: at('--file') || draft.FILE })
  .then(async (r) => {
    console.log(`پیش‌نویس: ${r.draft} گروه؛ افزودنی: ${r.added}؛ کنار (واژه‌اش در فهرستِ کنونی هست): ${r.skipped.length}`);
    for (const s of r.skipped) console.log(`  کنار: ${s.group.join('، ')} — هم‌پوشان: ${s.clash.join('، ')}`);
    if (!r.apply) console.log('پیش‌نمایش بود؛ برای بار کردن: --apply --reason "…"');
    else console.log(r.unchanged ? 'فهرست همان بود (چیزی عوض نشد).' : `بار شد — نسخهٔ ${r.version}.`);
    await closeDb();
  })
  .catch(async (e) => { console.error('✖', e.message); await closeDb(); process.exit(1); });
