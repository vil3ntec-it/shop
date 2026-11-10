'use strict';
/**
 * استودیوی عکسِ کالا — صفِ گروهی، کارگر و بازبینی (AI-05، IMG-PRO-14، IMG-05، IMG-06، IMG-PRO-08؛ §۳۴.۱، §۳۶.۸).
 * پردازشِ یک عکس: ‎lib/image-studio.js‎ (در رشتهٔ جدا: ‎lib/image-studio-thread.js‎). جدول‌ها: مهاجرتِ ۵۹۰.
 *
 *   createBatch(shopId, userId, body, ip)        ⇒ دسته (همان `key` ⇒ همان دسته؛ کلیدِ تکراری با درخواستِ دیگر ⇒ ۴۰۹)
 *   batchView(shopId, id, { after, base })       ⇒ پیشرفتِ دقیق از خودِ ردیف‌ها + قلم‌ها (قبل/بعد، حکم، ریسک)
 *   batchAction(shopId, userId, id, action, ip)  ⇒ pause | resume | cancel | retry (فقط ناموفق‌ها)
 *   versions(shopId, imageId, base)              ⇒ همهٔ نسخه‌های عکسِ خود برای مقایسهٔ قبل/بعد
 *   review(shopId, userId, imageId, ver, body)   ⇒ «تأیید» / «رد» ِ خروجیِ استودیو
 *   start/stop/tick/drain                        ⇒ کارگرِ پس‌زمینه (workers.js)
 *
 * ⛔ پرچمِ خاموشِ `shop.image_studio` (دکان یا `all`) کنارِ `shop.image_bank`: خاموش ⇒ ۴۰۳ و هیچ ردیف.
 * ⛔ اصل هرگز دست نمی‌خورد؛ خروجی نسخهٔ تازهٔ `processed` با `review = needs_review` و تا «تأیید» انتخاب‌شدنی
 *    نیست (‎image-studio.publishable‎ در ‎image-bank.selectIn‎). خروجیِ پرریسک (`risk` ِ گزارش) فقط با
 *    `confirm_risk: true` تأیید می‌شود و همه در سابقه. «بازگردانی به اصل» همان انتخابِ نسخهٔ ۱ است.
 * ⛔ یک‌بارگی: نسخه و `result_version` در یک تراکنش و فقط با همان `lease` ِ کار (اجارهٔ گم‌شده ⇒ هیچ نسخه)؛
 *    کارِ هم‌`digest` (همان عکس، نسخه و تنظیمات) خروجیِ پیشین را به کار می‌برد ⇒ تلاش یا دستهٔ دوباره هرگز
 *    نسخهٔ تکراری نمی‌سازد. خطای همیشگی (نوعِ پردازش‌نشدنی، خروجیِ بزرگ) بی تلاشِ دوباره؛ گذرا با backoff.
 * ⛔ منابع: همزمانیِ `IMAGE_STUDIO_WORKERS` (۱ تا ۴، پیش‌فرض ۲)، هر کار در رشتهٔ جدا با سقفِ حافظه و مهلت،
 *    برداشتنِ نوبتی میانِ دکان‌ها، سقفِ ۱۰۰۰ عکس در دسته، ۵۰۰۰ کارِ باز، ۲۰۰۰ عکس در روز و ۳۰ نسخه برای هر عکس.
 */
const crypto = require('crypto');
const path = require('path');
const { Worker } = require('worker_threads');
const { one, many, now, tx } = require('../db');
const bank = require('./image-bank');
const bytes = require('./image-bytes');
const studio = require('./image-studio');
const audit = require('./audit');
const { ulid } = require('./ulid');
const { ApiError, badRequest, notFound, conflict } = require('../middleware/errors');

