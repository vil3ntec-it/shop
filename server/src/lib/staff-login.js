'use strict';
/**
 * ورودِ شاگرد با کد (`POST /api/auth/staff`) و رازِ دستگاه برای نشستِ باز (`POST /api/auth/staff/device-key`).
 *
 * ⛔ یافتهٔ CodeQL (۲.۱۴.۵): عضوِ موجود پیش‌تر با «کدِ دکان + شناسهٔ دستگاهِ بدنه» برمی‌گشت و وضعیتِ کد (باطل، تمام‌شده)
 * عمداً سنجیده نمی‌شد ⇒ هر کس کدِ شاگرد و شناسهٔ دستگاهِ عضوِ دیگر را داشت نشستِ همان عضو (حتی مدیر) را می‌گرفت.
 * شناسهٔ دستگاه راز نیست (`X-Device` ِ هر درخواست؛ همکار در Pull می‌بیندش). حالا (پرچمِ پیش‌فرض‌روشنِ
 * `auth.staff_session_guard`، `lib/staff-device-keys.js`):
 *   - عضوِ موجود فقط با **رازِ دستگاه** (نخستین پیوستن یا `enrollDevice`)؛
 *   - کد باید هنوز برای همان عضو معتبر باشد (`staffCodes.assertValidForMember`)؛
 *   - شناسهٔ بسته بی راز: کدِ ثابت ⇒ `staff_device_unverified` ([UNVERIFIED_MESSAGE] — برنامهٔ کهنه همین متن را نشان
 *     می‌دهد)؛ کدِ تازهٔ صاحب ⇒ **حسابِ تازه**، هرگز همان حساب؛
 *   - هر ورود در سابقه: `staff_login.ok` / `staff_login.failed` با دلیل، هرگز کد یا راز.
 * سقفِ نرخ در مسیر است: `joinLimit` (هر IP) و سطلِ «هر کد» با کلیدِ [codeKey].
 */
const { query, one, newId, now } = require('../db');
const v = require('./validate');
const audit = require('./audit');
const staffCodes = require('./staff-codes');
const deviceKeys = require('./staff-device-keys');
const tenancy = require('./tenancy');
const { membershipOf } = require('./shops');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

/** پیامِ نسخهٔ کهنه یا دستگاهِ ناشناس (`staff_device_unverified`) */
const UNVERIFIED_MESSAGE = 'این دستگاه شناخته نشد. کدِ تازه از صاحبِ دکان بگیرید.';

/**
 * کلیدِ سطلِ «هر کد»: HMAC ِ کدِ یکسان‌شده (`staffCodes.hashCode`)، نه خودش — کدِ خام در حافظهٔ سقف نمی‌نشیند. بدنهٔ بی کد
 * یا ناجور ⇒ `null` (همان IP).
 */
function codeKey(req) {
  const raw = req.body?.code;
  if (typeof raw !== 'string' || raw.length > 40) return null;
  const n = staffCodes.normalize(raw);
  return n ? `c:${staffCodes.hashCode(n)}` : null;
}

/** سابقهٔ ورودِ شاگرد (موفق یا دلیلِ شکست) — هرگز کد یا رازِ دستگاه؛ نوشتنِ ناموفقِ سابقه ورود را نمی‌شکند */
async function trailLog(action, trail, ip, detail) {
  try {
    await audit.log({
      shopId: trail.shopId || '', userId: trail.userId || '', action,
      targetType: trail.codeId ? 'staff_code' : '', targetId: trail.codeId || '',
      detail: { ...detail, device: trail.device || undefined }, ip,
    });
  } catch { /* سابقه هرگز ورود را نمی‌شکند */ }
}

/**
 * @param {object} deps `issueSession(user, device, ip, app)` و `loginPayload(user, session)` ِ `routes/auth.js`
 * @returns بدنهٔ پاسخِ ۲۰۱ (همان `loginPayload`، و `deviceKey` فقط وقتی رازِ تازه داده شد)
 */
