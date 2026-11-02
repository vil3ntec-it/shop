'use strict';
/**
 * نگهبانِ حسابِ گیرندهٔ حساب‌پی — درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱: «امنیتِ حساب‌پی و هر حساب/کارتی که در برنامه
 * گذاشته می‌شود خیلی بالا باشد؛ کسی نتواند چیزی بدزدد یا حساب/کارت یا QR ِ خودش را در برنامه بگذارد»
 * (PAY-21، PAY-06، مهاجرتِ ۲۰۵). همیشه روشن — هیچ کلیدِ تنظیمی ندارد. حساب و رمز: `lib/hesabpay-account.js`.
 *
 * تهدید: کارمند، یا دزدی که گوشیِ صاحب را دارد، یا کسی که رمزِ ورودِ صاحب را دزدیده، حسابِ گیرندهٔ پول را با حسابِ
 * خودش عوض کند.
 *
 *  ۱. فعال شدنِ دیرهنگام — نخستین حسابِ دکان فوری؛ هر حسابِ بعدی (ساده یا خودکار) ۲۴ ساعت «در انتظار» است و تا آن
 *     دم حسابِ کهنه فعال می‌ماند (پرداخت‌ها هیچ‌چیزِ حسابِ تازه را نمی‌بینند). پوش به همهٔ دستگاه‌های صاحب و نوارِ
 *     «این من نبودم — لغو کن» (با تأییدِ دوبارهٔ ورود) ⇒ حسابِ تازه پاک و بخشِ حساب‌پی ۲۴ ساعت بسته. فعال شدن
 *     تنبل است (با هر خواندنِ حساب) و تا پرداختِ خودکارِ بازی هست عقب می‌افتد. هر پله سابقه دارد.
 *  ۲. دستگاهِ مطمئن — هر تغییر (حساب، برداشتن، رمز، بازنشانی) فقط از نشستی که:
 *       • زنجیرهٔ توکنش (ورود ⇐ تازه‌سازی‌ها، `tokens.session_at`) دست‌کم ۷ روزه است — نشانهٔ سرور، نه `device_uid`، یا
 *       • نخستین ورودِ قدیمی‌ترین دستگاهِ فعالِ همین صاحب است (صاحبِ تازه از گوشیِ خودش).
 *     دستگاهِ تازه ⇒ رد **پیش از** سنجیدنِ رمز (حتی یک تلاشِ رمز ندارد)، سابقه و خبر به صاحب. به‌علاوهٔ رمزِ
 *     حساب‌پی و تأییدِ دوبارهٔ ورود (`lib/reauth.js`، همان `ReauthGate` ِ برنامه) در خودِ مسیر.
 *  ۴. اثرِ انگشتِ گیرنده (`payee_fp` = هشِ دکان + حالت + شماره/شناسهٔ پذیرنده) روی حسابِ فعال، هر قصدِ پرداخت و هر
 *     ردیفِ پرداختِ صندوق؛ گزارشِ «پول به کدام حساب رفت» و هشدار برای پرداختی که به حسابِ غیرِ فعال اشاره دارد.
 *  ۵. «خودکار»: «پرداخت شد» فقط وقتی پذیرنده (شناسهٔ پذیرندهٔ همین دکان در وب‌هوک یا درِ وضعیت) + مرجع (قصد/جلسهٔ
 *     خودِ ما) + اثرِ انگشتِ حسابِ فعال همه بخوانند (مبلغ را `payment-intents` می‌سنجد)؛ وگرنه «نیازمندِ بررسی».
 */
const crypto = require('crypto');
const { one, query, tx, now } = require('../db');
const audit = require('./audit');
const { conflict, forbidden, ApiError } = require('../middleware/errors');

const DAY = 24 * 60 * 60 * 1000;
const HOLD_MS = DAY;
const LOCK_MS = DAY;
const TRUST_DAYS = 7;
const TRUST_MS = TRUST_DAYS * DAY;
const NAME_MAX = 60;
const REFUSAL_PUSH_MS = 15 * 60 * 1000;

const acc = () => require('./hesabpay-account');

// ════════════════════════════════════════════════════════════════════
//  اثرِ انگشت و نمای گیرنده
// ════════════════════════════════════════════════════════════════════

