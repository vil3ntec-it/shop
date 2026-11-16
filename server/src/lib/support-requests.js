'use strict';
/**
 * «از پشتیبانی بخواه» — درخواستِ ساخت‌یافتهٔ پشتیبانی با یک لمس، و «درستش کن» ِ یک‌کلیکیِ مدیر (مهاجرتِ ۹۶۰).
 *
 *   برنامه ── POST /api/support/requests {code, context, lang} ──▶ همان رشتهٔ پشتیبانی (`lib/support.js`) + یک ردیف
 *   مدیر  ── GET /api/admin/support-requests ──▶ صف (کد، دکان، سن) ── POST …/:id/fix/<کار> ──▶ همان منطقِ موجود
 *            (پرچم: `lib/live-config.set` روی همان دکان؛ اشتراک: `subscriptions.grantOnce`؛ کدِ ورود: `login-outbox`)
 *            ⇒ ردیفِ سابقه (پیش ⇒ پس) + پاسخِ خودکار در رشتهٔ کاربر + پوش به دستگاه‌های همان دکان.
 *
 * ⛔ چتِ دوم ساخته نشد: پیامِ درخواست و پاسخِ خودکار در همان `support_threads`/`support_messages` می‌نشینند.
 * ⛔ کد و پارامتر فقط از فهرستِ `support-problems.json` (آینهٔ `core/support/SupportProblem.kt`)؛ بقیه ۴۰۰.
 * ⛔ `context` فقط واقعیت‌های فهرست‌شده با شکلِ سخت (نسخه، سکو، نامکِ صفحه، کدِ کوتاه، شمار) — نشانی، ایمیل، توکن،
 *    کارت و متنِ آزاد از هیچ‌کدام نمی‌گذرد؛ ناشناخته بی‌صدا می‌افتد.
 * ⛔ یکتا «تا باز است» برای هر (دکان یا رشتهٔ مهمان، کد، پارامتر) — لمسِ دوباره و همکارِ دوم همان ردیف را می‌گیرند.
 * ⛔ هر «درستش کن» یک بار برای هر درخواست (`support_request_fixes`، UNIQUE) و با `Idempotency-Key` ِ مدیر
 *    (`admin-write-once`)؛ پمپ (`app='pump'`) این‌جا راه ندارد.
 */
const crypto = require('crypto');
const { query, one, many, tx, newId, now } = require('../db');
const support = require('./support');
const push = require('./push');
const audit = require('./audit');
const REG = require('./support-problems.json');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const APP = 'shop';
const LANGS = ['fa', 'en', 'ps'];
const PROBLEMS = new Map(REG.problems.map((p) => [p.code, p]));
const FIX_STALE_MS = 10 * 60 * 1000;
/** کارهای «درستش کن» که دکان را عوض می‌کنند ⇒ پوش به همهٔ دستگاه‌های همان دکان */
const SHOP_WIDE = new Set(['enable_flag', 'grant_subscription', 'raise_device_limit']);

/* ── فهرست ─────────────────────────────────────────────────────────── */

/** «flag_off:shop.orders» ⇒ { problem, param, def }؛ ناشناخته ⇒ ۴۰۰ `bad_problem` */
function parseCode(raw, rawParam) {
  const s = String(raw ?? '').trim();
  const i = s.indexOf(':');
  const code = i >= 0 ? s.slice(0, i) : s;
  const param = String(i >= 0 ? s.slice(i + 1) : (rawParam ?? '')).trim();
  const def = PROBLEMS.get(code);
  if (!def) throw badRequest('این درخواست شناخته نیست', 'bad_problem');
  if (def.param.length ? !def.param.includes(param) : param !== '') {
    throw badRequest('این درخواست شناخته نیست', 'bad_problem');
  }
  return { problem: code, param, def };
}

const langOf = (x) => {
  const l = String(x || '').trim().toLowerCase();
  if (l === 'prs' || l === 'fa-af') return 'fa';   //  دری همان متنِ فارسی
  return LANGS.includes(l) ? l : 'fa';
};

