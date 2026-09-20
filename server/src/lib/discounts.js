'use strict';
/**
 * تخفیف، تاریخچهٔ قیمت، افزونه و کمپین — بخشِ ۱۱.۳.۲ی پرامپت.
 *
 * ── قاعده‌ها ────────────────────────────────────────────────────────
 * ⛔ قیمت فقط از سرور می‌آید: قیمتِ پایهٔ هر کد از `plans.listPlans`
 *    (همان که `GET /plans` می‌دهد) خوانده می‌شود، نه از هیچ عددِ ثابتی.
 * ⛔ هر پرس‌وجویی روی `discount_codes` و `subscription_addons` و
 *    `plan_price_history` باید `app` را شرط کند — کدِ دکان روی پمپ نمی‌نشیند.
 * ⛔ اشتراک‌های قبلی قیمتِ خودشان را دارند (`subscriptions.price`)؛ تاریخچهٔ
 *    قیمت فقط می‌گوید پلن کِی چند بود.
 */
const { query, one, many, newId, now } = require('../db');
const { badRequest, notFound, forbidden } = require('../middleware/errors');
const plans = require('./plans');
const tenancy = require('./tenancy');
const { catalogOf } = require('./features');

function appOf(raw) {
  return tenancy.sectionOf(raw) || 'shop';
}

function currencyOf(app) { return app === 'pump' ? 'USD' : 'AFN'; }

function normalizeCode(raw) {
  return String(raw || '').trim().toUpperCase().replace(/\s+/g, '').slice(0, 40);
}

function randomCode() {
  const alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  let s = '';
  const { randomInt } = require('crypto');
  for (let i = 0; i < 8; i++) s += alphabet[randomInt(alphabet.length)];
  return s;
}

function shapeCode(r) {
  return {
    id: r.id, code: r.code, app: r.app, plan: r.plan || '', kind: r.kind, value: Number(r.value),
    currency: r.currency, userId: r.user_id || '', expiresAt: r.expires_at ? Number(r.expires_at) : null,
    maxUses: r.max_uses === null || r.max_uses === undefined ? null : Number(r.max_uses),
    oncePerCustomer: !!r.once_per_customer, uses: Number(r.uses || 0), note: r.note || '',
    status: r.status, createdBy: r.created_by || '', createdAt: Number(r.created_at),
  };
}

/* ==========================================================
   کدهای تخفیف
   ========================================================== */

async function createCode(input = {}, { createdBy = '' } = {}) {
  const app = appOf(input.app);
  const kind = input.kind === 'amount' ? 'amount' : 'percent';
  const value = Math.round(Number(input.value) || 0);
  if (kind === 'percent' && (value <= 0 || value > 100)) throw badRequest('درصدِ تخفیف باید بین ۱ تا ۱۰۰ باشد', 'bad_percent');
  if (kind === 'amount' && value <= 0) throw badRequest('مبلغِ تخفیف باید بزرگ‌تر از صفر باشد', 'bad_amount');
  const plan = String(input.plan || '').trim();
  if (plan) {
    const p = await plans.getPlan(plan, app);
    if (!p) throw badRequest('پلن در این بخش پیدا نشد', 'bad_plan');
  }
  const expiresAt = input.expiresAt ? Number(input.expiresAt) : null;
  if (expiresAt !== null && (!Number.isFinite(expiresAt) || expiresAt <= now())) {
    throw badRequest('مهلتِ کد باید در آینده باشد', 'bad_until');
  }
  const maxUses = input.maxUses === undefined || input.maxUses === null || input.maxUses === ''
    ? null : Math.max(1, Math.round(Number(input.maxUses) || 1));
  let code = normalizeCode(input.code);
  const t = now();
  //  کدِ نیامده: خودمان می‌سازیم، و اگر تکراری درآمد دوباره
  for (let tries = 0; tries < 5; tries++) {
    if (!code) code = randomCode();
    const dup = await one('SELECT 1 FROM discount_codes WHERE app=$1 AND code=$2', [app, code]);
    if (!dup) break;
    if (input.code) throw badRequest('این کد در این بخش قبلاً ساخته شده است', 'code_taken');
    code = '';
  }
  const row = await one(
    `INSERT INTO discount_codes (id, code, app, plan, kind, value, currency, user_id, expires_at, max_uses,
                                 once_per_customer, uses, note, status, created_by, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,0,$12,'active',$13,$14) RETURNING *`,
    [newId('dsc'), code, app, plan, kind, value, currencyOf(app), String(input.userId || input.user_id || ''),
      expiresAt, maxUses, input.oncePerCustomer === undefined ? true : !!input.oncePerCustomer,
      String(input.note || '').slice(0, 300), createdBy, t]
  );
  return shapeCode(row);
}

