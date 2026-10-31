#!/usr/bin/env node
'use strict';
/**
 * کلیدِ خصوصیِ امضای مجوز — جابه‌جایی از `app_config` به فایلِ بیرون از دیتابیس، و چرخش (SEC-ART-05 / F0-28).
 * ⛔ هیچ فرمانی مقدارِ کلیدِ خصوصی را چاپ نمی‌کند؛ فقط `kid`، جا و شکلِ کار.
 *
 *   node scripts/license-key.js status            حالت، جای کلید، kid، نسخهٔ دیتابیس و یکی بودنش
 *   node scripts/license-key.js export <file>     کلیدِ امروز (همان kid) ⇒ فایلِ تازهٔ 0600 (روی فایلِ موجود نمی‌نویسد)
 *   node scripts/license-key.js purge-db          نسخهٔ دیتابیس را برمی‌دارد — فقط اگر کلیدِ فایل/محیط همان kid باشد
 *   node scripts/license-key.js generate <file>   کلیدِ تازه برای چرخش ⇒ فایلِ 0600؛ kid و کلیدِ عمومی (راز نیست) چاپ می‌شود
 *
 * گام‌ها (OWNER-ACTIONS-fa.md بخشِ ۳): export ⇒ LICENSE_PRIVATE_KEY_FILE=<file> و LICENSE_KEY_MODE=env ⇒ راه‌اندازیِ دوباره
 * ⇒ `GET /api/license/public-key` همان باشد ⇒ purge-db. پشتیبان‌های قدیم هنوز کلید را دارند ⇒ چرخش (generate).
 */
const crypto = require('crypto');
const fs = require('fs');
const config = require('../src/config');
const plans = require('../src/lib/plans');
const license = require('../src/lib/license');
const { query, closeDb } = require('../src/db');

function writeSecret(file, pem) {
  //  ‎wx‎: هرگز روی فایلِ موجود (شاید کلیدِ دیگری) نمی‌نویسد؛ ‎0600‎ از همان لحظهٔ ساختن
  const fd = fs.openSync(file, 'wx', 0o600);
  try { fs.writeSync(fd, pem); } finally { fs.closeSync(fd); }
  if (process.platform !== 'win32') fs.chmodSync(file, 0o600);
}

async function dbKey() {
  const pem = await plans.getConfig(license.KEY_PRIVATE, '');
  if (!pem) return null;
  const key = crypto.createPrivateKey(pem);
  return { pem, kid: license.kidOf(license.spkiOf(key)) };
}

async function status(out) {
  const db = await dbKey().catch(() => null);
  const pub = await plans.getConfig(license.KEY_PUBLIC, '');
  let active = null;
  let error = null;
  try { active = await license.preflight(); } catch (e) { error = e.code || 'error'; }
  let fileMode = null;
  if (config.license.file && fs.existsSync(config.license.file) && process.platform !== 'win32') {
    fileMode = (fs.statSync(config.license.file).mode & 0o777).toString(8);
  }
  out({
    mode: config.license.mode,
    activeSource: active ? active.source : null,
    activeKid: active ? active.kid : null,
    error,
    dbPrivateCopy: !!db,
    dbKid: db ? db.kid : null,
    dbPublicKid: pub ? license.kidOf(pub) : null,
    file: config.license.file || null,
    fileMode,
    revokedKids: config.license.revokedKids,
  });
  return error ? 1 : 0;
}

async function exportKey(file, out) {
  if (!file) throw new Error('مسیرِ فایل را بدهید: export <file>');
  let pem = null;
  let source = null;
  if (process.env.LICENSE_PRIVATE_KEY && process.env.LICENSE_PRIVATE_KEY.trim()) {
    pem = process.env.LICENSE_PRIVATE_KEY.trim().replace(/\\n/g, '\n'); source = 'env';
  } else {
    const db = await dbKey();
    if (db) { pem = db.pem; source = 'app_config'; }
  }
  if (!pem) throw new Error('کلیدی برای بیرون آوردن نیست (نه LICENSE_PRIVATE_KEY، نه app_config)');
  const key = crypto.createPrivateKey(pem);
  //  PKCS#8 ِ یک‌دست، هر شکلی که پیش‌تر ذخیره شده بود
  writeSecret(file, key.export({ type: 'pkcs8', format: 'pem' }));
  out({ exported: file, from: source, kid: license.kidOf(license.spkiOf(key)), mode: '0600',
    next: 'LICENSE_PRIVATE_KEY_FILE=<file> و LICENSE_KEY_MODE=env، راه‌اندازیِ دوباره، سپس purge-db' });
  return 0;
}

async function purgeDb(out) {
  const db = await dbKey();
  if (!db) { out({ purged: false, reason: 'نسخه‌ای در app_config نیست' }); return 0; }
  if (!config.license.file && !(process.env.LICENSE_PRIVATE_KEY || '').trim()) {
    throw new Error('اول کلید را بیرون از دیتابیس بگذارید (LICENSE_PRIVATE_KEY_FILE)؛ وگرنه پاک کردن یعنی گم کردنِ کلید');
  }
  license._reset();
  const active = await license.preflight();
  if (active.source === 'db' || active.kid !== db.kid) {
    throw new Error(`kidِ کلیدِ فایل/محیط (${active.kid}) با app_config (${db.kid}) یکی نیست — پاک نمی‌شود`);
  }
  //  کلیدِ عمومی می‌ماند: نشانهٔ «کلیدی بوده» تا سرور هرگز بی‌صدا کلیدِ تازه نسازد
  await plans.setConfig(license.KEY_PUBLIC, await license.publicKey());
  await query('DELETE FROM app_config WHERE key=$1', [license.KEY_PRIVATE]);
  out({ purged: true, kid: db.kid, note: 'پشتیبان‌های پیش از امروز هنوز کلید را دارند؛ اگر بیرون رفته‌اند ⇒ چرخش' });
  return 0;
}

async function generate(file, out) {
  if (!file) throw new Error('مسیرِ فایل را بدهید: generate <file>');
  const pair = crypto.generateKeyPairSync('ec', {
    namedCurve: 'prime256v1',
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
    publicKeyEncoding: { type: 'spki', format: 'der' },
  });
  writeSecret(file, pair.privateKey);
  const spki = Buffer.from(pair.publicKey).toString('base64');
  out({ generated: file, kid: license.kidOf(spki), publicKey: spki, mode: '0600',
    next: 'کلیدِ عمومی را در برنامه‌ها سنجاق کنید (TOHID_LICENSE_PUBLIC_KEY) و پس از به‌روز شدنِ نصب‌ها LICENSE_PRIVATE_KEY_FILE را به این فایل ببرید؛ kidِ قدیم ⇒ LICENSE_REVOKED_KIDS' });
  return 0;
}

async function main(argv, out = (o) => console.log(JSON.stringify(o, null, 2))) {
  const [cmd, arg] = argv;
  if (cmd === 'status') return status(out);
  if (cmd === 'export') return exportKey(arg, out);
  if (cmd === 'purge-db') return purgeDb(out);
  if (cmd === 'generate') return generate(arg, out);
  console.error('کاربرد: node scripts/license-key.js status | export <file> | purge-db | generate <file>');
  return 2;
}

module.exports = { main };

if (require.main === module) {
  main(process.argv.slice(2))
    .then(async (code) => { await closeDb(); process.exit(code); })
    .catch(async (e) => { console.error('✖', e.message); await closeDb().catch(() => {}); process.exit(1); });
}
