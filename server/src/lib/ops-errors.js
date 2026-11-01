'use strict';
/**
 * مرکزِ رخداد — پنلِ مرکزیِ مالک ۴.۱۰ (خطاها، گزارشِ خرابیِ برنامه‌ها، رخدادها).
 *
 * ── دو منبع، یک فهرست ─────────────────────────────────────────────────
 *   client_errors   خطا و خرابیِ برنامه‌ها (`POST /api/errors`؛ گزارشِ خرابیِ OPS-03 هم همین‌جا می‌نشیند)
 *   server_errors   هر ۵xx ِ خودِ سرورِ حساب — تا امروز فقط در لاگِ کنسول بود (`recordServer`، از
 *                   `middleware/errors.js`). شناسهٔ ردیف همان «کدِ پیگیری» است که کاربر در پیامِ خطا می‌بیند.
 *
 * ── یکی کردنِ تکرار (dedup) ──────────────────────────────────────────
 * هر خطا یک «اثرِ انگشت» دارد: منبع + برنامه/مسیر + پیامِ عادی‌شده (عدد ⇒ #، شناسه/هگز ⇒ *) + نخستین
 * قابِ Stack بی شمارهٔ خط. خطاهای هم‌اثر یک **رخداد** (`ops_incidents`) با شمارنده‌اند، نه هزار ردیف.
 * `ingest()` ردیف‌های تازه را از جایی که دفعهٔ پیش ماند (`ops_cursors`) می‌خواند — تک‌پرواز و زیرِ قفلِ
 * تراکنشی، پس دو خوانندهٔ هم‌زمان یک خطا را دو بار نمی‌شمارند.
 *
 * ── چرخهٔ هر رخداد ──────────────────────────────────────────────────
 *   open ⇄ investigating → resolved → (وقوعِ تازه پس از «حل شد») ⇒ open و رخدادِ «بازگشایی»
 * هر تغییر (وضعیت، اولویت، یادداشت، پیوندِ پشتیبانی) یک ردیفِ تاریخچه (`ops_incident_events`) و یک
 * ردیفِ سابقهٔ مدیر (`audit_logs`) در **همان تراکنش** دارد. تاریخچه هیچ درِ ویرایش یا پاک کردن ندارد.
 *
 * ⛔ هیچ راز: پیام‌ها پیش از نوشتن پوشانده می‌شوند (ایمیل، شماره، توکن، نشانیِ دارای رمز، و خودِ
 *    مقدارهای رازِ `config`)؛ Stack ِ کامل در `server_errors` نوشته نمی‌شود — فقط «کجا» (`file:line`).
 */
const crypto = require('crypto');
const config = require('../config');
const { query, one, many, newId, now, tx } = require('../db');
const audit = require('./audit');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const LIMITS = Object.freeze({
  batch: 2000,               //  ردیف در هر دورِ ingest
  ingestEveryMs: 15_000,     //  خواندنِ خودکار پیش از فهرست، حداکثر هر ۱۵ ثانیه
  serverPerMinute: 120,      //  سقفِ ثبتِ ۵xx در دقیقه (بیشتر ⇒ فقط شمرده: `dropped`)
  title: 160, message: 300, note: 1000, occurrences: 50,
});
const STATUSES = Object.freeze(['open', 'investigating', 'resolved']);
const PRIORITIES = Object.freeze(['p1', 'p2', 'p3', 'p4']);
const SOURCES = Object.freeze(['server', 'client']);

/* ───────────────────────────── پوشاندن و اثرِ انگشت ───────────────────────────── */

let secretValues = null;
function secrets() {
  if (secretValues) return secretValues;
  const out = [];
  const walk = (o, depth = 0) => {
    if (!o || typeof o !== 'object' || depth > 3) return;
    for (const [k, v] of Object.entries(o)) {
      if (typeof v === 'string' && v.length >= 8 && /secret|pass|token|key|dsn|url/i.test(k)) out.push(v);
      else if (v && typeof v === 'object') walk(v, depth + 1);
    }
  };
  walk(config);
  if (process.env.DATABASE_URL) out.push(process.env.DATABASE_URL);
  secretValues = [...new Set(out)].sort((a, b) => b.length - a.length);
  return secretValues;
}

