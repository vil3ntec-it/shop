'use strict';
/**
 * پورتالِ مشتری (بخشِ ۱۴) و «اعلان‌های من / پرداخت‌های من / تنظیماتِ من».
 *
 * ── دو در، یک منطق ──────────────────────────────────────────────────
 *   • `/api/portal/*`  با `requireAnyUser`: صفحهٔ وبِ مشتری، که هم دکانش را
 *     می‌بیند هم پمپش را (هویت مشترک است؛ داده‌ی هر بخش با عضویتِ واقعیِ
 *     همان کاربر خوانده می‌شود، نه با چیزی که مرورگر فرستاده).
 *   • `/api/me/*` (دکان) و `/api/pump/*` (پمپ): همان هندلرها، با نشستِ
 *     همان بخش — برای برنامه‌ها و SDK.
 *
 * ⛔ شناسهٔ دکان/پمپ هیچ‌وقت از درخواست خوانده نمی‌شود: از عضویتِ
 *    `req.user.id` می‌آید (`shops.membershipOf` / `stations.membershipOf`).
 * ⛔ پرداخت و رسید فقط برای حسابِ خودِ آدم برمی‌گردد.
 */
const express = require('express');
const { one, many, query, newId, now } = require('../db');
const v = require('../lib/validate');
const plans = require('../lib/plans');
const subs = require('../lib/subscriptions');
const entitlement = require('../lib/entitlement');
const notices = require('../lib/notices');
const discounts = require('../lib/discounts');
const support = require('../lib/support');
const apps = require('../lib/managed-apps');
const shops = require('../lib/shops');
const stations = require('../lib/stations');
const tenancy = require('../lib/tenancy');
const { requireAnyUser } = require('../middleware/auth');
const { notFound, badRequest } = require('../middleware/errors');
const { publicUser } = require('./auth');

const DAY = 24 * 60 * 60 * 1000;

/* ==========================================================
   ابزارِ مشترک
   ========================================================== */

/** عضویتِ این کاربر در یک بخش — یا null. */
async function membership(app, userId) {
  if (app === 'pump') {
    const m = await stations.membershipOf(userId);
    return m ? { app, tenantId: m.station_id, name: m.station_name || '', role: m.role, code: m.station_code || '' } : null;
  }
  const m = await shops.membershipOf(userId);
  return m ? { app: 'shop', tenantId: m.shop_id, name: m.shop_name || '', role: m.role } : null;
}

/** شناسهٔ حساب برای هندلرهای مشترک: از خودِ درخواست (me/pump) یا از عضویت. */
async function tenantOf(app, req) {
  if (app === 'shop' && req.shopId !== undefined) return req.shopId || '';
  if (app === 'pump' && req.stationId !== undefined) return req.stationId || '';
  const m = await membership(app, req.user.id);
  return m ? m.tenantId : '';
}

async function cloudSyncOf(app, tenantId) {
  if (!tenantId) return false;
  const T = tenancy.byApp(app);
  const r = await one(`SELECT cloud_sync FROM ${T.tenantTable} WHERE id=$1`, [tenantId]);
  return !!(r && r.cloud_sync);
}

/** نمای اشتراک برای «اشتراکِ من»: روزِ مانده، رنگ، دائمی. */
function subscriptionView(ent, at = now()) {
  const s = ent.subscription || {};
  const permanent = !!s.endsAt && (s.plan === 'perm' || s.endsAt - at > 10 * 365 * DAY);
  const days = s.endsAt ? Math.max(0, Math.ceil(((s.graceEndsAt || s.endsAt) - at) / DAY)) : 0;
  const total = s.startsAt && s.endsAt ? Math.max(1, Math.ceil((s.endsAt - s.startsAt) / DAY)) : 0;
  //  سبز > ۳۰ روز · زرد ≤ ۳۰ (و ≤ ۷ همان زرد، پررنگ‌تر در صفحه) · سرخ منقضی
  let color = 'red';
  if (permanent) color = 'green';
  else if (s.active) color = days > 30 ? 'green' : 'yellow';
  else if (ent.source === 'trial') color = 'yellow';
  return {
    source: ent.source, status: s.status || 'none', active: !!s.active, plan: s.plan || '',
    startsAt: s.startsAt || 0, endsAt: s.endsAt || 0, daysLeft: permanent ? null : days,
    totalDays: total, progress: permanent ? 100 : (total ? Math.max(0, Math.min(100, Math.round(days / total * 100))) : 0),
    permanent, color,
    //  ⚠️ رقمِ فارسی — همان قاعدهٔ متغیرهای اعلان
    label: permanent ? 'دائمی ✓' : (s.active ? `${days.toLocaleString('fa-AF')} روز مانده`
      : (ent.source === 'trial' ? `آزمایشی — ${Number(ent.trial.daysLeft || 0).toLocaleString('fa-AF')} روز`
        : (s.status === 'none' ? 'بدون اشتراک' : 'منقضی'))),
    features: ent.features, addons: ent.addons || [], trial: ent.trial,
  };
}