const FLAG = 'shop.image_studio';
const TOOL = 'studio/1';
const SPACE = 'shop';
const workers = Math.min(4, Math.max(1, Math.floor(Number(process.env.IMAGE_STUDIO_WORKERS)) || 2));
const LIMITS = Object.freeze({
  BATCH_MAX: 1000, OPEN_MAX: 5000, DAILY_MAX: 2000, VERSIONS_MAX: 30, ITEMS_PAGE: 200, LIST_MAX: 50,
  ATTEMPTS_MAX: 3, BACKOFF_MS: 15000, BACKOFF_MAX_MS: 10 * 60 * 1000,
  LEASE_MS: 2 * 60 * 1000, JOB_TIMEOUT_MS: 60 * 1000, TICK_MS: 2000, HEAP_MB: 256,
  WORKERS: workers,
});
const ID_RE = /^img_[0-9A-HJKMNP-TV-Z]{26}$/;
const KEY_RE = /^[A-Za-z0-9_.:-]{8,64}$/;
const ACTIONS = ['pause', 'resume', 'cancel', 'retry'];

const sha256 = (s) => crypto.createHash('sha256').update(s).digest('hex');
const json = (t, def) => { try { return JSON.parse(t); } catch { return def; } };
const enabled = (shopId) => bank.flagOf(FLAG, shopId);

/** همان عکس + همان نسخه + همان تنظیمات ⇒ همان خروجی (کلیدِ دوباره‌به‌کاربردن) */
function digestOf(imageId, version, preset, options) {
  const o = Object.keys(options).sort().map((k) => [k, options[k]]);
  return sha256(JSON.stringify([imageId, version, preset, o]));
}

/* ═══════════════════════════════════════════════════════ دسته */

async function createBatch(shopId, userId, body, ip = '') {
  const b = body && typeof body === 'object' ? body : {};
  const key = typeof b.key === 'string' ? b.key : '';
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی (key) ۸ تا ۶۴ نویسهٔ لاتین', 'bad_key');
  const ids = b.image_ids;
  if (!Array.isArray(ids) || !ids.length || ids.length > LIMITS.BATCH_MAX) {
    throw badRequest(`فهرستِ عکس‌ها ۱ تا ${LIMITS.BATCH_MAX}`, 'bad_images');
  }
  if (ids.some((x) => typeof x !== 'string' || !ID_RE.test(x))) throw badRequest('شناسهٔ عکس درست نیست', 'bad_images');
  if (new Set(ids).size !== ids.length) throw badRequest('عکسِ تکراری در فهرست', 'duplicate_image');
  const { preset, options, label } = studio.normalize(b);
  const reqDigest = sha256(JSON.stringify([ids, preset, options]));
  return tx(async (c) => {
    //  SR44-2: سقف‌های صف و روز زیرِ قفلِ همین دکان — درخواستِ هم‌زمان شمارِ دستهٔ پیشین را می‌بیند، نه پیش از آن
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`image-studio:${shopId}`]);
    const id = 'isb_' + ulid(), t = now();
    const ins = await c.query(
      `INSERT INTO image_studio_batches (id, shop_id, idem_key, request_digest, preset, options, total, created_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$9) ON CONFLICT (shop_id, idem_key) DO NOTHING RETURNING id`,
      [id, shopId, key, reqDigest, preset, JSON.stringify(options), ids.length, userId, t]);
    if (!ins.rows.length) {
      const had = (await c.query('SELECT id, request_digest FROM image_studio_batches WHERE shop_id=$1 AND idem_key=$2', [shopId, key])).rows[0];
      if (!had || had.request_digest !== reqDigest) throw conflict('این کلید پیش‌تر برای درخواستِ دیگری به کار رفته است', 'key_reused');
      return { id: had.id, existing: true };
    }
    //  فقط عکس‌های خودِ همین دکان؛ شناسهٔ دکانِ دیگر همان «نیست» است (بی نشت)
    const own = (await c.query(
      `SELECT i.id, i.current_version AS v, iv.review, iv.settings FROM images i
         JOIN image_versions iv ON iv.image_id = i.id AND iv.version = i.current_version
        WHERE i.owner_shop_id=$1 AND i.space=$2 AND i.id = ANY($3::text[])`, [shopId, SPACE, ids])).rows;
    const byId = new Map(own.map((r) => [r.id, r]));
    const missing = ids.filter((x) => !byId.has(x));
    if (missing.length) {
      //  فقط جایگاه در همان فهرستِ فرستاده (نه شناسه): چیزی جز ورودیِ خودِ درخواست‌کننده برنمی‌گردد
      const e = notFound('بعضی عکس‌ها پیدا نشد', 'image_not_found');
      e.data = { indexes: ids.map((x, i) => (byId.has(x) ? -1 : i)).filter((i) => i >= 0).slice(0, 50) };
      throw e;
    }
    for (const r of own) {
      try { studio.publishable(r); } catch { throw conflict('نسخهٔ کنونیِ عکس تأییدشده نیست', 'source_not_approved'); }
    }
    const open = await c.query(`SELECT COUNT(*)::int AS n FROM image_studio_jobs WHERE shop_id=$1 AND status IN ('queued','running')`, [shopId]);
    if (Number(open.rows[0].n) + ids.length > LIMITS.OPEN_MAX) {
      throw new ApiError(429, 'studio_queue_full', `صفِ استودیوی این دکان پر است (حداکثر ${LIMITS.OPEN_MAX} کارِ باز)`);
    }
    //  سقفِ روزانه: هر خروجی تا ~۱ مگابایت جا می‌گیرد (نسخه + فهرست + صفحهٔ کالا) — انبار با گزینه‌های پیاپی پر نمی‌شود
    const day = await c.query('SELECT COUNT(*)::int AS n FROM image_studio_jobs WHERE shop_id=$1 AND created_at > $2', [shopId, t - 86400000]);
    if (Number(day.rows[0].n) + ids.length > LIMITS.DAILY_MAX) {
      throw new ApiError(429, 'studio_daily_limit', `سقفِ روزانهٔ استودیو (${LIMITS.DAILY_MAX} عکس) پر است`);
    }
    for (let k = 0; k < ids.length; k += 200) {
      const part = ids.slice(k, k + 200), vals = [], args = [];
      for (const imageId of part) {
        const v = Number(byId.get(imageId).v), p = args.length;
        args.push('isj_' + ulid(), id, shopId, imageId, v, digestOf(imageId, v, preset, options), t);
        vals.push(`($${p + 1},$${p + 2},$${p + 3},$${p + 4},$${p + 5},$${p + 6},$${p + 7},$${p + 7})`);
      }
      await c.query(
        `INSERT INTO image_studio_jobs (id, batch_id, shop_id, image_id, source_version, digest, created_at, updated_at)
         VALUES ${vals.join(',')}`, args);
    }
    await audit.log({
      shopId, userId, action: 'image.studio_batch', targetType: 'image_studio_batch', targetId: id, ip,
      detail: { images: ids.length, preset, label },
    }, { client: c });
    return { id, existing: false };
  });
}