/** متنِ یک بخش (`title`/`ask`/`done`) به زبانِ کاربر، با نامِ پرچم/قابلیت به‌جای {name} */
function textOf(def, field, lang, param) {
  const l = langOf(lang);
  const t = def[field] || {};
  const n = REG.names[param] || {};
  return String(t[l] || t.fa || '').replace('{name}', n[l] || n.fa || '');
}

/* ── واقعیت‌ها ─────────────────────────────────────────────────────── */

const SHAPES = {
  version: (v) => (typeof v === 'string' && /^v?\d{1,4}(\.\d{1,5}){0,3}([-+][A-Za-z0-9.]{1,20})?$/.test(v.trim()) ? v.trim() : undefined),
  platform: (v) => (['android', 'desktop', 'ios', 'web'].includes(v) ? v : undefined),
  slug: (v) => (typeof v === 'string' && /^[a-z0-9][a-z0-9_.:-]{0,39}$/.test(v) ? v : undefined),
  count: (v) => (Number.isSafeInteger(v) && v >= 0 && v <= 1e6 ? v : undefined),
};

/** فقط واقعیت‌های مجازِ همین مشکل، هر کدام با شکلِ خودش؛ بقیه بی‌صدا می‌افتند */
function cleanContext(def, raw) {
  const out = {};
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) return out;
  for (const k of def.facts) {
    const shape = SHAPES[REG.facts[k]];
    const val = shape ? shape(raw[k]) : undefined;
    if (val !== undefined) out[k] = val;
  }
  return out;
}

/* ── شکلِ بیرونی ───────────────────────────────────────────────────── */

const codeOf = (r) => (r.param ? `${r.problem}:${r.param}` : r.problem);
const ctxOf = (r) => (typeof r.context === 'string' ? JSON.parse(r.context) : r.context || {});
const jsonOf = (x) => (typeof x === 'string' ? JSON.parse(x) : x || {});

/** برای خودِ کاربر — بی متن و بی رشته */
function shapeMine(r) {
  return { id: r.id, code: codeOf(r), problem: r.problem, param: r.param, status: r.status, createdAt: Number(r.created_at) };
}

function shapeFix(f) {
  return { action: f.action, result: f.result, by: f.actor, at: Number(f.created_at), before: jsonOf(f.before), after: jsonOf(f.after) };
}

/** برای مدیر — متنِ فارسیِ مشکل، کارِ «درستش کن» و حالِ رشته */
function shapeAdmin(r, fixes = []) {
  const def = PROBLEMS.get(r.problem) || PROBLEMS.get('other');
  return {
    id: r.id, threadId: r.thread_id, shopId: r.shop_id, shopName: r.shop_name || '', who: r.who || '',
    code: codeOf(r), problem: r.problem, param: r.param, title: textOf(def, 'title', 'fa', r.param),
    fix: def.fix, needsShop: def.needsShop, lang: r.lang, context: ctxOf(r),
    status: r.status, unreadAdmin: Number(r.unread_admin || 0), threadStatus: r.thread_status || '',
    createdAt: Number(r.created_at), updatedAt: Number(r.updated_at),
    resolvedAt: r.resolved_at ? Number(r.resolved_at) : null, resolvedBy: r.resolved_by || '',
    fixes: fixes.map(shapeFix),
  };
}

/* ── سمتِ کاربر ────────────────────────────────────────────────────── */

const scopeKeyOf = (shopId, threadId) => (shopId ? `shop:${shopId}` : `thr:${threadId}`);

/**
 * درخواستِ تازه یا همان درخواستِ باز. `id` همان «کیستیِ» `routes/support.who` است (توکن یا دستگاه).
 * ⇒ { request, created, sameShop }
 */