/* ==========================================================
   هندلرهای مشترک (me / pump / portal)
   ========================================================== */

async function noticesHandler(app, req, res, next) {
  try {
    const tenantId = await tenantOf(app, req);
    const out = await notices.inboxFor({ app, userId: req.user.id, tenantId, limit: v.integer(req.query?.limit, { min: 1, max: 200, def: 50 }) });
    res.json({ ...out, serverTime: now() });
  } catch (err) { next(err); }
}

async function readNoticeHandler(app, req, res, next) {
  try {
    const tenantId = await tenantOf(app, req);
    res.json({ notice: await notices.markRead({ id: v.id(req.params.id), app, userId: req.user.id, tenantId }) });
  } catch (err) { next(err); }
}

const sales = () => require('./admin-sales');

async function paymentsOf(app, tenantId) {
  if (!tenantId) return [];
  const rows = await many(
    `SELECT ${sales().PAYMENT_COLS} FROM sub_payments p ${sales().PAYMENT_JOIN}
      WHERE p.app=$1 AND p.tenant_id=$2 AND p.deleted=false ORDER BY p.paid_at DESC LIMIT 200`,
    [app, tenantId]
  );
  return rows.map(r => ({ ...sales().shapePayment(r), receiptUrl: `/api/portal/payments/${r.id}/receipt` }));
}

async function paymentsHandler(app, req, res, next) {
  try {
    const tenantId = await tenantOf(app, req);
    res.json({ payments: await paymentsOf(app, tenantId), serverTime: now() });
  } catch (err) { next(err); }
}

async function settingsHandler(app, req, res, next) {
  try {
    const tenantId = await tenantOf(app, req);
    res.json({ settings: { app, cloudSync: await cloudSyncOf(app, tenantId) } });
  } catch (err) { next(err); }
}

async function saveSettingsHandler(app, req, res, next) {
  try {
    const tenantId = await tenantOf(app, req);
    if (!tenantId) return next(badRequest(app === 'pump' ? 'اول پمپتان را بسازید' : 'اول دکانتان را بسازید', app === 'pump' ? 'no_station' : 'no_shop'));
    const T = tenancy.byApp(app);
    if (req.body?.cloudSync !== undefined) {
      await query(`UPDATE ${T.tenantTable} SET cloud_sync=$2, updated_at=$3 WHERE id=$1`,
        [tenantId, v.bool(req.body.cloudSync, false), now()]);
      await require('../lib/audit').log({ shopId: app === 'shop' ? tenantId : '', userId: req.user.id, action: 'settings.cloud_sync', detail: { app, cloudSync: v.bool(req.body.cloudSync, false) } });
    }
    res.json({ settings: { app, cloudSync: await cloudSyncOf(app, tenantId) } });
  } catch (err) { next(err); }
}

/** سنجیدنِ کدِ تخفیف و قیمتِ نهایی — بی هیچ خرجی؛ خرج با صدورِ اشتراک است. */
async function redeemHandler(app, req, res, next) {
  try {
    const out = await discounts.quote(req.body?.code, { app, plan: v.text(req.body?.plan, { max: 20 }), userId: req.user.id });
    const cfg = await plans.allConfig();
    res.json({
      ...out,
      currencyLabel: app === 'pump' ? (cfg.pump_currency || 'دالر') : (cfg.currency || 'افغانی'),
      serverTime: now(),
    });
  } catch (err) { next(err); }
}

async function downloadsOf(app = '') {
  const cfg = await plans.allConfig();
  const list = await apps.list();
  const out = [];
  for (const a of ['shop', 'pump']) {
    if (app && a !== app) continue;
    out.push({
      app: a, title: notices.APP_LABEL[a],
      url: cfg[`download_url_${a}`] || '',
      version: cfg[`latest_version_${a}`] || '',
      notes: cfg[`release_notes_${a}`] || '',
      minVersion: cfg.min_app_version || '',
    });
  }
  return {
    downloads: out,
    //  برنامه‌های دیگرِ صاحبِ سامانه — با نشانیِ خودشان
    apps: list.filter(x => x.status === 'active' && x.url).map(x => ({ slug: x.slug, title: x.title, kind: x.kind, url: x.url })),
  };
}

