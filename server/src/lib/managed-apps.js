'use strict';
/**
 * برنامه‌ها و سایت‌های دیگر.
 *
 * ── قرار صاحب مخزن ─────────────────────────────────────────────────
 * «این برنامه‌ی مدیریت تنها برای شاپ نباشد؛ برنامه‌ها و سایت‌های دیگرم
 * را هم از همین‌جا اداره کنم.»
 *
 * پس هر برنامه یا سایت یک ردیف اینجاست. سه چیز از هر کدام دیده می‌شود:
 *
 *   ۱) بالا هست یا نه — سرور خودش نشانیِ سلامتش را می‌سنجد
 *   ۲) چند نفر آمده‌اند — از همان جدولِ بازدیدکننده‌ها، با slug همین برنامه
 *   ۳) پیام‌های پشتیبانی‌اش
 *
 * ── کلید ───────────────────────────────────────────────────────────
 * برنامه‌ای که خودش می‌خواهد خبر بدهد (بازدید، خطا) یک کلید می‌گیرد.
 * کلید خام فقط یک بار — همان لحظه‌ی ساخت — دیده می‌شود و بعد از آن فقط
 * HMACش می‌ماند. پس حتی خواندنِ دیتابیس هم کلیدِ کسی را لو نمی‌دهد.
 */
const { createHmac, randomBytes, timingSafeEqual } = require('crypto');
const { query, one, many, newId, now } = require('../db');
const config = require('../config');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const KINDS = ['app', 'site', 'service'];
const STATUSES = ['active', 'paused', 'archived'];

function pepper() {
  return config.secrets.api || config.secrets.jwt || 'shop-managed-apps';
}

function hashKey(raw) {
  return createHmac('sha256', pepper()).update(String(raw || '')).digest('hex');
}

function shape(r) {
  return {
    id: r.id,
    slug: r.slug,
    title: r.title,
    kind: r.kind,
    url: r.url,
    healthUrl: r.health_url,
    icon: r.icon,
    color: r.color,
    note: r.note,
    status: r.status,
    keySet: !!r.api_key_hash,
    keyHint: r.api_key_hint,
    lastCheckAt: r.last_check_at ? Number(r.last_check_at) : null,
    lastOk: r.last_ok,
    lastStatus: r.last_status === null ? null : Number(r.last_status),
    lastMs: r.last_ms === null ? null : Number(r.last_ms),
    lastError: r.last_error || '',
    createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at),
    ...(r.visitors !== undefined ? { visitors: Number(r.visitors) } : {}),
    ...(r.guests !== undefined ? { guests: Number(r.guests) } : {}),
    ...(r.threads !== undefined ? { openThreads: Number(r.threads) } : {}),
  };
}

/** slug فقط حروف کوچک انگلیسی، رقم و خط تیره — چون در URL و کلیدِ جدول‌ها می‌آید. */
function cleanSlug(raw) {
  const s = String(raw || '').trim().toLowerCase().replace(/[^a-z0-9-]/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '');
  if (s.length < 2 || s.length > 40) throw badRequest('نام کوتاه باید بین ۲ تا ۴۰ حرف انگلیسی باشد', 'bad_slug');
  return s;
}

async function list({ includeArchived = false } = {}) {
  const rows = await many(
    `SELECT a.*,
            (SELECT COUNT(*)::int FROM app_visitors v WHERE v.app = a.slug) AS visitors,
            (SELECT COUNT(*)::int FROM app_visitors v WHERE v.app = a.slug AND v.user_id='') AS guests,
            (SELECT COUNT(*)::int FROM support_threads t WHERE t.app = a.slug AND t.status <> 'closed') AS threads
       FROM managed_apps a
      WHERE ($1 = true OR a.status <> 'archived')
      ORDER BY a.status, a.slug`,
    [!!includeArchived]
  );
  return rows.map(shape);
}

async function get(idOrSlug) {
  const row = await one('SELECT * FROM managed_apps WHERE id=$1 OR slug=$1', [String(idOrSlug || '')]);
  return row ? shape(row) : null;
}

async function create(patch = {}) {
  const slug = cleanSlug(patch.slug);
  const exists = await one('SELECT 1 FROM managed_apps WHERE slug=$1', [slug]);
  if (exists) throw conflict('این نام کوتاه قبلاً گرفته شده است', 'slug_taken');
  const t = now();
  const row = await one(
    `INSERT INTO managed_apps (id, slug, title, kind, url, health_url, icon, color, note,
                               status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$11) RETURNING *`,
    [newId('app'), slug,
      String(patch.title || slug).slice(0, 80),
      KINDS.includes(patch.kind) ? patch.kind : 'app',
      String(patch.url || '').slice(0, 300),
      String(patch.healthUrl || '').slice(0, 300),
      String(patch.icon || '').slice(0, 20),
      String(patch.color || '').slice(0, 20),
      String(patch.note || '').slice(0, 500),
      STATUSES.includes(patch.status) ? patch.status : 'active', t]
  );
  return shape(row);
}

