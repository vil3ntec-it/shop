'use strict';
/**
 * شعبه درونِ یک دکان — فازِ ۱ ِ سرور (تصمیمِ صاحب‌کار ۱، ۱b، ۳۴ ِ ۱۴۰۵/۰۸/۲۳؛ طراحی: `docs/BRANCHES-fa.md`).
 *
 * قاعده (سرور مرجع است؛ برنامه فقط نشان می‌دهد):
 *   ⛔ پرچمِ `shop.branches` (live_config، پیش‌فرض خاموش؛ همان `orders.flagOf`). خاموش ⇒ هیچ خواندنی از این جدول‌ها،
 *      هیچ سنجشی و هیچ اثری از سرآیندِ `X-Branch-Id`: پاسخ‌ها بایت‌به‌بایت همان امروز.
 *   ⛔ شعبهٔ اصلی ('') همیشه هست و همهٔ دادهٔ امروز است (بی کوچ). شعبهٔ دیگر `brn_<۲۴ هگز>`.
 *   ⛔ صاحب ⇐ همهٔ شعبه‌ها (و جمع)؛ عضوِ دیگر ⇐ فقط شعبه‌هایی که صاحب داده؛ بی ردیف = فقط اصلی (همان امروز).
 *      شعبهٔ بایگانی‌شده از دسترسیِ همه بیرون می‌رود (داده‌اش می‌ماند؛ صاحب در گزارش می‌بیند).
 *   ⛔ شناسهٔ شعبهٔ ناشناخته، بدشکل، بایگانی، از دکانِ دیگر یا بیرون از دسترسی ⇒ همان یک پاسخ (`branch_forbidden`) —
 *      وجودِ شعبه از بیرون شمرده نمی‌شود.
 *   ⛔ دسترسی در هر درخواست از نو خوانده می‌شود ⇒ تغییرِ صاحب از درخواستِ بعد اثر دارد.
 */
const { one, many, tx, newId, now } = require('../db');
const { flagOf } = require('./orders');
const key = require('./branch-key');
const { badRequest, forbidden, notFound, conflict } = require('../middleware/errors');

const FLAG = 'shop.branches';
const MAIN = key.MAIN;
const MAIN_NAME = 'شعبهٔ اصلی';
const ID_RE = /^brn_[0-9a-f]{24}$/;
const MAX_BRANCHES = 50;
const MAX_NAME = 60;
const HEADER = 'x-branch-id';

const enabled = (shopId) => flagOf(key.shopOf(shopId), FLAG);

function nameOf(raw) {
  const s = String(raw ?? '').replace(/[\u0000-\u001f\u007f]/g, '').replace(/\s+/g, ' ').trim();
  if (!s) throw badRequest('نامِ شعبه لازم است', 'bad_name');
  if (s.length > MAX_NAME) throw badRequest(`نامِ شعبه حداکثر ${MAX_NAME} نویسه`, 'bad_name');
  return s;
}

/** شناسهٔ شعبه از نشانی: `main` یا '' ⇐ اصلی؛ بدشکل ⇐ null */
function idOf(raw) {
  const s = String(raw ?? '').trim();
  if (s === '' || s === 'main') return MAIN;
  return ID_RE.test(s) ? s : null;
}

/** ادعای شعبه از سرآیندِ درخواست: نبود/تهی ⇐ null؛ هر مقدارِ دیگر همان متن (سنجش با `resolve`) */
function claimOf(req) {
  const raw = req && req.headers ? req.headers[HEADER] : undefined;
  const s = Array.isArray(raw) ? String(raw[0] ?? '') : String(raw ?? '');
  const t = s.trim();
  return t === '' ? null : t.slice(0, 80);
}

// ── شعبه‌ها ───────────────────────────────────────────────────────────

/** همهٔ شعبه‌های دکان، اصلی اول (با ردیف یا بی آن)، بقیه به ترتیبِ ساخت */
async function list(shopId) {
  const rows = await many(
    'SELECT id, name, status, created_at FROM shop_branches WHERE shop_id=$1 ORDER BY created_at ASC, id ASC',
    [shopId]
  );
  const main = rows.find((r) => r.id === MAIN);
  const out = [{ id: MAIN, name: main ? main.name : MAIN_NAME, status: 'active', main: true }];
  for (const r of rows) if (r.id !== MAIN) out.push({ id: r.id, name: r.name, status: r.status, main: false });
  return out;
}

async function nameTaken(q, shopId, name, exceptId) {
  const branches = await listWith(q, shopId);
  return branches.some((b) => b.status === 'active' && b.id !== exceptId && b.name.toLowerCase() === name.toLowerCase());
}
async function listWith(q, shopId) {
  const rows = (await q.query('SELECT id, name, status FROM shop_branches WHERE shop_id=$1', [shopId])).rows;
  const main = rows.find((r) => r.id === MAIN);
  return [{ id: MAIN, name: main ? main.name : MAIN_NAME, status: 'active' }, ...rows.filter((r) => r.id !== MAIN)];
}

