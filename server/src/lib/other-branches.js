'use strict';
/**
 * POS-16/S17 — «موجودیِ همین کالا در شعبه‌های دیگر» از صندوق یا قیمت‌پرسی (فازِ ۴ ِ `docs/BRANCHES-fa.md` §۱۰؛ تصمیم به ما
 * سپرده شد، همان شیوهٔ POS های حرفه‌ای: «دیدنِ موجودیِ مکان‌های دیگر» ِ Shopify POS و Square — فقط عدد، فقط‌خواندنی).
 *
 *   ⛔ پاسخ فقط `[{ branchId, branchName, qty }]`: هیچ بها، فروش، حرکت، وظیفه یا سندِ آن شعبه. `qty` = «آزاد» ِ همان شعبه به
 *      هزارم (مکان‌های قابل‌فروش منهای نگه‌داشتهٔ سفارشِ آنلاین، هرگز منفی — منفی خودش نشانِ بررسیِ داخلیِ آن شعبه است).
 *   ⛔ کدام شعبه‌ها فقط از سرور: شعبه‌های فعالِ همین دکان جز شعبهٔ همین درخواست (سرآیندِ سنجیده)، و از آن‌ها فقط آن‌هایی که این
 *      عضو دسترسی دارد (`shop_member_branches`) — یا همه اگر مجوزِ `stock.view_other_branches` را دارد (صاحب همیشه؛ دیگران فقط
 *      وقتی صاحب داده، `shop_member_branch_perms`، مهاجرتِ ۹۳۵). هیچ شناسهٔ شعبه‌ای از درخواست خوانده نمی‌شود.
 *   ⛔ پشتِ `shop.branches` (و موجودیِ سرور `shop.catalog`)؛ خاموش ⇒ درِ ناشناخته.
 */
const { one, many, tx, now } = require('../db');
const stock = require('./stock');
const catalog = require('./catalog');
const branches = require('./branches');
const cb = require('./catalog-branch');
const { badRequest, notFound } = require('../middleware/errors');

const PERM = 'stock.view_other_branches';
const PERMS = Object.freeze([PERM]);

/** userId ⇒ مجوزهای شعبه‌ایِ داده‌شده */
async function grantsOf(shopId) {
  const by = new Map();
  for (const r of await many('SELECT user_id, perm FROM shop_member_branch_perms WHERE shop_id=$1 ORDER BY perm', [shopId])) {
    by.set(r.user_id, [...(by.get(r.user_id) || []), r.perm]);
  }
  return by;
}

/** فهرستِ اعضای `branches.members` با `perms` — فقط برای عضوی که دارد (بی ردیف ⇒ همان پاسخِ پیشین) */
async function withPerms(shopId, members) {
  const by = await grantsOf(shopId);
  return members.map((m) => (by.has(m.userId) ? { ...m, perms: by.get(m.userId) } : m));
}

/** مجوزهای شعبه‌ایِ یک عضو (فقط صاحب، سنجش در در): فهرستِ کامل جایگزین می‌شود؛ [] ⇒ برداشتن */
async function setPerms(shopId, idOrUserId, raw, byUserId) {
  if (!Array.isArray(raw) || raw.length > PERMS.length * 4) throw badRequest('فهرستِ مجوزها لازم است', 'bad_perms');
  const perms = [];
  for (const p of raw) {
    if (!PERMS.includes(p)) throw badRequest('این مجوز شناخته نیست', 'unknown_permission');
    if (!perms.includes(p)) perms.push(p);
  }
  return tx(async (c) => {
    const m = (await c.query(
      `SELECT id, user_id, role FROM shop_members WHERE shop_id=$2 AND (id=$1 OR user_id=$1) AND status <> 'removed'`,
      [String(idOrUserId || ''), shopId]
    )).rows[0];
    if (!m) throw notFound('این عضو در دکانِ شما نیست', 'member_not_found');
    if (m.role === 'owner') throw badRequest('صاحبِ دکان همیشه همهٔ شعبه‌ها را می‌بیند', 'owner_fixed');
    await c.query('DELETE FROM shop_member_branch_perms WHERE shop_id=$1 AND user_id=$2', [shopId, m.user_id]);
    for (const p of perms) {
      await c.query(
        'INSERT INTO shop_member_branch_perms (shop_id, user_id, perm, granted_by, granted_at) VALUES ($1,$2,$3,$4,$5)',
        [shopId, m.user_id, p, String(byUserId || ''), now()]
      );
    }
    return { member: m, perms };
  });
}

/** آیا این عضو موجودیِ همهٔ شعبه‌ها را می‌بیند (صاحب همیشه) */
async function seesAll(shopId, member) {
  if (!member) return false;
  if (member.role === 'owner') return true;
  return !!(await one('SELECT 1 AS x FROM shop_member_branch_perms WHERE shop_id=$1 AND user_id=$2 AND perm=$3',
    [shopId, String(member.user_id || ''), PERM]));
}

/**
 * موجودیِ آزادِ کالای `productId` در شعبه‌های دیگرِ دیدنی برای این عضو ⇒ `[{ branchId, branchName, qty }]` (ترتیبِ شعبه‌ها:
 * اصلی، سپس ترتیبِ ساخت). کالای ناشناس ⇒ ۴۰۴ ِ همان کاتالوگ. شکستِ خواندنِ دفترِ یک شعبه پاسخ را نمی‌اندازد (آخرین عددِ سرور).
 */
async function stockElsewhere(shopId, here, member, productId) {
  const pid = String(productId || '');
  if (!pid || pid.length > 128 || pid.includes('\u0000')) throw notFound('کالا پیدا نشد', 'product_not_found');
  if (!(await one('SELECT 1 AS x FROM catalog_products WHERE shop_id=$1 AND product_id=$2', [shopId, pid]))) {
    throw notFound('کالا پیدا نشد', 'product_not_found');
  }
  const others = (await branches.list(shopId)).filter((b) => b.status === 'active' && b.id !== here);
  let shown = others;
  if (others.length && !(await seesAll(shopId, member))) {
    const access = await branches.accessOf(shopId, member);
    shown = others.filter((b) => branches.allows(access, b.id));
  }
  const settings = await stock.settingsOf(shopId);
  const out = [];
  for (const b of shown) {
    const key = branches.partitionOf(shopId, b.id);
    try { await catalog.refresh(key, { maxAgeMs: 1, throttle: false }); } catch { /* همان آخرین عددِ سرور */ }
    const lv = (await stock.levelsOf(null, key, [pid])).get(pid) || {};
    const held = (await cb.heldOf(key, [pid])).get(pid) || 0;
    out.push({ branchId: b.id, branchName: b.name, qty: stock.freeOf(lv, held, settings).totalMilli });
  }
  return out;
}

module.exports = { PERM, PERMS, grantsOf, withPerms, setPerms, seesAll, stockElsewhere };
