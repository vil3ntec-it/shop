'use strict';
/**
 * کد وی‌آی‌پی — شش رقم که اشتراک را فعال می‌کند.
 *
 * ── مشکلی که این حل می‌کند ─────────────────────────────────────────
 * تا امروز صاحب سامانه برای دادن اشتراک به کسی باید دکانش را در پنل
 * پیدا می‌کرد و دستی تمدید می‌زد. یعنی طرف باید اول ثبت‌نام می‌کرد، اسم
 * دکانش را می‌گفت، و صاحب سامانه هم باید همان لحظه پشت پنل می‌بود.
 *
 * حالا: مدیر یک کد می‌سازد و ایمیل طرف را می‌نویسد. سرور **خودش** کد را
 * ایمیل می‌کند. طرف هر وقت خواست، همان شش رقم را در برنامه یا سایت
 * می‌زند و اشتراکش فعال می‌شود. صاحب سامانه دیگر واسطه نیست.
 *
 * ── چرا کد در دیتابیس نیست ─────────────────────────────────────────
 * فقط HMACش ذخیره می‌شود، مثل کد شاگرد. پس کسی که به دیتابیس دست پیدا
 * کند نمی‌تواند کدها را بردارد و خودش اشتراک بسازد.
 *
 * ── چرا شش رقم و نه چیزی درازتر ────────────────────────────────────
 * چون قرار است کسی آن را از روی ایمیل بخواند و در گوشی تایپ کند. سدّ
 * امنیتی‌اش تعداد رقم نیست: هر کد یک بار مصرف است، مهلت دارد، و مسیر
 * مصرفش محدودِ نرخ است — پس حدس زدن شش رقم عملاً ناممکن می‌ماند.
 *
 * ── دو دفترِ جدا ───────────────────────────────────────────────────
 * کدِ دکان و کدِ پمپ در دو جدول می‌نشینند. اگر یک‌جا بودند، کدی که برای
 * پمپ ساخته شده می‌توانست اشتراکِ دکانی را باز کند — و آن اشتباهی است
 * که فقط وقتی دیده می‌شود که پول از دست رفته باشد.
 */
const { createHmac, randomInt } = require('crypto');
const { query, one, many, tx, newId, now } = require('../db');
const config = require('../config');
const plans = require('./plans');
const mailer = require('./mailer');
const { sanitizeFeatures } = require('./features');
const tenancy = require('./tenancy');
const { badRequest, notFound, forbidden, conflict } = require('../middleware/errors');

const DIGITS = 6;

function pepper() {
  return config.secrets.api || config.secrets.otp || config.secrets.jwt || 'shop-vip-code';
}

function normalize(raw) {
  return String(raw || '').replace(/\D/g, '');
}

function hashCode(raw) {
  return createHmac('sha256', pepper()).update(normalize(raw)).digest('hex');
}

function randomCode() {
  let s = String(randomInt(1, 10));           // رقم اول صفر نباشد
  for (let i = 1; i < DIGITS; i++) s += String(randomInt(10));
  return s;
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
}

/**
 * بستنِ همین منطق روی جدولِ کدهای یک بخش.
 * @param {object} T یکی از ثابت‌های lib/tenancy.js
 */