function itemOf(r, base) {
  const res = json(r.result, {});
  const url = (v, size) => bank.fileUrl(base, r.image_id, v, size);
  return {
    image_id: r.image_id, status: r.status, attempts: Number(r.attempts), error: r.error || null,
    source_version: Number(r.source_version), result_version: Number(r.result_version) || null, reused: !!r.reused,
    review: r.review || null, verdict: res.verdict || null, risk: res.risk || [], label: res.label || null,
    before: url(Number(r.source_version), 'original'),
    after: Number(r.result_version) ? url(Number(r.result_version), 'original') : null,
  };
}

async function batchView(shopId, id, { after = '', base = '' } = {}) {
  const b = await one('SELECT * FROM image_studio_batches WHERE id=$1 AND shop_id=$2', [String(id).slice(0, 40), shopId]);
  if (!b) throw notFound('دسته پیدا نشد');
  const counts = { queued: 0, running: 0, done: 0, failed: 0, cancelled: 0 };
  for (const r of await many('SELECT status, COUNT(*)::int AS n FROM image_studio_jobs WHERE batch_id=$1 GROUP BY status', [b.id])) {
    counts[r.status] = Number(r.n);
  }
  const review = { needs_review: 0, approved: 0, rejected: 0 };
  for (const r of await many(
    `SELECT v.review, COUNT(*)::int AS n FROM image_studio_jobs j
       JOIN image_versions v ON v.image_id=j.image_id AND v.version=j.result_version
      WHERE j.batch_id=$1 AND j.status='done' GROUP BY v.review`, [b.id])) review[r.review] = Number(r.n);
  const args = [b.id];
  let sql = `SELECT j.*, v.review FROM image_studio_jobs j
               LEFT JOIN image_versions v ON v.image_id=j.image_id AND v.version=j.result_version AND j.result_version > 0
              WHERE j.batch_id=$1`;
  if (after) { args.push(String(after).slice(0, 40)); sql += ' AND j.id > $2'; }
  const rows = await many(`${sql} ORDER BY j.id LIMIT ${LIMITS.ITEMS_PAGE + 1}`, args);
  const items = rows.slice(0, LIMITS.ITEMS_PAGE).map((r) => itemOf(r, base));
  const finished = counts.done + counts.failed + counts.cancelled;
  return {
    id: b.id, state: b.state, preset: b.preset, options: json(b.options, {}), total: Number(b.total),
    created_at: Number(b.created_at), progress: { ...counts, finished, percent: Math.floor((finished * 100) / Number(b.total)) },
    review, items, next: rows.length > LIMITS.ITEMS_PAGE ? rows[LIMITS.ITEMS_PAGE - 1].id : null,
  };
}

