'use strict';
/**
 * پنلِ مدیریت — «کانالِ آزمایشی» (F0-27): کدام دکان و دستگاه نسخهٔ آزمایشی می‌گیرد.
 *
 *   GET /api/admin/release/beta   ⇐ { shops: {ids, updatedAt, updatedBy}, devices: {…}, version }
 *   PUT /api/admin/release/beta   { shops?: [...], devices?: [...], reason? }
 *
 * فهرست همان `live_config`ِ scope `release` (کلیدهای `beta.shops` و `beta.devices`)
 * است که `routes/releases.js` می‌خواند؛ این‌جا فقط درِ پنل است، رفتارِ انتشار
 * دست نمی‌خورد. پیش از این تنها راه `PUT /api/admin/pump/live-config` بود (درِ پمپ)،
 * بی پنل و بی سابقهٔ «چه کسی اضافه/کم شد».
 *
 *   ⛔ فقط مدیرِ ارشد: عضوِ این فهرست هر مرجِ `main` را از به‌روزرسانیِ درون‌برنامه
 *      می‌گیرد.
 *   ⛔ شناسه همان قاعدهٔ `releases.js` (‎[A-Za-z0-9_-]{1,80}‎)؛ شناسهٔ ناجور رد می‌شود،
 *      نه بی‌صدا دور ریخته — وگرنه صاحب محصول خیال می‌کرد دستگاهش آزمایشی است.
 *   ⛔ هر تغییر با «افزوده/برداشته» و دلیلِ اختیاری در سابقه (`admin.release_beta_set`).
 *
 * ── انتشارِ مرحله‌ای (OPS-01، `lib/release-stage.js`) ─────────────────────────
 *   GET    /api/admin/release/stage   ⇐ { stages: { <برچسب>: {version, percent, shops?, since?, halted?, updatedAt, updatedBy} | null },
 *                                          guard: {enabled, …}, version }
 *   PUT    /api/admin/release/stage   { tag, version, percent, shops?, reason? } — شروع، بالا/پایین بردنِ درصد، فهرستِ دکان‌ها،
 *                                          ایست (۰ و بی فهرست)، و ادامه پس از ایستِ خودکار (هر PUT ایست را برمی‌دارد)
 *   DELETE /api/admin/release/stage   { tag, reason? }                     — پایانِ مرحله ⇒ برچسبِ روان برای همه
 *   همان کلیدِ `stage.<برچسب>` که `releases.js` می‌خواند؛ ورودیِ ناجور ۴۰۰ (نه ذخیرهٔ چیزی که
 *   `releases.js` بی‌صدا نادیده بگیرد). هر تغییر در سابقه (`admin.release_stage_set|_end`).
 *   `shops` نیامد ⇒ همان فهرستِ پیشینِ همین نسخه (نسخهٔ تازه ⇒ بی فهرست). `since` = لحظهٔ شروعِ همین نسخه.
 *
 * ── ایستِ خودکار (OPS-01، `lib/release-stage-guard.js`؛ پرچمِ `auto_halt`، پیش‌فرض خاموش) ─────────
 *   GET  /api/admin/release/stage/guard         ⇐ { enabled, settings, flag, checkedAt, results: {<برچسب>: سنجش} }
 *   PUT  /api/admin/release/stage/guard         { enabled?, minErrorDevices?, factor?, floorPercent?, reason? }
 *   POST /api/admin/release/stage/guard/check   ⇐ همان GET، ولی با پرچمِ روشن همین حالا می‌ایستاند
 *   فقط مدیرِ ارشد؛ سقفِ نرخ؛ سابقهٔ `admin.release_guard_set` (و ایستِ خودکار `admin.release_stage_halt`).
 */
const express = require('express');
const liveConfig = require('../lib/live-config');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { reasonOf } = require('../lib/admin-access');
const { badRequest } = require('../middleware/errors');
const { rateLimit } = require('../middleware/ratelimit');
const stage = require('../lib/release-stage');
const guard = require('../lib/release-stage-guard');

