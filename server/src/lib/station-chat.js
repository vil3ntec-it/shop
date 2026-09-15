'use strict';
/**
 * ══ چتِ پشتیبانی — مشتریِ کیو‌آر ↔ صاحبِ پمپ ═════════════════════════════
 *
 *   مشتری (صفحهٔ view/ با رمزِ k)  ──▶  station_chat_messages  ◀──  برنامهٔ کامپیوتر (توکنِ دستگاه)
 *                                            │
 *                                            └──▶ web-push به مرورگرِ مشتری (حتی بسته)
 *
 * قاعده‌ها:
 *   • هویتِ مشتری همان رمزِ حسابِ کیو‌آر است؛ هیچ حسابی نمی‌سازد.
 *   • پاک کردن «نرم» است: متن و رسانه می‌رود، جای پیام می‌ماند («پاک شد»)
 *     تا دو طرف بفهمند چیزی بوده.
 *   • بلاک: مشتری نمی‌تواند بنویسد؛ خواندن آزاد است.
 *   • رسانه: تصویر ≤ ۸ مگ، صدا ≤ ۸ مگ، ویدیو ≤ ۲۵ مگ.
 *   • پوش: کلیدهای VAPID یک بار ساخته و در جدولِ پیکربندی می‌مانند
 *     (یا از محیط: VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY / VAPID_SUBJECT).
 */
const crypto = require('crypto');
const { one, many, newId, now } = require('../db');
const plans = require('./plans');
const stations = require('./stations');
const { badRequest, forbidden, notFound } = require('../middleware/errors');

const LIMITS = { image: 8 * 1024 * 1024, audio: 8 * 1024 * 1024, video: 25 * 1024 * 1024 };
const MAX_TEXT = 4000;
const KEEP_PER_THREAD = 2000;

function timingEqual(a, b) {
  const x = Buffer.from(String(a), 'utf8');
  const y = Buffer.from(String(b), 'utf8');
  return x.length === y.length && crypto.timingSafeEqual(x, y);
}

function cleanAcct(raw) {
  const s = String(raw || '').trim();
  return /^[a-z][0-9]{1,18}$/.test(s) ? s : null;
}

function kindOf(mime) {
  const m = String(mime || '').toLowerCase();
  if (m.startsWith('image/')) return 'image';
  if (m.startsWith('video/')) return 'video';
  if (m.startsWith('audio/')) return 'audio';
  return null;
}

/**
 * «این رمز به کدام حساب می‌خورد؟» — همان قفلِ کیو‌آرِ زنده: فایلِ
 * ‎acct-<id>‎ی پمپ باید باشد و ‎k‎ش با رمزِ داخلِ کیو‌آر یکی باشد.
 * خروجی: ‎{station, acct, snapshotName}‎ یا ‎null‎.
 */
async function openWithKey(code, acctRaw, key) {
  const acct = cleanAcct(acctRaw);
  const k = String(key || '').trim();
  if (!acct || !/^[A-Za-z0-9]{8,64}$/.test(k)) return null;
  const st = await stations.byCode(code);
  if (!st) return null;
  const row = await one(
    'SELECT data FROM station_files WHERE station_id=$1 AND path=$2', [st.id, 'acct-' + acct]
  );
  const env = row && row.data && typeof row.data === 'object' ? row.data : null;
  if (!env || !timingEqual(env.k || '', k)) return null;
  const d = env.d && typeof env.d === 'object' ? env.d : {};
  return { station: st, acct, snapshotName: String(d.n || '') };
}

function shape(row) {
  const deleted = !!row.deleted_at;
  return {
    id: row.id,
    seq: Number(row.seq),
    from: row.from_side,
    name: row.name || '',
    kind: deleted ? 'text' : row.kind,
    text: deleted ? '' : row.text,
    mediaId: deleted ? null : (row.media_id || null),
    at: Number(row.created_at),
    deleted,
  };
}

async function thread(stationId, acct) {
  return one('SELECT * FROM station_chat_threads WHERE station_id=$1 AND acct=$2', [stationId, acct]);
}

async function touchThread(stationId, acct, patch = {}) {
  const t = now();
  const cur = await thread(stationId, acct);
  if (!cur) {
    return one(
      `INSERT INTO station_chat_threads (station_id, acct, name, updated_at) VALUES ($1,$2,$3,$4) RETURNING *`,
      [stationId, acct, patch.name || '', t]
    );
  }
  const name = patch.name !== undefined && patch.name !== '' ? patch.name : cur.name;
  return one(
    `UPDATE station_chat_threads SET name=$3, updated_at=$4 WHERE station_id=$1 AND acct=$2 RETURNING *`,
    [stationId, acct, name, t]
  );
}