async function open(id, { code, param, context, lang }) {
  if (id.app !== APP) throw badRequest('درخواستِ ساخت‌یافته فقط برای برنامهٔ دکان است', 'bad_app');
  const { problem, param: p, def } = parseCode(code, param);
  if (def.needsShop && !id.shopId) throw badRequest('این درخواست مالِ یک دکان است؛ اول وارد حسابِ دکان شوید', 'shop_required');
  const l = langOf(lang);
  const ctx = cleanContext(def, context);
  const thread = await support.threadFor({ ...id, subject: textOf(def, 'title', 'fa', p).slice(0, 120) });
  const scopeKey = scopeKeyOf(id.shopId, thread.id);
  const t = now();
  const rid = newId('srq');
  const made = await one(
    `INSERT INTO support_requests (id, thread_id, shop_id, user_id, device_uid, scope_key, problem, param, lang, context, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11,$11)
     ON CONFLICT (scope_key, problem, param) WHERE status = 'open' DO NOTHING RETURNING *`,
    [rid, thread.id, id.shopId || '', id.userId || '', id.deviceUid || '', scopeKey, problem, p, l, JSON.stringify(ctx), t]
  );
  if (!made) {
    const prior = await one(
      `SELECT * FROM support_requests WHERE scope_key=$1 AND problem=$2 AND param=$3 AND status='open'`, [scopeKey, problem, p]);
    if (!prior) throw conflict('درخواست همین حالا بسته شد؛ دوباره بفرستید', 'request_race');
    return { request: shapeMine(prior), created: false, sameShop: prior.thread_id !== thread.id };
  }
  let message;
  try {
    //  همان راهِ پیامِ کاربر (S117، پوشِ مدیر، صندوقِ زندهٔ پنل) — نامِ فرستنده همان `who` ِ همیشگی
    message = await support.post(thread.id, { sender: 'user', senderId: id.userId, senderName: id.who, body: textOf(def, 'ask', l, p) });
  } catch (err) {
    await query('DELETE FROM support_requests WHERE id=$1', [rid]);
    throw err;
  }
  const row = await one('UPDATE support_requests SET message_id=$2 WHERE id=$1 RETURNING *', [rid, message.id]);
  return { request: shapeMine(row), created: true, sameShop: false, message };
}

/** درخواست‌های بازِ همین کاربر (رشتهٔ خودش، و دکانِ خودش) — فقط کد و حال */
async function mine(id) {
  if (id.app !== APP) return [];
  const thread = await support.threadFor(id);
  const rows = await many(
    `SELECT * FROM support_requests WHERE status='open' AND (thread_id=$1 OR ($2 <> '' AND scope_key=$3))
      ORDER BY created_at DESC LIMIT 50`,
    [thread.id, id.shopId || '', `shop:${id.shopId || ''}`]
  );
  return rows.map(shapeMine);
}

/* ── سمتِ مدیر: صف و پرونده ───────────────────────────────────────── */

const ROW_SQL = `SELECT r.*, s.name AS shop_name, t.who, t.unread_admin, t.status AS thread_status
                   FROM support_requests r
                   JOIN support_threads t ON t.id = r.thread_id
                   LEFT JOIN shops s ON s.id = r.shop_id`;

/**
 * صف: تازه‌ترین بالا. `status` (open | resolved | all)، `problem` (کدِ پایه یا «پایه:پارامتر»)، `shop`،
 * سن به ساعت (`minAgeHours` = کهنه‌تر از، `maxAgeHours` = تازه‌تر از) — همه پارامترِ پرسش، هرگز چسبیده به SQL.
 */
async function list({ status = 'open', problem = '', shop = '', minAgeHours = 0, maxAgeHours = 0, limit = 100, offset = 0 } = {}) {
  const t = now();
  let base = '';
  let param = null;
  if (problem) ({ problem: base, param } = parseFilter(problem));
  const args = [status === 'all' ? '' : status, base, param === null ? '' : param, param === null, shop,
    minAgeHours ? Math.round(t - minAgeHours * 3600e3) : 0, maxAgeHours ? Math.round(t - maxAgeHours * 3600e3) : 0, limit, offset];
  const rows = await many(
    `${ROW_SQL}
      WHERE ($1 = '' OR r.status = $1) AND ($2 = '' OR r.problem = $2) AND ($4 OR r.param = $3) AND ($5 = '' OR r.shop_id = $5)
        AND ($6::bigint = 0 OR r.created_at <= $6::bigint) AND ($7::bigint = 0 OR r.created_at >= $7::bigint)
      ORDER BY r.created_at DESC, r.id DESC LIMIT $8 OFFSET $9`,
    args
  );
  const counts = await one(
    `SELECT COUNT(*) FILTER (WHERE r.status='open')::int AS open,
            COUNT(*) FILTER (WHERE r.status='open' AND t.unread_admin > 0)::int AS unread
       FROM support_requests r JOIN support_threads t ON t.id = r.thread_id`
  );
  return { requests: rows.map((r) => shapeAdmin(r)), open: counts.open, unread: counts.unread };
}

