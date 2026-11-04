#!/usr/bin/env node
'use strict';
/**
 * کلیدِ خصوصیِ امضای مجوز — فقط برای مدیرِ سرور، روی خودِ سرور (O-18؛ docs/RULES-fa.md «کلیدها»).
 *
 *   node scripts/license-key.js status                 کلید از کجا خوانده می‌شود و kid ِ آن (هرگز خودِ کلید)
 *   node scripts/license-key.js export <فایل> [--pem]   همان کلیدِ امروز در <فایل>، حالتِ 600:
 *                                                       پیش‌فرض یک خطِ آمادهٔ .env (`LICENSE_PRIVATE_KEY="…\n…"`)،
 *                                                       `--pem` خودِ PEM برای `LICENSE_PRIVATE_KEY_FILE`
 *
 * ⛔ هرگز کلید نمی‌سازد (حتی در development)، هرگز آن را چاپ نمی‌کند و روی فایلِ موجود نمی‌نویسد.
 * ⛔ PGlite (پنلِ خانگی): پیش از اجرا سرور را خاموش کنید؛ دو فرآیند یک پوشهٔ PGlite را با هم باز نکنند.
 */
const fs = require('fs');
const path = require('path');
const license = require('../src/lib/license');

const SOURCE_FA = { env: 'LICENSE_PRIVATE_KEY', file: 'LICENSE_PRIVATE_KEY_FILE', db: 'پایگاه‌داده (app_config)' };

/** همان کلیدِ امروز در `out` (حالتِ 600، `wx`: فایلِ موجود ⇒ EEXIST)؛ فقط `{ source, kid }` برمی‌گردد */
async function exportKey(out, { pem = false } = {}) {
  const k = await license.existing();
  const text = k.privateKey.export({ type: 'pkcs8', format: 'pem' }).trim();
  const body = pem ? `${text}\n` : `LICENSE_PRIVATE_KEY="${text.replace(/\r?\n/g, '\\n')}"\n`;
  fs.writeFileSync(out, body, { mode: 0o600, flag: 'wx' });
  fs.chmodSync(out, 0o600);
  return { source: k.source, kid: k.kid };
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
  console.error('کاربرد: node scripts/license-key.js status | export <فایل> [--pem]');
  return 2;
}

module.exports = { exportKey };

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