async function login(req, { issueSession, loginPayload }) {
  const ip = clientIp(req);
  //  ⛔ هر ورود با کدِ شاگرد در سابقه می‌نشیند — موفق و هر دلیلِ شکست (هرگز خودِ کد یا رازِ دستگاه)
  const trail = { shopId: '', userId: '', codeId: '', device: '' };
  try {
    const rawCode = v.text(req.body?.code, { max: 40, required: true, field: 'کد شاگرد' });
    const name = v.text(req.body?.name, { max: 80 });
    const body = req.body?.device;
    const device = body && typeof body === 'object' && !Array.isArray(body) ? body : {};
    const deviceUid = v.id(device.uid || device.deviceId || '', {
      field: 'شناسه دستگاه', required: false, max: 64,
    });
    trail.device = deviceUid;

    const code = await staffCodes.codeRow(rawCode);
    const shopId = code.shop_id;
    trail.shopId = shopId;
    trail.codeId = code.id;
    const subject = deviceUid ? `${shopId}:${deviceUid}` : '';
    //  کلیدِ اضطراری (پیش‌فرض روشن): فقط `false` ِ صریح رفتارِ پیش از ۲.۱۴.۵ را برمی‌گرداند
    const guard = await deviceKeys.guardOn(shopId);

    /*
     *  ⛔ همان دستگاه پیش از این آمده؟ — فقط با **رازِ دستگاه**، نه با شناسه‌اش.
     *
     *  شناسهٔ دستگاه راز نیست (همکار در Pull و `X-Device` می‌بیندش). پیش‌تر «کد (هر وضعیتی) + شناسهٔ دستگاهِ عضوِ
     *  دیگر» نشستِ همان عضو را می‌داد. حالا عضوِ موجود فقط با رازی برمی‌گردد که همین دستگاه در نخستین پیوستن گرفته
     *  (`staff-device-keys`). شناسه‌ای که به حسابی بسته است ولی راز نیامد: کدِ ثابت ⇒ ردِ روشن
     *  (`staff_device_unverified`)؛ کدِ تازهٔ صاحب (یک‌بار/شماره‌دار، هنوز باز) ⇒ **حسابِ تازه**، هرگز همان حساب.
     */
    let user = null;
    let proof = false;
    //  `verify` خودش رازِ تهی یا شناسهٔ تهی را `null` می‌کند؛ پس تصمیم فقط با نتیجهٔ سنجش در پایگاه است، نه با بودنِ راز (CodeQL)
    const k = await deviceKeys.verify({ shopId, deviceUid, secret: deviceKeys.presented(device) });
    if (k) {
      user = await one('SELECT * FROM users WHERE id=$1', [k.user_id]);
      proof = Boolean(user);
    }
    if (!user && subject) {
      const bound = await one(
        `SELECT u.* FROM user_identities i JOIN users u ON u.id = i.user_id
          WHERE i.provider='staff' AND i.subject=$1`, [subject]
      );
      if (bound && !guard) user = bound;  //  کلیدِ اضطراری خاموش ⇒ رفتارِ پیشین
      else if (bound && !staffCodes.isFreshInvite(code)) {
        trail.userId = bound.id;
        throw forbidden(UNVERIFIED_MESSAGE, 'staff_device_unverified');
      }
    }

    const t = now();
    let fresh = false;
    if (!user && await staffCodes.preflight(rawCode)) {  // دستگاهِ تازه ⇒ کد پیش از ساختنِ حساب (بی «حسابِ شبح»)
      fresh = true;
      user = await one(
        `INSERT INTO users (id, name, kind, status, created_at, updated_at)
         VALUES ($1,$2,'staff','active',$3,$3) RETURNING *`,
        [newId('usr'), name || 'شاگرد', t]
      );
      if (subject) {
        //  شناسه‌ای که از پیش به حسابِ دیگری بسته است همان‌جا می‌ماند (DO NOTHING)؛ حسابِ تازه با رازِ خودش برمی‌گردد
        await query(
          `INSERT INTO user_identities (id, user_id, provider, subject, created_at)
           VALUES ($1,$2,'staff',$3,$4) ON CONFLICT (provider, subject) DO NOTHING`,
          [newId('idn'), user.id, subject, t]
        );
      }
    } else if (name && name !== user.name) {
      await query('UPDATE users SET name=$2, updated_at=$3 WHERE id=$1', [user.id, name, t]);
      user.name = name;
    }
    trail.userId = user.id;

    if (user.status !== 'active') {
      throw forbidden('این حساب غیرفعال شده است', 'user_disabled');
    }

    /*
     *  عضوِ فعالِ همین دکان است؟ کد دوباره خرج نمی‌شود.
     *
     *  بدون این، هر بار باز کردن برنامه یک استفاده از کد می‌خورد و
     *  کدی که «یک بار مصرف» ساخته شده، دفعه‌ی دوم کار نمی‌کرد.
     *  ⛔ ولی خودِ کد باید هنوز برای همین عضو معتبر باشد (باطل، تمام‌شده، مالِ کسِ دیگر ⇒ رد).
     */
    const member = await membershipOf(user.id);
    let mode = 'relogin';
    if (!member || member.shop_id !== shopId) {
      await staffCodes.redeem(rawCode, user.id, ip, { freshAt: fresh ? t : 0 });  // ناموفق ⇒ حسابِ ساخته‌شده در همین لحظه پاک
      mode = fresh ? 'join' : 'rejoin';
    } else if (guard) {
      await staffCodes.assertValidForMember(code, user.id);
    }

    //  رازِ دستگاه فقط پس از پیوستنِ سنجیده (کد خرج شد) و فقط برای برنامه‌ای که نگهش می‌دارد؛ ورودِ دوباره با
    //  رازِ درست همان راز را نگه می‌دارد (چرخاندن یعنی پاسخِ گم‌شده = دستگاهِ قفل‌شده)
    let deviceKey = null;
    if (mode !== 'relogin' && deviceUid && deviceKeys.capable(device)) {
      deviceKey = await deviceKeys.issue({ shopId, userId: user.id, deviceUid });
    }

    //  کدِ شاگرد مالِ دفترِ دکان است، پس نشستش هم همان بخش است. بخشِ پمپ راهِ پیوستنِ خودش را دارد.
    const session = await issueSession(user, { uid: deviceUid, name: device.name, platform: device.platform }, ip, tenancy.SHOP.app);
    /*
     *  ⛔ هم‌زمانی: «سنجشِ راز ⇐ نشست» دو گامِ جداست. «باطل کردنِ این دستگاه» یا «خروج از همه» ِ صاحب که میانِ آن دو
     *  کامیت شد، نشستِ تازه را نمی‌دید (هنوز نبود) و گوشیِ گم‌شده با نشستِ زنده می‌ماند. پس پس از نوشتنِ نشست راز
     *  دوباره سنجیده می‌شود — با همان `UPDATE` ِ قفل‌گیرِ `verify`: باطل‌کننده‌ای که ردیفِ راز را گرفته تا کامیتش
     *  منتظر می‌ماند؛ باطل‌کننده‌ای که پس از این می‌رسد (راز ⇐ نشست‌ها، همان ترتیبِ `staff-device-keys`) نشستِ تازه را
     *  هم می‌بندد. رازِ چرخانده (پیوستنِ دوباره، `deviceKey`) نه: رازِ تازه همین الان داده شد.
     */
    if (k && !deviceKey && !(await deviceKeys.verify({ shopId, deviceUid, secret: deviceKeys.presented(device) }))) {
      const tokens = require('./tokens');
      await tokens.revoke(session.accessToken, 'access');
      await tokens.revoke(session.refreshToken, 'refresh');
      throw forbidden(UNVERIFIED_MESSAGE, 'staff_device_unverified');
    }
    const payload = await loginPayload(user, session);
    if (deviceKey) payload.deviceKey = deviceKey;
    await trailLog('staff_login.ok', trail, ip, { mode, proof, guard, keyIssued: Boolean(deviceKey) });
    return payload;
  } catch (err) {
    await trailLog('staff_login.failed', trail, ip, { reason: (err && err.expose && err.code) || 'error' });
    throw err;
  }
}