/** پالایهٔ کد: «flag_off» یا «flag_off:shop.orders» — فقط از فهرست */
function parseFilter(raw) {
  const s = String(raw).trim();
  if (!s.includes(':')) {
    if (!PROBLEMS.has(s)) throw badRequest('این کد شناخته نیست', 'bad_problem');
    return { problem: s, param: null };
  }
  const { problem, param } = parseCode(s);
  return { problem, param };
}

async function rowOf(rid) {
  const id = String(rid || '');
  if (!/^[A-Za-z0-9_-]{1,80}$/.test(id)) throw notFound('این درخواست پیدا نشد', 'request_not_found');
  const r = await one(`${ROW_SQL} WHERE r.id=$1`, [id]);
  if (!r) throw notFound('این درخواست پیدا نشد', 'request_not_found');
  return r;
}

/**
 * ⛔ «دکانِ دیگر ⇒ ۴۰۴»: پنل شناسهٔ دکانی را که نشان داده می‌فرستد؛ درخواستِ دکانِ دیگر همان «پیدا نشد» است
 * (کلیکِ روی کارتِ کهنه هرگز دکانِ دیگری را عوض نمی‌کند).
 */
async function rowFor(rid, shopId) {
  const r = await rowOf(rid);
  if (shopId !== undefined && shopId !== null && String(shopId) !== r.shop_id) {
    throw notFound('این درخواست پیدا نشد', 'request_not_found');
  }
  return r;
}

async function fixesOf(rid) {
  return many('SELECT * FROM support_request_fixes WHERE request_id=$1 ORDER BY created_at, id', [rid]);
}

/** حالِ زندهٔ همان چیزی که «درستش کن» عوض می‌کند — فقط فرادادهٔ سکو (پرچم، اشتراک)، نه دادهٔ دکان */
async function liveState(r) {
  const def = PROBLEMS.get(r.problem);
  if (!def || !r.shop_id) return {};
  if (def.fix === 'enable_flag') return { flag: await flagState(r.shop_id, r.param) };
  if (def.fix === 'grant_subscription' || def.fix === 'raise_device_limit') return { subscription: await subState(r.shop_id) };
  return {};
}

async function detail(rid) {
  const r = await rowOf(rid);
  return {
    request: shapeAdmin(r, await fixesOf(r.id)),
    thread: support.shapeThread(await one('SELECT * FROM support_threads WHERE id=$1', [r.thread_id])),
    messages: await support.messages(r.thread_id),
    state: await liveState(r),
    serverTime: now(),
  };
}

async function resolve(req, rid, { shopId, note = '' } = {}) {
  const r = await rowFor(rid, shopId);
  if (r.status === 'resolved') return { request: shapeAdmin(r, await fixesOf(r.id)), unchanged: true };
  const t = now();
  const by = String(req.actor?.name || req.admin.name || req.admin.username || req.admin.id).slice(0, 80);
  const out = await tx(async (c) => {
    const u = (await c.query(
      `UPDATE support_requests SET status='resolved', resolved_at=$2, resolved_by=$3, updated_at=$2 WHERE id=$1 AND status='open' RETURNING *`,
      [r.id, t, by])).rows[0];
    if (u) {
      await audit.log({
        shopId: r.shop_id, actorType: 'admin', userId: req.admin.id, action: 'admin.support_request_resolved',
        targetType: 'support_request', targetId: r.id,
        detail: { problem: r.problem, param: r.param, ...(note ? { note: String(note).slice(0, 200) } : {}) },
      }, { client: c });
    }
    return u;
  });
  return { request: shapeAdmin({ ...r, ...(out || {}) }, await fixesOf(r.id)), unchanged: !out };
}