/** اثرِ انگشتِ یک حساب — بسته به دکان؛ خودِ شماره/شناسه از آن خوانده نمی‌شود و هیچ رازی در آن نیست. */
function fpOf(shopId, mode, identity) {
  const id = String(identity || '').trim();
  if (!id || !['simple', 'auto'].includes(mode)) return '';
  return `hp1_${crypto.createHash('sha256').update(`hesabpay-payee:v1:${shopId}:${mode}:${id}`, 'utf8').digest('hex').slice(0, 32)}`;
}

/** ردیفِ پیش از ۲۰۵ (بی اثرِ انگشت): از شماره، یا شناسهٔ پذیرنده، یا (خودکارِ بی‌پذیرنده) ۴ نویسهٔ آخرِ کلید */
function fpOfRow(row) {
  if (!row || !['simple', 'auto'].includes(row.mode)) return '';
  if (row.payee_fp) return row.payee_fp;
  if (row.mode === 'simple') return fpOf(row.shop_id, 'simple', row.account);
  const merchant = row.merchant_enc ? acc()._unseal(row.shop_id, 'merchant', row.merchant_enc) : '';
  return fpOf(row.shop_id, 'auto', merchant || `key:${row.key_last4}`);
}

/** «پول به حسابِ <نام> — <۴ رقم>»: ساده ⇒ شماره، خودکار ⇒ شناسهٔ پذیرنده (یا کلید) */
const last4Of = (r) => String((r.mode === 'simple' ? r.account : r.merchant_last4 || r.key_last4) || '').slice(-4);

/** گیرندهٔ فعال برای صندوق/صاحب، یا null */
function payeeOf(row) {
  if (!row || !['simple', 'auto'].includes(row.mode)) return null;
  return { mode: row.mode, name: row.account_name || '', last4: last4Of(row), fp: fpOfRow(row) };
}

/** حسابِ در انتظار (بی هیچ کلید) */
function pendingOf(row) {
  const p = row && row.pending;
  if (!p || typeof p !== 'object' || !Number(row.pending_until)) return null;
  return { mode: p.mode, name: p.account_name || '', last4: last4Of(p), at: Number(p.at) || null, until: Number(row.pending_until) };
}

/** نامِ صاحبِ حساب: ۱ تا ۶۰ نویسه، بی نویسهٔ کنترلی؛ نداده ⇒ '' (فراخواننده نامِ دکان را می‌گذارد) */
function cleanName(v) {
  if (v === undefined || v === null || v === '') return '';
  if (typeof v !== 'string') return null;
  const s = v.trim().replace(/\s+/g, ' ');
  if (s.length > NAME_MAX || /[\u0000-\u001f\u007f]/.test(s)) return null;
  return s;
}

/** ردیفِ پیش از ۲۰۵: اثرِ انگشت یک بار نوشته و در تاریخچه ثبت می‌شود (گزارش و سنجشِ قصدها). */
async function ensureFp(row) {
  if (!row || !['simple', 'auto'].includes(row.mode) || row.payee_fp) return row;
  const fp = fpOfRow(row);
  if (!fp) return row;
  await query(`UPDATE shop_hesabpay SET payee_fp=$2 WHERE shop_id=$1 AND payee_fp='' AND mode=$3`, [row.shop_id, fp, row.mode]);
  await query(
    `INSERT INTO hesabpay_payees (shop_id, fp, mode, name, last4, active_from) VALUES ($1,$2,$3,$4,$5,$6)
     ON CONFLICT (shop_id, fp) DO NOTHING`,
    [row.shop_id, fp, row.mode, row.account_name || '', last4Of(row), Number(row.active_since || row.updated_at) || now()]
  );
  return { ...row, payee_fp: fp };
}

