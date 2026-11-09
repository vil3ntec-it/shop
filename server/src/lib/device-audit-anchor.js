'use strict';
/**
 * STF-09 — لنگرِ سرورِ زنجیرهٔ سابقهٔ عملیاتِ دستگاه (`auditLog`، قالبِ «tohid-audit-chain-v1»؛ مهاجرتِ ۴۱۰).
 *
 *   POST /api/sync/v1/audit-anchor  { device_id, format, heads: [{chain, seq, hash}] }   (حداکثر ۴ سر)
 *                                    ⇒ { enabled, acks: [{chain, seq, hash}], problems: [{chain, seq, problem}], last_epoch }
 *   GET  /api/sync/v1/audit-anchor  ⇒ { enabled, valid, broken, chains, pending }  (سنجشِ سرور برای صفحهٔ «سابقهٔ عملیات»؛ audit.view)
 *
 * ردیف‌ها همان راهِ همیشگیِ `sync_rows` را می‌روند؛ دستگاه (`core/audit/AuditAnchor`) سرِ هر زنجیرهٔ خودش را از
 * لنگرِ محلی (`AuditChain.Anchors`، بیرون از پشتیبان) پس از هر دورِ همگام‌سازی، فقط وقتی عوض شده، می‌فرستد —
 * همان کارِ `POST /api/sync/v1/chain` ِ زنجیرهٔ اسناد (`lib/doc-chain.js`)، در درِ جدای فقط‌دکان چون
 * `routes/sync-v1.js` مشترک با پمپ است. سر شاملِ ردیف‌هایی هم هست که هنوز همگام نشده‌اند ⇒ بریدنشان دیده می‌شود.
 *
 * قاعده‌ها (همان روحِ `doc-chain.report` و `AuditChain.resume`):
 *   - زنجیره فقط مالِ همین دستگاه است (`<device_id>/<دوره>`). ⛔ `device_id` ِ بدنه باید **همان** دستگاهِ نشست باشد
 *     (`req.sync.deviceUid` ⇐ `tokens.device_id` ⇐ `devices.device_uid`) — همیشه، مستقل از پرچمِ `sync.device_bind`
 *     ِ `requireSyncAccount` (که خاموشش فقط ثبت می‌کند): بی این، هر عضوِ دکان با شناسهٔ دستگاهِ دیگری سر می‌نشاند و
 *     «انشعاب»/«عقب‌گرد» ِ دروغ روی صفحهٔ صاحب می‌ساخت یا پیش از خودِ آن دستگاه جای سرش را می‌گرفت ([ownDevice]).
 *     نشستِ بی دستگاه (نسخه‌ای که هنگامِ ورود شناسه نفرستاد) ⇐ ۴۰۳ `device_unbound`: لنگر نمی‌گیرد تا دوباره وارد شود
 *     (برنامه شکست را می‌بلعد و همگام‌سازی نمی‌خوابد)؛ پذیرفتنش همان جعل را برای هر نشستِ بی دستگاه باز می‌گذاشت.
 *   - همان شماره و همان هش ⇐ هیچ نوشتنی (تکرار، پاسخِ گم‌شده، تلاشِ دوباره).
 *   - همان شماره با هشِ دیگر ⇐ `fork` (تاریخچهٔ دستگاه بازنویسی شد).
 *   - سرِ عقب‌تر از بزرگ‌ترین سرِ ثبت‌شدهٔ همان دوره، یا ادامهٔ دوره‌ای کهنه پس از دورهٔ تازه‌تر ⇐ `rollback`
 *     (دستگاه هرگز دوره را از عقب ادامه نمی‌دهد؛ دفترِ عقب‌رفته دورهٔ تازه می‌گیرد).
 *   - دورهٔ تازه (بازیابی، نصبِ دوباره) پذیرفته است: دورهٔ کهنه شاهد می‌ماند و یک رویدادِ `restarted` (نه شکست).
 *     جهشِ بیش از ۱۶ دوره ⇐ `bad_epoch`، بی ردیف و بی رویداد (ساختنِ دوره‌های بی‌شمار جدول را پر می‌کرد).
 * هر مشکل یک بار رویدادِ امنیتی می‌شود (`audit_logs`، کارِ `security.audit_chain`).
 *
 * ⛔ هرگز همگام‌سازی یا فروش را نمی‌بندد: مشکل فقط در پاسخ و سابقه است؛ فقط ورودیِ بدشکل ۴۰۰.
 * ⛔ پشتِ پرچمِ خاموشِ `shop.doc_chain` (همان پرچمِ زنجیرهٔ اسناد و حلقه‌های سابقه): خاموش ⇒ `{enabled:false}`.
 * ⛔ رایگان (SUB-10): پاسبانیِ یکپارچگی است، نه کلیدِ پولی؛ اشتراکِ تمام‌شده آن را نمی‌بندد.
 */