/* ── «درستش کن» ────────────────────────────────────────────────────── */

async function flagState(shopId, key) {
  const orders = require('./orders');
  const own = await one('SELECT value FROM live_config WHERE scope=$1 AND key=$2', [shopId, key]);
  const hold = await one(
    `SELECT 1 AS x FROM shop_holds WHERE shop_id=$1 AND kind='service_disabled' AND service=$2 AND lifted_at IS NULL LIMIT 1`, [shopId, key]);
  let explicit = null;
  try { explicit = own ? JSON.parse(own.value) : null; } catch { explicit = null; }
  return { key, effective: await orders.flagOf(shopId, key, (sql, p) => query(sql, p)), explicit, hold: Boolean(hold) };
}

async function subState(shopId) {
  const subs = require('./subscriptions');
  const st = subs.stateOf(await subs.latestOf(shopId));
  return { id: st.id || '', status: st.status, phase: st.phase, active: st.active, plan: st.plan, endsAt: st.endsAt,
    daysLeft: st.daysLeft, maxDevices: st.maxDevices ?? null, features: st.features || [] };
}

/**
 * یک بار برای هر (درخواست، کار): نشانِ `pending` پیش از کار، `done` پس از آن؛ شکست ⇒ نشان برداشته می‌شود.
 * نشانِ ماندهٔ فرآیندِ مرده پس از `FIX_STALE_MS` کهنه است. ⇒ { fix, already }
 */
async function once(r, action, actor, run) {
  const t = now();
  const fid = newId('srf');
  await query(`DELETE FROM support_request_fixes WHERE request_id=$1 AND action=$2 AND result='pending' AND created_at < $3`,
    [r.id, action, t - FIX_STALE_MS]);
  const claimed = await one(
    `INSERT INTO support_request_fixes (id, request_id, action, actor, result, created_at) VALUES ($1,$2,$3,$4,'pending',$5)
     ON CONFLICT (request_id, action) DO NOTHING RETURNING id`, [fid, r.id, action, actor, t]);
  if (!claimed) {
    const prior = await one('SELECT * FROM support_request_fixes WHERE request_id=$1 AND action=$2', [r.id, action]);
    if (prior && prior.result === 'pending') throw conflict('همین کار همین حالا در راه است', 'fix_in_progress');
    return { fix: prior ? shapeFix(prior) : null, already: true };
  }
  let out;
  try {
    out = await run();
  } catch (err) {
    await query('DELETE FROM support_request_fixes WHERE id=$1', [fid]).catch(() => {});
    throw err;
  }
  const row = await one(
    `UPDATE support_request_fixes SET before=$2::jsonb, after=$3::jsonb, result=$4 WHERE id=$1 RETURNING *`,
    [fid, JSON.stringify(out.before || {}), JSON.stringify(out.after || {}), out.result || 'done']);
  return { fix: shapeFix(row), already: false, extra: out.extra };
}

/** پاسخِ خودکار در رشتهٔ کاربر (همان `support.post`: پوش به خودِ کاربر) + پوش به دستگاه‌های دیگرِ همان دکان */
async function announce(req, r, def, action) {
  const body = textOf(def, 'done', r.lang, r.param);
  const thread = await one('SELECT user_id FROM support_threads WHERE id=$1', [r.thread_id]);
  const message = await support.post(r.thread_id, {
    sender: 'admin', senderId: req.admin.id, senderName: String(req.actor?.name || req.admin.name || req.admin.username || ''), body,
  });
  let pushed = null;
  if (SHOP_WIDE.has(action) && r.shop_id) {
    try {
      pushed = await push.sendTo({ shopId: r.shop_id, app: APP, exceptUserId: thread?.user_id || '' }, {
        title: textOf(PROBLEMS.get('other'), 'title', r.lang, ''), body: body.slice(0, 120), data: { type: 'support', threadId: r.thread_id },
      });
    } catch (err) {
      console.error('[support-requests:push]', err.message);
    }
  }
  return { messageId: message.id, pushed: pushed ? Number(pushed.sent || 0) : 0 };
}

