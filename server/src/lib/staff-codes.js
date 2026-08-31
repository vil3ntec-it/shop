'use strict';
/**
 * کد شاگرد — راه ورود شاگرد به دکانِ صاحب کار.
 *
 * خودِ کد در دیتابیس نیست؛ فقط HMAC آن با راز سرور ذخیره می‌شود.
 * پس نه کسی که به دیتابیس دست پیدا کند می‌تواند وارد دکانی شود و نه
 * حتی مدیر سامانه کد شاگرد کسی را می‌بیند.
 */
const { createHmac, randomInt } = require('crypto');
const { query, one, many, tx, newId, now } = require('../db');
const config = require('../config');
const { badRequest, notFound, conflict, forbidden } = require('../middleware/errors');

// حروف مبهم (I، O، 0، 1) کنار گذاشته شده‌اند تا کد را اشتباه نخوانند
const ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const GROUPS = 3;
const GROUP_LEN = 4;
const PREFIX = 'SHG';

function pepper() {
  return config.secrets.api || config.secrets.otp || config.secrets.jwt || 'shop-staff-code';
}

/** SHG-8F29-KD72-PL51 → SHG8F29KD72PL51 */
function normalize(raw) {
  const s = String(raw || '').toUpperCase().replace(/[^A-Z0-9]/g, '');
  return s;
}

function format(body) {
  const parts = [];
  for (let i = 0; i < body.length; i += GROUP_LEN) parts.push(body.slice(i, i + GROUP_LEN));
  return `${PREFIX}-${parts.join('-')}`;
}

function hashCode(raw) {
  return createHmac('sha256', pepper()).update(normalize(raw)).digest('hex');
}

function randomBody() {
  let s = '';
  for (let i = 0; i < GROUPS * GROUP_LEN; i++) s += ALPHABET[randomInt(ALPHABET.length)];
  return s;
}

/**
 * ساخت کد تازه.
 * تا وقتی کدی یکتا نشده، دوباره تولید می‌شود — پس حتی با میلیون‌ها دکان
 * دو کد یکسان صادر نمی‌شود.
 */
async function create(shopId, createdBy, { role = 'staff', expiresAt = null, maxUses = 1 } = {}) {
  if (!['manager', 'staff'].includes(role)) throw badRequest('نقش کد معتبر نیست');
  if (maxUses < 0 || maxUses > 500) throw badRequest('تعداد استفاده معتبر نیست');
  if (expiresAt !== null && (!Number.isFinite(expiresAt) || expiresAt <= now())) {
    throw badRequest('تاریخ انقضا باید در آینده باشد');
  }

  for (let attempt = 0; attempt < 8; attempt++) {
    const body = randomBody();
    const code = format(body);
    const codeHash = hashCode(code);
    const exists = await one('SELECT 1 FROM staff_codes WHERE code_hash=$1', [codeHash]);
    if (exists) continue;
    const id = newId('stc');
    await query(
      `INSERT INTO staff_codes (id, shop_id, code_hash, code_hint, role, created_by, created_at, expires_at, max_uses, used_count, status)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,0,'active')`,
      [id, shopId, codeHash, body.slice(-4), role, createdBy, now(), expiresAt, maxUses]
    );
    // کد فقط همین یک بار برگردانده می‌شود؛ بعد از آن دیگر قابل بازیابی نیست
    return { id, code, role, expiresAt, maxUses };
  }
  throw conflict('ساخت کد یکتا ممکن نشد، دوباره تلاش کنید', 'code_generation_failed');
}

/**
 * کد ثابتِ یک دکان.
 *
 * از shop_id و شماره‌ی نسل با راز سرور ساخته می‌شود — پس همیشه همان است
 * و لازم نیست جایی به شکل خام نگه داشته شود. صاحب دکان هر بار که نگاه
 * کند همان کد را می‌بیند و می‌تواند به هر تعداد شاگرد بدهدش.
 */
function deriveStanding(shopId, generation) {
  const raw = createHmac('sha256', pepper())
    .update(`standing:${shopId}:${generation}`)
    .digest();
  let body = '';
  for (let i = 0; i < GROUPS * GROUP_LEN; i++) body += ALPHABET[raw[i] % ALPHABET.length];
  return format(body);
}