const router = express.Router();

const SCOPE = 'release';
const KEYS = { shops: 'beta.shops', devices: 'beta.devices' };
const LABEL = { shops: 'دکان‌ها', devices: 'دستگاه‌ها' };
const ID = /^[A-Za-z0-9_-]{1,80}$/;
const MAX_IDS = 200;

/** مقدارِ ذخیره‌شده ⇐ شناسه‌ها؛ همان خواندنِ `releases.js` (آرایه یا «a, b») */
function stored(value) {
  const raw = Array.isArray(value) ? value : String(value ?? '').split(',');
  return [...new Set(raw.map((x) => String(x).trim()).filter((x) => ID.test(x)))];
}

async function current() {
  const { items, version } = await liveConfig.list(SCOPE);
  const pick = (key) => {
    const it = items.find((i) => i.key === key);
    return { ids: stored(it?.value), updatedAt: it?.updatedAt || 0, updatedBy: it?.updatedBy || '' };
  };
  return { shops: pick(KEYS.shops), devices: pick(KEYS.devices), version };
}

/** ورودیِ پنل ⇐ شناسه‌ها؛ نبودِ فیلد ⇐ undefined (دست نمی‌خورد) */
function parse(input, field) {
  if (input === undefined) return undefined;
  const raw = Array.isArray(input) ? input : String(input ?? '').split(/[\s,،]+/);
  const ids = [...new Set(raw.map((x) => String(x).trim()).filter(Boolean))];
  const bad = ids.filter((x) => !ID.test(x));
  if (bad.length) {
    throw badRequest(`شناسهٔ نامعتبر در ${LABEL[field]}: ${bad.slice(0, 3).join('، ')}`, 'bad_release_id');
  }
  if (ids.length > MAX_IDS) throw badRequest(`حداکثر ${MAX_IDS} شناسه در ${LABEL[field]}`, 'too_many_ids');
  return ids;
}

router.get('/beta', requireSuperAdmin, async (req, res, next) => {
  try { res.json(await current()); } catch (err) { next(err); }
});

router.put('/beta', requireSuperAdmin, async (req, res, next) => {
  try {
    const b = req.body || {};
    const wanted = { shops: parse(b.shops, 'shops'), devices: parse(b.devices, 'devices') };
    if (wanted.shops === undefined && wanted.devices === undefined) {
      throw badRequest('فهرستِ دکان‌ها یا دستگاه‌ها لازم است', 'bad_body');
    }
    const reason = reasonOf(req);   // اختیاری — فقط در سابقه
    const before = await current();
    const by = req.admin?.username || req.admin?.id || '';
    const changes = {};
    for (const field of ['shops', 'devices']) {
      const ids = wanted[field];
      if (ids === undefined) continue;
      const prev = before[field].ids;
      const added = ids.filter((x) => !prev.includes(x));
      const removed = prev.filter((x) => !ids.includes(x));
      if (!added.length && !removed.length) continue;
      await liveConfig.set(SCOPE, KEYS[field], ids, by);
      //  رشته، نه آرایه: `audit.scrub` آرایه را شیءِ {0,1,…} می‌کند
      changes[field] = { added: added.join(', '), removed: removed.join(', '), count: ids.length };
    }
    if (Object.keys(changes).length) {
      await audit.log({
        actorType: 'admin', userId: req.admin.id, action: 'admin.release_beta_set',
        targetType: 'live_config', targetId: `${SCOPE}:beta`,
        detail: { ...changes, ...(reason ? { reason } : {}) },
      });
      //  فهرستِ آزمایشی در `releases.js` ۶۰ ثانیه در حافظه است — همان دم تازه شود
      require('./releases')._forgetBeta();
    }
    res.json({ ok: true, changed: Object.keys(changes), ...(await current()) });
  } catch (err) { next(err); }
});

// ── انتشارِ مرحله‌ای (OPS-01) ─────────────────────────────────────────

