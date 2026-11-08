#!/usr/bin/env node
'use strict';
/**
 * جابه‌جاییِ عکس‌ها میانِ پایگاه و انبارِ شیء — شرح: docs/SCALING-fa.md §۳ «انبارِ عکس»، منطق: src/lib/image-store-move.js.
 *
 *   node scripts/image-store-migrate.js status
 *   node scripts/image-store-migrate.js copy     [--table=product_photos,…] [--batch=100] [--limit=N]
 *   node scripts/image-store-migrate.js verify   [--table=…]
 *   node scripts/image-store-migrate.js purge    [--confirm]          ← بی --confirm فقط شمارش
 *   node scripts/image-store-migrate.js rollback [--table=…]          ← با IMAGE_STORE=db و IMAGE_STORE_READ=disk|s3
 *   node scripts/image-store-migrate.js gc       [--older-than-hours=24] [--apply]   ← بی --apply فقط گزارش
 *
 * پیکربندی همان env ِ سرور است (DATABASE_URL، IMAGE_STORE، IMAGE_STORE_DIR یا IMAGE_STORE_S3_* و کلیدِ S3 از env).
 * خروجِ ۰ = درست؛ ۱ = خطا یا ردیفی که جابه‌جا نشد (failed/missing/mismatch/kept).
 */
const config = require('../src/config');
const { closeDb } = require('../src/db');
const move = require('../src/lib/image-store-move');

const [cmd, ...rest] = process.argv.slice(2);
const flags = Object.fromEntries(rest.map((a) => {
  const m = /^--([a-z-]+)(?:=(.*))?$/.exec(a);
  if (!m) throw new Error(`آرگومانِ ناشناخته: ${a}`);
  return [m[1], m[2] === undefined ? true : m[2]];
}));
const tables = typeof flags.table === 'string' ? flags.table.split(',').map((s) => s.trim()).filter(Boolean) : undefined;
const log = (m) => console.log(m);

const BAD = ['failed', 'missing', 'mismatch', 'kept'];
const bad = (out) => Object.values(out.tables || out).some((c) => c && typeof c === 'object' && BAD.some((k) => Number(c[k]) > 0));

async function main() {
  const problems = config.validate().filter((p) => /IMAGE_STORE|S3/.test(p));
  if (problems.length) throw new Error(problems.join('؛ '));
  const opts = { tables, log, batch: Number(flags.batch) || undefined };
  switch (cmd) {
    case 'status': return move.status({ tables });
    case 'copy': return move.copy({ ...opts, limit: flags.limit ? Number(flags.limit) : undefined });
    case 'verify': return move.verify(opts);
    case 'purge': return move.purge({ ...opts, confirm: flags.confirm === true });
    case 'rollback': return move.rollback(opts);
    case 'gc': return move.gc({
      log, apply: flags.apply === true,
      olderThanMs: Math.max(1, Number(flags['older-than-hours'] ?? 24)) * 3600 * 1000,
    });
    default: throw new Error('فرمان: status | copy | verify | purge | rollback | gc');
  }
}

main()
  .then(async (out) => {
    console.log(JSON.stringify(out, null, 2));
    await closeDb();
    if (bad(out)) process.exit(1);
  })
  .catch(async (err) => {
    console.error('✖', err.message);
    await closeDb();
    process.exit(1);
  });