/** شعبهٔ تازه (فقط صاحب؛ سنجشِ نقش در در) */
async function create(shopId, rawName, byUserId) {
  const name = nameOf(rawName);
  return tx(async (c) => {
    //  یک ساخت در هر لحظه برای هر دکان ⇒ سقف و «نامِ تکراری» زیرِ هم‌زمانی هم درست
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`branches:${shopId}`]);
    const n = Number((await c.query("SELECT COUNT(*)::int AS n FROM shop_branches WHERE shop_id=$1 AND id <> ''", [shopId])).rows[0].n);
    if (n >= MAX_BRANCHES) throw badRequest(`حداکثر ${MAX_BRANCHES} شعبه`, 'too_many_branches');
    if (await nameTaken(c, shopId, name, null)) throw conflict('شعبه‌ای با همین نام هست', 'branch_name_taken');
    const id = newId('brn');
    const t = now();
    await c.query(
      `INSERT INTO shop_branches (shop_id, id, name, status, created_by, created_at, updated_at) VALUES ($1,$2,$3,'active',$4,$5,$5)`,
      [shopId, id, name, String(byUserId || ''), t]
    );
    return { id, name, status: 'active', main: false };
  });
}

/** نام یا وضعیت (فعال/بایگانی). شعبهٔ اصلی فقط نام می‌گیرد. */
async function update(shopId, rawId, body, byUserId) {
  const id = idOf(rawId);
  if (id === null) throw notFound('این شعبه در دکانِ شما نیست', 'branch_not_found');
  const b = body && typeof body === 'object' ? body : {};
  const hasName = Object.prototype.hasOwnProperty.call(b, 'name');
  const hasStatus = Object.prototype.hasOwnProperty.call(b, 'status');
  if (!hasName && !hasStatus) throw badRequest('نام یا وضعیت لازم است', 'nothing_to_change');
  if (hasStatus && !['active', 'archived'].includes(b.status)) throw badRequest('وضعیت فقط active یا archived', 'bad_status');
  if (id === MAIN && hasStatus && b.status !== 'active') throw badRequest('شعبهٔ اصلی بایگانی نمی‌شود', 'main_branch_fixed');
  const name = hasName ? nameOf(b.name) : null;
  return tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`branches:${shopId}`]);
    const t = now();
    if (id === MAIN) {
      if (name === null) return (await listWith(c, shopId))[0];
      if (await nameTaken(c, shopId, name, MAIN)) throw conflict('شعبه‌ای با همین نام هست', 'branch_name_taken');
      await c.query(
        `INSERT INTO shop_branches (shop_id, id, name, status, created_by, created_at, updated_at) VALUES ($1,'',$2,'active',$3,$4,$4)
         ON CONFLICT (shop_id, id) DO UPDATE SET name=excluded.name, updated_at=excluded.updated_at`,
        [shopId, name, String(byUserId || ''), t]
      );
      return { id: MAIN, name, status: 'active', main: true };
    }
    const cur = (await c.query('SELECT id, name, status FROM shop_branches WHERE shop_id=$1 AND id=$2 FOR UPDATE', [shopId, id])).rows[0];
    if (!cur) throw notFound('این شعبه در دکانِ شما نیست', 'branch_not_found');
    const nextName = name === null ? cur.name : name;
    const nextStatus = hasStatus ? b.status : cur.status;
    if (nextStatus === 'active' && await nameTaken(c, shopId, nextName, id)) throw conflict('شعبه‌ای با همین نام هست', 'branch_name_taken');
    await c.query('UPDATE shop_branches SET name=$3, status=$4, updated_at=$5 WHERE shop_id=$1 AND id=$2', [shopId, id, nextName, nextStatus, t]);
    return { id, name: nextName, status: nextStatus, main: false };
  });
}

// ── اعضا ─────────────────────────────────────────────────────────────

/** همهٔ اعضای غیرِ مالک با شعبه‌هایشان (`['']` = پیش‌فرض: فقط اصلی) */
async function members(shopId) {
  const rows = await many(
    `SELECT m.id, m.user_id, m.role, m.status, u.name
       FROM shop_members m JOIN users u ON u.id = m.user_id
      WHERE m.shop_id=$1 AND m.status <> 'removed' AND m.role <> 'owner'
      ORDER BY m.created_at ASC, m.id ASC`,
    [shopId]
  );
  const links = await many('SELECT user_id, branch_id FROM shop_member_branches WHERE shop_id=$1 ORDER BY branch_id', [shopId]);
  const by = new Map();
  for (const l of links) by.set(l.user_id, [...(by.get(l.user_id) || []), l.branch_id]);
  return rows.map((r) => ({
    id: r.id, userId: r.user_id, name: r.name || '', role: r.role, status: r.status, branches: by.get(r.user_id) || [MAIN],
  }));
}

