'use strict';
/**
 * همگام‌سازیِ زندهٔ تنظیماتِ دکان — `/api/shop/settings-sync` (مهاجرتِ ۳۰۰؛ `docs/DATA-SAFETY-fa.md` «تنظیماتِ دکان»).
 *
 * پیش‌تر قاعده‌های دکان (گردکردنِ نقد، کارمزد، قاعدهٔ برگشت، مالیات…) فقط با پشتیبانِ ابریِ دوازده‌ساعته به سرور
 * می‌رسیدند. حالا هر کلیدِ «مالِ دکان» یک ردیف است و دستگاه‌ها با سوکتِ زنده (`{event:'settings', rev}`) و هر
 * وصل (`hello`) تازه می‌شوند. کدام کلید مالِ دکان است و کدام مالِ هر دستگاه (چاپگر، کشو، بارکدخوان، صفحه، آموزش):
 * `ShopSettingsKeys.kt` ِ برنامه — سرور فقط شکلِ کلید و مقدار را می‌سنجد؛ برنامه کلیدِ بیرون از فهرستِ خودش را
 * هرگز نمی‌نشاند.
 *
 * ── قاعدهٔ تعارض ─────────────────────────────────────────────────────────────
 * آخرین نویسنده برنده، **هر کلید جدا**، با ساعتِ سرور: هر نوشتن زیرِ قفلِ همان دکان `rev` ِ تازه (یکی بیش از
 * بزرگ‌ترین) و `updated_at = now()` می‌گیرد؛ ساعتِ دستگاه هیچ‌جا خوانده نمی‌شود. دو کلیدِ جدا هرگز هم را نمی‌برند.
 * هر تغییر یک ردیفِ `shop_setting_keys_log` (پیش، پس، کاربر، نقش، دستگاه، ساعتِ سرور) و یک خبرِ سابقهٔ دکان
 * (`settings.sync`). نوشتنِ همان مقدار هیچ ردیف و `rev` ای نمی‌سازد.
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.settings_sync`؛ خاموش ⇒ `GET` فقط `{enabled:false}` و `PUT` ۴۰۳ `settings_sync_disabled`.
 * ⛔ نوشتن فقط صاحب و مدیر (`settings.write` و نقشِ پایهٔ همان دو)؛ خواندن هر عضو (`data.read`: صندوقِ کارمند هم باید
 *    قاعدهٔ دکان را داشته باشد). shop_id فقط از عضویتِ توکن؛ پمپ به این در نمی‌رسد (`requireShop`).
 * ⛔ سقف‌ها: ۲۰۰ کلید در هر نوشتن، ۵۰۰ کلید برای هر دکان، رشته ۴۰۹۶ نویسه، مجموعه ۲۰۰ × ۲۵۶.
 */
const { one, many, tx, now, newId } = require('../db');
const { flagOf } = require('./orders');
const { badRequest, forbidden } = require('../middleware/errors');

const FLAG = 'shop.settings_sync';
const STORES = Object.freeze(['tohid-policy', 'tohid-receipt', 'tohid-modules']);
const TYPES = Object.freeze(['s', 'i', 'l', 'f', 'b', 'set', 'del']);
const KEY_RE = /^[a-z0-9_]{1,64}$/;
const WRITERS = Object.freeze(['owner', 'manager']);
const MAX_ENTRIES = 200;
const MAX_KEYS = 500;
const MAX_TEXT = 4096;
const MAX_SET = 200;
const MAX_SET_ITEM = 256;
const PAGE = 1000;
const INT_MIN = -2147483648;
const INT_MAX = 2147483647;

const flagOn = (shopId) => flagOf(shopId, FLAG);

/** مقدارِ درستِ همان نوع، یا ۴۰۰ `bad_setting` */
function valueOf(t, v, where) {
  const bad = () => badRequest(`مقدارِ «${where}» درست نیست`, 'bad_setting');
  switch (t) {
    case 's':
      if (typeof v !== 'string' || v.length > MAX_TEXT) throw bad();
      return v;
    case 'i':
      if (!Number.isInteger(v) || v < INT_MIN || v > INT_MAX) throw bad();
      return v;
    case 'l':
      if (!Number.isSafeInteger(v)) throw bad();
      return v;
    case 'f':
      if (typeof v !== 'number' || !Number.isFinite(v)) throw bad();
      return v;
    case 'b':
      if (typeof v !== 'boolean') throw bad();
      return v;
    case 'set': {
      if (!Array.isArray(v) || v.length > MAX_SET) throw bad();
      const out = [];
      for (const x of v) {
        if (typeof x !== 'string' || x.length > MAX_SET_ITEM) throw bad();
        if (!out.includes(x)) out.push(x);
      }
      return out.sort();
    }
    case 'del':
      return null;
    default:
      throw bad();
  }
}

/** `entries` ِ بدنه ⇒ فهرستِ درست (هر کلید یک بار؛ تکراری ⇒ آخری)، یا ۴۰۰ */
function entriesOf(body) {
  const list = body && typeof body === 'object' && !Array.isArray(body) ? body.entries : undefined;
  if (!Array.isArray(list) || list.length === 0 || list.length > MAX_ENTRIES) {
    throw badRequest(`فهرستِ تنظیم‌ها (۱ تا ${MAX_ENTRIES}) لازم است`, 'bad_setting');
  }
  const out = new Map();
  for (const e of list) {
    if (!e || typeof e !== 'object' || Array.isArray(e)) throw badRequest('تنظیمِ نادرست', 'bad_setting');
    const store = typeof e.store === 'string' ? e.store : '';
    const key = typeof e.key === 'string' ? e.key : '';
    const t = typeof e.t === 'string' ? e.t : '';
    if (!STORES.includes(store)) throw badRequest('انبارِ تنظیم شناخته نیست', 'bad_setting');
    if (!KEY_RE.test(key)) throw badRequest('نامِ تنظیم درست نیست', 'bad_setting');
    if (!TYPES.includes(t)) throw badRequest('نوعِ تنظیم شناخته نیست', 'bad_setting');
    const v = valueOf(t, e.v, key);
    out.set(`${store}\u0000${key}`, { store, key, t, v });
  }
  return [...out.values()];
}