/** تاریخچهٔ گیرنده‌ها درونِ تراکنش: کهنه بازنشسته، تازه فعال */
async function payeeSwitch(c, shopId, prevFp, next, t) {
  if (prevFp && prevFp !== (next && next.fp)) {
    await c.query('UPDATE hesabpay_payees SET retired_at=$3 WHERE shop_id=$1 AND fp=$2 AND retired_at IS NULL', [shopId, prevFp, t]);
  }
  if (!next || !next.fp) return;
  await c.query(
    `INSERT INTO hesabpay_payees (shop_id, fp, mode, name, last4, active_from) VALUES ($1,$2,$3,$4,$5,$6)
     ON CONFLICT (shop_id, fp) DO UPDATE SET mode=EXCLUDED.mode, name=EXCLUDED.name, last4=EXCLUDED.last4,
       active_from=EXCLUDED.active_from, retired_at=NULL`,
    [shopId, next.fp, next.mode, next.name || '', next.last4 || '', t]
  );
}

// ════════════════════════════════════════════════════════════════════
//  دستگاهِ مطمئن و درِ پیش از رمز
// ════════════════════════════════════════════════════════════════════

/** نخستین دستگاه: نشست باید همان ورودِ نخستِ ساختنِ ردیفِ دستگاه باشد (نه ورودِ تازه‌ای با همان `device_uid`) */
const FIRST_LOGIN_MS = 10 * 60 * 1000;

/**
 * این **نشست** روی این دستگاه مطمئن است؟ نشانه فقط چیزی است که سرور داده: زنجیرهٔ توکنِ همین نشست (`tokens.session_at`
 * = ورودی که آن را ساخت؛ تازه‌سازی نگهش می‌دارد) بسته به ردیفِ دستگاهِ همان توکن — **نه** `device_uid` که برنامه
 * می‌فرستد. پس رمزِ ورودِ دزدیده + شناسهٔ دستگاهِ دانسته ⇒ زنجیرهٔ تازه ⇒ نامطمئن.
 *   • زنجیرهٔ نشست دست‌کم ۷ روزه، یا
 *   • نخستین ورودِ قدیمی‌ترین دستگاهِ فعالِ همین صاحب (زنجیره ≤ ۱۰ دقیقه پس از ساختنِ ردیفِ دستگاه): صاحبِ تازه از گوشیِ خودش.
 * `actor.session` = `{at, deviceId}` از `req.tokenRow`؛ نبودش، دستگاهِ کسِ دیگر یا ناهمخوان ⇒ نه.
 */
async function trustOf(actor, t = now()) {
  const d = actor && actor.device;
  const s = actor && actor.session;
  const born = Number(s && s.at);
  if (!d || d.status !== 'active' || String(d.user_id || '') !== String(actor.id || '')
      || !s || String(s.deviceId || '') !== String(d.id) || !Number.isFinite(born) || born <= 0) {
    return { trusted: false, trustedAfter: null, reason: 'no_device' };
  }
  const after = born + TRUST_MS;
  if (after <= t) return { trusted: true, trustedAfter: after, reason: 'age' };
  if (born - Number(d.created_at) <= FIRST_LOGIN_MS) {
    const oldest = await one(
      `SELECT id FROM devices WHERE user_id=$1 AND status='active' ORDER BY created_at, id LIMIT 1`, [String(actor.id)]
    );
    if (oldest && oldest.id === d.id) return { trusted: true, trustedAfter: after, reason: 'first_device' };
  }
  return { trusted: false, trustedAfter: after, reason: 'new_session' };
}

function sectionLocked(until, t) {
  const e = new ApiError(423, 'hesabpay_section_locked',
    'بخشِ حساب‌پی پس از «این من نبودم» تا ۲۴ ساعت بسته است؛ پول به همان حسابِ قبلیِ دکان می‌رود.');
  e.data = { lockedUntil: Number(until), retryAfter: Math.max(1, Math.ceil((Number(until) - t) / 1000)) };
  return e;
}

function untrusted(trustedAfter) {
  const e = forbidden(`تغییرِ حساب‌پی فقط از دستگاهِ همیشگیِ خودتان ممکن است (دستگاهی که دست‌کم ${TRUST_DAYS} روز پیش وارد `
    + 'شده و از آن پس بیرون نرفته). این ورود تازه است؛ از گوشی یا کمپیوترِ همیشگیِ خودتان بکوشید.', 'hesabpay_device_untrusted');
  e.data = { trustedAfter: trustedAfter || null, trustDays: TRUST_DAYS };
  return e;
}

