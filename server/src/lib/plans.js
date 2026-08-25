'use strict';
/**
 * پلن‌های اشتراک و تنظیمات قابل ویرایش.
 *
 * قیمت‌ها، مدت‌ها و قابلیت‌های هر پلن در دیتابیس‌اند و از پنل مدیریت
 * تغییر می‌کنند — برای عوض کردن قیمت لازم نیست کسی به کد دست بزند.
 */
const { randomBytes } = require('crypto');
const { PAID_KEYS } = require('./features');

const DEFAULT_TRIAL_DAYS = 7;

/** پلن‌های اولیه. فقط یک بار و روی دیتابیس خالی نوشته می‌شوند. */
const DEFAULT_PLANS = [
  { code: 'w1',     title: '۱ هفته', amount: 1, unit: 'week',  price: 100,  sort: 10 },
  { code: 'm1',     title: '۱ ماه',  amount: 1, unit: 'month', price: 300,  sort: 20 },
  { code: 'm3',     title: '۳ ماه',  amount: 3, unit: 'month', price: 800,  sort: 30 },
  { code: 'm6',     title: '۶ ماه',  amount: 6, unit: 'month', price: 1500, sort: 40 },
  { code: 'y1',     title: '۱ سال',  amount: 1, unit: 'year',  price: 2800, sort: 50, badge: 'پیشنهاد ما' },
  { code: 'y2',     title: '۲ سال',  amount: 2, unit: 'year',  price: 5000, sort: 60 },
  { code: 'y3',     title: '۳ سال',  amount: 3, unit: 'year',  price: 6800, sort: 70, badge: 'بیشترین صرفه' },
  { code: 'custom', title: 'دلخواه', amount: null, unit: null, price: 0, sort: 80, negotiable: true },
];

const DEFAULT_CONFIG = {
  trial_days: String(DEFAULT_TRIAL_DAYS),
  whatsapp_number: '0792236008',
  whatsapp_message: 'سلام، می‌خواهم اشتراک برنامه توحید را بخرم.',
  currency: 'افغانی',
};

function newId(prefix) { return `${prefix}_${randomBytes(9).toString('hex')}`; }

/** روزهای تقریبی یک پلن — برای محاسبه‌ی قیمت روزانه. */
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

function seedDefaults(db) {
  const t = Date.now();
  const count = db.prepare('SELECT COUNT(*) n FROM plans').get().n;
  if (count === 0) {
    const insert = db.prepare(`
      INSERT INTO plans (id,code,title,amount,unit,price_afn,negotiable,features,max_devices,badge,sort_order,active,created_at,updated_at)
      VALUES (@id,@code,@title,@amount,@unit,@price,@negotiable,'[]',5,@badge,@sort,1,@t,@t)
    `);
    const tx = db.transaction(() => {
      for (const p of DEFAULT_PLANS) {
        insert.run({
          id: newId('plan'), code: p.code, title: p.title,
          amount: p.amount ?? null, unit: p.unit ?? null,
          price: p.price, negotiable: p.negotiable ? 1 : 0,
          badge: p.badge || '', sort: p.sort, t,
        });
      }
    });
    tx();
  }
  const putCfg = db.prepare(`INSERT INTO app_config (key,value,updated_at) VALUES (?,?,?)
                             ON CONFLICT(key) DO NOTHING`);
  for (const [k, v] of Object.entries(DEFAULT_CONFIG)) putCfg.run(k, v, t);
}

// ---------- تنظیمات ----------
function getConfig(db) {
  const rows = db.prepare('SELECT key, value FROM app_config').all();
  const out = { ...DEFAULT_CONFIG };
  for (const r of rows) out[r.key] = r.value;
  return out;
}
function setConfig(db, key, value) {
  db.prepare(`INSERT INTO app_config (key,value,updated_at) VALUES (?,?,?)
              ON CONFLICT(key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at`)
    .run(key, String(value), Date.now());
}
function trialDays(db) {
  const n = Number(getConfig(db).trial_days);
  return Number.isFinite(n) && n >= 0 ? Math.round(n) : DEFAULT_TRIAL_DAYS;
}

// ---------- پلن‌ها ----------
function parseFeatures(json) {
  try { const v = JSON.parse(json); return Array.isArray(v) ? v : []; } catch { return []; }
}

