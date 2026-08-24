'use strict';
/**
 * حساب مشترک دکان.
 *
 * یک دکان = یک دفتر مشترک. صاحب دکان آن را می‌سازد و با «کد دعوت»
 * تا سقف تعیین‌شده (پیش‌فرض ۵ نفر) عضو اضافه می‌کند. همه‌ی اعضا روی
 * همان داده کار می‌کنند، هر کدام روی گوشی خودشان.
 */
const { getDb, newId, now } = require('../db');
const { randomInt } = require('crypto');

const INVITE_TTL_MS = 7 * 24 * 60 * 60 * 1000;   // کد دعوت یک هفته معتبر است

class ShopError extends Error {
  constructor(message, code = 'shop_error', status = 400) {
    super(message); this.code = code; this.status = status; this.expose = true;
  }
}

/** کد دعوت خوانا: بدون حروف گیج‌کننده (0/O، 1/I). */
function makeInviteCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let out = '';
  for (let i = 0; i < 8; i++) out += alphabet[randomInt(alphabet.length)];
  return out.slice(0, 4) + '-' + out.slice(4);
}

function getShop(shopId) {
  return getDb().prepare('SELECT * FROM shops WHERE id=?').get(shopId) || null;
}

/** دکانی که کاربر عضو فعال آن است (هر کاربر فقط یک دکان). */
function getUserShop(userId) {
  return getDb().prepare(`
    SELECT s.*, m.role AS my_role
    FROM shop_members m JOIN shops s ON s.id = m.shop_id
    WHERE m.user_id = ? AND m.status = 'active'
    LIMIT 1
  `).get(userId) || null;
}

function getMembership(shopId, userId) {
  return getDb().prepare(
    "SELECT * FROM shop_members WHERE shop_id=? AND user_id=? AND status='active'"
  ).get(shopId, userId) || null;
}

function listMembers(shopId) {
  return getDb().prepare(`
    SELECT m.user_id, m.role, m.joined_at, u.name, u.email, u.phone, u.last_login_at
    FROM shop_members m JOIN users u ON u.id = m.user_id
    WHERE m.shop_id = ? AND m.status = 'active'
    ORDER BY CASE m.role WHEN 'owner' THEN 0 ELSE 1 END, m.joined_at
  `).all(shopId);
}

function activeMemberCount(shopId) {
  return getDb().prepare(
    "SELECT COUNT(*) n FROM shop_members WHERE shop_id=? AND status='active'"
  ).get(shopId).n;
}

/** ساخت دکان. سازنده به‌طور خودکار صاحب آن می‌شود. */
function createShop(userId, name, maxMembers = 5) {
  const db = getDb();
  if (getUserShop(userId)) {
    throw new ShopError('شما از قبل عضو یک دکان هستید', 'already_in_shop', 409);
  }
  const t = now();
  const shop = {
    id: newId('shop'),
    name: String(name || '').trim().slice(0, 80) || 'دکان من',
    owner_id: userId,
    max_members: Math.min(20, Math.max(1, Number(maxMembers) || 5)),
    created_at: t, updated_at: t,
  };
  db.transaction(() => {
    db.prepare(`INSERT INTO shops (id,name,owner_id,max_members,created_at,updated_at)
                VALUES (@id,@name,@owner_id,@max_members,@created_at,@updated_at)`).run(shop);
    db.prepare(`INSERT INTO shop_members (shop_id,user_id,role,status,joined_at)
                VALUES (?,?,'owner','active',?)`).run(shop.id, userId, t);
    db.prepare('INSERT INTO shop_rev (shop_id,last_rev) VALUES (?,0)').run(shop.id);
    db.prepare('INSERT INTO shop_settings (shop_id,data,rev,updated_at) VALUES (?,?,0,?)')
      .run(shop.id, '{}', t);
  })();
  return getShop(shop.id);
}

/** ساخت کد دعوت — فقط صاحب دکان. */
function createInvite(shopId, byUserId, role = 'staff') {
  const m = getMembership(shopId, byUserId);
  if (!m || m.role !== 'owner') {
    throw new ShopError('فقط صاحب دکان می‌تواند عضو دعوت کند', 'not_owner', 403);
  }
  const shop = getShop(shopId);
  if (activeMemberCount(shopId) >= shop.max_members) {
    throw new ShopError(
      `سقف اعضای این دکان ${shop.max_members} نفر است. برای دعوت نفر جدید، یکی از اعضا را حذف کنید.`,
      'member_limit_reached', 409
    );
  }
  const t = now();
  const code = makeInviteCode();
  getDb().prepare(`INSERT INTO shop_invites (code,shop_id,created_by,role,expires_at,created_at)
                   VALUES (?,?,?,?,?,?)`)
    .run(code, shopId, byUserId, role === 'owner' ? 'owner' : 'staff', t + INVITE_TTL_MS, t);
  return { code, expiresAt: t + INVITE_TTL_MS, role };
}