/** هر چه شبیهِ داده‌ی شخصی یا راز است پوشانده می‌شود (همان قاعدهٔ `routes/errors.js`، به‌اضافهٔ رازهای سرور). */
function scrub(text, limit = LIMITS.message) {
  let s = String(text || '');
  for (const v of secrets()) if (v && s.includes(v)) s = s.split(v).join('[راز]');
  return s
    .replace(/\b[a-z][a-z0-9+.-]*:\/\/[^\s/@]+@[^\s]*/gi, '[نشانی]')
    .replace(/[\w.+-]+@[\w-]+\.[\w.]+/g, '[ایمیل]')
    .replace(/\b(?:\+?9[38][\s-]?)?0?7\d{8}\b/g, '[شماره]')
    .replace(/\b(?:\+?98[\s-]?)?0?9\d{9}\b/g, '[شماره]')
    .replace(/\b(?:Bearer\s+)?[A-Za-z0-9_-]{32,}\b/g, '[توکن]')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, limit);
}

/** پیامِ عادی‌شده برای اثرِ انگشت: عدد، هگز و شناسهٔ بلند یکی می‌شوند. */
function normalize(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/\b[0-9a-f]{8,}\b/g, '*')
    .replace(/\b[a-z]{2,6}_[a-z0-9]{8,}\b/g, '*')
    .replace(/\d+/g, '#')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 200);
}

/** نخستین قابِ Stack که مالِ کدِ خودِ برنامه است، بی شمارهٔ خط و ستون. */
function topFrame(stack) {
  const lines = String(stack || '').split('\n').map((l) => l.trim()).filter((l) => l.startsWith('at ') || /\.(kt|java|js|ts)\b/.test(l));
  const own = lines.find((l) => !/node_modules|node:internal|kotlin\.|java\.|android\./.test(l)) || lines[0] || '';
  return own.replace(/:\d+(:\d+)?\)?$/, '').replace(/\(.*[\\/]/, '(').slice(0, 160);
}

const sha = (s) => crypto.createHash('sha256').update(s).digest('hex').slice(0, 32);

function clientFingerprint(row) {
  return `c_${sha(['client', row.app || '', normalize(row.message), normalize(topFrame(row.stack))].join('|'))}`;
}

/* ───────────────────────────── ثبتِ ۵xx ِ سرور ───────────────────────────── */

let windowStart = 0;
let windowCount = 0;
let dropped = 0;
let lastRecordError = '';

/** مسیرِ الگو (`/api/admin/shops/:id`) اگر Express آن را دید؛ وگرنه مسیرِ بی‌توکن با شناسه‌های بلندِ `:id`. */
function routeOf(req) {
  if (req?.route?.path && typeof req.route.path === 'string') return `${req.baseUrl || ''}${req.route.path}`.slice(0, 160);
  const redact = require('./log-redact').redactPath(req?.originalUrl || req?.url || '');
  return redact.split('/').map((s) => (/\d{3,}|[a-z]+_[A-Za-z0-9]{6,}/.test(s) ? ':id' : s)).join('/').slice(0, 160);
}

function locationOf(err) {
  const line = String(err?.stack || '').split('\n').map((l) => l.trim()).find((l) => /[\\/]src[\\/]/.test(l) && !/node_modules/.test(l));
  if (!line) return '';
  const m = /[\\/](src[\\/][^:)]+):(\d+)/.exec(line);
  return m ? `${m[1].replace(/\\/g, '/')}:${m[2]}` : '';
}

let serverVersion = '';
function versionOfServer() {
  if (!serverVersion) { try { serverVersion = require('../../package.json').version; } catch { serverVersion = ''; } }
  return serverVersion;
}

/**
 * ثبتِ یک ۵xx — از `errorHandler`، بی await و بی هیچ اثری روی پاسخ. هر شکستِ خودش فقط شمرده می‌شود.
 * @param {{ref:string, err:Error, req:object, status:number}} p
 */