async function batches(shopId, { limit = 20 } = {}) {
  const lim = Math.min(LIMITS.LIST_MAX, Math.max(1, Math.floor(Number(limit)) || 20));
  const rows = await many(
    `SELECT b.id, b.state, b.preset, b.total, b.created_at,
            (SELECT COUNT(*)::int FROM image_studio_jobs j WHERE j.batch_id=b.id AND j.status IN ('done','failed','cancelled')) AS finished
       FROM image_studio_batches b WHERE b.shop_id=$1 ORDER BY b.created_at DESC, b.id DESC LIMIT ${lim}`, [shopId]);
  return rows.map((r) => ({ id: r.id, state: r.state, preset: r.preset, total: Number(r.total), finished: Number(r.finished), created_at: Number(r.created_at) }));
}

async function batchAction(shopId, userId, id, action, ip = '') {
  if (!ACTIONS.includes(action)) throw badRequest('کار فقط pause، resume، cancel یا retry', 'bad_action');
  return tx(async (c) => {
    const b = (await c.query('SELECT * FROM image_studio_batches WHERE id=$1 AND shop_id=$2 FOR UPDATE', [String(id).slice(0, 40), shopId])).rows[0];
    if (!b) throw notFound('دسته پیدا نشد');
    const t = now();
    let changed = 0;
    if (action === 'pause' || action === 'resume') {
      const [from, to] = action === 'pause' ? ['active', 'paused'] : ['paused', 'active'];
      if (b.state === to) return { id: b.id, state: b.state, unchanged: true };
      if (b.state !== from) throw conflict('این دسته در این حال نیست', 'bad_state');
      await c.query('UPDATE image_studio_batches SET state=$2, updated_at=$3 WHERE id=$1', [b.id, to, t]);
      changed = 1;
    } else if (action === 'cancel') {
      if (b.state === 'cancelled') return { id: b.id, state: b.state, unchanged: true };
      await c.query(`UPDATE image_studio_batches SET state='cancelled', updated_at=$2 WHERE id=$1`, [b.id, t]);
      //  کارِ «در کار» تمام می‌شود ولی هنگامِ ثبت می‌بیند دسته لغو شده ⇒ بی نسخه
      changed = (await c.query(`UPDATE image_studio_jobs SET status='cancelled', updated_at=$2 WHERE batch_id=$1 AND status='queued'`, [b.id, t])).rowCount || 0;
    } else {
      if (b.state === 'cancelled') throw conflict('دستهٔ لغوشده دوباره اجرا نمی‌شود', 'bad_state');
      changed = (await c.query(
        `UPDATE image_studio_jobs SET status='queued', attempts=0, next_at=0, error='', lease='', lease_until=0, updated_at=$2
          WHERE batch_id=$1 AND status='failed'`, [b.id, t])).rowCount || 0;
      if (!changed) return { id: b.id, state: b.state, unchanged: true, retried: 0 };
    }
    await audit.log({
      shopId, userId, action: `image.studio_${action}`, targetType: 'image_studio_batch', targetId: b.id, ip, detail: { jobs: changed },
    }, { client: c });
    const state = action === 'cancel' ? 'cancelled' : action === 'pause' ? 'paused' : action === 'resume' ? 'active' : b.state;
    return { id: b.id, state, ...(action === 'retry' ? { retried: changed } : {}) };
  });
}

