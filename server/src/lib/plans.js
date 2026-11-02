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

/**
 * ⛔ **دورهٔ آزمایشی — یک منبع (F0-13).**
 *
 *   • **مقدار** فقط در `app_config` است: `trial_days` (دکان) و
 *     `pump_trial_days` (پمپ) — از پنلِ مدیر عوض می‌شود.
 *   • **پیش‌فرض** فقط همین `TRIAL_DEFAULT` است: همان که سرِ هر راه‌اندازی
 *     (`seedDefaults`، `ON CONFLICT DO NOTHING`) کاشته می‌شود و همان که
 *     `trialDaysOf` وقتی ردیفی نیست برمی‌گرداند.
 *   • **خواننده** فقط `trialDaysOf(app)` است — `entitlement`، فهرستِ مدیر،
 *     `/config`، `/plans` و `/me/plans` همه از همین.
 *
 * ⚠️ پیش از F0-13 دو عدد بود: کاشته ۷ و پشتوانه ۱۴ (و `TRIAL_DAYS`ی در
 * محیط که هیچ جا خوانده نمی‌شد). هر نصبی ردیفِ کاشته را داشت، پس **در عمل**
 * دکان ۷ روز بود؛ همان ماند. عددِ نهایی منتظرِ تصمیمِ D-03 است — و چون
 * ردیف فقط یک بار کاشته می‌شود، عوض کردنِ این پیش‌فرض روی نصب‌های موجود
 * اثری ندارد؛ آن‌جا باید `trial_days` را در پنل عوض کرد.
 *
 * پمپ یک ماه — خواستهٔ صریحِ صاحب سامانه (۱۴۰۵/۰۷/۰۸): «برای کسایی که
 * تازه حساب افتتاح می‌کنن هم یک ماه رایگان داده بشه.»
 *
 * ⚠️ مقدارِ نوشته‌شده در تنظیمات همیشه جلوتر است، پس نصبی که خودش عددی
 * گذاشته با به‌روزرسانی چیزی‌اش عوض نمی‌شود.
 */
const TRIAL_DEFAULT = Object.freeze({ shop: '7', pump: '30' });