async function downloadsHandler(req, res, next) {
  try {
    res.json({ ...(await downloadsOf(tenancy.sectionOf(req.query?.app) || '')), serverTime: now() });
  } catch (err) { next(err); }
}

/**
 * خطایی که برنامهٔ مشتری گزارش می‌دهد (SDK: `reportError`).
 *
 * ⛔ کاربر و حساب از **توکن** برداشته می‌شوند، نه از بدنه: وگرنه هر کسی
 *    می‌توانست خطا را به نامِ دیگری بنویسد و دفترِ خطا بی‌ارزش می‌شد.
 * ⚠️ متن بریده می‌شود؛ یک استثنای بزرگ نباید یک ردیفِ چندمگابایتی بسازد.
 */
async function reportErrorHandler(app, req, res, next) {
  try {
    const tenantId = await tenantOf(app, req);
    const id = newId('cer');
    await query(
      `INSERT INTO client_errors (id, app, user_id, tenant_id, version, platform, message, stack, context, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
      [id, app, req.user.id, tenantId,
        v.text(req.body?.version, { max: 32 }), v.text(req.body?.platform, { max: 40 }),
        v.text(req.body?.message, { max: 1000 }), String(req.body?.stack || '').slice(0, 8000),
        JSON.stringify(v.payload(req.body?.context ?? {}, { max: 8 * 1024 })), now()]
    );
    res.status(201).json({ ok: true, id });
  } catch (err) { next(err); }
}

/** تپشِ برنامه: اشتراک، اعلان‌های نخوانده، نسخهٔ تازه — یک درخواست. */
async function heartbeatHandler(app, req, res, next) {
  try {
    const tenantId = await tenantOf(app, req);
    const ent = tenantId ? await entitlement.forApp(app).entitlementOf(tenantId) : null;
    const inbox = await notices.inboxFor({ app, userId: req.user.id, tenantId, limit: 20 });
    const dl = await downloadsOf(app);
    if (req.query?.version) {
      await query('UPDATE tokens SET app_version=$2 WHERE token_hash=$1', [req.tokenRow.token_hash, String(req.query.version).slice(0, 32)]).catch(() => {});
    }
    res.json({
      app, tenantId, subscription: ent ? subscriptionView(ent) : null,
      entitlement: ent ? { source: ent.source, features: ent.features } : null,
      unreadNotices: inbox.unread, latest: dl.downloads[0] || null, serverTime: now(),
    });
  } catch (err) { next(err); }
}

/* ==========================================================
   روترِ پورتال — `/api/portal/*`
   ========================================================== */

const router = express.Router();
router.use(requireAnyUser);

/** همه‌چیزِ صفحهٔ پورتال در یک درخواست. */
router.get('/me', async (req, res, next) => {
  try {
    const at = now();
    const memberships = [];
    for (const app of ['shop', 'pump']) {
      const m = await membership(app, req.user.id);
      if (!m) continue;
      await subs.forApp(app).expireDue(at);
      const ent = await entitlement.forApp(app).entitlementOf(m.tenantId, at);
      memberships.push({
        ...m,
        subscription: subscriptionView(ent, at),
        cloudSync: await cloudSyncOf(app, m.tenantId),
        addons: await discounts.listAddons({ app, tenantId: m.tenantId }),
        history: (await subs.forApp(app).changeLog(m.tenantId, 20)).map(r => ({
          action: r.action, plan: r.plan, newEndsAt: r.new_ends_at ? Number(r.new_ends_at) : null,
          createdAt: Number(r.created_at), note: r.note || '',
        })),
      });
    }
    res.json({
      user: publicUser(req.user),
      app: req.appSection,
      memberships,
      downloads: (await downloadsOf()).downloads,
      serverTime: at,
    });
  } catch (err) { next(err); }
});

router.get('/subscriptions', async (req, res, next) => {
  try {
    const out = [];
    for (const app of ['shop', 'pump']) {
      const m = await membership(app, req.user.id);
      if (!m) continue;
      const ent = await entitlement.forApp(app).entitlementOf(m.tenantId);
      out.push({ ...m, subscription: subscriptionView(ent) });
    }
    res.json({ subscriptions: out, serverTime: now() });
  } catch (err) { next(err); }
});

router.get('/payments', async (req, res, next) => {
  try {
    const out = [];
    for (const app of ['shop', 'pump']) {
      const m = await membership(app, req.user.id);
      if (m) out.push(...await paymentsOf(app, m.tenantId));
    }
    res.json({ payments: out.sort((a, b) => b.paidAt - a.paidAt), serverTime: now() });
  } catch (err) { next(err); }
});

/** رسیدِ چاپی — فقط رسیدِ حسابِ خودِ آدم. */
router.get('/payments/:id/receipt', async (req, res, next) => {
  try {
    const row = await one(`SELECT ${sales().PAYMENT_COLS} FROM sub_payments p ${sales().PAYMENT_JOIN} WHERE p.id=$1 AND p.deleted=false`,
      [v.id(req.params.id)]);
    if (!row) return next(notFound('رسید پیدا نشد', 'payment_not_found'));
    const m = await membership(row.app, req.user.id);
    if (!m || m.tenantId !== row.tenant_id) return next(notFound('رسید پیدا نشد', 'payment_not_found'));
    res.set('Content-Type', 'text/html; charset=utf-8');
    res.send(await sales().receiptHtml(row));
  } catch (err) { next(err); }
});

router.get('/devices', async (req, res, next) => {
  try {
    const rows = await many(
      'SELECT id, device_uid, name, platform, status, created_at, last_seen_at FROM devices WHERE user_id=$1 ORDER BY last_seen_at DESC NULLS LAST',
      [req.user.id]
    );
    res.json({ devices: rows.map(r => ({ ...r, current: req.device ? req.device.id === r.id : false })) });
  } catch (err) { next(err); }
});

router.delete('/devices/:id', async (req, res, next) => {
  try {
    const id = v.id(req.params.id, { field: 'شناسه دستگاه' });
    await query(`UPDATE devices SET status='revoked' WHERE id=$1 AND user_id=$2`, [id, req.user.id]);
    await query('UPDATE tokens SET revoked_at=$1 WHERE device_id=$2 AND revoked_at IS NULL', [now(), id]);
    res.json({ ok: true });
  } catch (err) { next(err); }
});

/** پشتیبانی — همان رشتهٔ همیشگیِ همین کاربر در بخشِ نشست. */
async function supportIdentity(req) {
  const app = req.appSection === 'pump' ? 'pump' : 'shop';
  const m = await membership(app, req.user.id);
  return {
    app, userId: req.user.id,
    shopId: app === 'shop' && m ? m.tenantId : '',
    stationId: app === 'pump' && m ? m.tenantId : '',
    who: req.user.name || '', contact: req.user.email || req.user.phone || '',
  };
}

router.get('/support', async (req, res, next) => {
  try {
    const thread = await support.threadFor(await supportIdentity(req));
    const after = v.integer(req.query?.after, { min: 0, max: 1e15, def: 0 });
    if (!after) await support.markRead(thread.id, 'user');
    res.json({ thread: support.shapeThread(thread), messages: await support.messages(thread.id, { after }), serverTime: now() });
  } catch (err) { next(err); }
});

router.post('/support/messages', async (req, res, next) => {
  try {
    const id = await supportIdentity(req);
    const body = support.cleanBody(req.body?.body ?? req.body?.text);
    const thread = await support.threadFor({ ...id, subject: v.text(req.body?.subject, { max: 120 }) });
    const message = await support.post(thread.id, { sender: 'user', senderId: id.userId, senderName: id.who, body });
    res.status(201).json({ message });
  } catch (err) { next(err); }
});

router.get('/notices', (req, res, next) => noticesHandler(req.appSection === 'pump' ? 'pump' : 'shop', req, res, next));
router.post('/notices/:id/read', (req, res, next) => readNoticeHandler(req.appSection === 'pump' ? 'pump' : 'shop', req, res, next));
router.get('/downloads', downloadsHandler);
router.get('/settings', async (req, res, next) => {
  try {
    const out = {};
    for (const app of ['shop', 'pump']) {
      const m = await membership(app, req.user.id);
      if (m) out[app] = { cloudSync: await cloudSyncOf(app, m.tenantId) };
    }
    res.json({ settings: out });
  } catch (err) { next(err); }
});
router.put('/settings', (req, res, next) => saveSettingsHandler(tenancy.sectionOf(req.body?.app) || (req.appSection === 'pump' ? 'pump' : 'shop'), req, res, next));
router.post('/redeem', (req, res, next) => redeemHandler(tenancy.sectionOf(req.body?.app) || (req.appSection === 'pump' ? 'pump' : 'shop'), req, res, next));
router.get('/heartbeat', (req, res, next) => heartbeatHandler(req.appSection === 'pump' ? 'pump' : 'shop', req, res, next));
router.post('/errors', (req, res, next) => reportErrorHandler(req.appSection === 'pump' ? 'pump' : 'shop', req, res, next));

module.exports = router;
Object.assign(module.exports, {
  noticesHandler, readNoticeHandler, paymentsHandler, settingsHandler, saveSettingsHandler,
  redeemHandler, heartbeatHandler, downloadsHandler, reportErrorHandler, cloudSyncOf, subscriptionView, membership, downloadsOf,
});