/* ═══════════════════════════════════════════════ نسخه‌ها و بازبینی */

/** عکسِ خودِ همین دکان یا null (شناسهٔ بدشکل یا دکانِ دیگر همان «نیست») — `c` = تراکنش، null = بیرون از آن */
async function ownImage(c, shopId, imageId, lock = false) {
  if (typeof imageId !== 'string' || !ID_RE.test(imageId)) return null;
  const sql = `SELECT * FROM images WHERE id=$1 AND owner_shop_id=$2 AND space=$3${lock ? ' FOR UPDATE' : ''}`;
  return c ? (await c.query(sql, [imageId, shopId, SPACE])).rows[0] || null : one(sql, [imageId, shopId, SPACE]);
}

async function versions(shopId, imageId, base = '') {
  const img = await ownImage(null, shopId, imageId);
  if (!img) throw notFound('عکس پیدا نشد');
  const rows = await many(
    `SELECT v.version, v.kind, v.parent, v.tool, v.settings, v.review, v.created_at, b.width, b.height, b.size
       FROM image_versions v JOIN image_blobs b ON b.hash=v.blob_hash WHERE v.image_id=$1 ORDER BY v.version`, [img.id]);
  const derivs = await bank.derivativesOf([img.id]);
  return {
    image_id: img.id, current_version: Number(img.current_version),
    versions: rows.map((r) => {
      const s = r.tool === TOOL ? json(r.settings, {}) : null, have = derivs.get(img.id + '@' + r.version);
      const url = (size) => bank.fileUrl(base, img.id, Number(r.version), size);
      return {
        version: Number(r.version), kind: r.kind, parent: Number(r.parent), tool: r.tool, review: r.review,
        width: Number(r.width), height: Number(r.height), bytes: Number(r.size), created_at: Number(r.created_at),
        studio: s && { preset: s.preset, label: s.label, verdict: s.verdict, risk: s.risk || [], steps: s.steps, fidelity: s.fidelity },
        urls: { original: url('original'), thumb: have && have.has('thumb') ? url('thumb') : null, display: have && have.has('display') ? url('display') : null },
      };
    }),
  };
}

async function review(shopId, userId, imageId, version, body, ip = '') {
  const b = body && typeof body === 'object' ? body : {};
  const decision = b.decision;
  if (decision !== 'approve' && decision !== 'reject') throw badRequest('تصمیم فقط approve یا reject', 'bad_decision');
  const ver = Number(version);
  if (!Number.isInteger(ver) || ver < 1 || ver > 100000) throw badRequest('نسخه درست نیست', 'bad_version');
  return tx(async (c) => {
    const img = await ownImage(c, shopId, imageId, true);
    if (!img) throw notFound('عکس پیدا نشد');
    const v = (await c.query('SELECT * FROM image_versions WHERE image_id=$1 AND version=$2 FOR UPDATE', [img.id, ver])).rows[0];
    if (!v) throw notFound('این نسخه نیست');
    if (v.tool !== TOOL) throw conflict('این نسخه خروجیِ استودیو نیست', 'not_studio_output');
    const s = json(v.settings, {}), to = decision === 'approve' ? 'approved' : 'rejected';
    if (v.review !== 'needs_review') {
      if (v.review === to) return { image_id: img.id, version: ver, review: to, unchanged: true };
      throw conflict('این نسخه پیش‌تر بررسی شده است', 'already_reviewed');
    }
    const risk = Array.isArray(s.risk) ? s.risk : [];
    if (to === 'approved' && risk.length && b.confirm_risk !== true) {
      const e = conflict('این خروجی پرریسک است — پس از مقایسه با اصل، «تأییدِ ریسک» لازم است', 'risk_confirmation_required');
      e.data = { risk };
      throw e;
    }
    await c.query('UPDATE image_versions SET review=$3 WHERE image_id=$1 AND version=$2', [img.id, ver, to]);
    await c.query(`UPDATE images SET rev=nextval('image_bank_rev'), updated_at=$2 WHERE id=$1`, [img.id, now()]);
    await audit.log({
      shopId, userId, action: to === 'approved' ? 'image.studio_approved' : 'image.studio_rejected', targetType: 'image',
      targetId: img.id, ip, detail: { version: ver, risk, confirm_risk: to === 'approved' && risk.length > 0 },
    }, { client: c });
    return { image_id: img.id, version: ver, review: to };
  });
}