/**
 * درِ پیش از رمز برای هر تغییر (همگام، درونِ قفلِ ردیف): بخشِ بسته ⇒ ۴۲۳؛ دستگاهِ نامطمئن ⇒ ۴۰۳. `tr` همان
 * `trustOf(actor)` است که فراخواننده پیش از تراکنش گرفته؛ نبودش (یا هر چیزِ جز `trusted: true`) ⇒ نامطمئن.
 * `refusal` را `withPin` سابقه می‌کند. @returns {ApiError|null}
 */
function gate(row, tr, t, { trust = true } = {}) {
  const locked = Number(row.section_locked_until || 0);
  if (locked > t) return Object.assign(sectionLocked(locked, t), { refusal: 'section_locked' });
  if (!trust || (tr && tr.trusted === true)) return null;
  return Object.assign(untrusted(tr ? tr.trustedAfter : null), { refusal: (tr && tr.reason) || 'no_device' });
}

const lastRefusalPush = new Map();
/** خبرِ «کسی از دستگاهِ تازه خواست حساب‌پی را عوض کند» — حداکثر یک بار در ۱۵ دقیقه برای هر دکان */
async function refusalNotice(shopId, refusal) {
  if (refusal === 'section_locked') return;
  const t = Date.now();
  if (t - (lastRefusalPush.get(shopId) || 0) < REFUSAL_PUSH_MS) return;
  lastRefusalPush.set(shopId, t);
  await acc().notifyOwners(shopId, 'change_refused');
}

// ════════════════════════════════════════════════════════════════════
//  فعال شدن و «این من نبودم»
// ════════════════════════════════════════════════════════════════════

/** کی فعال شود: ۲۴ ساعت پس از ثبت، و هرگز پیش از پایانِ نگهِ بازنشانیِ رمز */
const activateAt = (row, t) => Math.max(t + HOLD_MS, Number(row.hold_until || 0));

/**
 * حسابِ در انتظاری که زمانش رسیده ⇒ فعال (درونِ قفلِ ردیف؛ دو خواننده با هم یک بار). تا پرداختِ خودکارِ بازِ حسابِ
 * کهنه هست عقب می‌افتد (وب‌هوکش با همان راز سنجیده شود). @returns {Promise<boolean>}
 */
async function activateDue(shopId) {
  const peek = await one('SELECT pending_until FROM shop_hesabpay WHERE shop_id=$1', [shopId]);
  const due = Number(peek?.pending_until || 0);
  if (!due || due > now()) return false;
  const out = await tx(async (c) => {
    const row = (await c.query('SELECT * FROM shop_hesabpay WHERE shop_id=$1 FOR UPDATE', [shopId])).rows[0];
    const t = now();
    const p = row && row.pending;
    if (!p || !Number(row.pending_until) || Number(row.pending_until) > t) return null;
    if (row.mode === 'auto' && await acc().openGatewayPayment(c, shopId, t)) return null;
    const prev = payeeOf(row);
    await c.query(
      `UPDATE shop_hesabpay SET mode=$2, account=$3, qr_payload=$4, api_key_enc=$5, secret_enc=$6, merchant_enc=$7,
              key_last4=$8, merchant_last4=$9, account_name=$10, payee_fp=$11, active_since=$12, pending=NULL,
              pending_until=0, version=version+1, updated_at=$12 WHERE shop_id=$1`,
      [shopId, p.mode, p.account || '', p.qr_payload || '', p.api_key_enc || '', p.secret_enc || '', p.merchant_enc || '',
        p.key_last4 || '', p.merchant_last4 || '', p.account_name || '', p.payee_fp || '', t]
    );
    const next = { mode: p.mode, name: p.account_name || '', last4: last4Of(p), fp: p.payee_fp || '' };
    await payeeSwitch(c, shopId, prev && prev.fp, next, t);
    await audit.logIn(c, {
      shopId, actorType: 'system', action: 'hesabpay.change_activated', targetType: 'shop', targetId: shopId,
      detail: { mode: p.mode, last4: next.last4, previousMode: row.mode, previousLast4: prev ? prev.last4 : '', requestedBy: p.by || '' },
    });
    return true;
  });
  if (out) await acc().notifyOwners(shopId, 'change_activated');
  return Boolean(out);
}

