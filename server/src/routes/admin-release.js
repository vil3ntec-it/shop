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
 */
const express = require('express');
const liveConfig = require('../lib/live-config');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { reasonOf } = require('../lib/admin-access');
const { badRequest } = require('../middleware/errors');

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

module.exports = router;