/** پیوستن با کد دعوت. */
function joinWithInvite(userId, rawCode) {
  const db = getDb();
  const code = String(rawCode || '').trim().toUpperCase().replace(/\s/g, '');
  const inv = db.prepare('SELECT * FROM shop_invites WHERE code=?').get(code);
  if (!inv) throw new ShopError('کد دعوت نامعتبر است', 'bad_invite', 404);
  if (inv.used_by) throw new ShopError('این کد قبلاً استفاده شده است', 'invite_used', 409);
  if (inv.expires_at < now()) throw new ShopError('این کد منقضی شده است', 'invite_expired', 410);

  const existing = getUserShop(userId);
  if (existing) {
    if (existing.id === inv.shop_id) return getShop(inv.shop_id);   // از قبل عضو است
    throw new ShopError('شما عضو دکان دیگری هستید. اول از آن خارج شوید.', 'already_in_shop', 409);
  }

  const shop = getShop(inv.shop_id);
  if (!shop) throw new ShopError('دکان پیدا نشد', 'shop_not_found', 404);
  if (activeMemberCount(shop.id) >= shop.max_members) {
    throw new ShopError(`سقف اعضای این دکان ${shop.max_members} نفر است`, 'member_limit_reached', 409);
  }

  const t = now();
  db.transaction(() => {
    // اگر قبلاً حذف شده بود، عضویتش دوباره فعال می‌شود
    db.prepare(`INSERT INTO shop_members (shop_id,user_id,role,status,joined_at)
                VALUES (?,?,?,'active',?)
                ON CONFLICT(shop_id,user_id) DO UPDATE SET status='active', role=excluded.role`)
      .run(shop.id, userId, inv.role, t);
    db.prepare('UPDATE shop_invites SET used_by=?, used_at=? WHERE code=?').run(userId, t, code);
  })();
  return getShop(shop.id);
}

/** حذف عضو — فقط صاحب دکان، و صاحب دکان قابل حذف نیست. */
function removeMember(shopId, byUserId, targetUserId) {
  const me = getMembership(shopId, byUserId);
  if (!me || me.role !== 'owner') {
    throw new ShopError('فقط صاحب دکان می‌تواند عضو حذف کند', 'not_owner', 403);
  }
  const shop = getShop(shopId);
  if (targetUserId === shop.owner_id) {
    throw new ShopError('صاحب دکان قابل حذف نیست', 'cannot_remove_owner', 400);
  }
  const changed = getDb().prepare(
    "UPDATE shop_members SET status='removed' WHERE shop_id=? AND user_id=? AND status='active'"
  ).run(shopId, targetUserId).changes;
  if (!changed) throw new ShopError('این کاربر عضو دکان نیست', 'not_a_member', 404);
  return true;
}

/** خروج داوطلبانه از دکان. صاحب دکان نمی‌تواند خارج شود. */
function leaveShop(shopId, userId) {
  const shop = getShop(shopId);
  if (shop && shop.owner_id === userId) {
    throw new ShopError('صاحب دکان نمی‌تواند از دکان خارج شود', 'owner_cannot_leave', 400);
  }
  getDb().prepare("UPDATE shop_members SET status='removed' WHERE shop_id=? AND user_id=?")
    .run(shopId, userId);
  return true;
}

function renameShop(shopId, byUserId, name) {
  const m = getMembership(shopId, byUserId);
  if (!m || m.role !== 'owner') throw new ShopError('فقط صاحب دکان می‌تواند نام را تغییر دهد', 'not_owner', 403);
  getDb().prepare('UPDATE shops SET name=?, updated_at=? WHERE id=?')
    .run(String(name || '').trim().slice(0, 80) || 'دکان من', now(), shopId);
  return getShop(shopId);
}

module.exports = {
  ShopError, INVITE_TTL_MS, makeInviteCode,
  getShop, getUserShop, getMembership, listMembers, activeMemberCount,
  createShop, createInvite, joinWithInvite, removeMember, leaveShop, renameShop,
};