function recordServer({ ref, err, req, status }) {
  try {
    const t = Date.now();
    if (t - windowStart > 60_000) { windowStart = t; windowCount = 0; }
    windowCount += 1;
    if (windowCount > LIMITS.serverPerMinute) { dropped += 1; return; }
    const method = String(req?.method || '').slice(0, 10);
    const route = routeOf(req);
    const code = String(err?.code || '').slice(0, 60);
    const name = String(err?.name || 'Error').slice(0, 60);
    const fingerprint = `s_${sha(['server', method, route, code, name, normalize(err?.message)].join('|'))}`;
    query(
      `INSERT INTO server_errors (id, fingerprint, method, route, status, code, name, message, step, location, server_version, at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12) ON CONFLICT (id) DO NOTHING`,
      [String(ref || newId('srverr')).slice(0, 40), fingerprint, method, route, Number(status) || 500, code, name,
        scrub(err?.message), String(err?.step || '').slice(0, 60), locationOf(err), versionOfServer(), t]
    ).catch((e) => { dropped += 1; lastRecordError = String(e?.code || e?.name || 'error').slice(0, 40); });
  } catch (e) {
    dropped += 1;
    lastRecordError = String(e?.code || e?.name || 'error').slice(0, 40);
  }
}

function recorderStats() { return { dropped, lastRecordError, perMinuteCap: LIMITS.serverPerMinute }; }

/* ───────────────────────────── ساختنِ رخدادها (ingest) ───────────────────────────── */

let ingestRun = null;
let lastIngestAt = 0;

const PRIORITY_RANK = { p1: 1, p2: 2, p3: 3, p4: 4 };

function groupRows(rows, fpOf) {
  const groups = new Map();
  for (const r of rows) {
    const fp = fpOf(r);
    let g = groups.get(fp);
    if (!g) { g = { fp, ids: [], count: 0, first: r, last: r }; groups.set(fp, g); }
    g.ids.push(r.id);
    g.count += 1;
    if (Number(r.at) < Number(g.first.at)) g.first = r;
    if (Number(r.at) >= Number(g.last.at)) g.last = r;
  }
  return [...groups.values()];
}

async function upsertIncident(c, { fp, source, app, title, kind, first, last, count, version, priority }) {
  const t = now();
  const cur = (await c.query('SELECT * FROM ops_incidents WHERE fingerprint=$1 FOR UPDATE', [fp])).rows[0];
  if (!cur) {
    const id = newId('inc');
    await c.query(
      `INSERT INTO ops_incidents (id, fingerprint, source, app, title, kind, status, priority, first_seen_at, last_seen_at,
                                  occurrences, last_version, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,'open',$7,$8,$9,$10,$11,$12,$12)`,
      [id, fp, source, app, title || '—', kind, priority, first, last, count, version, t]
    );
    await c.query(
      `INSERT INTO ops_incident_events (id, incident_id, kind, to_value, actor, at) VALUES ($1,$2,'created','open','system',$3)`,
      [newId('ince'), id, t]
    );
    return;
  }
  const reopen = cur.status === 'resolved' && last > Number(cur.resolved_at || 0);
  await c.query(
    `UPDATE ops_incidents SET last_seen_at=GREATEST(last_seen_at,$2), first_seen_at=LEAST(first_seen_at,$3),
            occurrences=occurrences+$4, last_version=CASE WHEN $2 >= last_seen_at AND $5 <> '' THEN $5 ELSE last_version END,
            status=CASE WHEN $6 THEN 'open' ELSE status END, version=version+1, updated_at=$7
      WHERE id=$1`,
    [cur.id, last, first, count, version, reopen, t]
  );
  if (reopen) {
    await c.query(
      `INSERT INTO ops_incident_events (id, incident_id, kind, from_value, to_value, note, actor, at)
       VALUES ($1,$2,'reopened','resolved','open',$3,'system',$4)`,
      [newId('ince'), cur.id, `وقوعِ تازه پس از «حل شد»${version ? ` در نسخهٔ ${version}` : ''}`, t]
    );
  }
}