/**
 * «این من نبودم — لغو کن»: هر صاحبِ همین دکان، از هر دستگاهش، با تأییدِ دوبارهٔ ورود (در مسیر) — بی رمزِ حساب‌پی
 * (کسی که حساب را عوض کرده شاید رمز را هم دارد). حسابِ تازه پاک، بخش ۲۴ ساعت بسته، سابقه و خبر.
 */
async function cancelPending(shopId, actor) {
  const out = await tx(async (c) => {
    const row = (await c.query('SELECT * FROM shop_hesabpay WHERE shop_id=$1 FOR UPDATE', [shopId])).rows[0];
    const t = now();
    if (!row || !row.pending) {
      return { error: conflict('حسابِ در انتظاری نیست؛ حسابِ فعالِ دکان همان است که می‌بینید.', 'hesabpay_nothing_pending') };
    }
    const until = Math.max(Number(row.section_locked_until || 0), t + LOCK_MS);
    await c.query(
      `UPDATE shop_hesabpay SET pending=NULL, pending_until=0, section_locked_until=$2, version=version+1, updated_by=$3,
              updated_at=$4 WHERE shop_id=$1`, [shopId, until, String(actor.id || ''), t]
    );
    await acc().logIn(c, shopId, actor, 'hesabpay.change_cancelled', {
      mode: row.pending.mode, last4: last4Of(row.pending), requestedBy: row.pending.by || '', lockedUntil: until,
    });
    return { ok: true };
  });
  if (out.error) throw out.error;
  await acc().notifyOwners(shopId, 'change_cancelled');
  return acc().status(shopId, actor);
}

// ════════════════════════════════════════════════════════════════════
//  «خودکار»: پول به پذیرندهٔ همین دکان رسید؟
// ════════════════════════════════════════════════════════════════════

/**
 * پیش از «پرداخت شد» ِ حساب‌پیِ هر دکان (مبلغ و ارز را `payment-intents` پیش‌تر سنجیده): پذیرنده = شناسهٔ پذیرندهٔ
 * **همین دکان** در وب‌هوک یا پاسخِ درِ وضعیت؛ مرجع = قصد یا جلسه‌ای که خودمان ساختیم؛ گیرنده = اثرِ انگشتِ قصد همان
 * حسابِ فعال. ساختگیِ آزمون (`perShop` ندارد) بیرون است. @returns {{ok:boolean, missing:string[]}}
 */
function payeeProof(ad, intent, ev = {}, st = null) {
  if (!ad || !ad.perShop) return { ok: true, missing: [] };
  const merchant = ad.merchantMatches(ev.merchantId) || Boolean(st && ad.merchantMatches(st.merchantId));
  const reference = (Boolean(ev.reference) && ev.reference === intent.id)
    || (Boolean(ev.sessionId) && ev.sessionId === intent.provider_session_id)
    || (Boolean(st && st.reference) && st.reference === intent.id);
  const payee = Boolean(intent.payee_fp) && intent.payee_fp === ad.payeeFp;
  const missing = [!merchant && 'merchant', !reference && 'reference', !payee && 'payee'].filter(Boolean);
  return { ok: missing.length === 0, missing };
}

/**
 * پرداختی که رسیدنش به حسابِ همین دکان ثابت نشد ⇒ «نیازمندِ بررسی» (قصد همان وضعیت می‌ماند، هرگز «پرداخت شد»)،
 * سابقه برای پنل و صاحب، و خبر به صاحب — فقط بارِ نخست. @returns {Promise<'needs_review'>}
 */
async function holdForReview(intent, missing, source) {
  const r = await one(
    'UPDATE payment_intents SET needs_review=true, updated_at=$2 WHERE id=$1 AND needs_review=false RETURNING *',
    [intent.id, now()]
  );
  if (r) {
    console.warn(`[payments] رسیدنِ پول به حسابِ دکان ثابت نشد: ${intent.id} (${intent.shop_id}) — ${missing.join(',')}`);
    //  `missing` متن (نه آرایه): `audit.scrub` آرایه را به شیء می‌کند
    const detail = { reason: 'payee_unproven', missing: missing.join(','), source, kind: r.kind, refId: r.ref_id, amountMinor: Number(r.amount_minor),
      payeeFp: String(r.payee_fp || '').slice(0, 12) };
    await audit.log({ shopId: r.shop_id, actorType: 'system', action: 'payment.review', targetType: 'payment_intent', targetId: r.id, detail });
    await audit.log({ shopId: r.shop_id, actorType: 'system', action: 'hesabpay.payee_alert', targetType: 'payment_intent', targetId: r.id, detail });
    await acc().notifyOwners(r.shop_id, 'payee_alert');
  }
  return 'needs_review';
}

