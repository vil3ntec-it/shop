'use strict';
/**
 * مرکزِ اعلان — بخشِ ۱۱.۳.۳ی پرامپت.
 *
 * ── چه چیزی این را لازم کرد ────────────────────────────────────────
 * تا امروز تنها راهِ خبر دادن به مشتری‌ها «پیامِ همگانی» بود: یک متن در
 * چتِ پشتیبانی، بی گزارش. مدیر «۴۲ نفر» می‌دید و نمی‌دانست کدامشان
 * خواندند و کدامشان ایمیل نگرفتند. و اعلان‌های خودکار (رو به پایان،
 * منقضی، تمدید) متنشان در کد بود و از پنل عوض نمی‌شد.
 *
 * ── چه می‌کند ──────────────────────────────────────────────────────
 *   • گیرنده: همه / یک بخش / فیلتر (شهر، پلن، رو به پایان، منقضی،
 *     دائمی) / یک نفر  →  `resolveAudience`
 *   • کانال: داخلِ برنامه، پوش، ایمیل — هر ترکیبی
 *   • متغیرها: {نام} {برنامه} {روز-مانده} {قیمت} {کد-تخفیف}، برای هر
 *     گیرنده جدا پر می‌شوند  →  `render`
 *   • زمان‌بندی و تکرارِ ماهانه  →  `tick` (هر دقیقه از `index.js`)
 *   • گزارشِ ارسال: یک ردیف برای هر گیرنده در هر کانال، با نتیجهٔ
 *     **واقعی** (ایمیل رفت یا خطای خودِ سرورِ ایمیل)
 *   • اعلان‌های خودکار با همان قالب‌های قابلِ ویرایش  →  `checkSystem`
 *
 * ⛔ **شناسهٔ گیرنده هیچ‌وقت از ایمیل حدس زده نمی‌شود**: هر گیرنده از
 * جدول‌های `shops`/`stations` و صاحبشان می‌آید.
 * ⛔ **هر پرس‌وجویی که «both» را باز می‌کند، هر ردیف را به بخشِ خودش مهر
 * می‌زند**: تحویل مالِ `shop` یا `pump` است، نه «هر دو».
 */
const { query, one, many, newId, now } = require('../db');
const { badRequest, notFound } = require('../middleware/errors');
const plans = require('./plans');
const subs = require('./subscriptions');
const mailer = require('./mailer');
const push = require('./push');
const tenancy = require('./tenancy');

const DAY = 24 * 60 * 60 * 1000;
const CHANNELS = ['inapp', 'push', 'email'];
const APP_LABEL = { shop: 'دکان', pump: 'پمپ‌بنزین' };

/* ==========================================================
   قالب‌های آماده
   ----------------------------------------------------------
   از پنل ویرایش می‌شوند؛ این‌ها فقط مقدارِ روزِ اول‌اند و با
   `ON CONFLICT DO NOTHING` می‌نشینند، پس ویرایشِ مدیر هیچ‌وقت
   روی‌نویسی نمی‌شود.
   ========================================================== */
const TEMPLATES = [
  { key: 'welcome', title: 'خوش آمدید، {نام}',
    body: 'حسابِ شما در بخشِ {برنامه} آماده است. اگر پرسشی داشتید همین‌جا در پشتیبانی بنویسید — پاسخ می‌دهیم.',
    channels: ['inapp', 'email'] },
  { key: 'expiring', title: 'اشتراکِ {برنامه} {روز-مانده} روز دیگر تمام می‌شود',
    body: '{نام} عزیز، اشتراکِ شما در بخشِ {برنامه} {روز-مانده} روز دیگر تمام می‌شود. برای این‌که قابلیت‌ها بسته نشوند، تمدیدش کنید. قیمتِ تمدید: {قیمت}.',
    channels: ['inapp', 'email'] },
  { key: 'expired', title: 'اشتراکِ {برنامه} تمام شد',
    body: '{نام} عزیز، اشتراکِ شما در بخشِ {برنامه} تمام شده است. دفترِ شما سرِ جایش است و هیچ داده‌ای پاک نمی‌شود؛ با تمدید همه‌چیز همان لحظه باز می‌شود.',
    channels: ['inapp', 'email'] },
  { key: 'renewed', title: 'اشتراکِ {برنامه} تمدید شد',
    body: '{نام} عزیز، اشتراکِ شما در بخشِ {برنامه} تمدید شد و تا {روز-مانده} روزِ دیگر فعال است. سپاس از همراهی‌تان.',
    channels: ['inapp', 'email'] },
  { key: 'discount', title: 'کدِ تخفیف برای شما: {کد-تخفیف}',
    body: '{نام} عزیز، با کدِ {کد-تخفیف} می‌توانید اشتراکِ {برنامه} را با تخفیف تمدید کنید. کد را در بخشِ اشتراکِ برنامه بزنید.',
    channels: ['inapp', 'email'] },
  { key: 'update', title: 'نسخهٔ تازهٔ {برنامه} آماده است',
    body: 'نسخهٔ تازهٔ {برنامه} منتشر شد. برنامه خودش به‌روز می‌شود؛ اگر نشد، از پورتال آخرین نسخه را بگیرید.',
    channels: ['inapp', 'push'] },
  { key: 'suspended', title: 'اشتراکِ {برنامه} معلق شد',
    body: '{نام} عزیز، اشتراکِ شما در بخشِ {برنامه} معلق شده است. برای ادامه با پشتیبانی تماس بگیرید.',
    channels: ['inapp', 'email'] },
  { key: 'personal', title: 'پیامی از پشتیبانی',
    body: 'سلام {نام}،\n\n',
    channels: ['inapp', 'email'] },
];