const db = require('../db');
const hc = require('./hash-chain');
const audit = require('./audit');
const { badRequest, forbidden } = require('../middleware/errors');

const FLAG = 'shop.doc_chain';
const ACTION = 'security.audit_chain';
const FORMAT = 'tohid-audit-chain-v1';
const MAX_HEADS = 4;
const MAX_EPOCH = 999999999;
/** همان سقفِ `doc-chain` */
const MAX_EPOCH_STEP = 16;
const EPOCH_RE = /^[1-9][0-9]{0,8}$/;

const PROBLEMS = Object.freeze({
  fork: 'همان ردیف با هشِ دیگری گزارش شد (تاریخچهٔ دستگاه بازنویسی شده)',
  rollback: 'سرِ زنجیره از آنچه دستگاه پیش‌تر گزارش کرده بود عقب‌تر است',
  restarted: 'دورهٔ تازه‌ای برای زنجیرهٔ سابقهٔ همین دستگاه آغاز شد',
  bad_epoch: 'شمارهٔ دوره نامعقول است (رویداد نیست)',
});

const enabled = (shopId) => require('./orders').flagOf(shopId, FLAG);
const isInt = (v, min, max) => Number.isSafeInteger(v) && v >= min && v <= max;

/** `<device>/<دوره>` ⇐ دوره؛ نادرست ⇐ 0 */
function epochOf(chain, deviceId) {
  if (typeof chain !== 'string' || !chain.startsWith(`${deviceId}/`)) return 0;
  const tail = chain.slice(deviceId.length + 1);
  return EPOCH_RE.test(tail) ? Number(tail) : 0;
}

/** گزارشِ دستگاه ⇐ سرهای سنجیده، به ترتیبِ (دوره، شماره)؛ نادرست ⇐ ۴۰۰ */
function parseReport(input, deviceId) {
  const b = input && typeof input === 'object' && !Array.isArray(input) ? input : {};
  if (b.format !== FORMAT) throw badRequest(`قالبِ زنجیره باید ${FORMAT} باشد`, 'bad_format');
  const heads = Array.isArray(b.heads) ? b.heads : [];
  if (heads.length > MAX_HEADS) throw badRequest(`حداکثر ${MAX_HEADS} سر در هر گزارش`, 'too_many_heads');
  const out = heads.map((h) => {
    const x = h && typeof h === 'object' && !Array.isArray(h) ? h : {};
    const epoch = epochOf(x.chain, deviceId);
    if (!epoch || epoch > MAX_EPOCH || !isInt(x.seq, 1, Number.MAX_SAFE_INTEGER) || typeof x.hash !== 'string' || !hc.HEX64.test(x.hash)) {
      throw badRequest('سرِ زنجیرهٔ سابقه نادرست است (زنجیره فقط مالِ همین دستگاه)', 'bad_head');
    }
    return { chain: x.chain, epoch, seq: x.seq, hash: x.hash };
  });
  if (new Set(out.map((h) => h.chain)).size !== out.length) throw badRequest('هر زنجیره فقط یک سر', 'bad_head');
  return out.sort((x, y) => (x.epoch - y.epoch) || (x.seq - y.seq));
}