async function update(id, patch = {}) {
  const cur = await one('SELECT * FROM managed_apps WHERE id=$1', [id]);
  if (!cur) throw notFound('این برنامه پیدا نشد', 'app_not_found');
  const row = await one(
    `UPDATE managed_apps SET title=$2, kind=$3, url=$4, health_url=$5, icon=$6, color=$7,
            note=$8, status=$9, updated_at=$10 WHERE id=$1 RETURNING *`,
    [id,
      patch.title === undefined ? cur.title : String(patch.title).slice(0, 80),
      patch.kind === undefined || !KINDS.includes(patch.kind) ? cur.kind : patch.kind,
      patch.url === undefined ? cur.url : String(patch.url).slice(0, 300),
      patch.healthUrl === undefined ? cur.health_url : String(patch.healthUrl).slice(0, 300),
      patch.icon === undefined ? cur.icon : String(patch.icon).slice(0, 20),
      patch.color === undefined ? cur.color : String(patch.color).slice(0, 20),
      patch.note === undefined ? cur.note : String(patch.note).slice(0, 500),
      patch.status === undefined || !STATUSES.includes(patch.status) ? cur.status : patch.status,
      now()]
  );
  return shape(row);
}

async function remove(id) {
  //  پاک نمی‌شود، بایگانی می‌شود: بازدیدها و گفت‌وگوهای همان برنامه
  //  به slugش بسته‌اند و با پاک کردنِ ردیف، بی‌صاحب می‌شدند.
  const row = await one(
    `UPDATE managed_apps SET status='archived', updated_at=$2 WHERE id=$1 RETURNING *`, [id, now()]
  );
  if (!row) throw notFound('این برنامه پیدا نشد', 'app_not_found');
  return shape(row);
}

/** کلید تازه. خام فقط همین یک بار برمی‌گردد. */
async function rotateKey(id) {
  const raw = `ak_${randomBytes(24).toString('base64url')}`;
  const row = await one(
    `UPDATE managed_apps SET api_key_hash=$2, api_key_hint=$3, updated_at=$4 WHERE id=$1 RETURNING *`,
    [id, hashKey(raw), raw.slice(-4), now()]
  );
  if (!row) throw notFound('این برنامه پیدا نشد', 'app_not_found');
  return { key: raw, app: shape(row) };
}

/** برنامه‌ای که این کلید مالِ اوست. */
async function bySecret(rawKey) {
  const clean = String(rawKey || '').trim();
  if (!clean) return null;
  const hash = hashKey(clean);
  const rows = await many(`SELECT * FROM managed_apps WHERE api_key_hash <> '' AND status='active'`);
  for (const r of rows) {
    const a = Buffer.from(r.api_key_hash, 'hex');
    const b = Buffer.from(hash, 'hex');
    if (a.length === b.length && timingSafeEqual(a, b)) return shape(r);
  }
  return null;
}

/**
 * سنجیدن سلامتِ همه‌ی برنامه‌هایی که نشانیِ سلامت دارند.
 *
 * نتیجه در همان ردیف می‌نشیند تا برنامه‌ی مدیریت لازم نباشد خودش به
 * سایت‌ها وصل شود — گوشی‌ای که پشت فیلتر یا روی نتِ ضعیف است، همان
 * سایتِ سالم را «خراب» نشان می‌داد.
 */
async function checkHealth({ timeoutMs = 8000 } = {}) {
  const rows = await many(`SELECT * FROM managed_apps WHERE status='active' AND health_url <> ''`);
  const out = [];
  for (const r of rows) {
    const started = Date.now();
    let ok = false; let status = null; let error = '';
    try {
      const res = await fetch(r.health_url, { signal: AbortSignal.timeout(timeoutMs) });
      status = res.status;
      ok = res.ok;
      if (!ok) error = `پاسخ ${res.status}`;
    } catch (err) {
      error = String(err.message || err).slice(0, 200);
    }
    const ms = Date.now() - started;
    await query(
      `UPDATE managed_apps SET last_check_at=$2, last_ok=$3, last_status=$4, last_ms=$5, last_error=$6 WHERE id=$1`,
      [r.id, now(), ok, status, ms, error]
    );
    out.push({ slug: r.slug, ok, status, ms, error });
  }
  return out;
}

module.exports = { list, get, create, update, remove, rotateKey, bySecret, checkHealth, shape, cleanSlug, KINDS, STATUSES };