async function seedTemplates() {
  const t = now();
  for (const tpl of TEMPLATES) {
    await query(
      `INSERT INTO notice_templates (key, app, title, body, channels, editable, updated_at)
       VALUES ($1,'both',$2,$3,$4::jsonb,true,$5) ON CONFLICT (key, app) DO NOTHING`,
      [tpl.key, tpl.title, tpl.body, JSON.stringify(tpl.channels), t]
    );
  }
}

/** قالب برای یک بخش — اولِ همان بخش، وگرنه «both». */
async function templateFor(key, app = 'shop') {
  const rows = await many(
    `SELECT * FROM notice_templates WHERE key=$1 AND app IN ($2,'both')
      ORDER BY (app = $2) DESC LIMIT 1`,
    [String(key || ''), app === 'pump' ? 'pump' : 'shop']
  );
  return rows[0] || null;
}

async function listTemplates() {
  return many('SELECT * FROM notice_templates ORDER BY key, app');
}

async function saveTemplate(key, app, patch = {}) {
  const k = String(key || '').trim().toLowerCase().replace(/[^a-z0-9_-]/g, '');
  if (!k) throw badRequest('کلیدِ قالب لازم است', 'bad_key');
  const a = ['shop', 'pump', 'both'].includes(app) ? app : 'both';
  const cur = await one('SELECT * FROM notice_templates WHERE key=$1 AND app=$2', [k, a]);
  const channels = cleanChannels(patch.channels, cur ? cur.channels : ['inapp', 'email']);
  return one(
    `INSERT INTO notice_templates (key, app, title, body, channels, editable, updated_at)
     VALUES ($1,$2,$3,$4,$5::jsonb,true,$6)
     ON CONFLICT (key, app) DO UPDATE SET title=excluded.title, body=excluded.body,
       channels=excluded.channels, updated_at=excluded.updated_at
     RETURNING *`,
    [k, a,
      patch.title === undefined ? (cur ? cur.title : '') : String(patch.title).slice(0, 200),
      patch.body === undefined ? (cur ? cur.body : '') : String(patch.body).slice(0, 8000),
      JSON.stringify(channels), now()]
  );
}

function cleanChannels(input, def = ['inapp']) {
  if (!Array.isArray(input)) return def;
  const out = input.filter(c => CHANNELS.includes(c));
  return out.length ? [...new Set(out)] : def;
}

/* ==========================================================
   متغیرها
   ========================================================== */

/** نامِ فارسیِ متغیرها و هم‌معنی‌های انگلیسی‌شان. */
const VAR_ALIASES = {
  name: 'نام', app: 'برنامه', days: 'روز-مانده', price: 'قیمت', code: 'کد-تخفیف',
  shop: 'دکان', station: 'پمپ', plan: 'پلن',
};

/**
 * پر کردنِ `{متغیر}`ها در یک متن.
 *
 * متغیرِ ناشناخته دست‌نخورده می‌ماند تا مدیر در پیش‌نمایش ببیندش، نه
 * این‌که بی‌صدا خالی شود.
 */
function render(text, vars = {}) {
  return String(text || '').replace(/\{([^{}]{1,40})\}/g, (m, key) => {
    const k = key.trim();
    const fa = VAR_ALIASES[k] || k;
    const value = vars[fa] !== undefined ? vars[fa] : vars[k];
    return value === undefined || value === null ? m : String(value);
  });
}

const CURRENCY_LABEL = { AFN: 'افغانی', USD: 'دالر' };

/** قیمتِ تمدید برای یک گیرنده — اول از خودِ اشتراک، وگرنه از پلنِ روز. */
async function priceOf(recipient) {
  if (recipient.price !== null && recipient.price !== undefined && Number(recipient.price) > 0) {
    return `${Number(recipient.price).toLocaleString('fa-AF')} ${CURRENCY_LABEL[recipient.currency] || recipient.currency || ''}`.trim();
  }
  const list = await plans.listPlans({ app: recipient.app });
  const p = list.find(x => x.code === recipient.plan) || list[0];
  if (!p) return '';
  const cfg = await plans.allConfig();
  const unit = recipient.app === 'pump' ? (cfg.pump_currency || 'دالر') : (cfg.currency || 'افغانی');
  return `${Number(p.price).toLocaleString('fa-AF')} ${unit}`;
}