async function listCodes({ app = '', status = '', limit = 200 } = {}) {
  const rows = await many(
    `SELECT * FROM discount_codes WHERE ($1 = '' OR app = $1) AND ($2 = '' OR status = $2)
      ORDER BY created_at DESC LIMIT $3`,
    [app ? appOf(app) : '', String(status || ''), Math.min(Number(limit) || 200, 1000)]
  );
  return rows.map(shapeCode);
}

async function revokeCode(id) {
  const row = await one(`UPDATE discount_codes SET status='revoked' WHERE id=$1 RETURNING *`, [String(id || '')]);
  if (!row) throw notFound('کد پیدا نشد', 'code_not_found');
  return shapeCode(row);
}

/**
 * سنجیدنِ یک کد و حسابِ قیمت — بی هیچ خرجی.
 *
 * قیمتِ پایه همان قیمتِ **روز**ِ پلن است (با تخفیفِ خودِ پلن، اگر داشت)،
 * چون این کد برای خریدِ تازه/تمدید است و تمدید با قیمتِ روز می‌شود.
 */
async function quote(rawCode, { app, plan = '', userId = '' } = {}) {
  const a = appOf(app);
  const code = normalizeCode(rawCode);
  if (!code) throw badRequest('کد لازم است', 'code_required');
  const row = await one('SELECT * FROM discount_codes WHERE app=$1 AND code=$2', [a, code]);
  if (!row) throw notFound('این کد معتبر نیست', 'bad_code');
  if (row.status !== 'active') throw forbidden('این کد دیگر کار نمی‌کند', 'code_inactive');
  if (row.expires_at && Number(row.expires_at) < now()) throw forbidden('مهلتِ این کد تمام شده است', 'code_expired');
  if (row.max_uses !== null && row.max_uses !== undefined && Number(row.uses) >= Number(row.max_uses)) {
    throw forbidden('سقفِ استفادهٔ این کد پر شده است', 'code_exhausted');
  }
  if (row.user_id && userId && row.user_id !== userId) throw forbidden('این کد برای مشتریِ دیگری است', 'code_other_customer');
  if (row.user_id && !userId) throw forbidden('این کد فقط با حسابِ خودِ مشتری کار می‌کند', 'code_other_customer');
  if (row.once_per_customer && userId) {
    const used = await one('SELECT 1 FROM discount_uses WHERE code_id=$1 AND user_id=$2', [row.id, userId]);
    if (used) throw forbidden('این کد را قبلاً استفاده کرده‌اید', 'code_used');
  }
  const list = await plans.listPlans({ app: a });
  const wanted = String(plan || row.plan || '').trim();
  if (row.plan && wanted && row.plan !== wanted) throw forbidden('این کد برای پلنِ دیگری است', 'code_other_plan');
  const p = wanted ? list.find(x => x.code === wanted) : null;
  if (wanted && !p) throw badRequest('پلن پیدا نشد', 'bad_plan');

  const priceFor = (base) => {
    const cut = row.kind === 'percent' ? Math.round(base * Number(row.value) / 100) : Number(row.value);
    return Math.max(0, base - cut);
  };
  const prices = (p ? [p] : list).map(x => ({
    plan: x.code, title: x.title, price: x.price, finalPrice: priceFor(x.price), savings: x.price - priceFor(x.price),
  }));
  return {
    code: shapeCode(row),
    currency: row.currency,
    plan: p ? p.code : '',
    price: p ? p.price : null,
    finalPrice: p ? priceFor(p.price) : null,
    savings: p ? p.price - priceFor(p.price) : null,
    prices,
  };
}