/**
 * ⛔ رازِ دستگاه برای نشستِ **بازِ** شاگرد که پیش از ۲.۱۴.۵ وارد شده و رازی نگرفته (مهاجرتِ آرام، بی بیرون انداختن).
 *
 * فقط حسابِ کدِ شاگرد (`users.kind='staff'`) با عضویتِ فعال، و فقط برای **دستگاهِ همین نشست** (`tokens.device_id` ⇐
 * `devices.device_uid`) — شناسه از بدنه خوانده نمی‌شود. اگر این دستگاه رازِ زنده دارد هیچ کاری نمی‌شود (چرخاندن نه:
 * توکنِ دزدیده نباید رازِ دستگاهِ درست را باطل کند). «لازم نیست» ⇒ `deviceKey: null` و دلیل.
 */
async function enrollDevice(req) {
  const none = (reason) => ({ deviceKey: null, reason });
  if (req.appSection !== tenancy.SHOP.app || req.user.kind !== 'staff') return none('not_staff_account');
  const deviceUid = req.device?.device_uid || '';
  if (!deviceUid) return none('no_device');
  const member = await membershipOf(req.user.id);
  if (!member || member.role === 'owner') return none('not_member');
  const deviceKey = await deviceKeys.issue({ shopId: member.shop_id, userId: req.user.id, deviceUid, onlyIfNone: true });
  //  ⛔ رازِ باطل‌شده (خروج از همه، کدِ باطل، «باطل کردنِ این دستگاه»…) از نشستِ باز برنمی‌گردد: فقط کدِ تازهٔ صاحب
  if (!deviceKey) {
    return none(await deviceKeys.hasLive({ shopId: member.shop_id, userId: req.user.id, deviceUid }) ? 'exists' : 'revoked');
  }
  await audit.log({
    shopId: member.shop_id, userId: req.user.id, action: 'staff_device_key.issued',
    targetType: 'device', targetId: req.device.id, detail: { device: deviceUid }, ip: clientIp(req),
  });
  return { deviceKey };
}

module.exports = { login, enrollDevice, codeKey, UNVERIFIED_MESSAGE };