async function variablesFor(recipient, notice = {}) {
  const fixed = notice.variables && typeof notice.variables === 'object' ? notice.variables : {};
  return {
    'نام': recipient.name || recipient.tenantName || 'مشتری',
    'برنامه': APP_LABEL[recipient.app] || recipient.app,
    //  ⚠️ رقمِ فارسی، نه لاتین: متنِ کاربر همه‌جا فارسی است و یک «45»ِ
    //  لاتین وسطِ جمله بدقواره می‌افتد.
    'روز-مانده': recipient.daysLeft === null || recipient.daysLeft === undefined
      ? '—' : Math.max(0, recipient.daysLeft).toLocaleString('fa-AF'),
    'قیمت': await priceOf(recipient),
    //  کمپینِ «both» برای هر بخش کدِ خودش را دارد (`codes: {shop, pump}`)
    'کد-تخفیف': (fixed.codes && fixed.codes[recipient.app]) || fixed['کد-تخفیف'] || fixed.code || '',
    'دکان': recipient.tenantName || '',
    'پمپ': recipient.tenantName || '',
    'پلن': recipient.planTitle || recipient.plan || '',
    ...Object.fromEntries(Object.entries(fixed).filter(([k]) => k !== 'codes' && k !== 'کد-تخفیف')),
  };
}

/* ==========================================================
   قالبِ ایمیل — راست‌به‌چپ، با نامِ VILL3N
   ========================================================== */