/** خرجِ واقعی — وقتی اشتراک با این کد صادر شد. */
async function useCode(codeId, { app, userId = '', tenantId = '', subscriptionId = '', price = 0, finalPrice = 0 }) {
  const a = appOf(app);
  const row = await one('SELECT * FROM discount_codes WHERE id=$1 AND app=$2', [String(codeId || ''), a]);
  if (!row) throw notFound('کد پیدا نشد', 'code_not_found');
  await query(
    `INSERT INTO discount_uses (id, code_id, app, user_id, tenant_id, subscription_id, price, final_price, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)`,
    [newId('dsu'), row.id, a, userId, tenantId, subscriptionId, Math.round(price || 0), Math.round(finalPrice || 0), now()]
  );
  await query('UPDATE discount_codes SET uses = uses + 1 WHERE id=$1', [row.id]);
  return shapeCode(await one('SELECT * FROM discount_codes WHERE id=$1', [row.id]));
}

/* ==========================================================
   تاریخچهٔ قیمت
   ========================================================== */

async function recordPrice({ app, plan, prevPrice = null, price, changedBy = '' }) {
  const a = appOf(app);
  await query(
    `INSERT INTO plan_price_history (id, app, plan, prev_price, price, currency, changed_at, changed_by)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8)`,
    [newId('pph'), a, String(plan), prevPrice === null ? null : Math.round(prevPrice), Math.round(price),
      currencyOf(a), now(), changedBy]
  );
}

async function priceHistory({ app, plan = '', limit = 100 } = {}) {
  const rows = await many(
    `SELECT * FROM plan_price_history WHERE app=$1 AND ($2 = '' OR plan=$2)
      ORDER BY changed_at DESC LIMIT $3`,
    [appOf(app), String(plan || ''), Math.min(Number(limit) || 100, 1000)]
  );
  return rows.map(r => ({
    id: r.id, app: r.app, plan: r.plan, prevPrice: r.prev_price === null ? null : Number(r.prev_price),
    price: Number(r.price), currency: r.currency, changedAt: Number(r.changed_at), changedBy: r.changed_by,
  }));
}

/* ==========================================================
   تخفیفِ مستقیم روی یک اشتراک
   ========================================================== */

/**
 * قیمتِ خودِ اشتراک کم می‌شود و دلیلش در تاریخچه می‌نشیند.
 * اگر اشتراک هنوز قیمت نداشت (نسلِ اول)، از قیمتِ روزِ پلن شروع می‌شود.
 */