/** شعبه‌های یک عضو (فقط صاحب). فهرستِ دقیقاً [''] ⇐ ردیف‌ها پاک (= پیش‌فرض). */
async function setMember(shopId, idOrUserId, raw, byUserId) {
  if (!Array.isArray(raw) || raw.length < 1 || raw.length > MAX_BRANCHES) throw badRequest('فهرستِ شعبه‌ها لازم است', 'bad_branches');
  const ids = [];
  for (const x of raw) {
    const id = idOf(x);
    if (id === null) throw notFound('این شعبه در دکانِ شما نیست', 'branch_not_found');
    if (!ids.includes(id)) ids.push(id);
  }
  return tx(async (c) => {
    const m = (await c.query(
      `SELECT id, user_id, role FROM shop_members WHERE shop_id=$2 AND (id=$1 OR user_id=$1) AND status <> 'removed'`,
      [String(idOrUserId || ''), shopId]
    )).rows[0];
    if (!m) throw notFound('این عضو در دکانِ شما نیست', 'member_not_found');
    if (m.role === 'owner') throw badRequest('صاحبِ دکان همیشه همهٔ شعبه‌ها را دارد', 'owner_fixed');
    const active = new Set((await listWith(c, shopId)).filter((b) => b.status === 'active').map((b) => b.id));
    for (const id of ids) if (!active.has(id)) throw notFound('این شعبه در دکانِ شما نیست', 'branch_not_found');
    await c.query('DELETE FROM shop_member_branches WHERE shop_id=$1 AND user_id=$2', [shopId, m.user_id]);
    if (!(ids.length === 1 && ids[0] === MAIN)) {
      const t = now();
      for (const id of ids) {
        await c.query(
          'INSERT INTO shop_member_branches (shop_id, user_id, branch_id, updated_by, updated_at) VALUES ($1,$2,$3,$4,$5)',
          [shopId, m.user_id, id, String(byUserId || ''), t]
        );
      }
    }
    return { member: m, branches: ids.length === 1 && ids[0] === MAIN ? [MAIN] : ids };
  });
}

// ── دسترسی ──────────────────────────────────────────────────────────

/**
 * شعبه‌هایی که این عضو می‌بیند/می‌نویسد ⇒ `{all, ids}` (`ids` فقط شعبه‌های فعال؛ اصلی همیشه فعال).
 * فقط وقتی پرچم روشن است صدا زده می‌شود (صدازننده پرچم را پیش‌تر سنجیده است).
 * @param member `{role, user_id}` (همان `req.member`) یا `{role, userId}` (بافتِ همگام‌سازی)
 */
async function accessOf(shopId, member) {
  const role = member ? member.role : '';
  const userId = member ? String(member.user_id || member.userId || '') : '';
  const branches = await list(shopId);
  const active = branches.filter((b) => b.status === 'active').map((b) => b.id);
  if (role === 'owner') return { all: true, ids: active };
  const rows = userId ? await many('SELECT branch_id FROM shop_member_branches WHERE shop_id=$1 AND user_id=$2', [shopId, userId]) : [];
  if (!rows.length) return { all: false, ids: [MAIN] };
  const mine = new Set(rows.map((r) => r.branch_id));
  return { all: false, ids: active.filter((id) => mine.has(id)) };
}

const allows = (access, id) => !!access && access.ids.includes(id);

/**
 * ادعا ⇐ شعبهٔ مجاز. ادعای نبود ⇐ اصلی اگر مجاز است، وگرنه ۴۰۳ `branch_required` (برنامهٔ کهنهٔ کارمندِ شعبه هرگز به
 * اصلی نمی‌افتد). هر ادعای نامجاز ⇐ ۴۰۳ `branch_forbidden` (یکسان برای ناشناخته و بیگانه).
 */
function resolve(access, claim) {
  if (claim === null || claim === undefined) {
    if (allows(access, MAIN)) return MAIN;
    throw forbidden('شعبه را مشخص کنید (سرآیندِ X-Branch-Id)', 'branch_required');
  }
  const id = idOf(claim);
  if (id === null || !allows(access, id)) throw forbidden('این شعبه در دسترسیِ شما نیست', 'branch_forbidden');
  return id;
}

/** آیا هیچ عضوی در هیچ دکانی شعبه گرفته؟ (راهِ تندِ نگهبان: نه ⇒ همه شعبهٔ اصلی را دارند) */
async function anyAssignments() {
  return !!(await one('SELECT 1 AS x FROM shop_member_branches LIMIT 1'));
}

/** بافتِ همگام‌سازیِ v3 برای یک شعبهٔ غیرِ اصلی: ردیف‌ها در بخشِ جدا، همهٔ سنجش‌های دکان با `shopId` */
function ctxFor(sync, branchId) {
  if (!branchId) return sync;
  return { ...sync, shopId: sync.accountId, branchId, accountId: key.partitionOf(sync.accountId, branchId) };
}

module.exports = {
  FLAG, MAIN, MAIN_NAME, MAX_BRANCHES, HEADER, enabled, idOf, claimOf, list, create, update, members, setMember,
  accessOf, allows, resolve, anyAssignments, ctxFor,
  partitionOf: key.partitionOf, shopOf: key.shopOf,
};