async function ingestTable(c, name) {
  const cur = (await c.query('SELECT at, last_id FROM ops_cursors WHERE name=$1', [name])).rows[0] || { at: 0, last_id: '' };
  const at0 = Number(cur.at) || 0;
  const id0 = String(cur.last_id || '');
  const rows = name === 'client_errors'
    ? (await c.query(
      `SELECT id, app, account_kind, account_id, app_version, version, message, stack, at FROM client_errors
        WHERE at > $1 OR (at = $1 AND id > $2) ORDER BY at, id LIMIT ${LIMITS.batch}`, [at0, id0])).rows
    : (await c.query(
      `SELECT id, fingerprint, method, route, status, code, name, message, server_version, at FROM server_errors
        WHERE at > $1 OR (at = $1 AND id > $2) ORDER BY at, id LIMIT ${LIMITS.batch}`, [at0, id0])).rows;
  if (!rows.length) return 0;
  if (name === 'client_errors') {
    const groups = groupRows(rows, clientFingerprint);
    for (const g of groups) {
      await c.query('UPDATE client_errors SET fingerprint=$1 WHERE id = ANY($2::text[])', [g.fp, g.ids]);
      const l = g.last;
      await upsertIncident(c, {
        fp: g.fp, source: 'client', app: String(l.app || '').slice(0, 40), title: scrub(l.message, LIMITS.title),
        kind: normalize(topFrame(l.stack)).slice(0, 80), first: Number(g.first.at), last: Number(l.at), count: g.count,
        version: String(l.app_version || l.version || '').slice(0, 32), priority: 'p3',
      });
    }
  } else {
    const groups = groupRows(rows, (r) => r.fingerprint);
    for (const g of groups) {
      const l = g.last;
      await upsertIncident(c, {
        fp: g.fp, source: 'server', app: 'server', title: scrub(`${l.method} ${l.route} — ${l.message || l.name}`, LIMITS.title),
        kind: [l.name, l.code].filter(Boolean).join(':').slice(0, 80), first: Number(g.first.at), last: Number(l.at),
        count: g.count, version: String(l.server_version || '').slice(0, 32), priority: 'p2',
      });
    }
  }
  const last = rows[rows.length - 1];
  await c.query(
    `INSERT INTO ops_cursors (name, at, last_id) VALUES ($1,$2,$3)
     ON CONFLICT (name) DO UPDATE SET at=EXCLUDED.at, last_id=EXCLUDED.last_id`,
    [name, Number(last.at), String(last.id)]
  );
  return rows.length;
}

/** ردیف‌های تازه ⇒ رخدادها. تک‌پرواز در همین پروسه، و زیرِ قفلِ تراکنشی بینِ پروسه‌ها. */
async function ingest({ force = false } = {}) {
  if (!force && Date.now() - lastIngestAt < LIMITS.ingestEveryMs) return { skipped: true };
  if (ingestRun) return ingestRun;
  ingestRun = (async () => {
    try {
      let total = 0;
      for (const table of ['client_errors', 'server_errors']) {
        //  هر دسته در تراکنشِ خودش؛ دستهٔ پُر ⇒ دورِ بعد همان‌جا ادامه می‌دهد
        for (let i = 0; i < 5; i++) {
          const n = await tx(async (c) => {
            const got = (await c.query("SELECT pg_try_advisory_xact_lock(hashtext('ops:ingest')) AS ok")).rows[0];
            if (!got || got.ok === false) return 0;
            return ingestTable(c, table);
          });
          total += n;
          if (n < LIMITS.batch) break;
        }
      }
      lastIngestAt = Date.now();
      return { ingested: total, at: lastIngestAt };
    } finally {
      ingestRun = null;
    }
  })();
  return ingestRun;
}

/* ───────────────────────────── خواندن ───────────────────────────── */