/** سابقهٔ زنجیره: چه کسی، کی، کدام دکان، کدام کار، پیش ⇒ پس (کلیدها بی «code»/«token» تا `audit.scrub` نبُرد) */
async function logFix(req, r, action, fix, reason) {
  await audit.log({
    shopId: r.shop_id, actorType: 'admin', userId: req.admin.id, action: 'admin.support_request_fix',
    targetType: 'support_request', targetId: r.id,
    detail: { problem: r.problem, param: r.param, fix: action, before: fix.before, after: fix.after, result: fix.result, reason },
  });
}

const reasonOf = (r, note) => `پشتیبانی: درخواستِ ${r.id} (${codeOf(r)})${note ? ` — ${String(note).slice(0, 120)}` : ''}`;
const actorOf = (req) => require('./admin-service').label(req);

/**
 * کارِ «درستش کن» ِ همان مشکل. `kind` باید همان `fix` ِ فهرست باشد (وگرنه ۴۰۹ `fix_not_for_problem`)،
 * درخواست باز باشد (۴۰۹ `request_resolved`) و دکان همان (۴۰۴). ⇒ { ok, already, fix, request, state, reply }
 */
async function fix(req, rid, kind, { shopId, note = '', resolve: alsoResolve = false, days, devices } = {}) {
  const r = await rowFor(rid, shopId);
  const def = PROBLEMS.get(r.problem);
  if (!def || def.fix !== kind) throw conflict('این کار برای این درخواست نیست', 'fix_not_for_problem');
  if (r.status !== 'open') throw conflict('این درخواست بسته شده است', 'request_resolved');
  if (def.needsShop && !r.shop_id) throw conflict('این درخواست دکانی ندارد', 'shop_required');
  const reason = reasonOf(r, note);
  const run = RUNNERS[kind];
  if (!run) throw conflict('این درخواست کارِ خودکار ندارد', 'fix_not_for_problem');
  const done = await once(r, kind, actorOf(req), () => run(req, r, { reason, days, devices }));
  let reply = null;
  if (!done.already) {
    await logFix(req, r, kind, done.fix, reason);
    if (done.fix.result === 'done') reply = await announce(req, r, def, kind);
  }
  let request = shapeAdmin(r, await fixesOf(r.id));
  if (alsoResolve && !done.already && done.fix.result === 'done') request = (await resolve(req, r.id, { note: `fix:${kind}` })).request;
  return { ok: true, already: done.already, fix: done.fix, request, state: await liveState(r), reply, ...(done.extra || {}) };
}

