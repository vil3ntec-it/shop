#!/usr/bin/env node
'use strict';
/**
 * کلیدِ خصوصیِ امضای مجوز — فقط برای مدیرِ سرور، روی خودِ سرور (O-18؛ docs/RULES-fa.md «کلیدها»).
 *
 *   node scripts/license-key.js status                 کلید از کجا خوانده می‌شود و kid ِ آن (هرگز خودِ کلید)
 *   node scripts/license-key.js export <فایل> [--pem]   همان کلیدِ امروز در <فایل>، حالتِ 600:
 *                                                       پیش‌فرض یک خطِ آمادهٔ .env (`LICENSE_PRIVATE_KEY="…\n…"`)،
 *                                                       `--pem` خودِ PEM برای `LICENSE_PRIVATE_KEY_FILE`
 *   node scripts/license-key.js generate <فایل> [--pem] فقط سرورِ **نو** که هرگز مجوز نداده: کلیدِ تازه در <فایل> (600)
 *
 * ⛔ export و status هرگز کلید نمی‌سازند (حتی در development). هیچ فرمانی کلید را چاپ نمی‌کند یا روی فایلِ موجود می‌نویسد.
 * ⛔ generate جایِ جابه‌جایی نیست (VPS ِ تازه ⇐ export از سرورِ پیشین): اگر هر ردی از کلید (محیط، فایل، ردیفِ خصوصی یا
 *    عمومیِ پایگاه‌داده) یا هر کدِ آفلاین/دستگاهِ مجوزگرفته در پایگاه‌داده باشد، نمی‌سازد. کلیدِ تازه یعنی مجوزِ همهٔ نصب‌ها رد.
 * ⛔ PGlite (پنلِ خانگی): پیش از اجرا سرور را خاموش کنید؛ دو فرآیند یک پوشهٔ PGlite را با هم باز نکنند.
 */
const fs = require('fs');
const path = require('path');
const license = require('../src/lib/license');

const SOURCE_FA = {
  env: 'LICENSE_PRIVATE_KEY', file: 'LICENSE_PRIVATE_KEY_FILE', db: 'پایگاه‌داده (app_config)', 'db-public': 'کلیدِ عمومیِ پایگاه‌داده',
  'db-sealed': 'کلیدِ مهرشدهٔ پشتیبان (BACKUP_SECRETS_KEY)',
};

/** PEM در `out`: حالتِ 600، `wx` (فایلِ موجود ⇒ EEXIST)؛ پیش‌فرض یک خطِ .env */
function writeKey(out, text, pem) {
  const t = String(text).trim();
  const body = pem ? `${t}\n` : `LICENSE_PRIVATE_KEY="${t.replace(/\r?\n/g, '\\n')}"\n`;
  fs.writeFileSync(out, body, { mode: 0o600, flag: 'wx' });
  fs.chmodSync(out, 0o600);
}

/** همان کلیدِ امروز در `out`؛ فقط `{ source, kid }` برمی‌گردد */
async function exportKey(out, { pem = false } = {}) {
  const k = await license.existing();
  writeKey(out, k.privateKey.export({ type: 'pkcs8', format: 'pem' }), pem);
  return { source: k.source, kid: k.kid };
}

/**
 * کلیدِ تازه فقط برای سرورِ نو: هر ردی از کلید یا از امضای پیشین ⇒ `license_key_exists` / `license_key_used`، بی فایل.
 * پایگاه‌داده باید خواندنی باشد (نخواندنی ⇒ خطا، نه «نیست»)؛ پس پیش از آن سرور یک بار بالا آمده باشد (مهاجرت‌ها).
 */
async function generateKey(out, { pem = false } = {}) {
  const found = await license.traces();
  if (found.length) {
    const where = found.map((t) => `${SOURCE_FA[t.source]}${t.kid ? ` (kid=${t.kid})` : ''}`).join('، ');
    throw Object.assign(new Error(`کلید از پیش هست: ${where}. کلیدِ تازه نمی‌سازم؛ همان را با export بیرون بیاورید.`),
      { code: 'license_key_exists' });
  }
  const { one } = require('../src/db');
  const used = await one(`SELECT (SELECT count(*) FROM pump_offline_codes) + (SELECT count(*) FROM shop_offline_codes)
    + (SELECT count(*) FROM devices WHERE platform = 'license') AS n`);
  if (Number(used && used.n) > 0) {
    throw Object.assign(new Error('این پایگاه‌داده کد یا مجوزی با کلیدی پیشین دارد (جابه‌جایی؟). کلیدِ تازه نمی‌سازم؛'
      + ' کلیدِ سرورِ پیشین را با export بیاورید.'), { code: 'license_key_used' });
  }
  const text = license.newPem();
  writeKey(out, text, pem);
  return { kid: license.kidOf(require('crypto').createPublicKey(text).export({ type: 'spki', format: 'der' }).toString('base64')) };
}

async function main(argv) {
  const [cmd, out, ...rest] = argv;
  if (cmd === 'status') {
    const k = await license.existing();
    console.log(`کلیدِ امضای مجوز از ${SOURCE_FA[k.source]} خوانده می‌شود (kid=${k.kid}).`);
    return 0;
  }
  if (cmd === 'export' && out) {
    const r = await exportKey(out, { pem: rest.includes('--pem') });
    console.log(`کلید (از ${SOURCE_FA[r.source]}، kid=${r.kid}) در ${path.resolve(out)} نوشته شد (فقط خواندنی برای همین کاربر).`);
    console.log('این فایل را در گیت، چت یا پشتیبانِ بیرونی نگذارید؛ پس از گذاشتن در .env پاکش کنید.');
    return 0;
  }
  if (cmd === 'generate' && out) {
    const r = await generateKey(out, { pem: rest.includes('--pem') });
    console.log(`کلیدِ تازه (kid=${r.kid}) در ${path.resolve(out)} نوشته شد (فقط خواندنی برای همین کاربر). فقط برای سرورِ نو؛`
      + ' آن را در .env بگذارید، فایل را پاک کنید و کلیدِ عمومیِ GET /api/license/public-key را در ساختِ برنامه‌ها سنجاق کنید.');
    return 0;
  }
  console.error('کاربرد: node scripts/license-key.js status | export <فایل> [--pem] | generate <فایل> [--pem]');
  return 2;
}

module.exports = { exportKey, generateKey };

if (require.main === module) {
  main(process.argv.slice(2))
    .catch((err) => {
      //  ⛔ فقط کد و پیامِ خودِ ما؛ هیچ چیزی از کلید در پیام‌های `license.js` نیست
      if (err && err.code === 'EEXIST') console.error('این فایل از پیش هست؛ نشانیِ تازه‌ای بدهید (روی فایلِ موجود نوشته نمی‌شود).');
      else console.error(`نشد: ${(err && err.code) || ''} ${(err && err.message) || err}`);
      return 1;
    })
    .then(async (code) => {
      try { await require('../src/db').closeDb(); } catch { /* پایگاه‌داده باز نشده بود */ }
      process.exit(code);
    });
}