const same = (row, e) => row && row.type === e.t && JSON.stringify(row.value) === JSON.stringify(e.v);

function view(r) {
  return {
    store: r.store, key: r.key, t: r.type, v: r.value,
    rev: Number(r.rev), at: Number(r.updated_at), by: r.updated_by,
  };
}

/** ردیف‌های تازه‌تر از [since] (به ترتیبِ `rev`)، حداکثر یک صفحه */
async function read(shopId, since = 0) {
  const from = Number.isSafeInteger(since) && since > 0 ? since : 0;
  const rows = await many(
    `SELECT store, key, type, value, rev, updated_at, updated_by FROM shop_setting_keys
      WHERE shop_id=$1 AND rev > $2 ORDER BY rev ASC LIMIT $3`,
    [String(shopId), from, PAGE + 1],
  );
  const head = await one('SELECT COALESCE(MAX(rev), 0) AS rev FROM shop_setting_keys WHERE shop_id=$1', [String(shopId)]);
  const more = rows.length > PAGE;
  return { rev: Number(head ? head.rev : 0), rows: rows.slice(0, PAGE).map(view), more };
}

/**
 * نوشتن — آخرین نویسنده (ساعتِ سرور) برنده، هر کلید جدا. همان مقدار ⇒ هیچ.
 * @returns {{ rev, applied: Array<{store,key,rev,at}>, unchanged: number }}
 */
async function write(shopId, actor, body) {
  if (!WRITERS.includes(actor.role)) throw forbidden('فقط صاحب یا مدیرِ دکان تنظیمِ دکان را عوض می‌کند', 'forbidden');
  const entries = entriesOf(body);
  const deviceId = typeof body.device_id === 'string' ? body.device_id.slice(0, 64) : '';
  const shop = String(shopId);
  return tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`shop-settings:${shop}`]);
    const have = (await c.query('SELECT store, key, type, value, rev FROM shop_setting_keys WHERE shop_id=$1', [shop])).rows;
    const byKey = new Map(have.map((r) => [`${r.store}\u0000${r.key}`, r]));
    const fresh = entries.filter((e) => !byKey.has(`${e.store}\u0000${e.key}`)).length;
    if (have.length + fresh > MAX_KEYS) throw badRequest(`بیش از ${MAX_KEYS} تنظیم برای یک دکان`, 'too_many_settings');
    let rev = have.reduce((m, r) => Math.max(m, Number(r.rev)), 0);
    const at = now();
    const applied = [];
    let unchanged = 0;
    for (const e of entries) {
      const old = byKey.get(`${e.store}\u0000${e.key}`);
      //  همان مقدار، یا برداشتنِ کلیدی که سرور هرگز نداشت ⇒ هیچ (نه ردیف، نه `rev`)
      if (same(old, e) || (e.t === 'del' && !old)) { unchanged++; continue; }
      rev += 1;
      const value = JSON.stringify(e.v);
      await c.query(
        `INSERT INTO shop_setting_keys (shop_id, store, key, type, value, rev, updated_at, updated_by, device_id)
         VALUES ($1,$2,$3,$4,$5::jsonb,$6,$7,$8,$9)
         ON CONFLICT (shop_id, store, key) DO UPDATE SET
           type=EXCLUDED.type, value=EXCLUDED.value, rev=EXCLUDED.rev, updated_at=EXCLUDED.updated_at,
           updated_by=EXCLUDED.updated_by, device_id=EXCLUDED.device_id`,
        [shop, e.store, e.key, e.t, value, rev, at, actor.userId, deviceId],
      );
      await c.query(
        `INSERT INTO shop_setting_keys_log (id, shop_id, store, key, old_type, old_value, new_type, new_value, rev, at, by_user, role, device_id)
         VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7,$8::jsonb,$9,$10,$11,$12,$13)`,
        [newId('ssl'), shop, e.store, e.key, old ? old.type : null, old ? JSON.stringify(old.value) : null,
          e.t, value, rev, at, actor.userId, actor.role, deviceId],
      );
      applied.push({ store: e.store, key: e.key, rev, at });
    }
    return { rev, applied, unchanged };
  });
}

/** سابقهٔ تغییرِ کلیدها (تازه‌ترین اول) — برای آزمون و پنل */
async function history(shopId, limit = 50) {
  const n = Math.min(Math.max(Number.parseInt(limit, 10) || 50, 1), 200);
  return many(
    `SELECT store, key, old_type, old_value, new_type, new_value, rev, at, by_user, role, device_id
       FROM shop_setting_keys_log WHERE shop_id=$1 ORDER BY at DESC, rev DESC LIMIT $2`,
    [String(shopId), n],
  );
}

module.exports = {
  FLAG, STORES, TYPES, KEY_RE, WRITERS, MAX_ENTRIES, MAX_KEYS, PAGE,
  flagOn, entriesOf, read, write, history,
};