function escapeHtml(s) {
  return String(s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

/**
 * عمداً بی‌تصویرِ بیرونی: بیشترِ برنامه‌های ایمیل تصویر را نمی‌آورند.
 * «لوگو» یک کادرِ متنی است که جایش با تنظیمِ `brand_logo_url` (اختیاری)
 * می‌تواند تصویر بگیرد.
 */
function emailHtml({ title, body, brand = 'VILL3N', logoUrl = '', footer = '' }) {
  const paragraphs = String(body || '').split(/\n{2,}/).map(p =>
    `<p style="margin:0 0 12px;font-size:15px;line-height:2;color:#334155">${escapeHtml(p).replace(/\n/g, '<br>')}</p>`
  ).join('');
  const logo = logoUrl
    ? `<img src="${escapeHtml(logoUrl)}" alt="${escapeHtml(brand)}" style="height:40px;border:0">`
    : `<div style="display:inline-block;font:900 22px/1 Arial,Tahoma,sans-serif;letter-spacing:3px;color:#fff;
                 background:linear-gradient(135deg,#2c5ce6,#7b3fe4);padding:10px 18px;border-radius:12px">${escapeHtml(brand)}</div>`;
  return `<!DOCTYPE html><html lang="fa" dir="rtl"><body style="margin:0;background:#f4f6fb">
<div dir="rtl" style="background:#f4f6fb;padding:28px 12px;font-family:Vazirmatn,Tahoma,Arial,sans-serif">
  <div style="max-width:560px;margin:0 auto">
    <div style="text-align:center;margin-bottom:14px">${logo}</div>
    <div style="background:#fff;border-radius:18px;border:1px solid #e2e8f0;padding:28px 26px;color:#0f172a">
      <h1 style="margin:0 0 14px;font-size:20px;line-height:1.7">${escapeHtml(title)}</h1>
      ${paragraphs}
    </div>
    <p style="margin:16px 0 0;text-align:center;font-size:12px;color:#94a3b8;line-height:2">
      ${escapeHtml(footer || `این ایمیل از سامانهٔ ${brand} فرستاده شده است.`)}
    </p>
  </div>
</div></body></html>`;
}

/* ==========================================================
   گیرنده‌ها
   ==========================================================
   یک شکل برای هر دو بخش:
     { app, userId, tenantId, tenantKind, name, tenantName, email, phone,
       city, plan, planTitle, status, daysLeft, endsAt, price, currency,
       permanent, subscriptionId }
*/

const PERMANENT_YEARS = 10;

function isPermanent(row, at) {
  if (!row.sub_id) return false;
  if (row.plan === 'perm' || row.plan === 'permanent') return true;
  return Number(row.ends_at) - at > PERMANENT_YEARS * 365 * DAY;
}

async function ownersOf(app) {
  const T = tenancy.byApp(app);
  const rows = await many(
    `SELECT s.id AS tenant_id, s.name AS tenant_name, s.owner_user_id, s.created_at AS tenant_created,
            u.name AS user_name, u.email, u.phone, u.city,
            (SELECT dl.label FROM device_locations dl WHERE dl.user_id = u.id
              ORDER BY dl.created_at DESC LIMIT 1) AS loc_label,
            sub.id AS sub_id, sub.plan, sub.status, sub.starts_at, sub.ends_at, sub.grace_days,
            sub.price, sub.currency
       FROM ${T.tenantTable} s
       LEFT JOIN users u ON u.id = s.owner_user_id
       LEFT JOIN LATERAL (
         SELECT * FROM ${T.subsTable} x WHERE x.${T.tenantKey} = s.id
          ORDER BY (x.status IN ('active','suspended','pending')) DESC, x.ends_at DESC, x.created_at DESC
          LIMIT 1
       ) sub ON true
      WHERE s.status = 'active'
      ORDER BY s.created_at DESC`
  );
  const at = now();
  const planList = await plans.listPlans({ activeOnly: false, app });
  const titles = Object.fromEntries(planList.map(p => [p.code, p.title]));
  return rows.map(r => {
    const state = r.sub_id ? subs.stateOf({ ...r, id: r.sub_id }, at) : null;
    return {
      app,
      userId: r.owner_user_id || '',
      tenantId: r.tenant_id,
      tenantKind: app === 'pump' ? 'station' : 'shop',
      name: r.user_name || '',
      tenantName: r.tenant_name || '',
      email: r.email || '',
      phone: r.phone || '',
      city: r.city || r.loc_label || '',
      plan: r.plan || '',
      planTitle: titles[r.plan] || '',
      status: state ? state.status : 'none',
      daysLeft: state ? Math.ceil((state.graceEndsAt - at) / DAY) : null,
      endsAt: state ? state.endsAt : 0,
      price: r.price === null || r.price === undefined ? null : Number(r.price),
      currency: r.currency || (app === 'pump' ? 'USD' : 'AFN'),
      permanent: isPermanent(r, at),
      subscriptionId: r.sub_id || '',
    };
  });
}

/**
 * گیرنده‌های یک اعلان.
 *
 * `audience.kind`:
 *   all     همهٔ مشتری‌های بخش(های) اعلان
 *   app     همان «all» — برای خوانایی در پنل
 *   filter  با فیلترها: city · plan · expiring_days · expired · permanent
 *   user    یک نفر: user_id (و/یا tenant_id)
 */
async function resolveAudience(app, audience = {}, { limit = 5000 } = {}) {
  const apps = app === 'both' ? ['shop', 'pump'] : [app === 'pump' ? 'pump' : 'shop'];
  const kind = String(audience.kind || 'all');
  let out = [];
  for (const a of apps) out = out.concat(await ownersOf(a));

  if (kind === 'user') {
    const uid = String(audience.user_id || audience.userId || '');
    const tid = String(audience.tenant_id || audience.tenantId || '');
    out = out.filter(r => (uid && r.userId === uid) || (tid && r.tenantId === tid));
    /*
     *  کاربری که هنوز دکان/پمپ ندارد هم می‌تواند گیرنده باشد — مثلاً
     *  «خوش‌آمد» درست بعد از ثبت‌نام. پس اگر در فهرستِ صاحب‌ها نبود،
     *  از خودِ `users` می‌آید.
     */
    if (!out.length && uid) {
      const u = await one('SELECT * FROM users WHERE id=$1', [uid]);
      if (u) {
        out = [{
          app: apps[0], userId: u.id, tenantId: '', tenantKind: apps[0] === 'pump' ? 'station' : 'shop',
          name: u.name || '', tenantName: '', email: u.email || '', phone: u.phone || '', city: u.city || '',
          plan: '', planTitle: '', status: 'none', daysLeft: null, endsAt: 0, price: null,
          currency: apps[0] === 'pump' ? 'USD' : 'AFN', permanent: false, subscriptionId: '',
        }];
      }
    }
  } else if (kind === 'filter') {
    const city = String(audience.city || '').trim().toLowerCase();
    const plan = String(audience.plan || '').trim();
    const expiringDays = audience.expiring_days === undefined || audience.expiring_days === null || audience.expiring_days === ''
      ? null : Number(audience.expiring_days);
    const expired = audience.expired === true || audience.expired === 'true' || audience.expired === 1;
    const permanent = audience.permanent === true || audience.permanent === 'true' || audience.permanent === 1;
    out = out.filter(r => {
      if (city && !String(r.city || '').toLowerCase().includes(city)) return false;
      if (plan && r.plan !== plan) return false;
      if (expiringDays !== null) {
        if (!r.subscriptionId || r.permanent) return false;
        if (r.daysLeft === null || r.daysLeft < 0 || r.daysLeft > expiringDays) return false;
        if (!['active', 'suspended'].includes(r.status)) return false;
      }
      if (expired && r.status !== 'expired') return false;
      if (permanent && !r.permanent) return false;
      return true;
    });
  }
  return out.slice(0, Math.max(1, Math.min(Number(limit) || 5000, 20000)));
}

/* ==========================================================
   خودِ اعلان — ساختن، خواندن، فهرست
   ========================================================== */

function shapeNotice(r) {
  if (!r) return null;
  return {
    id: r.id,
    app: r.app,
    audience: r.audience || { kind: 'all' },
    channels: Array.isArray(r.channels) ? r.channels : ['inapp'],
    title: r.title,
    body: r.body,
    templateKey: r.template_key || '',
    variables: r.variables || {},
    scheduleAt: r.schedule_at ? Number(r.schedule_at) : null,
    repeat: r.repeat,
    status: r.status,
    system: !!r.system,
    createdBy: r.created_by || '',
    createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at),
    sentAt: r.sent_at ? Number(r.sent_at) : null,
    runs: Number(r.runs || 0),
    counts: r.counts || {},
  };
}

function cleanAudience(input) {
  const a = input && typeof input === 'object' ? input : {};
  const kind = ['all', 'app', 'filter', 'user'].includes(a.kind) ? a.kind : 'all';
  const out = { kind };
  if (a.city) out.city = String(a.city).slice(0, 60);
  if (a.plan) out.plan = String(a.plan).slice(0, 20);
  if (a.expiring_days !== undefined && a.expiring_days !== null && a.expiring_days !== '') {
    out.expiring_days = Math.max(0, Math.min(365, Number(a.expiring_days) || 0));
  }
  if (a.expired) out.expired = true;
  if (a.permanent) out.permanent = true;
  if (a.user_id || a.userId) out.user_id = String(a.user_id || a.userId).slice(0, 80);
  if (a.tenant_id || a.tenantId) out.tenant_id = String(a.tenant_id || a.tenantId).slice(0, 80);
  return out;
}

async function create(input = {}, { createdBy = '', system = false } = {}) {
  const app = ['shop', 'pump', 'both'].includes(input.app) ? input.app : 'shop';
  const title = String(input.title || '').trim().slice(0, 200);
  const body = String(input.body || '').trim().slice(0, 8000);
  if (!title && !body) throw badRequest('عنوان یا متنِ اعلان لازم است', 'empty_notice');
  const scheduleAt = input.scheduleAt ? Number(input.scheduleAt) : (input.schedule_at ? Number(input.schedule_at) : null);
  const repeat = input.repeat === 'monthly' ? 'monthly' : 'none';
  const t = now();
  const status = scheduleAt ? 'scheduled' : 'draft';
  return one(
    `INSERT INTO notices (id, app, audience, channels, title, body, template_key, variables,
                          schedule_at, repeat, status, system, created_by, created_at, updated_at)
     VALUES ($1,$2,$3::jsonb,$4::jsonb,$5,$6,$7,$8::jsonb,$9,$10,$11,$12,$13,$14,$14) RETURNING *`,
    [newId('ntc'), app, JSON.stringify(cleanAudience(input.audience)),
      JSON.stringify(cleanChannels(input.channels)), title, body,
      String(input.templateKey || input.template_key || '').slice(0, 40),
      JSON.stringify(input.variables && typeof input.variables === 'object' ? input.variables : {}),
      scheduleAt, repeat, status, !!system, createdBy, t]
  );
}

async function get(id) {
  return one('SELECT * FROM notices WHERE id=$1', [String(id || '')]);
}

async function update(id, patch = {}) {
  const cur = await get(id);
  if (!cur) throw notFound('اعلان پیدا نشد', 'notice_not_found');
  if (cur.status === 'sending') throw badRequest('این اعلان در حالِ ارسال است', 'notice_busy');
  const scheduleAt = patch.scheduleAt !== undefined || patch.schedule_at !== undefined
    ? (patch.scheduleAt || patch.schedule_at ? Number(patch.scheduleAt || patch.schedule_at) : null)
    : (cur.schedule_at ? Number(cur.schedule_at) : null);
  const repeat = patch.repeat === undefined ? cur.repeat : (patch.repeat === 'monthly' ? 'monthly' : 'none');
  //  زمان‌بندی تازه ⇒ دوباره «زمان‌بندی‌شده»؛ برداشتنش ⇒ پیش‌نویس
  let status = cur.status;
  if (patch.scheduleAt !== undefined || patch.schedule_at !== undefined) {
    status = scheduleAt ? 'scheduled' : (cur.status === 'sent' ? 'sent' : 'draft');
  }
  return one(
    `UPDATE notices SET app=$2, audience=$3::jsonb, channels=$4::jsonb, title=$5, body=$6,
            variables=$7::jsonb, schedule_at=$8, repeat=$9, status=$10, template_key=$11, updated_at=$12
      WHERE id=$1 RETURNING *`,
    [cur.id,
      patch.app !== undefined && ['shop', 'pump', 'both'].includes(patch.app) ? patch.app : cur.app,
      JSON.stringify(patch.audience !== undefined ? cleanAudience(patch.audience) : cur.audience),
      JSON.stringify(patch.channels !== undefined ? cleanChannels(patch.channels) : cur.channels),
      patch.title === undefined ? cur.title : String(patch.title).trim().slice(0, 200),
      patch.body === undefined ? cur.body : String(patch.body).trim().slice(0, 8000),
      JSON.stringify(patch.variables !== undefined && patch.variables && typeof patch.variables === 'object' ? patch.variables : cur.variables),
      scheduleAt, repeat, status,
      patch.templateKey === undefined ? cur.template_key : String(patch.templateKey).slice(0, 40),
      now()]
  );
}

async function remove(id) {
  const row = await one('DELETE FROM notices WHERE id=$1 RETURNING *', [String(id || '')]);
  if (!row) throw notFound('اعلان پیدا نشد', 'notice_not_found');
  return row;
}

async function list({ status = '', app = '', system = null, limit = 100, offset = 0 } = {}) {
  return many(
    `SELECT * FROM notices
      WHERE ($1 = '' OR status = $1)
        AND ($2 = '' OR app = $2 OR app = 'both')
        AND ($3::boolean IS NULL OR system = $3::boolean)
      ORDER BY COALESCE(schedule_at, created_at) DESC, created_at DESC
      LIMIT $4 OFFSET $5`,
    [String(status || ''), String(app || ''), system, Math.min(Number(limit) || 100, 500), Number(offset) || 0]
  );
}

/* ==========================================================
   پیش‌نمایش
   ========================================================== */

async function preview(id, { limit = 20 } = {}) {
  const n = await get(id);
  if (!n) throw notFound('اعلان پیدا نشد', 'notice_not_found');
  const recipients = await resolveAudience(n.app, n.audience);
  const sample = [];
  for (const r of recipients.slice(0, limit)) {
    const vars = await variablesFor(r, n);
    sample.push({
      app: r.app, userId: r.userId, tenantId: r.tenantId, name: r.name, tenantName: r.tenantName,
      email: r.email, daysLeft: r.daysLeft, plan: r.plan,
      title: render(n.title, vars), body: render(n.body, vars),
    });
  }
  const first = sample[0];
  return {
    notice: shapeNotice(n),
    recipients: recipients.length,
    sample,
    emailHtml: first ? emailHtml({ title: first.title, body: first.body, ...(await brand()) }) : '',
  };
}

async function brand() {
  const cfg = await plans.allConfig();
  return { brand: cfg.brand_name || 'VILL3N', logoUrl: cfg.brand_logo_url || '' };
}

/* ==========================================================
   ارسال
   ========================================================== */

async function deliverOne(n, r, channel, { run, title, body, b }) {
  const t = now();
  const id = newId('ndv');
  const base = [id, n.id, r.app, r.userId || '', r.tenantId || '', channel, r.name || r.tenantName || '',
    channel === 'email' ? (r.email || '') : '', title, body, run, t];
  let status = 'queued';
  let error = '';
  let sentAt = null;

  if (channel === 'inapp') {
    if (!r.userId && !r.tenantId) { status = 'error'; error = 'no_target'; } else { status = 'sent'; sentAt = t; }
  } else if (channel === 'email') {
    if (!r.email) { status = 'error'; error = 'no_email'; } else {
      try {
        const out = await mailer.send({
          to: r.email, subject: title, text: body,
          html: emailHtml({ title, body, brand: b.brand, logoUrl: b.logoUrl }),
        });
        status = out && out.delivered ? 'sent' : 'error';
        sentAt = status === 'sent' ? now() : null;
        if (status === 'error') error = 'not_delivered';
      } catch (err) {
        status = 'error'; error = String(err.message || err).slice(0, 400);
      }
    }
  } else if (channel === 'push') {
    try {
      const out = await push.sendTo({
        userId: r.userId || '',
        shopId: r.tenantKind === 'shop' ? r.tenantId : '',
        stationId: r.tenantKind === 'station' ? r.tenantId : '',
        app: r.app,
      }, { title, body: body.slice(0, 180), data: { type: 'notice', noticeId: n.id }, channel: 'notice' });
      if (out && out.sent > 0) { status = 'sent'; sentAt = now(); } else {
        status = 'error'; error = out ? (out.error || out.skipped || 'not_sent') : 'not_sent';
      }
    } catch (err) {
      status = 'error'; error = String(err.message || err).slice(0, 400);
    }
  }

  await query(
    `INSERT INTO notice_deliveries (id, notice_id, app, user_id, tenant_id, channel, who, address,
                                    title, body, run, created_at, status, error, sent_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15)`,
    [...base, status, error, sentAt]
  );
  return status;
}

/**
 * فرستادنِ یک اعلان، همین حالا.
 *
 * برای هر گیرنده در هر کانال یک ردیف در `notice_deliveries` می‌نشیند و
 * نتیجه‌اش همان چیزی است که واقعاً شد — نه «فرستاده شد» به حدس.
 * ⚠️ نرسیدن به یکی جلوی بقیه را نمی‌گیرد.
 */
async function send(id, { by = '' } = {}) {
  const n = await get(id);
  if (!n) throw notFound('اعلان پیدا نشد', 'notice_not_found');
  if (n.status === 'sending') throw badRequest('این اعلان در حالِ ارسال است', 'notice_busy');
  await query(`UPDATE notices SET status='sending', updated_at=$2 WHERE id=$1`, [n.id, now()]);

  const run = Number(n.runs || 0) + 1;
  const b = await brand();
  const channels = Array.isArray(n.channels) && n.channels.length ? n.channels : ['inapp'];
  const counts = { recipients: 0, sent: 0, error: 0, delivered: 0, read: 0, byChannel: {} };
  let recipients = [];
  try {
    recipients = await resolveAudience(n.app, n.audience);
    counts.recipients = recipients.length;
    for (const r of recipients) {
      const vars = await variablesFor(r, n);
      const title = render(n.title, vars);
      const body = render(n.body, vars);
      for (const channel of channels) {
        const status = await deliverOne(n, r, channel, { run, title, body, b });
        counts.byChannel[channel] = counts.byChannel[channel] || { sent: 0, error: 0 };
        if (status === 'error') { counts.error++; counts.byChannel[channel].error++; } else { counts.sent++; counts.byChannel[channel].sent++; }
      }
    }
  } catch (err) {
    await query(`UPDATE notices SET status='failed', counts=$2::jsonb, updated_at=$3 WHERE id=$1`,
      [n.id, JSON.stringify({ ...counts, failure: String(err.message || err).slice(0, 300) }), now()]);
    throw err;
  }

  const t = now();
  const failed = counts.recipients > 0 && counts.sent === 0;
  let status = failed ? 'failed' : 'sent';
  let scheduleAt = n.schedule_at ? Number(n.schedule_at) : null;
  //  تکرارِ ماهانه: همان ردیف، یک ماه جلوتر، دوباره در صف
  if (n.repeat === 'monthly' && !failed) {
    const base = scheduleAt && scheduleAt > t - 31 * DAY ? scheduleAt : t;
    scheduleAt = plans.endOfPeriod(base, 1, 'month');
    status = 'scheduled';
  }
  await query(
    `UPDATE notices SET status=$2, sent_at=$3, runs=$4, counts=$5::jsonb, schedule_at=$6, updated_at=$3 WHERE id=$1`,
    [n.id, status, t, run, JSON.stringify(counts), scheduleAt]
  );
  try {
    await require('./audit').log({ actorType: by ? 'admin' : 'system', userId: by, action: 'notice.sent', targetType: 'notice', targetId: n.id, detail: counts });
  } catch { /* سابقه هیچ‌وقت جلوی ارسال را نمی‌گیرد */ }
  return { ...counts, status, run, scheduleAt };
}

/** ارسالِ آزمایشی به یک نشانی — هیچ ردیفی در گزارش نمی‌نشیند. */
async function sendTest(id, to) {
  const n = await get(id);
  if (!n) throw notFound('اعلان پیدا نشد', 'notice_not_found');
  const address = String(to || '').trim();
  if (!address.includes('@')) throw badRequest('نشانی ایمیل درست نیست', 'bad_email');
  const b = await brand();
  const sample = {
    app: n.app === 'pump' ? 'pump' : 'shop', name: 'نامِ مشتری', tenantName: 'دکانِ نمونه',
    daysLeft: 7, plan: '', price: null, currency: n.app === 'pump' ? 'USD' : 'AFN',
  };
  const vars = await variablesFor(sample, n);
  const title = render(n.title, vars);
  const body = render(n.body, vars);
  const out = await mailer.send({ to: address, subject: `[آزمایشی] ${title}`, text: body,
    html: emailHtml({ title, body, brand: b.brand, logoUrl: b.logoUrl }) });
  return { ok: true, via: out.via, title, body };
}

/* ==========================================================
   گزارش
   ========================================================== */

function shapeDelivery(r) {
  return {
    id: r.id, noticeId: r.notice_id, app: r.app, userId: r.user_id, tenantId: r.tenant_id,
    channel: r.channel, status: r.status, who: r.who, address: r.address,
    title: r.title, body: r.body, error: r.error, run: Number(r.run),
    createdAt: Number(r.created_at),
    sentAt: r.sent_at ? Number(r.sent_at) : null,
    deliveredAt: r.delivered_at ? Number(r.delivered_at) : null,
    readAt: r.read_at ? Number(r.read_at) : null,
  };
}

async function report(id, { limit = 500 } = {}) {
  const n = await get(id);
  if (!n) throw notFound('اعلان پیدا نشد', 'notice_not_found');
  const rows = await many(
    `SELECT * FROM notice_deliveries WHERE notice_id=$1 ORDER BY run DESC, created_at ASC LIMIT $2`,
    [n.id, Math.min(Number(limit) || 500, 5000)]
  );
  const summary = { total: rows.length, queued: 0, sent: 0, delivered: 0, read: 0, error: 0 };
  for (const r of rows) summary[r.status] = (summary[r.status] || 0) + 1;
  return { notice: shapeNotice(n), summary, deliveries: rows.map(shapeDelivery) };
}

/* ==========================================================
   سمتِ برنامه — «اعلان‌های من»
   ========================================================== */

/**
 * اعلان‌های داخلِ برنامه برای یک کاربر/دکان/پمپ.
 * خواندنِ فهرست یعنی «تحویل شد» (اگر هنوز فقط فرستاده شده بود).
 */
async function inboxFor({ app, userId = '', tenantId = '', limit = 50 }) {
  const a = app === 'pump' ? 'pump' : 'shop';
  const rows = await many(
    `SELECT * FROM notice_deliveries
      WHERE app=$1 AND channel='inapp'
        AND (($2 <> '' AND user_id=$2) OR ($3 <> '' AND tenant_id=$3))
      ORDER BY created_at DESC LIMIT $4`,
    [a, userId, tenantId, Math.min(Number(limit) || 50, 200)]
  );
  const t = now();
  const fresh = rows.filter(r => r.status === 'sent' || r.status === 'queued').map(r => r.id);
  if (fresh.length) {
    await query(
      `UPDATE notice_deliveries SET status='delivered', delivered_at=$2 WHERE id = ANY($1::text[])`,
      [fresh, t]
    );
    for (const r of rows) if (fresh.includes(r.id)) { r.status = 'delivered'; r.delivered_at = t; }
  }
  const unread = rows.filter(r => r.status !== 'read').length;
  return { unread, notices: rows.map(shapeDelivery) };
}

async function markRead({ id, app, userId = '', tenantId = '' }) {
  const a = app === 'pump' ? 'pump' : 'shop';
  const row = await one(
    `UPDATE notice_deliveries SET status='read', read_at=COALESCE(read_at,$5)
      WHERE id=$1 AND app=$2 AND channel='inapp'
        AND (($3 <> '' AND user_id=$3) OR ($4 <> '' AND tenant_id=$4))
      RETURNING *`,
    [String(id || ''), a, userId, tenantId, now()]
  );
  if (!row) throw notFound('اعلان پیدا نشد', 'notice_not_found');
  return shapeDelivery(row);
}

/* ==========================================================
   اعلان‌های خودکارِ سامانه
   ==========================================================
   همان قالب‌های قابلِ ویرایش، همان جدولِ تحویل. `system=true` تا در
   فهرستِ پنل از اعلان‌های دستی جدا دیده شوند.
*/

async function systemNotice(key, recipient, { variables = {}, channels = null } = {}) {
  const tpl = await templateFor(key, recipient.app);
  if (!tpl) return null;
  const n = await create({
    app: recipient.app,
    audience: { kind: 'user', user_id: recipient.userId || '', tenant_id: recipient.tenantId || '' },
    channels: channels || tpl.channels,
    title: tpl.title, body: tpl.body, templateKey: key, variables,
  }, { createdBy: 'system', system: true });
  const out = await send(n.id);
  return { notice: n, ...out };
}

/** گیرنده از روی یک ردیفِ اشتراک — برای هوک‌ها. */
async function recipientOfSubscription(app, sub) {
  const T = tenancy.byApp(app);
  const all = await ownersOf(app);
  return all.find(r => r.tenantId === sub[T.tenantKey]) || null;
}

/**
 * هوکِ تغییرِ اشتراک — از `subscriptions.js` صدا زده می‌شود.
 * هیچ‌وقت خطا بیرون نمی‌دهد: خبر رفاه است، اشتراک اصل.
 */
async function onSubscription({ app, action, row }) {
  try {
    const key = action === 'grant' || action === 'renew' ? 'renewed'
      : action === 'suspended' ? 'suspended' : '';
    if (!key) return null;
    const r = await recipientOfSubscription(app, row);
    if (!r) return null;
    return await systemNotice(key, r);
  } catch (err) {
    console.error('[notices:hook]', err.message);
    return null;
  }
}

/**
 * بررسیِ روزانه: ۷ · ۳ · ۱ روز مانده، و منقضی.
 * برای هر اشتراک و هر آستانه فقط یک بار (کلید در app_config).
 */
async function checkSystem({ thresholds = [7, 3, 1] } = {}) {
  const out = { expiring: 0, expired: 0, checked: 0 };
  for (const app of ['shop', 'pump']) {
    const ledger = subs.forApp(app);
    await ledger.expireDue();
    const owners = await ownersOf(app);
    for (const r of owners) {
      if (!r.subscriptionId || r.permanent) continue;
      out.checked++;
      if (['active', 'suspended'].includes(r.status) && r.daysLeft !== null && r.daysLeft >= 0) {
        const hit = thresholds.filter(d => r.daysLeft <= d).sort((a, b) => a - b)[0];
        if (hit === undefined) continue;
        const key = `sysnotice_${r.subscriptionId}_${hit}`;
        if (await plans.getConfig(key, '')) continue;
        await systemNotice('expiring', r);
        await plans.setConfig(key, String(now()));
        out.expiring++;
      } else if (r.status === 'expired' && r.daysLeft !== null && r.daysLeft > -30) {
        const key = `sysnotice_${r.subscriptionId}_expired`;
        if (await plans.getConfig(key, '')) continue;
        await systemNotice('expired', r);
        await plans.setConfig(key, String(now()));
        out.expired++;
      }
    }
  }
  return out;
}

/* ==========================================================
   زمان‌بند
   ========================================================== */

/** اعلان‌های زمان‌بندی‌شده‌ای که وقتشان رسیده — و بررسیِ روزانه. */
async function tick({ at = now(), daily = true } = {}) {
  const due = await many(
    `SELECT id FROM notices WHERE status='scheduled' AND schedule_at IS NOT NULL AND schedule_at <= $1
      ORDER BY schedule_at ASC LIMIT 50`,
    [at]
  );
  const out = { sent: [], system: null };
  for (const row of due) {
    try { out.sent.push({ id: row.id, ...(await send(row.id)) }); }
    catch (err) { console.error('[notices:tick]', err.message); }
  }
  if (daily) {
    const last = Number(await plans.getConfig('notices_system_checked_at', '0')) || 0;
    if (at - last >= DAY) {
      out.system = await checkSystem();
      await plans.setConfig('notices_system_checked_at', String(at));
    }
  }
  return out;
}

let timer = null;
function startRunner({ intervalMs = 60_000 } = {}) {
  if (timer) return timer;
  timer = setInterval(() => {
    tick().catch(err => console.error('[notices:runner]', err.message));
  }, intervalMs);
  if (timer.unref) timer.unref();
  return timer;
}
function stopRunner() {
  if (timer) clearInterval(timer);
  timer = null;
}

module.exports = {
  TEMPLATES, CHANNELS, APP_LABEL, seedTemplates, templateFor, listTemplates, saveTemplate,
  render, variablesFor, emailHtml, resolveAudience, ownersOf,
  create, get, update, remove, list, shapeNotice, preview, send, sendTest, report, shapeDelivery,
  inboxFor, markRead, systemNotice, onSubscription, checkSystem, tick, startRunner, stopRunner,
};