/**
 * کد ثابت را می‌دهد و اگر نبود می‌سازد.
 *
 * `max_uses = 0` یعنی بی‌شمار: یک کد برای همه‌ی شاگردها. مهلت هم ندارد.
 */
async function standing(shopId, createdBy = '', role = 'staff') {
  const live = await one(
    `SELECT * FROM staff_codes WHERE shop_id=$1 AND standing AND status='active'`,
    [shopId]
  );
  if (live) {
    return { id: live.id, code: deriveStanding(shopId, live.generation), role: live.role, generation: live.generation };
  }

  const last = await one(
    'SELECT COALESCE(MAX(generation), 0) AS g FROM staff_codes WHERE shop_id=$1 AND standing',
    [shopId]
  );
  const generation = Number(last ? last.g : 0) + 1;
  const code = deriveStanding(shopId, generation);
  const id = newId('stc');
  await query(
    `INSERT INTO staff_codes (id, shop_id, code_hash, code_hint, role, created_by, created_at,
                              expires_at, max_uses, used_count, status, standing, generation)
     VALUES ($1,$2,$3,$4,$5,$6,$7,NULL,0,0,'active',true,$8)`,
    [id, shopId, hashCode(code), normalize(code).slice(-4), role, createdBy, now(), generation]
  );
  return { id, code, role, generation };
}

/**
 * کد ثابت را عوض می‌کند — برای وقتی که لو رفته باشد.
 * شاگردهای فعلی بیرون نمی‌افتند؛ فقط کد قبلی دیگر کسی را وارد نمی‌کند.
 */
async function rotateStanding(shopId, createdBy = '') {
  await query(
    `UPDATE staff_codes SET status='revoked' WHERE shop_id=$1 AND standing AND status='active'`,
    [shopId]
  );
  return standing(shopId, createdBy);
}

async function list(shopId) {
  return many(
    `SELECT id, code_hint, role, created_at, expires_at, max_uses, used_count, status
       FROM staff_codes WHERE shop_id=$1 ORDER BY created_at DESC LIMIT 100`,
    [shopId]
  );
}

async function revoke(shopId, codeId) {
  const r = await one(
    `UPDATE staff_codes SET status='revoked' WHERE id=$1 AND shop_id=$2 AND status='active' RETURNING id`,
    [codeId, shopId]
  );
  if (!r) throw notFound('کد پیدا نشد یا از قبل باطل شده است', 'code_not_found');
  return true;
}

/**
 * استفاده از کد: شاگرد را عضو همان دکان می‌کند.
 * همه‌ی مرحله‌ها در یک تراکنش‌اند تا اگر جایی خطا داد، نه عضویت نیمه‌کاره
 * بماند و نه شمارنده‌ی استفاده بی‌جهت بالا برود.
 */
/**
 * این کد مالِ کدام دکان است — بدون آنکه خرج شود.
 *
 * ورودِ شاگرد لازم دارد پیش از ساختنِ حساب بداند دکان کدام است، تا
 * بتواند هویتِ پایدارِ «همین دستگاه روی همین دکان» را بسازد. اگر برای
 * این کار `redeem` را صدا می‌زدیم، هر بار یک استفاده از کد می‌خورد.
 */
async function shopIdOf(rawCode) {
  const normalized = normalize(rawCode);
  if (normalized.length !== PREFIX.length + GROUPS * GROUP_LEN || !normalized.startsWith(PREFIX)) {
    throw badRequest('قالب کد درست نیست', 'bad_code');
  }
  const row = await one('SELECT shop_id FROM staff_codes WHERE code_hash=$1', [hashCode(normalized)]);
  if (!row) throw notFound('این کد معتبر نیست', 'bad_code');
  /*
   * وضعیت کد اینجا سنجیده نمی‌شود، عمداً.
   *
   * کدی که «یک بار مصرف» است بعد از اولین ورود `exhausted` می‌شود. اگر
   * اینجا رد می‌کردیم، شاگرد فردا که برنامه را باز می‌کند دیگر وارد
   * نمی‌شد — با اینکه عضو فعال همان دکان است.
   *
   * سدّ سرِ جایش است: کسی که هنوز عضو نیست از `redeem` رد می‌شود و
   * آنجا وضعیت، مهلت و تعداد استفاده همه بررسی می‌شوند.
   */
  return row.shop_id;
}