/* ═══════════════════════════════════════════════════════ کارگر */

let isolateImpl = isolate;

/** پردازش در رشتهٔ جدا با سقفِ حافظه و مهلت؛ هر شکست ⇒ Error با `code` و `permanent` */
function isolate(task, timeoutMs = LIMITS.JOB_TIMEOUT_MS) {
  return new Promise((resolve, reject) => {
    let w;
    try {
      w = new Worker(path.join(__dirname, 'image-studio-thread.js'), { workerData: task, resourceLimits: { maxOldGenerationSizeMb: LIMITS.HEAP_MB } });
    } catch (e) { reject(Object.assign(new Error('worker'), { code: 'worker_error' })); return; }
    let done = false;
    const end = (fn, v) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      w.terminate().catch(() => {});
      fn(v);
    };
    const timer = setTimeout(() => end(reject, Object.assign(new Error('timeout'), { code: 'timeout' })), timeoutMs);
    w.once('message', (m) => {
      if (!m || !m.ok) {
        end(reject, Object.assign(new Error((m && m.message) || 'studio'), { code: (m && m.code) || 'studio_failed', permanent: !!(m && m.status >= 400 && m.status < 500) }));
        return;
      }
      const B = (u) => Buffer.from(u.buffer, u.byteOffset, u.byteLength);
      const derivatives = {};
      for (const k of Object.keys(m.derivatives || {})) derivatives[k] = B(m.derivatives[k]);
      end(resolve, { buf: B(m.buf), width: m.width, height: m.height, derivatives, report: m.report });
    });
    w.once('error', () => end(reject, Object.assign(new Error('worker'), { code: 'worker_error' })));
    w.once('exit', (code) => end(reject, Object.assign(new Error('exit ' + code), { code: 'worker_exit' })));
  });
}

/**
 * برداشتنِ کارِ وقت‌رسیده — نوبتی میانِ دکان‌ها (ROW_NUMBER در هر دکان)، اجارهٔ تمام‌شده هم دوباره برداشته می‌شود.
 * `FOR UPDATE SKIP LOCKED`: چند نمونهٔ سرور هم‌زمان کارهای **جدا** برمی‌دارند (نه همه همان چند ردیف و بقیه هیچ).
 */
async function claim(n) {
  const t = now(), lease = 'l_' + ulid();
  const due = (a) => `((${a}.status='queued' AND ${a}.next_at <= $1) OR (${a}.status='running' AND ${a}.lease_until < $1))`;
  return many(
    `WITH ranked AS (
       SELECT q.id, q.created_at, ROW_NUMBER() OVER (PARTITION BY q.shop_id ORDER BY q.created_at, q.id) AS rn
         FROM image_studio_jobs q JOIN image_studio_batches b ON b.id=q.batch_id
        WHERE b.state='active' AND ${due('q')}
     ), pick AS (
       SELECT p.id FROM image_studio_jobs p JOIN ranked r ON r.id=p.id
        WHERE ${due('p')} ORDER BY r.rn, r.created_at, r.id LIMIT $4 FOR UPDATE OF p SKIP LOCKED
     )
     UPDATE image_studio_jobs j SET status='running', lease=$2, lease_until=$3, attempts=j.attempts+1, updated_at=$1
       FROM pick WHERE j.id=pick.id
     RETURNING j.*`, [t, lease, t + LIMITS.LEASE_MS, n]);
}

