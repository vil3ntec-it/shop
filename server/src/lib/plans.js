'use strict';
/**
 * پلن‌های اشتراک و تنظیمات سراسری.
 *
 * قیمت‌ها و مدت‌ها در دیتابیس‌اند و از پنل مدیریت تغییر می‌کنند —
 * برای عوض کردن قیمت لازم نیست کسی به کد دست بزند.
 */
const { query, one, many, newId, now } = require('../db');

const DEFAULT_PLANS = [
  { code: 'm1', title: 'ماهانه', amount: 1, unit: 'month', price: 500,  sort: 10, badge: '' },
  { code: 'm6', title: '۶ ماهه', amount: 6, unit: 'month', price: 2000, sort: 20, badge: 'پیشنهاد ما' },
  { code: 'y1', title: '۱ ساله', amount: 1, unit: 'year',  price: 3000, sort: 30, badge: 'بیشترین صرفه' },
];

const DEFAULT_CONFIG = {
  trial_days: '14',
  whatsapp_number: '0792236008',
  whatsapp_message: 'سلام، می‌خواهم اشتراک برنامه فروشگاه را بخرم.',
  currency: 'افغانی',
};

/** روزهای تقریبی یک پلن. */
function approxDays(amount, unit) {
  if (!amount || !unit) return 0;
  switch (unit) {
    case 'day': return amount;
    case 'week': return amount * 7;
    case 'month': return amount * 30;
    case 'year': return amount * 365;
    default: return 0;
  }
}

/** پایان دوره بر اساس تقویم واقعی — «۱ ماهه» یعنی همین روز در ماه بعد. */
function endOfPeriod(startMs, amount, unit) {
  const d = new Date(startMs);
  switch (unit) {
    case 'day':   d.setUTCDate(d.getUTCDate() + amount); break;
    case 'week':  d.setUTCDate(d.getUTCDate() + amount * 7); break;
    case 'month': d.setUTCMonth(d.getUTCMonth() + amount); break;
    case 'year':  d.setUTCFullYear(d.getUTCFullYear() + amount); break;
    default: return startMs;
  }
  return d.getTime();
}

async function seedDefaults() {
  const t = now();
  const { n } = await one('SELECT COUNT(*)::int AS n FROM plans');
  if (n === 0) {
    for (const p of DEFAULT_PLANS) {
      await query(
        `INSERT INTO plans (id,code,title,amount,unit,price_afn,negotiable,features,max_devices,badge,sort_order,active,created_at,updated_at)
         VALUES ($1,$2,$3,$4,$5,$6,false,'[]'::jsonb,10,$7,$8,true,$9,$9)`,
        [newId('plan'), p.code, p.title, p.amount, p.unit, p.price, p.badge, p.sort, t]
      );
    }
  }
  for (const [k, v] of Object.entries(DEFAULT_CONFIG)) {
    await query(
      'INSERT INTO app_config (key,value,updated_at) VALUES ($1,$2,$3) ON CONFLICT (key) DO NOTHING',
      [k, v, t]
    );
  }
}

async function listPlans({ activeOnly = true } = {}) {
  const rows = await many(
    `SELECT * FROM plans ${activeOnly ? 'WHERE active = true' : ''} ORDER BY sort_order ASC, created_at ASC`
  );
  return rows.map(r => ({
    code: r.code, title: r.title, amount: r.amount, unit: r.unit,
    price: r.price_afn, negotiable: r.negotiable, badge: r.badge,
    features: r.features, maxDevices: r.max_devices, active: r.active,
    days: approxDays(r.amount, r.unit),
  }));
}

async function getPlan(code) {
  return one('SELECT * FROM plans WHERE code=$1', [code]);
}

async function getConfig(key, def = '') {
  const r = await one('SELECT value FROM app_config WHERE key=$1', [key]);
  return r ? r.value : def;
}

async function setConfig(key, value) {
  await query(
    `INSERT INTO app_config (key,value,updated_at) VALUES ($1,$2,$3)
     ON CONFLICT (key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at`,
    [key, String(value), now()]
  );
}

async function allConfig() {
  const rows = await many('SELECT key, value FROM app_config');
  return Object.fromEntries(rows.map(r => [r.key, r.value]));
}

module.exports = {
  DEFAULT_PLANS, DEFAULT_CONFIG, approxDays, endOfPeriod,
  seedDefaults, listPlans, getPlan, getConfig, setConfig, allConfig,
};