const RUNNERS = {
  /** پرچمِ همان دکان، ردیفِ صریحِ `true` فقط برای همان دکان (`live-config.set` با تاریخچه در همان تراکنش) */
  async enable_flag(req, r, { reason }) {
    const before = await flagState(r.shop_id, r.param);
    if (before.hold) {
      throw conflict('این سرویس برای همین دکان با «غیرفعال کردنِ سرویس» بسته شده؛ اول همان حالت را بردارید', 'shop_hold_active');
    }
    if (before.explicit === true && before.effective) return { before, after: before, result: 'unchanged' };
    const svc = require('./admin-service');
    await require('./live-config').set(r.shop_id, r.param, true, svc.label(req), { reason, actor: svc.actorRecord(req) });
    return { before, after: await flagState(r.shop_id, r.param) };
  },

  /**
   * تمدید/فعال کردن با همان `subscriptions.grantOnce` ِ پنل (قفلِ دکان، تاریخچه، «از پایانِ زنده ادامه»)؛ کلیدِ یکتاییِ
   * خودِ درخواست ⇒ تلاشِ دوباره پس از قطع هرگز دورهٔ دوم نمی‌سازد. پلن و قابلیت‌ها همان آخرین اشتراک؛ `plan_required` ⇒
   * همان قابلیت به فهرستِ صریح افزوده می‌شود (فهرستِ خالی = پلنِ کامل، دست نمی‌خورد).
   */
  async grant_subscription(req, r, { reason, days }) {
    const subs = require('./subscriptions');
    const n = days === undefined || days === null || days === '' ? 30 : Number(days);
    if (!Number.isInteger(n) || n < 1 || n > 366) throw badRequest('روزها باید ۱ تا ۳۶۶ باشد', 'bad_days');
    const before = await subState(r.shop_id);
    const last = await subs.latestOf(r.shop_id);
    if (last && last.status === 'suspended') {
      throw conflict('اشتراکِ این دکان معلق است؛ تصمیمِ تعلیق از صفحهٔ اشتراک برداشته می‌شود', 'subscription_suspended');
    }
    let features = last && Array.isArray(last.features) ? last.features : [];
    if (r.problem === 'plan_required' && features.length && !features.includes(r.param)) features = [...features, r.param];
    const idem = { scope: 'support-request-fix', key: `${r.id}:grant`, hash: crypto.createHash('sha256').update(`${r.id}|${n}`).digest('hex') };
    const granted = await subs.grantOnce(r.shop_id, {
      plan: last?.plan || 'custom', days: n, features, maxDevices: Number(last?.max_devices) || 10,
      graceDays: Number(last?.grace_days) || 0, note: reason.slice(0, 300), createdBy: req.admin.id, allowUnsuspend: false,
    }, idem);
    const after = await subState(r.shop_id);
    return { before, after: { ...after, days: n }, extra: { subscriptionId: granted.row?.id || '' } };
  },

  /** سقفِ دستگاهِ اشتراکِ زنده — همان `grantOnce` با همان پایان، پلن، قابلیت و مهلت (فقط `maxDevices` عوض می‌شود) */
  async raise_device_limit(req, r, { reason, devices }) {
    const subs = require('./subscriptions');
    const live = await subs.liveOf(r.shop_id);
    if (!live || subs.stateOf(live).status !== 'active') throw conflict('این دکان اشتراکِ فعال ندارد؛ اول تمدید کنید', 'no_active_subscription');
    const cur = Number(live.max_devices) || 1;
    const n = devices === undefined || devices === null || devices === '' ? cur + 1 : Number(devices);
    if (!Number.isInteger(n) || n <= cur || n > 100) throw badRequest(`شمارِ تازه باید بیشتر از ${cur} و تا ۱۰۰ باشد`, 'bad_devices');
    const before = await subState(r.shop_id);
    const idem = { scope: 'support-request-fix', key: `${r.id}:devices`, hash: crypto.createHash('sha256').update(`${r.id}|${n}`).digest('hex') };
    await subs.grantOnce(r.shop_id, {
      plan: live.plan, startsAt: Number(live.starts_at), endsAt: Number(live.ends_at), features: Array.isArray(live.features) ? live.features : [],
      maxDevices: n, graceDays: Number(live.grace_days) || 0, note: live.note || '', createdBy: req.admin.id,
      price: live.price === null || live.price === undefined ? null : Number(live.price), allowUnsuspend: false,
    }, idem);
    return { before, after: await subState(r.shop_id) };
  },

  /**
   * همان کدِ زنده، نه کدِ تازه (`admin-logins` ⇐ `login-outbox`): ایمیل از خودِ رشته (حسابِ کاربر یا «تماس» ِ مهمان).
   * کدِ زنده نیست ⇒ ۴۰۹ `no_live_code` (کاربر «فرستادنِ کد» را دوباره بزند). کد هرگز در پاسخ یا سابقه نیست.
   */
  async resend_email_code(req, r) {
    const codes = require('./login-codes');
    const outbox = require('./login-outbox');
    const th = await one(
      `SELECT t.contact, u.email FROM support_threads t LEFT JOIN users u ON u.id = t.user_id WHERE t.id=$1`, [r.thread_id]);
    const email = codes.normEmail(th?.email || th?.contact || '');
    if (!codes.isEmail(email)) throw conflict('برای این گفت‌وگو ایمیلی معلوم نیست؛ از کاربر بپرسید', 'no_email');
    const rec = await one(
      `SELECT request_id, app FROM login_requests WHERE email=$1 AND app=$2 AND consumed_at IS NULL AND superseded_at IS NULL
          AND expires_at > $3 ORDER BY created_at DESC LIMIT 1`, [email, APP, now()]);
    if (rec) {
      const before = { book: 'login', state: (await outbox.statusOf(rec.request_id))?.state || 'none', email: codes.mask(email) };
      if (!['queued', 'sending'].includes(before.state)) await outbox.enqueue({ id: rec.request_id, app: rec.app, email });
      const sent = await outbox.processById(rec.request_id);
      const state = (await outbox.statusOf(rec.request_id))?.state || 'none';
      if (!['sent', 'retry', 'skipped'].includes(sent)) throw conflict('ایمیل نرفت؛ دفترِ «ورودها» دلیلش را دارد', `resend_${sent}`);
      return { before, after: { book: 'login', state, sent, email: codes.mask(email) } };
    }
    //  دفترِ دوم (`otp_codes`: ثبت‌نام، رمزِ فراموش‌شده) — همان `otp.resend` ِ میزِ مدیر
    const o = await one(
      `SELECT id FROM otp_codes WHERE lower(destination)=$1 AND (app=$2 OR app='') AND consumed_at IS NULL AND expires_at > $3
        ORDER BY created_at DESC LIMIT 1`, [email, APP, now()]);
    if (!o) throw conflict('کدِ زنده‌ای برای این ایمیل نیست؛ کاربر یک بار دیگر «فرستادنِ کد» را بزند', 'no_live_code');
    const out = await require('./otp').resend(o.id);
    if (!out || !out.ok) throw conflict(out?.message || 'ایمیل نرفت', out?.error || 'resend_failed');
    return { before: { book: 'otp', email: codes.mask(email) }, after: { book: 'otp', via: out.via || '', email: codes.mask(email) } };
  },
};

