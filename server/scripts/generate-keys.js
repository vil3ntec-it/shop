#!/usr/bin/env node
'use strict';
/**
 * تولید جفت‌کلید امضای License.
 *
 * هشدار: با تولید کلید جدید، همه‌ی Licenseهای صادرشده قبلی باطل می‌شوند و
 * کاربران باید دوباره Sync کنند. کلید خصوصی را جای امن نگه دارید و هرگز
 * در گیت کامیت نکنید.
 */
const fs = require('fs');
const config = require('../src/config');
const ck = require('../src/lib/crypto-keys');

(async () => {
  const force = process.argv.includes('--force');
  if (fs.existsSync(config.privateKeyPath) && !force) {
    console.error(`کلید از قبل وجود دارد: ${config.privateKeyPath}`);
    console.error('برای جایگزینی (و باطل کردن همه Licenseهای فعلی) از --force استفاده کنید.');
    process.exit(1);
  }
  const kp = await ck.generateKeyPair();
  const { priv, pub } = ck.writeKeyPair(config.keysDir, kp);
  console.log('جفت‌کلید ساخته شد (ECDSA P-256):');
  console.log('  کلید خصوصی:', priv, '(دسترسی 600)');
  console.log('  کلید عمومی :', pub);
  console.log('  شناسه کلید :', kp.keyId);
  console.log('');
  console.log('کلید عمومی برای قرار دادن در برنامه (base64 SPKI):');
  console.log(kp.publicKeyB64);
})();