async function directDiscount({ app, subscriptionId, percent = 0, amount = 0, reason = '', by = '' }) {
  const a = appOf(app);
  const T = tenancy.byApp(a);
  const sub = await one(`SELECT * FROM ${T.subsTable} WHERE id=$1`, [String(subscriptionId || '')]);
  if (!sub) throw notFound('اشتراک پیدا نشد', 'subscription_not_found');
  const note = String(reason || '').trim();
  if (!note) throw badRequest('دلیلِ تخفیف لازم است', 'reason_required');
  let base = sub.price === null || sub.price === undefined ? null : Number(sub.price);
  if (base === null) {
    const list = await plans.listPlans({ activeOnly: false, app: a });
    const p = list.find(x => x.code === sub.plan);
    base = p ? p.fullPrice : 0;
  }
  const pct = Math.round(Number(percent) || 0);
  const amt = Math.round(Number(amount) || 0);
  if ((pct <= 0 || pct > 100) && amt <= 0) throw badRequest('درصد یا مبلغِ تخفیف لازم است', 'bad_discount');
  const cut = pct > 0 ? Math.round(base * pct / 100) : amt;
  const finalPrice = Math.max(0, base - cut);
  const row = await one(
    `UPDATE ${T.subsTable} SET price=$2, updated_at=$3 WHERE id=$1 RETURNING *`,
    [sub.id, finalPrice, now()]
  );
  await query(
    `INSERT INTO ${T.historyTable}
       (id, subscription_id, ${T.tenantKey}, action, plan, prev_status, new_status, prev_ends_at, new_ends_at, actor, note, created_at)
     VALUES ($1,$2,$3,'discount',$4,$5,$5,$6,$6,$7,$8,$9)`,
    [newId('sbh'), sub.id, sub[T.tenantKey], sub.plan, sub.status, Number(sub.ends_at), by,
      `تخفیف ${pct > 0 ? `${pct}٪` : `${amt} ${row.currency}`}: ${base} ⇒ ${finalPrice} — ${note}`.slice(0, 300), now()]
  );
  return { subscription: row, price: base, finalPrice, savings: base - finalPrice };
}

/* ==========================================================
   افزونه‌ها
   ========================================================== */

function shapeAddon(r) {
  return {
    id: r.id, app: r.app, subscriptionId: r.subscription_id, tenantId: r.tenant_id, feature: r.feature,
    price: Number(r.price || 0), currency: r.currency, note: r.note || '', createdBy: r.created_by,
    createdAt: Number(r.created_at), removedAt: r.removed_at ? Number(r.removed_at) : null,
  };
}

async function addAddon({ app, subscriptionId, feature, price = 0, note = '', by = '' }) {
  const a = appOf(app);
  const T = tenancy.byApp(a);
  const cat = catalogOf(a);
  const key = String(feature || '').trim();
  if (!cat.GRANTABLE_KEYS.includes(key)) throw badRequest('این قابلیت در کاتالوگِ این بخش نیست', 'bad_feature');
  const sub = await one(`SELECT * FROM ${T.subsTable} WHERE id=$1`, [String(subscriptionId || '')]);
  if (!sub) throw notFound('اشتراک پیدا نشد', 'subscription_not_found');
  const dup = await one(
    'SELECT * FROM subscription_addons WHERE app=$1 AND subscription_id=$2 AND feature=$3 AND removed_at IS NULL',
    [a, sub.id, key]
  );
  if (dup) return shapeAddon(dup);
  const row = await one(
    `INSERT INTO subscription_addons (id, app, subscription_id, tenant_id, feature, price, currency, note, created_by, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10) RETURNING *`,
    [newId('adn'), a, sub.id, sub[T.tenantKey], key, Math.round(Number(price) || 0), currencyOf(a),
      String(note || '').slice(0, 300), by, now()]
  );
  await query(
    `INSERT INTO ${T.historyTable}
       (id, subscription_id, ${T.tenantKey}, action, plan, prev_status, new_status, prev_ends_at, new_ends_at, actor, note, created_at)
     VALUES ($1,$2,$3,'addon',$4,$5,$5,$6,$6,$7,$8,$9)`,
    [newId('sbh'), sub.id, sub[T.tenantKey], sub.plan, sub.status, Number(sub.ends_at), by,
      `افزونهٔ ${key}${note ? ` — ${note}` : ''}`.slice(0, 300), now()]
  );
  return shapeAddon(row);
}

async function removeAddon(id, { app } = {}) {
  const row = await one(
    `UPDATE subscription_addons SET removed_at=$2 WHERE id=$1 AND app=$3 AND removed_at IS NULL RETURNING *`,
    [String(id || ''), now(), appOf(app)]
  );
  if (!row) throw notFound('افزونه پیدا نشد', 'addon_not_found');
  return shapeAddon(row);
}