const SORTS = Object.freeze({
  last: 'i.last_seen_at DESC, i.id',
  count: 'i.occurrences DESC, i.last_seen_at DESC, i.id',
  priority: "i.priority ASC, i.last_seen_at DESC, i.id",
  first: 'i.first_seen_at DESC, i.id',
});

function shapeIncident(r) {
  return {
    id: r.id, source: r.source, app: r.app, title: r.title, kind: r.kind, status: r.status, priority: r.priority,
    firstSeenAt: Number(r.first_seen_at), lastSeenAt: Number(r.last_seen_at), occurrences: Number(r.occurrences),
    lastVersion: r.last_version || '', supportThreadId: r.support_thread_id || '',
    resolvedAt: r.resolved_at == null ? null : Number(r.resolved_at), resolvedBy: r.resolved_by || '',
    version: Number(r.version), updatedAt: Number(r.updated_at),
    ...(r.recent_1h != null ? { recent1h: Number(r.recent_1h) } : {}),
  };
}

/**
 * فهرست با پالایش و صفحه‌بندیِ سمتِ سرور.
 * @param {{q?:string,status?:string,source?:string,app?:string,priority?:string,shop?:string,version?:string,
 *          sort?:string,limit?:number,offset?:number}} f
 */
async function list(f = {}) {
  const where = [];
  const args = [];
  const add = (sql, v) => { args.push(v); where.push(sql.replace('$?', `$${args.length}`)); };
  if (f.status === 'live') where.push("i.status <> 'resolved'");
  else if (STATUSES.includes(f.status)) add('i.status = $?', f.status);
  if (SOURCES.includes(f.source)) add('i.source = $?', f.source);
  if (PRIORITIES.includes(f.priority)) add('i.priority = $?', f.priority);
  if (f.app) add('i.app = $?', String(f.app).slice(0, 40));
  if (f.q) {
    const q = String(f.q).slice(0, 80);
    args.push(`%${q.replace(/[%_\\]/g, (m) => `\\${m}`)}%`);
    const like = args.length;
    args.push(q);
    where.push(`(i.title ILIKE $${like} OR i.kind ILIKE $${like} OR i.id = $${args.length})`);
  }
  if (f.version) {
    args.push(String(f.version).slice(0, 32));
    const v = args.length;
    where.push(`(i.last_version = $${v}
      OR EXISTS (SELECT 1 FROM client_errors ce WHERE ce.fingerprint = i.fingerprint AND ce.app_version = $${v})
      OR EXISTS (SELECT 1 FROM server_errors se WHERE se.fingerprint = i.fingerprint AND se.server_version = $${v}))`);
  }
  if (f.shop) {
    add(`EXISTS (SELECT 1 FROM client_errors ce WHERE ce.fingerprint = i.fingerprint AND ce.account_kind = 'shop' AND ce.account_id = $?)`,
      String(f.shop).slice(0, 80));
  }
  const sql = where.length ? `WHERE ${where.join(' AND ')}` : '';
  const limit = Math.min(200, Math.max(1, Number(f.limit) || 50));
  const offset = Math.max(0, Math.min(100_000, Number(f.offset) || 0));
  const order = SORTS[f.sort] || SORTS.last;
  const hour = now() - 3600_000;
  const rows = await many(
    `SELECT i.*,
            (SELECT COUNT(*)::int FROM client_errors ce WHERE ce.fingerprint = i.fingerprint AND ce.at > ${hour})
          + (SELECT COUNT(*)::int FROM server_errors se WHERE se.fingerprint = i.fingerprint AND se.at > ${hour}) AS recent_1h
       FROM ops_incidents i ${sql} ORDER BY ${order} LIMIT ${limit} OFFSET ${offset}`,
    args
  );
  const total = (await one(`SELECT COUNT(*)::int AS n FROM ops_incidents i ${sql}`, args)).n;
  const counts = Object.fromEntries((await many('SELECT status, COUNT(*)::int AS n FROM ops_incidents GROUP BY status')).map((r) => [r.status, r.n]));
  return { incidents: rows.map(shapeIncident), total, limit, offset, counts };
}