// ════════════════════════════════════════════════════════════════════
//  گزارش: پول به کدام حساب رفت
// ════════════════════════════════════════════════════════════════════

/**
 * برای صاحب: حسابِ فعال، در انتظار، همهٔ حساب‌هایی که روزی فعال بودند با جمعِ پرداخت‌های خودکارِ «پرداخت شد» ِ هر
 * کدام در `days` روزِ گذشته، و هشدارهای «گیرنده ثابت نشد». بی هیچ شماره، کلید یا راز (فقط نام و ۴ رقم).
 */
async function report(shopId, { days = 30 } = {}) {
  const d = Math.min(366, Math.max(1, Math.floor(Number(days)) || 30));
  await activateDue(shopId);
  const row = await ensureFp(await one('SELECT * FROM shop_hesabpay WHERE shop_id=$1', [shopId]));
  const since = now() - d * DAY;
  const [hist, sums, alerts] = await Promise.all([
    query(`SELECT fp, mode, name, last4, active_from, retired_at FROM hesabpay_payees WHERE shop_id=$1
            ORDER BY active_from DESC LIMIT 50`, [shopId]),
    query(`SELECT payee_fp, COUNT(*)::int AS n, COALESCE(SUM(amount_minor), 0)::bigint AS minor FROM payment_intents
            WHERE shop_id=$1 AND provider='hesabpay' AND status IN ('paid','refunded') AND created_at >= $2 GROUP BY payee_fp`,
    [shopId, since]),
    query(`SELECT target_id, detail, created_at FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.payee_alert'
            AND created_at >= $2 ORDER BY created_at DESC LIMIT 50`, [shopId, since]),
  ]);
  const paid = new Map(sums.rows.map((r) => [r.payee_fp, { count: Number(r.n), amountMinor: Number(r.minor) }]));
  const active = payeeOf(row);
  const accounts = hist.rows.map((r) => ({
    fp: r.fp, mode: r.mode, name: r.name, last4: r.last4, activeFrom: Number(r.active_from),
    retiredAt: r.retired_at === null ? null : Number(r.retired_at), active: Boolean(active && active.fp === r.fp),
    paid: paid.get(r.fp) || { count: 0, amountMinor: 0 },
  }));
  const known = new Set(accounts.map((a) => a.fp));
  //  پرداختِ بی اثرِ انگشت (پیش از ۲۰۵) یا با اثرِ انگشتِ ناشناخته جدا شمرده می‌شود — هرگز به حسابِ فعال نسبت داده نمی‌شود
  const unknown = [...paid.entries()].filter(([fp]) => !known.has(fp))
    .reduce((s, [, v]) => ({ count: s.count + v.count, amountMinor: s.amountMinor + v.amountMinor }), { count: 0, amountMinor: 0 });
  return {
    days: d, active, pending: pendingOf(row), accounts, unknown,
    alerts: alerts.rows.map((a) => ({
      //  `source`: 'till' (ردیفِ «پول رسید» ِ صندوق، `targetId` = فاکتور) یا قصدِ پرداختِ خودکار (`intentId`)
      source: a.detail?.source === 'till' ? 'till' : 'gateway',
      ...(a.detail?.source === 'till' ? { saleId: a.target_id } : { intentId: a.target_id }),
      at: Number(a.created_at), amountMinor: Number(a.detail?.amountMinor) || 0,
      missing: String(a.detail?.missing || '').split(',').filter(Boolean),
    })),
  };
}

/**
 * برای گزارشِ خودکارِ صاحب (`owner-report.js`): پولِ حساب‌پیِ بازه به تفکیکِ حساب — قصدهای خودکارِ پرداخت‌شده و ردیف‌های
 * «پول رسید» ِ صندوق (`payments[].payee` ِ فاکتورها) — و شمارِ هشدارها. حسابی که فعال نیست «حسابِ دیگر» نشان داده می‌شود.
 */