async function lastEpochOf(shopId, deviceId) {
  const r = await db.one('SELECT MAX(epoch) AS e FROM device_audit_anchors WHERE shop_id=$1 AND device_id=$2', [shopId, deviceId]);
  return Number(r && r.e) || 0;
}

async function at(shopId, chain, seq) {
  return db.one('SELECT hash, problem, other_hash FROM device_audit_anchors WHERE shop_id=$1 AND chain=$2 AND seq=$3', [shopId, chain, seq]);
}

/** رویدادِ امنیتی؛ شکستِ ثبت هیچ چیزی را نمی‌خواباند */
async function flag(ctx, deviceId, ip, { chain, epoch, problem, seq }) {
  try {
    await audit.log({
      shopId: ctx.accountId, actorType: 'user', userId: ctx.userId || '', ip,
      action: ACTION, targetType: 'audit_chain', targetId: chain,
      detail: { problem, epoch, seq, deviceId, text: PROBLEMS[problem] || problem },
    });
  } catch (err) {
    console.error('[audit-anchor] رویداد ثبت نشد:', err.message);
  }
}

/** همان شماره، هشِ دیگر ⇐ `fork` روی همان ردیف (نخستین هشِ دیگر می‌ماند)؛ رویداد فقط بارِ نخست */
async function markFork(ctx, deviceId, ip, head, have) {
  const shopId = ctx.accountId;
  if (have.problem !== 'fork') {
    await db.query(
      `UPDATE device_audit_anchors SET problem='fork', other_hash=$4 WHERE shop_id=$1 AND chain=$2 AND seq=$3 AND problem <> 'fork'`,
      [shopId, head.chain, head.seq, head.hash],
    );
    await flag(ctx, deviceId, ip, { chain: head.chain, epoch: head.epoch, problem: 'fork', seq: head.seq });
  }
  return { chain: head.chain, seq: head.seq, problem: 'fork' };
}

const UNBOUND = 'این نشست به دستگاهی بسته نیست؛ برای پاسبانیِ سابقه از برنامه خارج و دوباره وارد شوید.';
const MISMATCH = 'این شناسهٔ دستگاه مالِ این نشست نیست. از برنامه خارج و دوباره وارد شوید.';

/**
 * ⛔ گزارش فقط برای دستگاهِ همین نشست (`ctx.deviceUid`) — پیش از پرچم و پیش از هر خواندن؛ دقیقاً همان رشته (نه
 * شکلِ دیگرِ حروف، نه شناسهٔ همگام‌سازیِ حسابِ دوم: `AuditAnchorSync` همان `SyncStore.deviceUid` ِ خام را می‌فرستد).
 */
function ownDevice(ctx, deviceId) {
  const bound = typeof ctx?.deviceUid === 'string' ? ctx.deviceUid : '';
  if (!bound) throw forbidden(UNBOUND, 'device_unbound');
  if (deviceId !== bound) throw forbidden(MISMATCH, 'device_mismatch');
}

/**
 * گزارشِ یک دستگاه. `ctx` همان `req.sync` (فقط دکان). مشکلِ زنجیره هرگز خطا نیست — در `problems` برمی‌گردد.
 */