async function get(id) {
  const r = await one('SELECT * FROM ops_incidents WHERE id=$1', [String(id)]);
  if (!r) throw notFound('رخداد پیدا نشد', 'incident_not_found');
  const fp = r.fingerprint;
  const occ = r.source === 'client'
    ? (await many(
      `SELECT id, app, account_kind, CASE WHEN account_kind='shop' THEN account_id ELSE '' END AS shop_id,
              device_id, app_version, version, platform, message, at
         FROM client_errors WHERE fingerprint=$1 ORDER BY at DESC LIMIT ${LIMITS.occurrences}`, [fp]))
      .map((o) => ({
        id: o.id, at: Number(o.at), source: 'client', service: o.app, version: o.app_version || o.version || '',
        shopId: o.shop_id || '', accountKind: o.account_kind || '',
        //  ⛔ شناسهٔ دستگاه کامل نه — فقط چهار نویسهٔ آخر برای جدا کردنِ دستگاه‌ها
        device: o.device_id ? `…${String(o.device_id).slice(-4)}` : '', platform: o.platform || '',
        message: scrub(o.message), isCrashReport: /^cer_[0-9A-HJKMNP-TV-Z]{26}$/.test(o.id),
      }))
    : (await many(
      `SELECT id, method, route, status, code, name, message, step, location, server_version, at
         FROM server_errors WHERE fingerprint=$1 ORDER BY at DESC LIMIT ${LIMITS.occurrences}`, [fp]))
      .map((o) => ({
        id: o.id, at: Number(o.at), source: 'server', service: `${o.method} ${o.route}`, version: o.server_version || '',
        status: Number(o.status), code: o.code, name: o.name, step: o.step, location: o.location, message: o.message,
      }));
  const spread = r.source === 'client'
    ? await one(
      `SELECT COUNT(DISTINCT account_id) FILTER (WHERE account_kind='shop' AND account_id <> '')::int AS shops,
              COUNT(DISTINCT device_id) FILTER (WHERE device_id <> '')::int AS devices
         FROM client_errors WHERE fingerprint=$1`, [fp])
    : { shops: null, devices: null };
  const versions = r.source === 'client'
    ? await many(
      `SELECT COALESCE(NULLIF(app_version,''), NULLIF(version,''), '') AS v, COUNT(*)::int AS n, MAX(at) AS last
         FROM client_errors WHERE fingerprint=$1 GROUP BY 1 ORDER BY n DESC LIMIT 20`, [fp])
    : await many(
      `SELECT server_version AS v, COUNT(*)::int AS n, MAX(at) AS last FROM server_errors WHERE fingerprint=$1
        GROUP BY 1 ORDER BY n DESC LIMIT 20`, [fp]);
  const events = await many(
    'SELECT id, kind, from_value, to_value, note, result, actor, at FROM ops_incident_events WHERE incident_id=$1 ORDER BY at DESC, id DESC LIMIT 200',
    [r.id]
  );
  let support = null;
  if (r.support_thread_id) {
    const s = await one('SELECT id, app, shop_id, subject, status, updated_at FROM support_threads WHERE id=$1', [r.support_thread_id]).catch(() => null);
    support = s ? { id: s.id, app: s.app, shopId: s.shop_id, subject: scrub(s.subject, 120), status: s.status, updatedAt: Number(s.updated_at) || null } : { id: r.support_thread_id, missing: true };
  }
  return {
    incident: shapeIncident(r),
    spread: { shops: spread?.shops ?? null, devices: spread?.devices ?? null },
    versions: versions.map((x) => ({ version: x.v || '', count: Number(x.n), lastAt: Number(x.last) || null })),
    occurrences: occ,
    occurrencesTruncated: occ.length >= LIMITS.occurrences,
    events: events.map((e) => ({
      id: e.id, kind: e.kind, from: e.from_value, to: e.to_value, note: e.note, result: e.result, actor: e.actor, at: Number(e.at),
    })),
    support,
    checkedAt: now(),
  };
}

/* ───────────────────────────── نوشتن ───────────────────────────── */