function build(T) {
  const TBL = T.vipTable;
  const KEY = T.vipTenantKey;      // shop_id | station_id
  const USED = T.vipUsedKey;       // used_shop_id | used_station_id

  /** ردیف دیتابیس → چیزی که برنامه‌ی مدیریت می‌بیند. کد خام هرگز اینجا نیست. */
  function shape(row) {
    return {
      id: row.id,
      app: T.app,
      hint: row.code_hint,
      plan: row.plan,
      days: row.days === null ? null : Number(row.days),
      features: row.features,
      maxDevices: Number(row.max_devices),
      note: row.note,
      email: row.email,
      emailStatus: row.email_status,
      emailError: row.email_error,
      emailSentAt: row.email_sent_at ? Number(row.email_sent_at) : null,
      tenantId: row[KEY] || '',
      usedTenantId: row[USED] || '',
      //  نام‌های قدیمی، تا پنلِ فعلی نشکند
      shopId: row[KEY] || '',
      status: row.status,
      createdAt: Number(row.created_at),
      expiresAt: row.expires_at ? Number(row.expires_at) : null,
      usedAt: row.used_at ? Number(row.used_at) : null,
      usedBy: row.used_by || '',
      usedShopId: row[USED] || '',
    };
  }

  /**
   * ساخت کد.
   *
   * `days` اگر نیامده باشد از مدت پلن حساب می‌شود — پس مدیر می‌تواند فقط
   * بگوید «شش ماهه» و لازم نباشد ۱۸۰ را خودش بشمارد.
   */
  async function create({
    plan = 'custom', days = null, features = [], maxDevices = 10, note = '',
    email = '', shopId = null, tenantId = null, expiresInDays = 30, createdBy = '',
  } = {}) {
    const forTenant = tenantId ?? shopId ?? null;
    let finalDays = days;
    if (finalDays === null || finalDays === undefined) {
      const p = await plans.getPlan(plan);
      if (p) finalDays = plans.approxDays(p.amount, p.unit);
    }
    if (!finalDays || finalDays < 1) finalDays = 30;

    const t = now();
    const expiresAt = expiresInDays > 0 ? t + expiresInDays * 24 * 3600 * 1000 : null;

    for (let attempt = 0; attempt < 12; attempt++) {
      const code = randomCode();
      const codeHash = hashCode(code);
      const clash = await one(`SELECT 1 FROM ${TBL} WHERE code_hash=$1 AND status='active'`, [codeHash]);
      if (clash) continue;
      const id = newId('vip');
      const row = await one(
        `INSERT INTO ${TBL} (id, code_hash, code_hint, plan, days, features, max_devices, note,
                             email, email_status, ${KEY}, created_by, created_at, expires_at, status)
         VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7,$8,$9,$10,$11,$12,$13,$14,'active') RETURNING *`,
        [id, codeHash, code.slice(-2), plan, finalDays,
          JSON.stringify(sanitizeFeatures(features, T.app)),
          maxDevices, note, String(email || '').trim().toLowerCase(),
          email ? 'queued' : 'none', forTenant || null, createdBy, t, expiresAt]
      );
      //  کد خام فقط همین یک بار برمی‌گردد
      return { code, row: shape(row) };
    }
    throw conflict('ساخت کد یکتا ممکن نشد، دوباره تلاش کنید', 'code_generation_failed');
  }

  /**
   * فرستادن کد به ایمیلِ گیرنده.
   *
   * نتیجه در همان ردیف ثبت می‌شود — رفت یا نرفت و اگر نرفت چرا. بدون
   * این، مدیر «ساخته شد» می‌دید و نمی‌فهمید ایمیل اصلاً بیرون نرفته.
   */
  async function mail(id, code, { title = '', appName = 'توحید' } = {}) {
    const row = await one(`SELECT * FROM ${TBL} WHERE id=$1`, [id]);
    if (!row) throw notFound('کد پیدا نشد');
    if (!row.email) return shape(row);

    const days = Number(row.days) || 30;
    const planTitle = title || (await plans.getPlan(row.plan))?.title || row.plan;
    const subject = `کد اشتراک ${appName}`;
    const text = [
      'سلام،',
      '',
      `یک اشتراک ${planTitle} (${days} روز) برای شما فعال شده است.`,
      '',
      `کد شما: ${code}`,
      '',
      'برنامه را باز کنید، به بخش اشتراک بروید و همین کد را وارد کنید.',
      row.expires_at ? `این کد تا ${new Date(Number(row.expires_at)).toLocaleDateString('fa-IR')} معتبر است.` : '',
      row.note ? `\n${row.note}` : '',
    ].filter(Boolean).join('\n');

    const html = mailer.card({
      title: 'اشتراک شما آماده است',
      lead: `یک اشتراک <b>${planTitle}</b> به مدت <b>${days} روز</b> برای شما در نظر گرفته شده. این کد را در برنامه یا سایت وارد کنید:`,
      code,
      body: [
        '<b>چطور استفاده کنم؟</b><br>',
        'برنامه را باز کنید ← بخش اشتراک ← «کد اشتراک دارم» ← همین شش رقم را بزنید.',
        row.note ? `<br><br>${escapeHtml(row.note)}` : '',
      ].join(''),
      footer: row.expires_at
        ? `این کد یک بار مصرف است و تا ${new Date(Number(row.expires_at)).toLocaleDateString('fa-IR')} کار می‌کند.`
        : 'این کد یک بار مصرف است.',
    });

    try {
      await mailer.send({ to: row.email, subject, text, html });
      const saved = await one(
        `UPDATE ${TBL} SET email_status='sent', email_sent_at=$2, email_error='' WHERE id=$1 RETURNING *`,
        [id, now()]
      );
      return shape(saved);
    } catch (err) {
      const saved = await one(
        `UPDATE ${TBL} SET email_status='failed', email_error=$2 WHERE id=$1 RETURNING *`,
        [id, String(err.message || err).slice(0, 400)]
      );
      return shape(saved);
    }
  }

  async function list({ status = '', limit = 100 } = {}) {
    const rows = status
      ? await many(`SELECT * FROM ${TBL} WHERE status=$1 ORDER BY created_at DESC LIMIT $2`, [status, limit])
      : await many(`SELECT * FROM ${TBL} ORDER BY created_at DESC LIMIT $1`, [limit]);
    return rows.map(shape);
  }

  async function revoke(id) {
    const row = await one(
      `UPDATE ${TBL} SET status='revoked' WHERE id=$1 AND status='active' RETURNING *`, [id]
    );
    if (!row) throw notFound('کد پیدا نشد یا از قبل خرج شده است', 'code_not_found');
    return shape(row);
  }

  /**
   * خرج کردن کد — اشتراک را روی دکانِ (یا پمپِ) همین کاربر می‌نشاند.
   *
   * همه در یک تراکنش، با `FOR UPDATE` روی خودِ کد: اگر دو نفر همزمان یک
   * کد را بزنند، فقط یکی می‌گیرد. بدون این، یک کد می‌توانست دو اشتراک
   * بدهد.
   */
  async function redeem(rawCode, { userId, shopId, tenantId }) {
    const target = tenantId ?? shopId;
    const clean = normalize(rawCode);
    if (clean.length !== DIGITS) throw badRequest('کد باید شش رقم باشد', 'bad_code');
    if (!target) {
      throw badRequest(
        T.app === 'pump' ? 'اول پمپتان را بسازید' : 'اول دکانتان را بسازید',
        T.app === 'pump' ? 'station_required' : 'shop_required'
      );
    }

    const codeHash = hashCode(clean);
    const found = await tx(async (c) => {
      const { rows } = await c.query(`SELECT * FROM ${TBL} WHERE code_hash=$1 FOR UPDATE`, [codeHash]);
      const row = rows[0];
      if (!row) throw notFound('این کد معتبر نیست', 'bad_code');
      if (row.status === 'used') throw forbidden('این کد قبلاً استفاده شده است', 'code_used');
      if (row.status !== 'active') throw forbidden('این کد دیگر کار نمی‌کند', 'code_inactive');
      if (row.expires_at && Number(row.expires_at) < now()) {
        await c.query(`UPDATE ${TBL} SET status='expired' WHERE id=$1`, [row.id]);
        throw forbidden('مهلت این کد تمام شده است', 'code_expired');
      }
      if (row[KEY] && row[KEY] !== target) {
        throw forbidden(
          T.app === 'pump' ? 'این کد برای پمپ دیگری صادر شده است' : 'این کد برای دکان دیگری صادر شده است',
          T.app === 'pump' ? 'code_other_station' : 'code_other_shop'
        );
      }
      await c.query(
        `UPDATE ${TBL} SET status='used', used_at=$2, used_by=$3, ${USED}=$4 WHERE id=$1`,
        [row.id, now(), userId, target]
      );
      return row;
    });

    //  اشتراک بیرون از تراکنش صادر می‌شود: `grant` خودش تراکنش دارد و
    //  تودرتو کردنشان قفل‌های بی‌دلیل می‌سازد. اگر اینجا خطا بدهد، کد
    //  خرج‌شده می‌ماند — پس نتیجه‌اش را می‌سنجیم و در صورت خطا برش
    //  می‌گردانیم.
    try {
      const subs = require('./subscriptions').forApp(T.app);
      const sub = await subs.grant(target, {
        plan: found.plan,
        days: Number(found.days) || 30,
        features: found.features,
        maxDevices: Number(found.max_devices) || 10,
        note: found.note || `کد وی‌آی‌پی ${found.code_hint}`,
        createdBy: found.created_by || 'vip-code',
      });
      return { subscription: sub, plan: found.plan, days: Number(found.days) || 30 };
    } catch (err) {
      await query(
        `UPDATE ${TBL} SET status='active', used_at=NULL, used_by='', ${USED}='' WHERE id=$1`,
        [found.id]
      );
      throw err;
    }
  }

  return { create, mail, list, revoke, redeem, shape, tenancy: T };
}

const shop = build(tenancy.SHOP);
const pump = build(tenancy.PUMP);

module.exports = {
  //  بخشِ دکان، همان‌جا که همیشه بود
  ...shop,
  //  و بخش‌های دیگر، با نام
  build,
  shop,
  pump,
  forApp: (app) => (app === 'pump' ? pump : shop),
  normalize, hashCode, DIGITS,
};
