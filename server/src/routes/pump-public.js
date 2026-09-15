'use strict';
/**
 * ══ کیو‌آرِ زندهٔ مشتری — تنها مسیرِ عمومیِ بخشِ پمپ ══════════════════════
 *
 * ── خواستهٔ صاحب مخزن ──────────────────────────────────────────────
 * «من دارم زنده تغییرات میارم توی حسابِ طرف، طرف هم داره با کیو‌آر حسابشو
 *  چک می‌کنه و می‌خوام درجا برای اون هم بره… که هر دقیقه بتونه چک کنه.»
 *
 * ── راه ───────────────────────────────────────────────────────────
 *
 *   برنامهٔ کامپیوتر ──PUT files/acct-<شناسه>──▶ station_files   {v, k, at, d}
 *   گوشیِ مشتری     ──GET /pump/public/<کد>/acct/<شناسه>?k=…──▶ {at, d}
 *
 * مشتری هیچ حسابی ندارد و نباید داشته باشد؛ چیزی که دارد رمزِ **همان یک
 * حساب** است (‎k‎) که داخلِ کیو‌آرش چاپ شده. این‌جا آن رمز با رمزِ داخلِ
 * فایل مقایسه می‌شود (زمان‌ثابت) و فقط ‎{at, d}‎ برمی‌گردد — خودِ ‎k‎ هرگز.
 *
 * ⚠️ «پمپ نیست»، «فایل نیست» و «رمز غلط» عمداً یک جواب دارند (‎404‎)، تا
 * کسی با آزمون‌وخطا نفهمد کدام پمپ‌ها و کدام حساب‌ها هستند.
 *
 * ⚠️ CORS این مسیر باز است (‎*‎): صفحهٔ مشتری روی دامنهٔ خودِ پمپ است
 * (‎yaqobipump.top/view‎) و از این‌جا می‌پرسد. داده‌ای که پشتِ رمزِ همان
 * حساب است، برای همان حساب عمومی است؛ چیزِ دیگری از این در بیرون نمی‌رود.
 *
 * ⚠️ در ‎app.js‎ باید **پیش از** ‎/pump‎ سوار شود: آن روتر ‎requirePumpUser‎ی
 * سراسری دارد و این مسیر عمداً بی‌توکن است.
 */
const express = require('express');
const crypto = require('crypto');
const { one, now } = require('../db');
const config = require('../config');
const stations = require('../lib/stations');
const { rateLimit } = require('../middleware/ratelimit');
const { notFound } = require('../middleware/errors');

const router = express.Router();

/** همان پیشوندی که برنامه برای فایلِ هر حساب می‌گذارد (‎AcctLive.CloudPrefix‎). */
const PREFIX = 'acct-';

function timingEqual(a, b) {
  const x = Buffer.from(String(a), 'utf8');
  const y = Buffer.from(String(b), 'utf8');
  if (x.length !== y.length) return false;
  return crypto.timingSafeEqual(x, y);
}

router.get(
  '/:code/acct/:id',
  rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'pump-public' }),
  async (req, res, next) => {
    try {
      res.set('Access-Control-Allow-Origin', '*');
      res.set('Cache-Control', 'no-store');

      const id = String(req.params.id || '').trim();
      const key = String(req.query.k || '').trim();
      const miss = () => notFound('چنین حسابی نیست', 'not_found');
      if (!/^[a-z][0-9]{1,18}$/.test(id) || !/^[A-Za-z0-9]{8,64}$/.test(key)) throw miss();

      const st = await stations.byCode(req.params.code);
      if (!st) throw miss();

      const row = await one(
        'SELECT data, updated_at FROM station_files WHERE station_id=$1 AND path=$2',
        [st.id, PREFIX + id]
      );
      const env = row && row.data && typeof row.data === 'object' ? row.data : null;
      if (!env || !timingEqual(env.k || '', key)) throw miss();

      res.json({
        ok: true,
        at: Number(env.at) || Number(row.updated_at) || 0,
        d: env.d === undefined ? null : env.d,
        serverTime: now(),
      });
    } catch (err) { next(err); }
  }
);