function actorName(req) {
  return require('./admin-service').label(req) || String(req.admin?.id || '');
}

async function locked(c, id) {
  const r = (await c.query('SELECT * FROM ops_incidents WHERE id=$1 FOR UPDATE', [String(id)])).rows[0];
  if (!r) throw notFound('رخداد پیدا نشد', 'incident_not_found');
  return r;
}

function checkVersion(r, expected) {
  if (expected != null && expected !== '' && Number(expected) !== Number(r.version)) {
    throw conflict('این رخداد همین حالا عوض شد — دوباره بخوانید', 'incident_changed');
  }
}

async function event(c, req, incidentId, ev) {
  await c.query(
    `INSERT INTO ops_incident_events (id, incident_id, kind, from_value, to_value, note, result, actor, actor_detail, at)
     VALUES ($1,$2,$3,$4,$5,$6,'ok',$7,$8::jsonb,$9)`,
    [newId('ince'), incidentId, ev.kind, ev.from || '', ev.to || '', ev.note || '', actorName(req),
      JSON.stringify(require('./admin-service').actorRecord(req)), now()]
  );
}

async function setStatus(id, req, { status, note = '', expectedVersion = null, ip = '' }) {
  if (!STATUSES.includes(status)) throw badRequest('وضعیت یکی از open، investigating، resolved', 'bad_status');
  const clean = scrub(note, LIMITS.note);
  return tx(async (c) => {
    const r = await locked(c, id);
    checkVersion(r, expectedVersion);
    if (r.status === status) return { incident: shapeIncident(r), changed: false };
    const t = now();
    const resolved = status === 'resolved';
    const upd = (await c.query(
      `UPDATE ops_incidents SET status=$2, resolved_at=$3, resolved_by=$4, version=version+1, updated_at=$5 WHERE id=$1 RETURNING *`,
      [r.id, status, resolved ? t : null, resolved ? actorName(req) : '', t]
    )).rows[0];
    await event(c, req, r.id, { kind: 'status', from: r.status, to: status, note: clean });
    await audit.log({
      actorType: 'admin', userId: req.admin.id, ip, action: 'admin.ops_incident_status', targetType: 'ops_incident',
      targetId: r.id, detail: { from: r.status, to: status, ...(clean ? { note: clean } : {}) },
    }, { client: c });
    return { incident: shapeIncident(upd), changed: true };
  });
}

async function setPriority(id, req, { priority, reason = '', expectedVersion = null, ip = '' }) {
  if (!PRIORITIES.includes(priority)) throw badRequest('اولویت یکی از p1 تا p4', 'bad_priority');
  const clean = scrub(reason, 200);
  return tx(async (c) => {
    const r = await locked(c, id);
    checkVersion(r, expectedVersion);
    if (r.priority === priority) return { incident: shapeIncident(r), changed: false };
    const upd = (await c.query(
      'UPDATE ops_incidents SET priority=$2, version=version+1, updated_at=$3 WHERE id=$1 RETURNING *', [r.id, priority, now()]
    )).rows[0];
    await event(c, req, r.id, { kind: 'priority', from: r.priority, to: priority, note: clean });
    await audit.log({
      actorType: 'admin', userId: req.admin.id, ip, action: 'admin.ops_incident_priority', targetType: 'ops_incident',
      targetId: r.id, detail: { from: r.priority, to: priority, ...(clean ? { reason: clean } : {}) },
    }, { client: c });
    return { incident: shapeIncident(upd), changed: true };
  });
}