async function redeem(rawCode, userId, ip = '') {
  const normalized = normalize(rawCode);
  if (normalized.length !== PREFIX.length + GROUPS * GROUP_LEN || !normalized.startsWith(PREFIX)) {
    throw badRequest('قالب کد درست نیست', 'bad_code');
  }
  const codeHash = hashCode(normalized);

  return tx(async (c) => {
    const { rows } = await c.query(
      'SELECT * FROM staff_codes WHERE code_hash=$1 FOR UPDATE', [codeHash]
    );
    const code = rows[0];
    if (!code) throw notFound('این کد معتبر نیست', 'bad_code');
    if (code.status !== 'active') throw forbidden('این کد دیگر کار نمی‌کند', 'code_inactive');
    if (code.expires_at && Number(code.expires_at) < now()) {
      await c.query(`UPDATE staff_codes SET status='revoked' WHERE id=$1`, [code.id]);
      throw forbidden('مهلت این کد تمام شده است', 'code_expired');
    }
    if (code.max_uses > 0 && code.used_count >= code.max_uses) {
      await c.query(`UPDATE staff_codes SET status='exhausted' WHERE id=$1`, [code.id]);
      throw forbidden('این کد قبلاً استفاده شده است', 'code_used');
    }

    const shop = (await c.query(`SELECT * FROM shops WHERE id=$1 AND status='active'`, [code.shop_id])).rows[0];
    if (!shop) throw notFound('دکان این کد فعال نیست', 'shop_not_found');

    // «چند کاربر روی یک دکان» قابلیت اشتراکی است. اگر اشتراک دکان تمام
    // شده باشد، شاگرد تازه اضافه نمی‌شود — تصمیم اینجا گرفته می‌شود، نه در گوشی.
    const ent = await require('./entitlement').entitlementOf(code.shop_id);
    if (!ent.features.includes('multi_device')) {
      throw forbidden('اشتراک این دکان اجازه‌ی افزودن شاگرد را نمی‌دهد', 'subscription_required');
    }
    if (shop.owner_user_id === userId) throw conflict('شما صاحب همین دکان هستید', 'already_owner');

    // اگر کاربر جای دیگری عضو است، بی‌خبر جابه‌جا نمی‌شود
    const other = (await c.query(
      `SELECT shop_id FROM shop_members WHERE user_id=$1 AND status='active' AND shop_id <> $2`,
      [userId, code.shop_id]
    )).rows[0];
    if (other) throw conflict('این حساب از قبل عضو دکان دیگری است', 'already_member');

    const t = now();
    const existing = (await c.query(
      'SELECT * FROM shop_members WHERE shop_id=$1 AND user_id=$2', [code.shop_id, userId]
    )).rows[0];

    if (existing) {
      await c.query(
        `UPDATE shop_members SET status='active', role=$3, updated_at=$4 WHERE id=$1 AND shop_id=$2`,
        [existing.id, code.shop_id, code.role, t]
      );
    } else {
      const active = (await c.query(
        `SELECT COUNT(*)::int AS n FROM shop_members WHERE shop_id=$1 AND status='active'`,
        [code.shop_id]
      )).rows[0].n;
      if (active >= shop.max_members) throw forbidden('ظرفیت اعضای این دکان پر است', 'shop_full');
      await c.query(
        `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
         VALUES ($1,$2,$3,$4,'active',$5,$5)`,
        [newId('mem'), code.shop_id, userId, code.role, t]
      );
    }

    const used = code.used_count + 1;
    const exhausted = code.max_uses > 0 && used >= code.max_uses;
    await c.query(
      `UPDATE staff_codes SET used_count=$2, status=$3 WHERE id=$1`,
      [code.id, used, exhausted ? 'exhausted' : 'active']
    );
    await c.query(
      'INSERT INTO staff_code_uses (id, staff_code_id, user_id, used_at, ip) VALUES ($1,$2,$3,$4,$5)',
      [newId('scu'), code.id, userId, t, ip]
    );

    return { shop, role: code.role };
  });
}

module.exports = {
  standing, rotateStanding, deriveStanding, create, list, revoke, redeem, shopIdOf, normalize, format, hashCode, PREFIX };