async function receivedBy(shopId, w, sales = []) {
  const row = await one('SELECT shop_id, mode, account, account_name, merchant_last4, key_last4, payee_fp, merchant_enc FROM shop_hesabpay WHERE shop_id=$1', [shopId]);
  const activeFp = row ? fpOfRow(row) : '';
  const names = new Map((await query('SELECT fp, name, last4 FROM hesabpay_payees WHERE shop_id=$1', [shopId])).rows
    .map((r) => [r.fp, r]));
  const by = new Map();
  const add = (fp, minor) => {
    const k = fp || '';
    const cur = by.get(k) || { fp: k, count: 0, amountMinor: 0 };
    cur.count += 1;
    cur.amountMinor += minor;
    by.set(k, cur);
  };
  const intents = await query(
    `SELECT payee_fp, amount_minor FROM payment_intents WHERE shop_id=$1 AND provider='hesabpay' AND status IN ('paid','refunded')
        AND paid_at >= $2 AND paid_at < $3`, [shopId, w.fromAt, w.until]
  );
  for (const r of intents.rows) add(r.payee_fp, Number(r.amount_minor));
  for (const s of sales) {
    for (const p of Array.isArray(s?.payments) ? s.payments : []) {
      if (p && p.method === 'hesabpay' && p.status === 'confirmed' && p.verification === 'cashier') add(String(p.payee || ''), Number(p.amount) || 0);
    }
  }
  const accounts = [...by.values()].map((a) => {
    const n = names.get(a.fp) || [...names.values()].find((x) => a.fp && x.fp.startsWith(a.fp)) || null;
    const active = Boolean(a.fp) && Boolean(activeFp) && activeFp.startsWith(a.fp);
    return { ...a, name: n ? n.name : '', last4: n ? n.last4 : '', active };
  });
  const alerts = Number((await one(
    `SELECT COUNT(*)::int AS n FROM audit_logs WHERE shop_id=$1 AND action='hesabpay.payee_alert' AND created_at >= $2 AND created_at < $3`,
    [shopId, w.fromAt, w.until]
  )).n);
  return { accounts, alerts };
}

// ════════════════════════════════════════════════════════════════════
//  ردیفِ «پول رسید» ِ صندوق با گیرندهٔ دیگر ⇒ هشدار و پوشِ فوری
// ════════════════════════════════════════════════════════════════════

const TILL_TICK_MS = Math.max(5_000, Number(process.env.HESABPAY_TILL_TICK_MS) || 30_000);
const TILL_BATCH = 2000;
/** صندوق پیکربندی را یک دقیقه نگه می‌دارد؛ ردیفِ همان دقیقه با حسابِ تازه‌کنارگذاشته هنوز درست است */
const TILL_GRACE_MS = 2 * 60 * 1000;
//  هر دو دفترِ همگام‌سازی (v1 و v3) — فقط خواندنِ opها با نمایهٔ (حساب، server_seq)، بی دست زدن به همگام‌سازی
const TILL_STORES = Object.freeze([['till_seq_v1', 'oplog', 'op_type'], ['till_seq_v3', 'sync_v3_ops', 'kind']]);
const TILL_APP = 'shop';

/** ردیف‌های حساب‌پیِ «تأییدِ صندوقدار» ِ یک op ِ فروش که `payee` دارند (برنامهٔ کهنه بی `payee` ⇒ بیرون) */
function tillRows(fields) {
  let list = fields && fields.payments;
  if (typeof list === 'string') { try { list = JSON.parse(list); } catch { list = null; } }
  return (Array.isArray(list) ? list : []).filter((p) => p && p.method === 'hesabpay' && typeof p.payee === 'string' && p.payee.trim())
    .map((p) => ({ fp: p.payee.trim().slice(0, 64), amountMinor: Number(p.amount ?? p.amountMinor) || 0 }));
}

/** این اثرِ انگشت در لحظهٔ فروش حسابِ (فعالِ) همین دکان بود؟ (تاریخچهٔ `hesabpay_payees`، با مهلتِ کشِ صندوق) */
function validAt(hist, fp, at) {
  const h = hist.find((x) => x.fp === fp);
  if (!h) return 'unknown';
  if (at < Number(h.active_from) - TILL_GRACE_MS) return 'not_yet';
  return h.retired_at !== null && at > Number(h.retired_at) + TILL_GRACE_MS ? 'retired' : '';
}

