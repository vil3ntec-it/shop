'use strict';
/**
 * پیکربندی سرور — همه چیز از متغیرهای محیطی خوانده می‌شود.
 * هیچ رمز یا کلیدی در کد نیست. نمونه‌ی متغیرها در .env.example است.
 */
const path = require('path');
const fs = require('fs');

// بارگذاری ساده‌ی .env بدون وابستگی بیرونی
(function loadDotEnv() {
  const p = path.join(__dirname, '..', '.env');
  if (!fs.existsSync(p)) return;
  for (const line of fs.readFileSync(p, 'utf8').split('\n')) {
    const s = line.trim();
    if (!s || s.startsWith('#')) continue;
    const i = s.indexOf('=');
    if (i < 0) continue;
    const k = s.slice(0, i).trim();
    let v = s.slice(i + 1).trim();
    if ((v.startsWith('"') && v.endsWith('"')) || (v.startsWith("'") && v.endsWith("'"))) v = v.slice(1, -1);
    if (process.env[k] === undefined) process.env[k] = v;
  }
})();

const ROOT = path.join(__dirname, '..');
const num = (v, d) => (v === undefined || v === '' || Number.isNaN(Number(v)) ? d : Number(v));
const bool = (v, d) => (v === undefined || v === '' ? d : /^(1|true|yes|on)$/i.test(String(v)));

const config = {
  env:  process.env.NODE_ENV || 'development',
  host: process.env.HOST || '0.0.0.0',
  port: num(process.env.PORT, 4700),

  // مسیرها
  dataDir: process.env.DATA_DIR || path.join(ROOT, 'data'),
  keysDir: process.env.KEYS_DIR || path.join(ROOT, 'data', 'keys'),
  get dbPath() { return process.env.DB_PATH || path.join(this.dataDir, 'license.db'); },
  get privateKeyPath() { return path.join(this.keysDir, 'license-private.pem'); },
  get publicKeyPath()  { return path.join(this.keysDir, 'license-public.pem'); },

  // License
  license: {
    issuer:   process.env.LICENSE_ISSUER   || 'tohid-license-server',
    audience: process.env.LICENSE_AUDIENCE || 'tohid-shop-app',
    version:  1,
  },

  // پیش‌فرض‌های اشتراک
  defaults: {
    timezone:       process.env.DEFAULT_TIMEZONE || 'Asia/Kabul',
    maxDevices:     num(process.env.DEFAULT_MAX_DEVICES, 1),
    graceDays:      num(process.env.DEFAULT_GRACE_DAYS, 0),
    licenseTtlDays: process.env.DEFAULT_LICENSE_TTL_DAYS === '' ? null
                    : num(process.env.DEFAULT_LICENSE_TTL_DAYS, null),
  },

  // طول عمر توکن‌ها (میلی‌ثانیه)
  tokens: {
    accessTtlMs:  num(process.env.ACCESS_TOKEN_TTL_MIN, 60) * 60 * 1000,
    refreshTtlMs: num(process.env.REFRESH_TOKEN_TTL_DAYS, 60) * 24 * 60 * 60 * 1000,
    adminTtlMs:   num(process.env.ADMIN_TOKEN_TTL_HOURS, 12) * 60 * 60 * 1000,
  },

  // محدودیت نرخ درخواست
  rateLimit: {
    windowMs:      num(process.env.RATE_WINDOW_MS, 15 * 60 * 1000),
    authMax:       num(process.env.RATE_AUTH_MAX, 10),
    generalMax:    num(process.env.RATE_GENERAL_MAX, 300),
    lockoutTries:  num(process.env.LOGIN_LOCKOUT_TRIES, 8),
    lockoutMs:     num(process.env.LOGIN_LOCKOUT_MIN, 15) * 60 * 1000,
  },

  // CORS — دامنه‌هایی که برنامه از آن‌ها سرو می‌شود
  corsOrigins: (process.env.CORS_ORIGINS || '')
    .split(',').map(s => s.trim()).filter(Boolean),

  allowRegistration: bool(process.env.ALLOW_REGISTRATION, true),
  trustProxy:        bool(process.env.TRUST_PROXY, false),
};

module.exports = config;