/** کلیدهای افزونهٔ زندهٔ یک اشتراک — همان که `entitlement.js` می‌خواند. */
async function addonKeysOf(app, subscriptionId) {
  if (!subscriptionId) return [];
  const rows = await many(
    'SELECT feature FROM subscription_addons WHERE app=$1 AND subscription_id=$2 AND removed_at IS NULL',
    [appOf(app), String(subscriptionId)]
  );
  return rows.map(r => r.feature);
}

async function listAddons({ app, subscriptionId = '', tenantId = '' } = {}) {
  const rows = await many(
    `SELECT * FROM subscription_addons WHERE app=$1 AND removed_at IS NULL
        AND ($2 = '' OR subscription_id=$2) AND ($3 = '' OR tenant_id=$3) ORDER BY created_at DESC`,
    [appOf(app), String(subscriptionId || ''), String(tenantId || '')]
  );
  return rows.map(shapeAddon);
}

/* ==========================================================
   کمپین: فیلتر ⇒ کد ⇒ اعلان، در یک تماس
   ========================================================== */

function shapeCampaign(r) {
  return {
    id: r.id, name: r.name, app: r.app, filter: r.filter || {}, discountCodeId: r.discount_code_id || '',
    noticeId: r.notice_id || '', status: r.status, createdBy: r.created_by, createdAt: Number(r.created_at),
  };
}

/**
 * `input`: { name, app: shop|pump|both, filter: audience, discount: {kind,value,expiresAt,maxUses,plan},
 *            notice: {title, body, channels, scheduleAt} , send: true|false }
 *
 * ⚠️ کمپینِ «both» دو کد می‌سازد (یکی برای هر بخش)، چون کدِ تخفیف
 * به بخش بسته است و قیمتِ دو بخش یکی نیست. اعلان یکی است و
 * {کد-تخفیف} برای هر گیرنده کدِ بخشِ خودش می‌شود.
 */
async function createCampaign(input = {}, { createdBy = '' } = {}) {
  const notices = require('./notices');
  const name = String(input.name || '').trim().slice(0, 120);
  if (!name) throw badRequest('نامِ کمپین لازم است', 'name_required');
  const app = ['shop', 'pump', 'both'].includes(input.app) ? input.app : 'shop';
  const apps = app === 'both' ? ['shop', 'pump'] : [app];
  const d = input.discount || {};
  const codes = {};
  for (const a of apps) {
    codes[a] = await createCode({
      app: a, kind: d.kind, value: d.value, plan: d.plan || '', expiresAt: d.expiresAt || null,
      maxUses: d.maxUses ?? null, oncePerCustomer: d.oncePerCustomer ?? true,
      code: apps.length === 1 ? d.code : (d.code ? `${normalizeCode(d.code)}-${a.toUpperCase()}` : ''),
      note: `کمپین: ${name}`,
    }, { createdBy });
  }
  const filter = input.filter && typeof input.filter === 'object' ? input.filter : { kind: 'all' };
  const nin = input.notice || {};
  const tpl = await notices.templateFor('discount', apps[0]);
  const notice = await notices.create({
    app,
    audience: filter.kind ? filter : { kind: 'filter', ...filter },
    channels: nin.channels || (tpl ? tpl.channels : ['inapp', 'email']),
    title: nin.title || (tpl ? tpl.title : 'کدِ تخفیف: {کد-تخفیف}'),
    body: nin.body || (tpl ? tpl.body : ''),
    templateKey: 'discount',
    //  کدِ هر بخش؛ `variablesFor` هم‌معنیِ `code` را می‌خواند و برای گیرندهٔ
    //  هر بخش کدِ همان بخش را می‌گذارد (پایین، در `campaignVariables`)
    variables: { 'کد-تخفیف': codes[apps[0]].code, ...(apps.length > 1 ? { codes: { shop: codes.shop.code, pump: codes.pump.code } } : {}) },
    scheduleAt: nin.scheduleAt || null,
  }, { createdBy });
  const row = await one(
    `INSERT INTO campaigns (id, name, app, filter, discount_code_id, notice_id, status, created_by, created_at)
     VALUES ($1,$2,$3,$4::jsonb,$5,$6,'active',$7,$8) RETURNING *`,
    [newId('cmp'), name, app, JSON.stringify(filter), apps.map(a => codes[a].id).join(','), notice.id, createdBy, now()]
  );
  let sent = null;
  if (input.send !== false && !nin.scheduleAt) sent = await notices.send(notice.id, { by: createdBy });
  return { campaign: shapeCampaign(row), codes: apps.map(a => codes[a]), notice: notices.shapeNotice(await notices.get(notice.id)), sent };
}