/**
 * آخرین خطای همگام‌سازی/برنامهٔ همان دکان (فقط خواندن، `client_errors` ِ `POST /api/errors`): خلاصه همیشه (پیامِ پوشانده،
 * نسخه، زمان، شمارِ ۷ روز)؛ Stack، دنبالهٔ لاگ و دستگاه فقط با اجازهٔ زندهٔ `read_private` ِ همین مدیر روی همین دکان
 * (`support-access.useGrant`، با شمار و سابقه) — بی آن `restricted: true`.
 */
async function syncErrorOf(req, rid, { shopId } = {}) {
  const r = await rowFor(rid, shopId);
  if (!r.shop_id) return { available: false, reason: 'no_shop', errors: [] };
  const rows = await many(
    `SELECT id, app_version, message, stack, log_tail, device_id, at FROM client_errors
      WHERE app=$1 AND account_kind='shop' AND account_id=$2 ORDER BY at DESC LIMIT 5`, [APP, r.shop_id]);
  const week = await one(
    `SELECT COUNT(*)::int AS n FROM client_errors WHERE app=$1 AND account_kind='shop' AND account_id=$2 AND at > $3`,
    [APP, r.shop_id, now() - 7 * 86400e3]);
  let grant = null;
  try {
    grant = await require('./support-access').useGrant(req, r.shop_id, 'read_private', { what: `support_request:${r.id}:sync_error` });
  } catch (e) {
    if (!['grant_required', 'human_actor_required'].includes(e?.code)) throw e;
  }
  await audit.log({
    shopId: r.shop_id, actorType: 'admin', userId: req.admin.id, action: 'admin.support_sync_error_viewed',
    targetType: 'support_request', targetId: r.id, detail: { count: rows.length, private: Boolean(grant), ...(grant ? { grant } : {}) },
  });
  return {
    available: true, restricted: !grant, weekCount: week.n,
    errors: rows.map((e) => ({
      id: e.id, appVersion: e.app_version || '', message: e.message || '', at: Number(e.at),
      ...(grant ? { stack: e.stack || '', logTail: e.log_tail || '', device: e.device_id || '' } : {}),
    })),
  };
}

module.exports = {
  REG, PROBLEMS, parseCode, langOf, textOf, cleanContext, open, mine, list, detail, resolve, fix, syncErrorOf, codeOf,
};