const OWN_LEASE = 'id=$1 AND status=$2 AND lease=$3';

/** خروجیِ آمادهٔ همان `digest` (کارِ دیگرِ همین دکان، نسخه‌اش هست) یا null — `c` = تراکنش، null = بیرون از آن */
async function doneOf(c, job) {
  const sql = `SELECT result_version, result FROM image_studio_jobs
    WHERE shop_id=$1 AND digest=$2 AND status='done' AND result_version > 0 AND id <> $3
      AND EXISTS (SELECT 1 FROM image_versions v WHERE v.image_id=image_studio_jobs.image_id AND v.version=image_studio_jobs.result_version)
    ORDER BY updated_at LIMIT 1`;
  const args = [job.shop_id, job.digest, job.id];
  return c ? (await c.query(sql, args)).rows[0] || null : one(sql, args);
}

async function failOrRetry(job, e) {
  const permanent = !!e.permanent || job.attempts >= LIMITS.ATTEMPTS_MAX;
  const code = String(e.code || 'studio_failed').slice(0, 64);
  if (permanent) {
    await one(`UPDATE image_studio_jobs SET status='failed', error=$4, lease='', lease_until=0, updated_at=$5 WHERE ${OWN_LEASE} RETURNING id`,
      [job.id, 'running', job.lease, code, now()]);
    return;
  }
  const wait = Math.min(LIMITS.BACKOFF_MAX_MS, LIMITS.BACKOFF_MS * 2 ** Math.max(0, job.attempts - 1));
  await one(`UPDATE image_studio_jobs SET status='queued', error=$4, next_at=$5, lease='', lease_until=0, updated_at=$6 WHERE ${OWN_LEASE} RETURNING id`,
    [job.id, 'running', job.lease, code, now() + wait, now()]);
}

/** ثبتِ خروجی: نسخه + نسخه‌های کوچک + پایانِ کار در یک تراکنش، فقط با همان اجاره */
async function complete(job, out, reuse) {
  await tx(async (c) => {
    const j = (await c.query('SELECT * FROM image_studio_jobs WHERE id=$1 FOR UPDATE', [job.id])).rows[0];
    if (!j || j.status !== 'running' || j.lease !== job.lease) return;     //  اجاره از دست رفت ⇒ هیچ نسخه
    const b = (await c.query('SELECT state, created_by FROM image_studio_batches WHERE id=$1', [j.batch_id])).rows[0];
    const t = now();
    const fin = (status, extra = {}) => c.query(
      `UPDATE image_studio_jobs SET status=$2, result_version=$3, reused=$4, result=$5, error=$6, lease='', lease_until=0, updated_at=$7 WHERE id=$1`,
      [j.id, status, extra.version || 0, !!extra.reused, extra.result || '{}', extra.error || '', t]);
    if (!b || b.state === 'cancelled') return fin('cancelled');
    if (reuse) return fin('done', { version: Number(reuse.result_version), reused: true, result: reuse.result });
    const img = await ownImage(c, j.shop_id, j.image_id, true);
    if (!img) return fin('failed', { error: 'image_gone' });
    //  SR44-3: کارِ هم‌`digest` ِ هم‌زمان زیرِ همین قفلِ عکس پیش‌تر ثبت شد ⇒ همان خروجی، نه نسخهٔ دوم
    const same = await doneOf(c, j);
    if (same) return fin('done', { version: Number(same.result_version), reused: true, result: same.result });
    const n = (await c.query('SELECT COUNT(*)::int AS n FROM image_versions WHERE image_id=$1', [img.id])).rows[0];
    if (Number(n.n) >= LIMITS.VERSIONS_MAX) return fin('failed', { error: 'versions_limit' });
    const info = bytes.inspect(out.buf);
    const r = out.report;
    const settings = { studio: 1, preset: r.preset, label: r.label, verdict: r.verdict, risk: r.risk, fidelity: r.fidelity, job: j.id, steps: {} };
    for (const [k, s] of Object.entries(r.steps)) settings.steps[k] = s.reason ? `${s.status}:${s.reason}` : s.status;
    //  نسخهٔ پدر = همان نسخه‌ای که پردازش شد (نه لزوماً نسخهٔ کنونی)
    const v = await bank.addVersion(c, { ...img, current_version: j.source_version }, out.buf, info,
      { tool: TOOL, settings, review: 'needs_review', by: b.created_by });
    for (const [size, buf] of Object.entries(out.derivatives || {})) await bank.putDerivative(c, img, v.version, size, buf);
    await fin('done', { version: v.version, result: JSON.stringify(r) });
    await audit.log({
      shopId: j.shop_id, userId: b.created_by, action: 'image.studio_output', targetType: 'image', targetId: img.id,
      detail: { version: v.version, job: j.id, verdict: r.verdict, risk: r.risk },
    }, { client: c });
  });
}