const STAGE_TAGS = require('./releases').STABLE;

async function stages() {
  const { items, version } = await liveConfig.list(SCOPE);
  const out = {};
  for (const tag of STAGE_TAGS) {
    const it = items.find((i) => i.key === `${stage.KEY_PREFIX}${tag}`);
    const s = it ? stage.parseStage(it.value) : null;
    out[tag] = s ? { ...s, updatedAt: it.updatedAt || 0, updatedBy: it.updatedBy || '' } : null;
  }
  const g = await guard.settings();
  return { stages: out, guard: { enabled: g.enabled, minErrorDevices: g.minErrorDevices, factor: g.factor, floorPercent: g.floorPercent }, version };
}

/** فهرستِ دکان‌های مرحله از ورودیِ پنل؛ نبود ⇒ undefined */
function stageShops(input) {
  if (input === undefined) return undefined;
  const raw = Array.isArray(input) ? input : String(input ?? '').split(/[\s,،]+/);
  const ids = [...new Set(raw.map((x) => String(x).trim()).filter(Boolean))];
  const bad = ids.filter((x) => !ID.test(x));
  if (bad.length) throw badRequest(`شناسهٔ نامعتبر در دکان‌ها: ${bad.slice(0, 3).join('، ')}`, 'bad_release_id');
  if (ids.length > stage.MAX_SHOPS) throw badRequest(`حداکثر ${stage.MAX_SHOPS} دکان در یک مرحله`, 'too_many_ids');
  return ids;
}

const shopsKey = (s) => [...((s && s.shops) || [])].sort().join(',');
const label = (s) => (s ? `${s.version}@${s.percent}${s.shops ? `+${s.shops.length}` : ''}${s.halted ? '!' : ''}` : '');

function stageTag(b) {
  const tag = String(b.tag || '');
  if (!STAGE_TAGS.includes(tag)) throw badRequest(`برچسب یکی از ${STAGE_TAGS.join('، ')}`, 'bad_release_tag');
  return tag;
}

router.get('/stage', requireSuperAdmin, async (req, res, next) => {
  try { res.json(await stages()); } catch (err) { next(err); }
});

router.put('/stage', requireSuperAdmin, async (req, res, next) => {
  try {
    const b = req.body || {};
    const tag = stageTag(b);
    const base = stage.parseStage({ version: b.version, percent: b.percent });
    if (!base) throw badRequest('نسخه (مثلِ 2.14.5) و درصدِ صحیحِ ۰ تا ۱۰۰ لازم است', 'bad_stage');
    const shops = stageShops(b.shops);
    const reason = reasonOf(req);
    const prev = (await stages()).stages[tag];
    const same = !!prev && prev.version === base.version;
    //  فهرست نیامد ⇒ همان فهرستِ همین نسخه؛ `since` فقط با نسخهٔ تازه از نو. ایست با هر PUT برداشته می‌شود
    //  (تصمیمِ تازهٔ مدیر)؛ نگهبانِ خودکار هرگز خودش دوباره راه نمی‌اندازد.
    const list = shops !== undefined ? shops : (same && prev.shops) || [];
    const want = {
      version: base.version, percent: base.percent, ...(list.length ? { shops: list } : {}),
      since: same && prev.since ? prev.since : require('../db').now(),
    };
    if (!prev || !same || prev.percent !== want.percent || shopsKey(prev) !== shopsKey(want) || prev.halted) {
      await liveConfig.set(SCOPE, `${stage.KEY_PREFIX}${tag}`, want, req.admin?.username || req.admin?.id || '');
      await audit.log({
        actorType: 'admin', userId: req.admin.id, action: 'admin.release_stage_set',
        targetType: 'live_config', targetId: `${SCOPE}:${stage.KEY_PREFIX}${tag}`,
        detail: {
          tag, version: want.version, percent: want.percent, shops: list.join(', '),
          from: label(prev), ...(prev && prev.halted ? { resumed: true } : {}), ...(reason ? { reason } : {}),
        },
      });
      require('./releases')._forgetBeta();
    }
    res.json({ ok: true, ...(await stages()) });
  } catch (err) { next(err); }
});