/** یک دکان: opهای فروشِ تازه از `till_seq_*` به بعد؛ هر فاکتور با گیرندهٔ نادرست یک هشدار (`once`) و یک پوش */
async function tillShop(s) {
  const hist = (await query('SELECT fp, active_from, retired_at FROM hesabpay_payees WHERE shop_id=$1', [s.shop_id])).rows;
  let alerts = 0;
  for (const [col, table, typeCol] of TILL_STORES) {
    const rows = (await query(
      `SELECT server_seq, row_id, fields, received_at FROM ${table}
        WHERE app=$2 AND account_kind=$2 AND account_id=$1 AND server_seq > $3 AND table_name='sales' AND ${typeCol} <> 'delete'
        ORDER BY server_seq LIMIT ${TILL_BATCH}`,
      [s.shop_id, TILL_APP, Number(s[col]) || 0]
    )).rows;
    for (const r of rows) {
      const at = Number(r.fields && r.fields.createdAt) || Number(r.received_at);
      const bad = tillRows(r.fields).map((p) => ({ ...p, why: validAt(hist, p.fp, at) })).filter((p) => p.why);
      if (!bad.length) continue;
      const row = await audit.log({
        shopId: s.shop_id, actorType: 'system', action: 'hesabpay.payee_alert', targetType: 'sale', targetId: r.row_id, once: true,
        detail: { reason: 'till_payee', source: 'till', missing: 'payee', why: bad[0].why, payeeFp: bad[0].fp.slice(0, 12),
          amountMinor: bad.reduce((x, p) => x + p.amountMinor, 0) },
      });
      if (row) {
        alerts += 1;
        console.warn(`[hesabpay] «پول رسید» ِ صندوق با گیرندهٔ نادرست: فاکتورِ ${r.row_id} (${s.shop_id}) — ${bad[0].why}`);
        await acc().notifyOwners(s.shop_id, 'till_payee_alert', { saleId: r.row_id });
      }
    }
    if (rows.length) {
      await query(`UPDATE shop_hesabpay SET ${col}=$2 WHERE shop_id=$1 AND ${col} < $2`, [s.shop_id, rows[rows.length - 1].server_seq]);
    }
  }
  return alerts;
}

/** یک دور برای همهٔ دکان‌هایی که روزی حسابِ حساب‌پی داشته‌اند ⇒ شمارِ هشدارهای تازه */
async function tillSweep() {
  const shops = (await query(
    `SELECT s.shop_id, s.till_seq_v1, s.till_seq_v3 FROM shop_hesabpay s
      WHERE EXISTS (SELECT 1 FROM hesabpay_payees p WHERE p.shop_id = s.shop_id) ORDER BY s.shop_id`
  )).rows;
  let n = 0;
  for (const s of shops) {
    try { n += await tillShop(s); } catch (err) { console.error('[hesabpay] سنجشِ صندوق', s.shop_id, err.message); }
  }
  return n;
}

let tillTimer = null;
let tillBusy = false;
function start() {
  if (tillTimer) return;
  const run = require('./job-beat').wrap('hesabpay-till', TILL_TICK_MS, async () => {
    if (tillBusy) return 0;
    tillBusy = true;
    try { return await tillSweep(); } finally { tillBusy = false; }
  });
  tillTimer = setInterval(() => run().catch((err) => console.error('[hesabpay]', err.message)), TILL_TICK_MS);
  if (tillTimer.unref) tillTimer.unref();
}
function stop() {
  if (tillTimer) clearInterval(tillTimer);
  tillTimer = null;
}

module.exports = {
  HOLD_MS, LOCK_MS, TRUST_DAYS, TRUST_MS, NAME_MAX,
  fpOf, fpOfRow, last4Of, payeeOf, pendingOf, cleanName, ensureFp, payeeSwitch,
  trustOf, gate, refusalNotice, activateAt, activateDue, cancelPending, payeeProof, holdForReview, report, receivedBy,
  tillRows, tillSweep, start, stop,
};