function decorate(row) {
  const feats = parseFeatures(row.features);
  const days = approxDays(row.amount, row.unit);
  const perDay = (!row.negotiable && days > 0 && row.price_afn > 0)
    ? Math.round((row.price_afn / days) * 10) / 10 : null;
  const perMonth = (!row.negotiable && days > 0 && row.price_afn > 0)
    ? Math.round(row.price_afn / (days / 30)) : null;
  return {
    id: row.id, code: row.code, title: row.title,
    amount: row.amount, unit: row.unit,
    price: row.price_afn, negotiable: !!row.negotiable,
    // خالی یعنی همه‌ی قابلیت‌های اشتراکی
    features: feats.length ? feats : PAID_KEYS.slice(),
    maxDevices: row.max_devices, badge: row.badge,
    sortOrder: row.sort_order, active: !!row.active,
    approxDays: days, pricePerDay: perDay, pricePerMonth: perMonth,
  };
}

function listPlans(db, { includeInactive = false } = {}) {
  const rows = includeInactive
    ? db.prepare('SELECT * FROM plans ORDER BY sort_order, price_afn').all()
    : db.prepare('SELECT * FROM plans WHERE active = 1 ORDER BY sort_order, price_afn').all();
  return rows.map(decorate);
}

function getPlan(db, code) {
  const row = db.prepare('SELECT * FROM plans WHERE code = ?').get(code);
  return row ? decorate(row) : null;
}

function updatePlan(db, code, patch) {
  const row = db.prepare('SELECT * FROM plans WHERE code = ?').get(code);
  if (!row) return null;
  const fields = {};
  if (patch.title !== undefined) fields.title = String(patch.title).slice(0, 60);
  if (patch.price !== undefined) fields.price_afn = Math.max(0, Math.round(Number(patch.price) || 0));
  if (patch.amount !== undefined) fields.amount = patch.amount === null ? null : Math.max(1, Math.round(Number(patch.amount) || 1));
  if (patch.unit !== undefined) fields.unit = patch.unit === null ? null : String(patch.unit);
  if (patch.negotiable !== undefined) fields.negotiable = patch.negotiable ? 1 : 0;
  if (patch.badge !== undefined) fields.badge = String(patch.badge).slice(0, 40);
  if (patch.sortOrder !== undefined) fields.sort_order = Math.round(Number(patch.sortOrder) || 0);
  if (patch.active !== undefined) fields.active = patch.active ? 1 : 0;
  if (patch.maxDevices !== undefined) fields.max_devices = Math.min(50, Math.max(1, Math.round(Number(patch.maxDevices) || 1)));
  if (patch.features !== undefined) {
    const { sanitizeFeatures } = require('./features');
    fields.features = JSON.stringify(sanitizeFeatures(patch.features));
  }
  if (!Object.keys(fields).length) return decorate(row);
  fields.updated_at = Date.now();
  const cols = Object.keys(fields);
  db.prepare(`UPDATE plans SET ${cols.map(c => `${c}=@${c}`).join(', ')} WHERE code=@code`)
    .run({ ...fields, code });
  return getPlan(db, code);
}

function createPlan(db, input) {
  const t = Date.now();
  const code = String(input.code || '').trim().toLowerCase().replace(/[^a-z0-9_]/g, '');
  if (!code) throw new Error('کد پلن نامعتبر است');
  if (db.prepare('SELECT id FROM plans WHERE code=?').get(code)) throw new Error('این کد پلن قبلاً وجود دارد');
  db.prepare(`INSERT INTO plans (id,code,title,amount,unit,price_afn,negotiable,features,max_devices,badge,sort_order,active,created_at,updated_at)
              VALUES (?,?,?,?,?,?,?,?,?,?,?,1,?,?)`)
    .run(newId('plan'), code, String(input.title || code).slice(0, 60),
         input.amount ?? null, input.unit ?? null,
         Math.max(0, Math.round(Number(input.price) || 0)), input.negotiable ? 1 : 0,
         JSON.stringify(Array.isArray(input.features) ? input.features : []),
         Math.max(1, Math.round(Number(input.maxDevices) || 5)),
         String(input.badge || '').slice(0, 40), Math.round(Number(input.sortOrder) || 99), t, t);
  return getPlan(db, code);
}

function deletePlan(db, code) {
  return db.prepare('DELETE FROM plans WHERE code=?').run(code).changes > 0;
}

module.exports = {
  DEFAULT_TRIAL_DAYS, DEFAULT_PLANS, DEFAULT_CONFIG,
  seedDefaults, getConfig, setConfig, trialDays,
  listPlans, getPlan, updatePlan, createPlan, deletePlan,
  approxDays, parseFeatures,
};