/** یک پیام. ‎from‎ = 'c' | 'o'. */
async function post({ stationId, acct, from, name = '', kind = 'text', text = '', mediaId = null }) {
  const th = await thread(stationId, acct);
  if (from === 'c' && th && th.blocked_at) throw forbidden('این گفت‌وگو بسته شده است', 'blocked');

  const clean = String(text || '').trim().slice(0, MAX_TEXT);
  if (kind === 'text' && !clean) throw badRequest('پیام خالی است', 'empty');
  if (kind !== 'text') {
    const m = mediaId ? await one(
      'SELECT id, mime FROM station_chat_media WHERE id=$1 AND station_id=$2 AND acct=$3', [mediaId, stationId, acct]
    ) : null;
    if (!m) throw badRequest('رسانه پیدا نشد', 'bad_media');
    if (kindOf(m.mime) !== kind) throw badRequest('نوعِ رسانه با پیام نمی‌خواند', 'bad_kind');
  }

  const t = now();
  const row = await one(
    `INSERT INTO station_chat_messages (id, station_id, acct, from_side, name, kind, text, media_id, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
    [newId('msg'), stationId, acct, from, String(name || '').trim().slice(0, 80), kind, clean, mediaId, t]
  );
  await touchThread(stationId, acct, from === 'c' ? { name: row.name } : {});

  //  گفت‌وگوی خیلی بلند: کهنه‌ترین‌ها می‌روند (با رسانه‌شان)
  const extra = await many(
    `SELECT id, media_id FROM station_chat_messages WHERE station_id=$1 AND acct=$2
     ORDER BY seq DESC OFFSET $3`, [stationId, acct, KEEP_PER_THREAD]
  );
  if (extra.length) {
    await many('DELETE FROM station_chat_messages WHERE id = ANY($1::text[]) RETURNING id', [extra.map(e => e.id)]);
    const media = extra.map(e => e.media_id).filter(Boolean);
    if (media.length) await many('DELETE FROM station_chat_media WHERE id = ANY($1::text[]) RETURNING id', [media]);
  }
  return shape(row);
}

/** پیام‌های یک گفت‌وگو بعد از ‎afterSeq‎ (پاک‌شده‌ها هم می‌آیند، بی متن). */
async function list({ stationId, acct, afterSeq = 0, limit = 200 }) {
  const rows = await many(
    `SELECT * FROM station_chat_messages WHERE station_id=$1 AND acct=$2 AND seq > $3
     ORDER BY seq ASC LIMIT $4`, [stationId, acct, Number(afterSeq) || 0, Math.min(500, Math.max(1, limit))]
  );
  return rows.map(shape);
}

/** پاک کردنِ نرم. مشتری فقط مالِ خودش را، صاحبِ پمپ هر پیامی را. */
async function remove({ stationId, acct, id, by }) {
  const row = await one(
    'SELECT * FROM station_chat_messages WHERE id=$1 AND station_id=$2' + (acct ? ' AND acct=$3' : ''),
    acct ? [id, stationId, acct] : [id, stationId]
  );
  if (!row) throw notFound('پیام پیدا نشد', 'not_found');
  if (by === 'c' && row.from_side !== 'c') throw forbidden('فقط پیامِ خودتان را می‌توانید پاک کنید', 'not_yours');
  if (row.deleted_at) return shape(row);
  if (row.media_id) await one('DELETE FROM station_chat_media WHERE id=$1 RETURNING id', [row.media_id]);
  const saved = await one(
    `UPDATE station_chat_messages SET deleted_at=$2, text='', media_id=NULL WHERE id=$1 RETURNING *`, [row.id, now()]
  );
  return shape(saved);
}

async function putMedia({ stationId, acct, mime, buf }) {
  const kind = kindOf(mime);
  if (!kind) throw badRequest('فقط عکس، ویدیو و صدا', 'bad_mime');
  if (!buf || !buf.length) throw badRequest('فایل خالی است', 'empty');
  if (buf.length > LIMITS[kind]) throw badRequest('فایل بزرگ‌تر از حد است', 'too_large');
  const row = await one(
    `INSERT INTO station_chat_media (id, station_id, acct, mime, size, data, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id, mime, size`,
    [newId('med'), stationId, acct, String(mime).toLowerCase().slice(0, 80), buf.length, buf, now()]
  );
  return { mediaId: row.id, kind, mime: row.mime, size: row.size };
}

async function getMedia({ stationId, acct, id }) {
  return one(
    'SELECT mime, size, data FROM station_chat_media WHERE id=$1 AND station_id=$2' + (acct ? ' AND acct=$3' : ''),
    acct ? [id, stationId, acct] : [id, stationId]
  );
}

async function setBlocked(stationId, acct, blocked) {
  await touchThread(stationId, acct);
  return one(
    `UPDATE station_chat_threads SET blocked_at=$3, updated_at=$4 WHERE station_id=$1 AND acct=$2 RETURNING *`,
    [stationId, acct, blocked ? now() : null, now()]
  );
}

async function seen(stationId, acct, side, seq) {
  await touchThread(stationId, acct);
  const col = side === 'o' ? 'owner_seen_seq' : 'cust_seen_seq';
  return one(
    `UPDATE station_chat_threads SET ${col}=GREATEST(${col}, $3), updated_at=$4
     WHERE station_id=$1 AND acct=$2 RETURNING *`,
    [stationId, acct, Number(seq) || 0, now()]
  );
}

/** فهرستِ گفت‌وگوها برای صاحبِ پمپ: آخرین پیام، نخوانده‌ها، بلاک. */
async function threads(stationId) {
  const rows = await many(
    `SELECT t.acct, t.name, t.blocked_at, t.owner_seen_seq, t.updated_at,
            (SELECT count(*) FROM station_chat_messages m
              WHERE m.station_id=t.station_id AND m.acct=t.acct AND m.from_side='c'
                AND m.seq > t.owner_seen_seq AND m.deleted_at IS NULL) AS unread,
            (SELECT row_to_json(x) FROM (
               SELECT m.* FROM station_chat_messages m
                WHERE m.station_id=t.station_id AND m.acct=t.acct ORDER BY m.seq DESC LIMIT 1) x) AS last
       FROM station_chat_threads t WHERE t.station_id=$1 ORDER BY t.updated_at DESC`, [stationId]
  );
  return rows.map(r => ({
    acct: r.acct,
    name: r.name || '',
    blocked: !!r.blocked_at,
    unread: Number(r.unread) || 0,
    updatedAt: Number(r.updated_at),
    last: r.last ? shape(r.last) : null,
  }));
}

// ── پوش ─────────────────────────────────────────────────────────────

let webpush = null;
try { webpush = require('web-push'); } catch { webpush = null; }

let vapidCache = null;
async function vapid() {
  if (vapidCache) return vapidCache;
  let pub = process.env.VAPID_PUBLIC_KEY || '';
  let priv = process.env.VAPID_PRIVATE_KEY || '';
  if (!pub || !priv) {
    pub = await plans.getConfig('vapid_public', '');
    priv = await plans.getConfig('vapid_private', '');
  }
  if ((!pub || !priv) && webpush) {
    const k = webpush.generateVAPIDKeys();
    pub = k.publicKey; priv = k.privateKey;
    await plans.setConfig('vapid_public', pub);
    await plans.setConfig('vapid_private', priv);
  }
  vapidCache = { publicKey: pub, privateKey: priv, subject: process.env.VAPID_SUBJECT || 'mailto:support@vill3n.top' };
  return vapidCache;
}

async function publicKey() {
  try { return (await vapid()).publicKey || ''; } catch { return ''; }
}

/** ثبتِ اشتراکِ پوشِ مرورگرِ مشتری (چند دستگاه ⇒ چند اشتراک؛ endpoint یکتا). */
async function savePush(stationId, acct, subscription, url = '') {
  if (!subscription || typeof subscription !== 'object' || typeof subscription.endpoint !== 'string') {
    throw badRequest('اشتراکِ پوش معتبر نیست', 'bad_subscription');
  }
  const th = await touchThread(stationId, acct);
  const list = Array.isArray(th.push) ? th.push : [];
  const next = list.filter(p => p && p.endpoint !== subscription.endpoint);
  next.push({ endpoint: subscription.endpoint, keys: subscription.keys || {}, url: String(url || '').slice(0, 2000), at: now() });
  while (next.length > 5) next.shift();
  return one(
    `UPDATE station_chat_threads SET push=$3::jsonb, updated_at=$4 WHERE station_id=$1 AND acct=$2 RETURNING *`,
    [stationId, acct, JSON.stringify(next), now()]
  );
}

/**
 * پوش به همهٔ مرورگرهای مشتری. هیچ‌وقت پرتاب نمی‌کند؛ اشتراکِ مرده (۴۰۴/۴۱۰)
 * همان‌جا پاک می‌شود. خروجی: چند تا رفت.
 */
async function pushTo(stationId, acct, payload, { sender = null } = {}) {
  const th = await thread(stationId, acct);
  const list = th && Array.isArray(th.push) ? th.push : [];
  if (!list.length) return 0;
  let keys;
  try { keys = await vapid(); } catch { return 0; }
  const send = sender || (webpush && keys.publicKey && keys.privateKey
    ? (sub, body) => webpush.sendNotification(sub, body, {
        vapidDetails: { subject: keys.subject, publicKey: keys.publicKey, privateKey: keys.privateKey },
        TTL: 24 * 3600,
      })
    : null);
  if (!send) return 0;

  let sent = 0;
  const alive = [];
  for (const p of list) {
    try {
      await send({ endpoint: p.endpoint, keys: p.keys }, JSON.stringify({ ...payload, url: payload.url || p.url || '' }));
      sent++;
      alive.push(p);
    } catch (err) {
      const code = Number(err && err.statusCode);
      if (code !== 404 && code !== 410) alive.push(p);   // خطای گذرا: اشتراک می‌ماند
    }
  }
  if (alive.length !== list.length) {
    await one(`UPDATE station_chat_threads SET push=$3::jsonb WHERE station_id=$1 AND acct=$2 RETURNING acct`,
      [stationId, acct, JSON.stringify(alive)]);
  }
  return sent;
}

module.exports = {
  LIMITS, cleanAcct, kindOf, openWithKey, post, list, remove, putMedia, getMedia,
  setBlocked, seen, threads, thread, savePush, pushTo, publicKey, shape,
};