async function report(ctx, deviceId, input, { ip = '' } = {}) {
  ownDevice(ctx, deviceId);
  const shopId = ctx.accountId;
  if (!(await enabled(shopId))) return { enabled: false };
  const heads = parseReport(input, deviceId);
  const acks = [];
  const problems = [];
  for (const head of heads) {
    const last = await lastEpochOf(shopId, deviceId);
    if (head.epoch > last + MAX_EPOCH_STEP) {
      problems.push({ chain: head.chain, seq: head.seq, problem: 'bad_epoch' });
      continue;
    }
    const have = await at(shopId, head.chain, head.seq);
    if (have) {
      if (have.hash === head.hash) {
        acks.push({ chain: head.chain, seq: head.seq, hash: head.hash });
        if (have.problem) problems.push({ chain: head.chain, seq: head.seq, problem: have.problem });
      } else {
        problems.push(await markFork(ctx, deviceId, ip, head, have));
      }
      continue;
    }
    const top = await db.one('SELECT MAX(seq) AS s FROM device_audit_anchors WHERE shop_id=$1 AND chain=$2', [shopId, head.chain]);
    const problem = head.epoch < last || head.seq < (Number(top && top.s) || 0) ? 'rollback' : '';
    const made = await db.one(
      `INSERT INTO device_audit_anchors (shop_id, chain, seq, hash, device_id, epoch, user_id, problem, reported_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT (shop_id, chain, seq) DO NOTHING RETURNING seq`,
      [shopId, head.chain, head.seq, head.hash, deviceId, head.epoch, ctx.userId || '', problem, db.now()],
    );
    if (!made) {
      //  گزارشِ هم‌زمانِ دیگری همین شماره را نشاند
      const now = await at(shopId, head.chain, head.seq);
      if (now && now.hash !== head.hash) { problems.push(await markFork(ctx, deviceId, ip, head, now)); continue; }
    } else if (problem) {
      await flag(ctx, deviceId, ip, { chain: head.chain, epoch: head.epoch, problem, seq: head.seq });
    } else if (head.epoch > last && last > 0) {
      await flag(ctx, deviceId, ip, { chain: head.chain, epoch: head.epoch, problem: 'restarted', seq: 0 });
    }
    acks.push({ chain: head.chain, seq: head.seq, hash: head.hash });
    if (problem) problems.push({ chain: head.chain, seq: head.seq, problem });
  }
  return { enabled: true, acks, problems, last_epoch: await lastEpochOf(shopId, deviceId) };
}

/** همهٔ لنگرهای یک دکان (برای `device-audit-chain.verifyShop`) */
async function anchorsOf(shopId) {
  const rows = await db.many(
    `SELECT chain, seq, hash, device_id, epoch, problem, other_hash, reported_at FROM device_audit_anchors
      WHERE shop_id=$1 ORDER BY chain, seq`, [String(shopId)],
  );
  return rows.map((r) => ({
    chain: r.chain, seq: Number(r.seq), hash: r.hash, deviceId: r.device_id, epoch: Number(r.epoch),
    problem: r.problem || '', otherHash: r.other_hash || '', reportedAt: Number(r.reported_at),
  }));
}

/** سنجشِ سرور برای صفحهٔ «سابقهٔ عملیات» ِ صاحب: همان `verify-chain --device-audit` برای همین دکان */
async function status(ctx) {
  const shopId = ctx.accountId;
  if (!(await enabled(shopId))) return { enabled: false };
  const r = await require('./device-audit-chain').verifyShop(shopId);
  const brk = (b) => (b ? { chain: b.chain, seq: b.seq, reason: b.reason, id: b.id || null } : null);
  return {
    enabled: true,
    valid: r.ok,
    broken: brk(r.broken),
    pending: r.pending || 0,
    chains: r.chains.map((c) => ({
      chain: c.chain, from: c.from, seq: c.seq, rows: c.rows,
      anchor: c.anchor ? { seq: c.anchor.seq, hash: c.anchor.hash } : null,
      pending: c.pending || 0, broken: brk(c.broken),
    })),
    legacy: r.legacy,
  };
}

module.exports = { FLAG, ACTION, FORMAT, PROBLEMS, MAX_HEADS, MAX_EPOCH_STEP, epochOf, parseReport, ownDevice, report, anchorsOf, status };