router.delete('/stage', requireSuperAdmin, async (req, res, next) => {
  try {
    const b = { ...(req.query || {}), ...(req.body || {}) };
    const tag = stageTag(b);
    const reason = reasonOf(req);
    const prev = (await stages()).stages[tag];
    const out = await liveConfig.remove(SCOPE, `${stage.KEY_PREFIX}${tag}`);
    if (out.removed) {
      await audit.log({
        actorType: 'admin', userId: req.admin.id, action: 'admin.release_stage_end',
        targetType: 'live_config', targetId: `${SCOPE}:${stage.KEY_PREFIX}${tag}`,
        detail: { tag, from: label(prev), ...(reason ? { reason } : {}) },
      });
      require('./releases')._forgetBeta();
    }
    res.json({ ok: true, removed: out.removed, ...(await stages()) });
  } catch (err) { next(err); }
});

// ── ایستِ خودکار (OPS-01، `lib/release-stage-guard.js`) ─────────────────────

const guardLimit = rateLimit({
  max: 30, windowMs: 10 * 60 * 1000, keyPrefix: 'admin-release-guard', key: (req) => String(req.admin?.id || ''),
});

/** عددِ ورودیِ پنل در بازه؛ نبود ⇒ undefined؛ ناجور ⇒ ۴۰۰ */
function guardNumber(b, field, integer) {
  const v = b[field];
  if (v === undefined) return undefined;
  const [min, max] = guard.BOUNDS[field];
  if (typeof v !== 'number' || !Number.isFinite(v) || v < min || v > max || (integer && !Number.isInteger(v))) {
    throw badRequest(`${field} باید عددِ ${min} تا ${max} باشد`, 'bad_guard');
  }
  return v;
}

async function guardView(act) {
  const out = await guard.run({ act });
  return { ...out, flag: guard.FLAG };
}

router.get('/stage/guard', requireSuperAdmin, guardLimit, async (req, res, next) => {
  try { res.set('Cache-Control', 'no-store'); res.json(await guardView(false)); } catch (err) { next(err); }
});

router.post('/stage/guard/check', requireSuperAdmin, guardLimit, async (req, res, next) => {
  try {
    const out = await guardView(true);
    res.set('Cache-Control', 'no-store');
    res.json({ ...out, ...(await stages()) });
  } catch (err) { next(err); }
});

router.put('/stage/guard', requireSuperAdmin, guardLimit, async (req, res, next) => {
  try {
    const b = req.body || {};
    if (b.enabled !== undefined && typeof b.enabled !== 'boolean') throw badRequest('enabled باید درست/نادرست باشد', 'bad_guard');
    const input = {
      enabled: b.enabled,
      minErrorDevices: guardNumber(b, 'minErrorDevices', true),
      factor: guardNumber(b, 'factor', false),
      floorPercent: guardNumber(b, 'floorPercent', false),
    };
    if (Object.values(input).every((v) => v === undefined)) throw badRequest('دست‌کم یکی از enabled، minErrorDevices، factor، floorPercent', 'bad_body');
    const reason = reasonOf(req);
    const { before, after } = await guard.configure(input, req.admin?.username || req.admin?.id || '');
    const pick = (x) => `${x.enabled ? 'on' : 'off'} ${x.minErrorDevices}/${x.factor}x/${x.floorPercent}%`;
    if (pick(before) !== pick(after)) {
      await audit.log({
        actorType: 'admin', userId: req.admin.id, action: 'admin.release_guard_set',
        targetType: 'live_config', targetId: `${SCOPE}:${guard.FLAG.key}`,
        detail: { from: pick(before), to: pick(after), ...(reason ? { reason } : {}) },
      });
    }
    res.json({ ok: true, ...(await guardView(false)) });
  } catch (err) { next(err); }
});

module.exports = router;