/* ══════════════════════════════════════════════════════════════════
   چتِ پشتیبانی — مشتری با رمزِ همان حساب (‎k‎)
   ══════════════════════════════════════════════════════════════════ */
const chat = require('../lib/station-chat');

const chatLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'pump-chat' });

/** همان قفلِ کیو‌آر؛ نشد ⇒ ۴۰۴ (پمپ/حساب/رمز همه یکی). */
async function openChat(req, res, next) {
  try {
    res.set('Access-Control-Allow-Origin', '*');
    res.set('Cache-Control', 'no-store');
    const ctx = await chat.openWithKey(req.params.code, req.params.id, req.query.k);
    if (!ctx) throw notFound('چنین حسابی نیست', 'not_found');
    req.chat = ctx;
    next();
  } catch (err) { next(err); }
}

router.get('/:code/acct/:id/chat', chatLimit, openChat, async (req, res, next) => {
  try {
    const { station, acct } = req.chat;
    const th = await chat.thread(station.id, acct);
    const messages = await chat.list({ stationId: station.id, acct, afterSeq: req.query.after, limit: 300 });
    res.json({
      ok: true,
      messages,
      blocked: !!(th && th.blocked_at),
      name: th ? th.name : '',
      ownerSeenSeq: th ? Number(th.owner_seen_seq) : 0,
      vapid: await chat.publicKey(),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

router.post('/:code/acct/:id/chat', chatLimit, openChat, async (req, res, next) => {
  try {
    const { station, acct, snapshotName } = req.chat;
    const b = req.body || {};
    const name = String(b.name || '').trim() || snapshotName;
    const kind = String(b.kind || 'text');
    const msg = await chat.post({
      stationId: station.id, acct, from: 'c', name,
      kind: ['text', 'image', 'video', 'audio'].includes(kind) ? kind : 'text',
      text: b.text, mediaId: b.mediaId || null,
    });
    res.status(201).json({ ok: true, message: msg });
  } catch (err) { next(err); }
});

router.post('/:code/acct/:id/chat/media', chatLimit, openChat,
  express.raw({ type: () => true, limit: '26mb' }),
  async (req, res, next) => {
    try {
      const { station, acct } = req.chat;
      const out = await chat.putMedia({
        stationId: station.id, acct,
        mime: req.headers['content-type'] || '', buf: Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0),
      });
      res.status(201).json({ ok: true, ...out });
    } catch (err) { next(err); }
  });

router.get('/:code/acct/:id/chat/media/:mid', chatLimit, openChat, async (req, res, next) => {
  try {
    const { station, acct } = req.chat;
    const m = await chat.getMedia({ stationId: station.id, acct, id: String(req.params.mid || '') });
    if (!m) throw notFound('رسانه پیدا نشد', 'not_found');
    res.set('Content-Type', m.mime);
    res.set('Content-Length', String(m.size));
    res.set('Cache-Control', 'private, max-age=3600');
    res.end(m.data);
  } catch (err) { next(err); }
});

router.delete('/:code/acct/:id/chat/:msg', chatLimit, openChat, async (req, res, next) => {
  try {
    const { station, acct } = req.chat;
    res.json({ ok: true, message: await chat.remove({ stationId: station.id, acct, id: String(req.params.msg || ''), by: 'c' }) });
  } catch (err) { next(err); }
});

router.post('/:code/acct/:id/chat/seen', chatLimit, openChat, async (req, res, next) => {
  try {
    const { station, acct } = req.chat;
    await chat.seen(station.id, acct, 'c', req.body?.seq);
    res.json({ ok: true });
  } catch (err) { next(err); }
});

/** اشتراکِ پوشِ مرورگر — تا با مرورگرِ بسته هم پیامِ صاحبِ پمپ برسد. */
router.post('/:code/acct/:id/chat/push', chatLimit, openChat, async (req, res, next) => {
  try {
    const { station, acct } = req.chat;
    await chat.savePush(station.id, acct, req.body?.subscription, req.body?.url);
    res.status(201).json({ ok: true });
  } catch (err) { next(err); }
});

module.exports = router;