/** یادداشتِ داخلی — با `Idempotency-Key` دوبار زدن یک یادداشت است. */
async function addNote(id, req, { note, ip = '' }) {
  const clean = scrub(note, LIMITS.note);
  if (clean.length < 2) throw badRequest('یادداشت را بنویسید', 'note_required');
  const idem = require('./idempotency');
  const key = idem.fromRequest(req, `ops-note:${req.admin.id}`);
  return tx(async (c) => {
    if (key) {
      await idem.lock(c, key);
      const prev = await idem.find(key, idem.viaClient(c));
      if (prev) return { ...(prev.response || {}), replayed: true };
    }
    const r = await locked(c, id);
    await event(c, req, r.id, { kind: 'note', note: clean });
    await c.query('UPDATE ops_incidents SET version=version+1, updated_at=$2 WHERE id=$1', [r.id, now()]);
    await audit.log({
      actorType: 'admin', userId: req.admin.id, ip, action: 'admin.ops_incident_note', targetType: 'ops_incident',
      targetId: r.id, detail: { length: clean.length },
    }, { client: c });
    const out = { ok: true, incidentId: r.id };
    if (key) await idem.save(c, key, { resultId: r.id, response: out });
    return out;
  });
}

/** پیوند به گفت‌وگوی پشتیبانی (یا برداشتن با ''). شناسه باید واقعاً باشد. */
async function linkSupport(id, req, { threadId, ip = '' }) {
  const tid = String(threadId || '').trim();
  if (tid && !/^[A-Za-z0-9_-]{1,80}$/.test(tid)) throw badRequest('شناسهٔ گفت‌وگو درست نیست', 'bad_thread_id');
  return tx(async (c) => {
    const r = await locked(c, id);
    if (tid) {
      const th = (await c.query('SELECT id FROM support_threads WHERE id=$1', [tid])).rows[0];
      if (!th) throw notFound('گفت‌وگوی پشتیبانی پیدا نشد', 'thread_not_found');
    }
    if ((r.support_thread_id || '') === tid) return { incident: shapeIncident(r), changed: false };
    const upd = (await c.query(
      'UPDATE ops_incidents SET support_thread_id=$2, version=version+1, updated_at=$3 WHERE id=$1 RETURNING *', [r.id, tid, now()]
    )).rows[0];
    await event(c, req, r.id, { kind: 'link', from: r.support_thread_id || '', to: tid });
    await audit.log({
      actorType: 'admin', userId: req.admin.id, ip, action: 'admin.ops_incident_link', targetType: 'ops_incident',
      targetId: r.id, detail: { from: r.support_thread_id || '', to: tid },
    }, { client: c });
    return { incident: shapeIncident(upd), changed: true };
  });
}

/** شمارِ تکرارِ هر رخدادِ زنده در پنجره — برای قاعدهٔ «خطای تکراری» ِ هشدارها. */
async function burst(windowMs, min) {
  const since = now() - windowMs;
  return many(
    `SELECT i.id, i.title, i.source, i.app, i.priority, x.n, x.shops FROM ops_incidents i
       JOIN (SELECT fingerprint, COUNT(*)::int AS n, COUNT(DISTINCT account_id) FILTER (WHERE account_kind='shop')::int AS shops
               FROM client_errors WHERE at > $1 AND fingerprint <> '' GROUP BY fingerprint
             UNION ALL
             SELECT fingerprint, COUNT(*)::int AS n, 0 AS shops FROM server_errors WHERE at > $1 GROUP BY fingerprint) x
         ON x.fingerprint = i.fingerprint
      WHERE i.status <> 'resolved' AND x.n >= $2
      ORDER BY x.n DESC LIMIT 100`,
    [since, min]
  );
}

/** شمارِ ۵xx ِ سرور در پنجره — برای «سلامت». */
async function serverErrorCounts(t = now()) {
  const r = await one(
    `SELECT COUNT(*) FILTER (WHERE at > $1)::int AS h1, COUNT(*) FILTER (WHERE at > $2)::int AS h24, MAX(at) AS last
       FROM server_errors WHERE at > $2`, [t - 3600_000, t - 86_400_000]
  );
  return { lastHour: r.h1, last24h: r.h24, lastAt: r.last == null ? null : Number(r.last), ...recorderStats() };
}

module.exports = {
  LIMITS, STATUSES, PRIORITIES, SOURCES, PRIORITY_RANK,
  scrub, normalize, topFrame, clientFingerprint, recordServer, recorderStats, routeOf,
  ingest, list, get, setStatus, setPriority, addNote, linkSupport, burst, serverErrorCounts,
};