async function listCampaigns({ limit = 100 } = {}) {
  const rows = await many('SELECT * FROM campaigns ORDER BY created_at DESC LIMIT $1', [Math.min(Number(limit) || 100, 500)]);
  return rows.map(shapeCampaign);
}

/**
 * آمارِ کمپین: چند نفر گرفتند، چند نفر دیدند، چند نفر با کد خریدند، و
 * چند نفر از گیرنده‌ها بعد از کمپین تمدید کردند.
 */
async function campaignStats(id) {
  const row = await one('SELECT * FROM campaigns WHERE id=$1', [String(id || '')]);
  if (!row) throw notFound('کمپین پیدا نشد', 'campaign_not_found');
  const codeIds = String(row.discount_code_id || '').split(',').filter(Boolean);
  const deliveries = row.notice_id
    ? await many('SELECT * FROM notice_deliveries WHERE notice_id=$1', [row.notice_id]) : [];
  const recipients = new Map();
  for (const d of deliveries) {
    const key = `${d.app}:${d.tenant_id || d.user_id}`;
    const cur = recipients.get(key) || { app: d.app, tenantId: d.tenant_id, userId: d.user_id, seen: false, sent: false };
    if (['sent', 'delivered', 'read'].includes(d.status)) cur.sent = true;
    if (d.status === 'read' || d.status === 'delivered') cur.seen = true;
    recipients.set(key, cur);
  }
  const uses = codeIds.length
    ? await one(`SELECT COUNT(*)::int n FROM discount_uses WHERE code_id = ANY($1::text[])`, [codeIds]) : { n: 0 };
  let renewed = 0;
  for (const app of ['shop', 'pump']) {
    const T = tenancy.byApp(app);
    const ids = [...recipients.values()].filter(r => r.app === app && r.tenantId).map(r => r.tenantId);
    if (!ids.length) continue;
    const r = await one(
      `SELECT COUNT(DISTINCT ${T.tenantKey})::int n FROM ${T.historyTable}
        WHERE ${T.tenantKey} = ANY($1::text[]) AND action IN ('grant','renew') AND created_at >= $2`,
      [ids, Number(row.created_at)]
    );
    renewed += r.n;
  }
  const codes = [];
  for (const cid of codeIds) {
    const c = await one('SELECT * FROM discount_codes WHERE id=$1', [cid]);
    if (c) codes.push(shapeCode(c));
  }
  return {
    campaign: shapeCampaign(row),
    codes,
    recipients: recipients.size,
    sent: [...recipients.values()].filter(r => r.sent).length,
    seen: [...recipients.values()].filter(r => r.seen).length,
    codeUses: uses.n,
    renewed,
  };
}

module.exports = {
  normalizeCode, shapeCode, createCode, listCodes, revokeCode, quote, useCode,
  recordPrice, priceHistory, directDiscount,
  addAddon, removeAddon, addonKeysOf, listAddons, shapeAddon,
  createCampaign, listCampaigns, campaignStats, shapeCampaign, currencyOf,
};