const DEFAULT_CONFIG = {
  trial_days: TRIAL_DEFAULT.shop,
  /*
   *  ⚠️ واحدِ پولِ بخشِ پمپ جداست و باید جدا بماند.
   *
   *  قیمتِ پلن‌های پمپ را صاحب مخزن به **دالر** داد (۱۲۰ · ۱۵۰ · ۶۰۰)
   *  در حالی که دکان به افغانی است. با یک `currency`ِ مشترک، صفحهٔ
   *  اشتراکِ پمپ «۱۲۰ افغانی» می‌نوشت — دو رقم کم‌تر از حقیقت، و
   *  مستقیم روی پول.
   */
  pump_currency: 'دالر',
  /*
   *  ⛔ **یک ماه رایگان برای پمپِ تازه** — خواستهٔ صریحِ صاحب سامانه
   *  (۱۴۰۵/۰۷/۰۸). تا دیروز این کلید اصلاً **کاشته نمی‌شد**، یعنی
   *  `/api/pump/plans` صفر می‌گفت در حالی که `entitlement.js` از
   *  پیش‌فرضِ خودش دوره را باز نگه می‌داشت — دو حرفِ جدا از یک سرور.
   *
   *  ⚠️ `ON CONFLICT DO NOTHING` است، پس نصبی که خودش عددی نوشته
   *  دست نمی‌خورد.
   */
  pump_trial_days: TRIAL_DEFAULT.pump,
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
  /*
   *  هر بخش پلن‌های خودش را دارد.
   *
   *  ── چرا ────────────────────────────────────────────────────────
   *  خواستهٔ صاحب مخزن: «شاپ و پمپ ربطی به هم نداشته باشند، حتی یک
   *  ذره.» تا دیروز جدول یکی بود، یعنی **قیمتِ دکان همان قیمتِ پمپ
   *  بود** و تخفیفی که برای دکان‌ها گذاشته می‌شد روی پمپ‌ها هم
   *  می‌نشست. این از هر اشتراکِ نشتی‌ای بدتر است: مستقیم روی پول.
   */
  for (const app of ['shop', 'pump']) {
    const { n } = await one('SELECT COUNT(*)::int AS n FROM plans WHERE app=$1', [app]);
    if (n > 0) continue;
    for (const p of DEFAULT_PLANS) {
      await query(
        `INSERT INTO plans (id,code,title,amount,unit,price_afn,negotiable,features,max_devices,badge,sort_order,active,created_at,updated_at,app)
         VALUES ($1,$2,$3,$4,$5,$6,false,'[]'::jsonb,10,$7,$8,true,$9,$9,$10)`,
        [newId('plan'), p.code, p.title, p.amount, p.unit, p.price, p.badge, p.sort, t, app]
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

/**
 * تخفیفِ یک پلن، همین حالا.
 *
 * ── چرا قیمتِ اصلی دست نمی‌خورد ────────────────────────────────────
 * تخفیف کنارِ قیمت می‌نشیند، نه به‌جایش. پس وقتی مهلتِ تخفیف تمام شد،
 * قیمتِ خودش برمی‌گردد و کسی لازم نیست عددِ قبلی را به یاد داشته باشد.
 * برنامه هم می‌تواند هر دو را نشان بدهد: خط‌خورده و تازه.
 *
 * دو راه هست و اگر هر دو پر باشند، «قیمتِ تخفیفی» می‌چربد — چون عددِ
 * صریح از درصد روشن‌تر است و اشتباهِ گِردکردن ندارد.
 */
function discountOf(row, at = Date.now()) {
  const price = Number(row.price_afn) || 0;
  const until = row.discount_until === null || row.discount_until === undefined
    ? null : Number(row.discount_until);
  const expired = until !== null && until < at;
  const percent = Number(row.discount_percent) || 0;
  const fixed = row.discount_price === null || row.discount_price === undefined
    ? null : Number(row.discount_price);

  if (expired || (percent <= 0 && fixed === null)) {
    return { price, finalPrice: price, discounted: false, percent: 0, savings: 0, label: '', until };
  }

  const finalPrice = fixed !== null
    ? Math.max(0, fixed)
    : Math.max(0, Math.round(price * (100 - percent) / 100));

  //  اگر «تخفیف» گران‌تر یا مساوی درآمد، تخفیفی در کار نیست
  if (finalPrice >= price) {
    return { price, finalPrice: price, discounted: false, percent: 0, savings: 0, label: '', until };
  }

  return {
    price,
    finalPrice,
    discounted: true,
    //  درصدِ واقعی، حتی وقتی مدیر قیمتِ ثابت گذاشته — برای نشانِ «٪۲۰»
    percent: price > 0 ? Math.round((price - finalPrice) * 100 / price) : 0,
    savings: price - finalPrice,
    label: row.discount_label || '',
    until,
  };
}

function shapePlan(row, at = Date.now()) {
  const d = discountOf(row, at);
  return {
    code: row.code, title: row.title, amount: row.amount, unit: row.unit,
    //  `price` همان چیزی است که باید پرداخت شود؛ قیمتِ پیش از تخفیف
    //  جداگانه می‌آید. این‌طور برنامه‌های قدیمی که تخفیف را نمی‌شناسند
    //  هم عددِ درست را نشان می‌دهند، نه قیمتِ گران‌ترِ بی‌تخفیف.
    price: d.finalPrice,
    fullPrice: d.price,
    discount: d.discounted
      ? { percent: d.percent, savings: d.savings, label: d.label, until: d.until }
      : null,
    negotiable: row.negotiable, badge: row.badge,
    features: row.features, maxDevices: row.max_devices, active: row.active,
    days: approxDays(row.amount, row.unit),
  };
}

/**
 * پلن‌های یک بخش.
 *
 * `app` نیامده یعنی `shop` — پس هر کدی که از قبل این را بی‌آرگومان
 * صدا می‌زد، همان پلن‌های دکان را می‌گیرد و چیزی عوض نشده.
 */
async function listPlans({ activeOnly = true, app = 'shop' } = {}) {
  const rows = await many(
    `SELECT * FROM plans WHERE app=$1 ${activeOnly ? 'AND active = true' : ''}
      ORDER BY sort_order ASC, created_at ASC`,
    [app]
  );
  const at = Date.now();
  return rows.map(r => shapePlan(r, at));
}

/*
 *  `run` = اتصالِ تراکنشِ جاری (`(sql, params) ⇒ {rows}`)؛ نیامده ⇒ استخر. درونِ تراکنش از همان اتصال، نه
 *  اتصالِ دوم ِ استخر — وگرنه تراکنش‌های هم‌زمانِ پشتِ یک قفل همهٔ استخر را نگه می‌دارند و این پرسش اتصال
 *  نمی‌گیرد (همان RSK-07 ِ `orders.flagOf`). خواندن همان است: READ COMMITTED همان ردیفِ ثبت‌شده را می‌بیند.
 */
async function getPlan(code, app = 'shop', run = null) {
  const sql = 'SELECT * FROM plans WHERE code=$1 AND app=$2';
  return run ? (await run(sql, [code, app])).rows[0] || null : one(sql, [code, app]);
}


/** کلید و پیش‌فرضِ دورهٔ آزمایشیِ یک بخش — تنها جای تصمیم. */
function trialConfig(app = 'shop') {
  const key = app === 'shop' ? 'trial_days' : `${app}_trial_days`;
  return { key, def: TRIAL_DEFAULT[app] || TRIAL_DEFAULT.shop };
}

/** روزهای دورهٔ آزمایشیِ یک بخش، از تنظیمات یا پیش‌فرض. */
async function trialDaysOf(app = 'shop', run = null) {
  const { key, def } = trialConfig(app);
  return Number(await getConfig(key, def, run)) || 0;
}

/** `run` مثلِ `getPlan`. */
async function getConfig(key, def = '', run = null) {
  const sql = 'SELECT value FROM app_config WHERE key=$1';
  const r = run ? (await run(sql, [key])).rows[0] : await one(sql, [key]);
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

/**
 * ⛔ تنها چیزی از `app_config` که از درِ مدیریت بیرون می‌رود.
 *
 * `app_config` جای **رازها** هم هست: کلیدِ خصوصیِ امضای مجوز
 * (`license_private_key`)، کلیدِ خصوصیِ پوش (`vapid_private`)، رمزِ SMTP
 * (`email_pass`)، کلیدِ پیامک و رمزِ خواندنِ پمپ‌ها. تا امروز
 * `GET /api/admin/plans` و `PATCH /api/admin/config` کلِ جدول را
 * (`allConfig()`) در پاسخ می‌گذاشتند — یعنی کلیدِ امضای مجوز به مرورگرِ هر
 * مدیری، و از راهِ پنلِ خانگی به هر کاربرِ واردشدهٔ آن پنل، می‌رسید. با آن
 * کلید هر کسی برای هر دستگاهی مجوزِ دلخواه امضا می‌کرد.
 *
 * پس فهرستِ **سفید** است، نه فهرستِ سیاه: کلیدِ تازه‌ای که فردا در
 * `app_config` بنشیند تا کسی صریح این‌جا ننویسدش بیرون نمی‌رود.
 * `allConfig()` برای مصرفِ **داخلیِ** سرور سرِ جایش است.
 */
const PUBLIC_CONFIG_KEYS = Object.freeze([
  'trial_days', 'pump_trial_days',
  'whatsapp_number', 'whatsapp_message',
  'currency', 'pump_currency',
  'min_app_version', 'brand_name',
]);

async function publicConfig() {
  const all = await allConfig();
  const out = {};
  for (const k of PUBLIC_CONFIG_KEYS) if (all[k] !== undefined) out[k] = all[k];
  return out;
}

module.exports = {
  DEFAULT_PLANS, DEFAULT_CONFIG, approxDays, endOfPeriod,
  seedDefaults, listPlans, getPlan, getConfig, setConfig, allConfig,
  publicConfig, PUBLIC_CONFIG_KEYS,
  TRIAL_DEFAULT, trialConfig, trialDaysOf,
  discountOf, shapePlan,
};