async function runJob(job) {
  try {
    if (job.attempts > LIMITS.ATTEMPTS_MAX) return await failOrRetry(job, { code: 'attempts_exhausted', permanent: true });
    const reuse = await doneOf(null, job);
    if (reuse) return await complete(job, null, reuse);
    const file = await bank.readFile(job.image_id, Number(job.source_version), 'original');
    if (!file) return await failOrRetry(job, { code: 'source_missing', permanent: true });
    const b = await one('SELECT preset, options FROM image_studio_batches WHERE id=$1', [job.batch_id]);
    const req = studio.normalize({ preset: b.preset, options: json(b.options, {}) });
    let info;
    try { info = bytes.inspect(file.data); } catch { return await failOrRetry(job, { code: 'source_invalid', permanent: true }); }
    const out = await isolateImpl({ buf: file.data, info, request: req, maxBytes: bank.DERIV.display.bytes, image_id: job.image_id });
    await complete(job, out, null);
  } catch (e) {
    await failOrRetry(job, e).catch(() => {});
    if (!e.permanent && !['timeout', 'worker_exit', 'worker_error'].includes(e.code)) console.error('[image-studio]', e.code || '', e.message);
  }
}

const inflight = new Set();
let timer = null;
let ticking = false;

/** یک دور: تا جای خالیِ همزمانی کار برمی‌دارد و در پس‌زمینه اجرا می‌کند */
async function tick() {
  if (ticking) return 0;
  ticking = true;
  try {
    const free = LIMITS.WORKERS - inflight.size;
    if (free <= 0) return 0;
    const jobs = await claim(free);
    for (const j of jobs) {
      const p = runJob(j).finally(() => inflight.delete(p));
      inflight.add(p);
    }
    return jobs.length;
  } finally { ticking = false; }
}

/** تا خالی شدنِ صفِ وقت‌رسیده (آزمون و ابزار) — با سقفِ زمان */
async function drain({ timeoutMs = 120000 } = {}) {
  const until = Date.now() + timeoutMs;
  while (Date.now() < until) {
    const got = await tick();
    if (inflight.size) { await Promise.race([...inflight]); continue; }
    if (!got) {
      const due = await one(
        `SELECT 1 AS x FROM image_studio_jobs j JOIN image_studio_batches b ON b.id=j.batch_id WHERE b.state='active'
            AND ((j.status='queued' AND j.next_at <= $1) OR (j.status='running' AND j.lease_until < $1)) LIMIT 1`, [now()]);
      if (!due) return;
    }
  }
}

function start() {
  if (timer) return timer;
  timer = setInterval(() => tick().catch((e) => console.error('[image-studio]', e.message)), LIMITS.TICK_MS);
  if (timer.unref) timer.unref();
  return timer;
}
function stop() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  FLAG, TOOL, LIMITS, enabled, createBatch, batchView, batches, batchAction, versions, review,
  claim, tick, drain, start, stop, digestOf,
  //  فقط آزمون: جای `isolate` (شکستِ گذرا، مهلت، رشتهٔ ازکارافتاده) — null ⇒ همان واقعی
  _setIsolate: (fn) => { isolateImpl = fn || isolate; },
};
