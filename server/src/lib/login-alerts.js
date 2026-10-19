'use strict';
/**
 * هشدارِ ورودِ مشکوک — بندِ ۲۱.۱ («هشدار ورود مشکوک»).
 *
 * ── چه چیزی «تازه» است ────────────────────────────────────────────
 *   • **دستگاهِ تازه**: شناسهٔ دستگاهی که تا امروز با این حساب وارد نشده بود.
 *   • **شبکهٔ تازه**، فقط وقتی برنامه شناسهٔ دستگاه نفرستاده: پیشوندِ IP
 *     (‎/24‎ برای IPv4، ‎/48‎ برای IPv6) که در آخرین IPِ هیچ دستگاهِ این
 *     حساب دیده نشده. دستگاهِ آشنا با IPِ تازه عادی است (اینترنتِ همراه
 *     مدام عوض می‌شود)، پس هشدار نمی‌سازد.
 *   • نخستین ورودِ این شخص به **همین بخش** (دکان یا پمپ) هشدار نیست — مثلِ
 *     ثبت‌نام است؛ نشانه‌اش نبودنِ ردیفِ `user_apps` برای آن بخش. دادهٔ هر
 *     بخش جداست، پس ورودِ نخست به بخشِ خالی به دفترِ بخشِ دیگر راه ندارد؛
 *     ورودِ بعدی از دستگاهِ تازه به هر دو بخش هشدار می‌سازد.
 *
 * ── «مکان» به زبانِ راست ──────────────────────────────────────────
 * سرور پایگاهِ داده‌ای برای IP → شهر ندارد. پس «مکان» همان شبکه (پیشوندِ
 * IP) است و در متنِ هشدار هم همین نوشته می‌شود — نه شهری که حدس زده شده.
 *
 * ── کجا می‌رسد ─────────────────────────────────────────────────────
 * فقط **داخلِ برنامه** (`notice_deliveries`، کانالِ `inapp`) برای خودِ همان
 * کاربر — همان صندوقی که `GET /api/me/notices` می‌خواند. پیامک یا ایمیل
 * فرستاده **نمی‌شود** و پاسخ هم ادعایش را نمی‌کند (`channels: ['inapp']`).
 * متنش قالبِ `login_new` در «مرکزِ اعلان» است و از پنل عوض می‌شود.
 *
 * ⚠️ هیچ‌وقت جلوی ورود را نمی‌گیرد: هر خطایی فقط در لاگ می‌نشیند.
 * ⚠️ بازگشت: `LOGIN_ALERTS=off`.
 */
const config = require('../config');
const { query, one, newId, now } = require('../db');
const notices = require('./notices');

function enabled() { return config.loginAlerts !== false; }

/** پیشوندِ شبکه برای مقایسه — نه خودِ IP. */
function networkOf(ip) {
  let s = String(ip || '').trim().toLowerCase();
  if (s.startsWith('::ffff:')) s = s.slice(7);
  if (/^\d+\.\d+\.\d+\.\d+$/.test(s)) return s.split('.').slice(0, 3).join('.');
  if (s.includes(':')) return s.split(':').slice(0, 3).join(':');
  return '';
}

/** نمایشِ شبکه برای صاحبِ حساب: ‎1.2.3.x‎ — IPِ کامل در متنِ اعلان نمی‌آید. */
function networkLabel(ip) {
  const n = networkOf(ip);
  if (!n) return 'نامعلوم';
  return n.includes(':') ? `${n}:…` : `${n}.x`;
}

/**
 *  عکسِ وضعیت **پیش از** ثبتِ دستگاه — بعد از `upsertDevice` دستگاهِ تازه
 *  دیگر تازه نیست و آخرین IP هم روی‌نویسی شده.
 */
async function before(user, deviceUid, ip, app = 'shop') {
  if (!enabled() || !user) return null;
  try {
    const been = await one('SELECT 1 AS ok FROM user_apps WHERE user_id=$1 AND app=$2', [user.id, app || 'shop']);
    if (!been) return null;
    const uid = String(deviceUid || '').trim();
    const known = uid
      ? await one('SELECT 1 AS ok FROM devices WHERE user_id=$1 AND device_uid=$2', [user.id, uid])
      : null;
    const net = networkOf(ip);
    let netKnown = true;
    if (!uid && net) {
      const rows = await query('SELECT last_ip FROM devices WHERE user_id=$1', [user.id]);
      netKnown = rows.rows.some((r) => networkOf(r.last_ip) === net);
    }
    return { newDevice: !!uid && !known, newNetwork: !uid && !!net && !netKnown };
  } catch (err) {
    console.warn('[login-alerts] بررسیِ پیش از ورود:', err.message);
    return null;
  }
}

/** اگر تازه بود، اعلانِ داخلِ برنامه برای خودِ کاربر. */
async function after(seen, { user, device, ip, app = 'shop', method = '' }) {
  if (!seen || !(seen.newDevice || seen.newNetwork)) return null;
  try {
    const section = app === 'pump' ? 'pump' : 'shop';
    const tpl = await notices.templateFor('login_new', section);
    if (!tpl) return null;
    const when = new Date(now()).toISOString().replace('T', ' ').slice(0, 16);
    const vars = {
      'نام': user.name || '',
      'دستگاه': [device?.name, device?.platform].filter(Boolean).join(' · ') || 'دستگاهِ بی‌نام',
      'شبکه': networkLabel(ip),
      'زمان': `${when} (UTC)`,
    };
    const n = await notices.create({
      app: section,
      audience: { kind: 'user', user_id: user.id },
      channels: ['inapp'],
      title: tpl.title, body: tpl.body, templateKey: 'login_new',
      variables: { reason: seen.newDevice ? 'new_device' : 'new_network', method },
    }, { createdBy: 'system', system: true });
    const t = now();
    const title = notices.render(n.title, vars);
    const body = notices.render(n.body, vars);
    await query(
      `INSERT INTO notice_deliveries (id, notice_id, app, user_id, tenant_id, channel, status, who, address,
                                      title, body, run, created_at, sent_at)
       VALUES ($1,$2,$3,$4,'','inapp','sent',$5,'',$6,$7,1,$8,$8)`,
      [newId('ndv'), n.id, section, user.id, user.name || '', title, body, t]
    );
    const counts = { recipients: 1, sent: 1, error: 0, byChannel: { inapp: { sent: 1, error: 0 } } };
    await query(
      `UPDATE notices SET status='sent', sent_at=$2, runs=1, counts=$3::jsonb, updated_at=$2 WHERE id=$1`,
      [n.id, t, JSON.stringify(counts)]
    );
    return { noticeId: n.id, channels: ['inapp'] };
  } catch (err) {
    console.warn('[login-alerts] اعلان ساخته نشد:', err.message);
    return null;
  }
}

module.exports = { before, after, networkOf, networkLabel, enabled };
