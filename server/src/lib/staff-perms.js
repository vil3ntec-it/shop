'use strict';
/**
 * مجوزهای سفارشیِ هر دکان — STF-01 تا STF-03 (بخشِ ۱۰.۱).
 *
 * سنجش با هستهٔ مشترک (`perm-core.js`، همان که برنامه‌ها می‌دوانند).
 * این‌جا فقط خواندن و نوشتنِ دو لایه است:
 *   ۱) لایهٔ نقش در این دکان   (`shop_role_perms`، مهاجرتِ ۰۷۲)
 *   ۲) لایهٔ همین کاربر          (`shop_member_perms`، مهاجرتِ ۰۷۳)
 *
 * ── قاعدهٔ «هیچ‌کس بیش از امروز نمی‌گیرد» ─────────────────────────────
 * `requirePermission` اول همان `can(role)`ِ امروز را می‌سنجد و فقط بعد از
 * آن لایه‌ها را؛ پس لایه فقط می‌تواند ببندد. تنها کلیدِ «full»
 * (`reports.money`) نمایشی است و از هیچ مسیرِ سرور نمی‌گذرد.
 *
 * ── کدام کلید قابل‌ویرایش است ───────────────────────────────────────
 * فقط کلیدی که سرور واقعاً اجرایش می‌کند (پنهان کردنِ دکمه امنیت نیست،
 * بخشِ ۲۹.۲ بندِ ۱۱). تخفیف، لغو و برگشت (`sales.discount`، `sales.cancel`،
 * `returns.create`) با op می‌آیند؛ از ۱۴۰۵/۰۷/۲۷ هر دو راه می‌سنجندشان:
 * Sync v3 در `authorize`ِ `sync-v3.js` و Sync v1 در `v1Guard`
 * (`staff-approval.js`، پیش از `sync-v1.js`ِ مشترک با پمپ که دست نمی‌خورد).
 * «نیازمند تأیید» فقط با «برگهٔ امضاشده»ی بخشِ ۱۰.۱ پذیرفته می‌شود.
 * کلیدِ تازه‌ای که هنوز اجرا نمی‌شود رد می‌شود: `not_enforceable_yet`.
 */
const core = require('./perm-core');
const { can, permissionsOf, STAFF_ROLES } = require('./permissions');
const { one, many, query, now } = require('../db');
const { badRequest, notFound } = require('../middleware/errors');

/** کلیدهایی که سرور امروز واقعاً اجرا می‌کند (یا فقط نمایشی‌اند) */
const SERVER_EDITABLE = Object.freeze([
  'audit.view', 'reports.money', 'settings.write',
  //  اجرا در همگام‌سازی، با سقف و برگهٔ امضاشده — `staff-approval.js`
  'sales.discount', 'sales.cancel', 'returns.create',
  //  اجرا روی opِ گاوصندوق در v1 و v3 — `safe-ledger.js`
  'safe.move',
  //  فقط نمایشی، مثلِ reports.money: گزارشِ X و Z ِ صندوقِ شیفت در برنامه (PAY-15، `ShopRole.allows`)
  'shift.report',
]);
//  همهٔ نقش‌های کارمند، از جمله چهار نقشِ تازهٔ A16 (مهاجرتِ ۱۲۳)
const EDITABLE_ROLES = STAFF_ROLES;

const MESSAGES = {
  unknown_permission: 'این مجوز شناخته نیست',
  unknown_role: 'این نقش قابل تنظیم نیست',
  owner_fixed: 'صاحب دکان همیشه همه‌کاره است و محدود نمی‌شود',
  not_editable: 'این مجوز قابل تغییر نیست',
  not_enforceable_yet: 'این مجوز هنوز در سرور قابل اجرا نیست؛ تا آن روز تغییرش فقط ظاهری بود و پذیرفته نمی‌شود',
  bad_override: 'قالبِ مجوز درست نیست',
  bad_mode: 'حالتِ مجوز باید «مجاز»، «ممنوع» یا «نیازمند تأیید» باشد',
  not_approvable: 'این مجوز «نیازمند تأیید» ندارد',
  exceeds_role: 'این مجوز بیش از حدِ همین نقش است',
  bad_cap: 'سقف باید عددی نامنفی باشد (درصد حداکثر ۱۰۰)',
  unknown_cap: 'این سقف برای این مجوز تعریف نشده است',
};

function parsePerms(v) {
  if (!v) return {};
  if (typeof v === 'string') { try { return JSON.parse(v) || {}; } catch { return {}; } }
  return typeof v === 'object' ? v : {};
}

/**
 * دو لایهٔ یک عضو: [نقش، کاربر] — هر کدام {} اگر نبود.
 * `run` = اتصالِ تراکنشِ صدازننده (`(sql, params) ⇒ {rows}`، مثلِ `orders.flagOf`)؛ نیامده ⇒ استخر.
 */
async function layersFor(shopId, role, userId, run = null) {
  if (!shopId || role === 'owner') return [{}, {}];
  const sql = `SELECT 'role' AS scope, perms FROM shop_role_perms WHERE shop_id=$1 AND role=$2
     UNION ALL
     SELECT 'member' AS scope, perms FROM shop_member_perms WHERE shop_id=$1 AND user_id=$3`;
  const params = [shopId, String(role || ''), String(userId || '')];
  const rows = run ? (await run(sql, params)).rows : await many(sql, params);
  const out = [{}, {}];
  for (const r of rows) out[r.scope === 'role' ? 0 : 1] = parsePerms(r.perms);
  return out;
}

/** لایه‌های عضوِ همین درخواست — یک بار برای هر درخواست */
async function layersOfReq(req) {
  if (!req._permLayers) {
    req._permLayers = await layersFor(req.shopId || req.member?.shop_id, req.member?.role, req.member?.user_id || req.user?.id);
  }
  return req._permLayers;
}

/** آیا این کلید در سرور لایه‌پذیر است (و پس باید از DB خوانده شود) */
function isLayered(key) {
  return SERVER_EDITABLE.includes(key) && Boolean(core.CATALOG[key]);
}

/** سنجشِ نهایی برای `requirePermission` — سقفِ نقش همیشه اول */
async function memberAllows(req, key) {
  const role = req.member?.role;
  if (!can(role, key)) return false;
  if (!isLayered(key)) return true;
  return core.allows(role, await layersOfReq(req), key);
}

/** فهرستِ قدیمیِ `permissions` — با لایه‌ها، ولی هرگز بیش از نقش */
function legacyPermissions(role, layers) {
  return permissionsOf(role).filter((k) => !isLayered(k) || core.allows(role, layers, k));
}

/** آنچه برنامه برای چیدنِ دکمه‌ها لازم دارد */
function policyPayload(role, layers) {
  return {
    v: core.VERSION,
    role: role || '',
    layers: [layers[0] || {}, layers[1] || {}],
    effective: core.effective(role, layers),
    editable: SERVER_EDITABLE.slice(),
  };
}

async function policyOf(shopId, member) {
  if (!member) return { permissions: [], policy: null };
  const layers = await layersFor(shopId, member.role, member.user_id);
  return { permissions: legacyPermissions(member.role, layers), policy: policyPayload(member.role, layers) };
}

/**
 * سنجشِ یک لایهٔ کامل پیش از ذخیره.
 * @returns {object} لایهٔ تمیز (فقط کلیدهای بامعنا)
 */
function cleanLayer(role, perms) {
  if (!perms || typeof perms !== 'object' || Array.isArray(perms)) {
    throw badRequest(MESSAGES.bad_override, 'bad_override');
  }
  const out = {};
  for (const key of Object.keys(perms)) {
    const o = perms[key];
    const code = core.validate(role, key, o);
    if (code) throw badRequest(`${MESSAGES[code] || MESSAGES.bad_override} (${key})`, code);
    if (!SERVER_EDITABLE.includes(key)) {
      throw badRequest(`${MESSAGES.not_enforceable_yet} (${key})`, 'not_enforceable_yet');
    }
    if (Object.keys(o).length) out[key] = o;
  }
  return out;
}

async function setRoleLayer(shopId, role, perms, byUserId) {
  if (!EDITABLE_ROLES.includes(role)) {
    throw badRequest(role === 'owner' ? MESSAGES.owner_fixed : MESSAGES.unknown_role, role === 'owner' ? 'owner_fixed' : 'unknown_role');
  }
  const layer = cleanLayer(role, perms);
  if (!Object.keys(layer).length) {
    await query('DELETE FROM shop_role_perms WHERE shop_id=$1 AND role=$2', [shopId, role]);
    return {};
  }
  await query(
    `INSERT INTO shop_role_perms (shop_id, role, perms, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5)
     ON CONFLICT (shop_id, role) DO UPDATE SET perms=excluded.perms, updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
    [shopId, role, JSON.stringify(layer), String(byUserId || ''), now()]
  );
  return layer;
}

/** عضوِ فعالِ همین دکان (شناسهٔ عضویت یا کاربر) — صاحب دکان نه */
async function editableMember(shopId, idOrUserId) {
  const m = await one(
    `SELECT * FROM shop_members WHERE shop_id=$2 AND (id=$1 OR user_id=$1) AND status <> 'removed'`,
    [String(idOrUserId || ''), shopId]
  );
  if (!m) throw notFound('این عضو در دکان شما نیست', 'member_not_found');
  if (m.role === 'owner') throw badRequest(MESSAGES.owner_fixed, 'owner_fixed');
  return m;
}

async function setMemberLayer(shopId, idOrUserId, perms, byUserId) {
  const m = await editableMember(shopId, idOrUserId);
  const layer = cleanLayer(m.role, perms);
  if (!Object.keys(layer).length) {
    await query('DELETE FROM shop_member_perms WHERE shop_id=$1 AND user_id=$2', [shopId, m.user_id]);
  } else {
    await query(
      `INSERT INTO shop_member_perms (shop_id, user_id, perms, updated_by, updated_at)
       VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (shop_id, user_id) DO UPDATE SET perms=excluded.perms, updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
      [shopId, m.user_id, JSON.stringify(layer), String(byUserId || ''), now()]
    );
  }
  return { member: m, layer };
}

/** همهٔ لایه‌ها و نتیجهٔ هر عضو — برای صفحهٔ مدیریتِ صاحب دکان و پنل */
async function overview(shopId) {
  const roleRows = await many('SELECT role, perms, updated_at FROM shop_role_perms WHERE shop_id=$1', [shopId]);
  const roles = {};
  for (const role of EDITABLE_ROLES) roles[role] = {};
  for (const r of roleRows) roles[r.role] = parsePerms(r.perms);

  const members = await many(
    `SELECT m.id, m.user_id, m.role, m.status, u.name, p.perms
       FROM shop_members m
       JOIN users u ON u.id = m.user_id
       LEFT JOIN shop_member_perms p ON p.shop_id = m.shop_id AND p.user_id = m.user_id
      WHERE m.shop_id=$1 AND m.status <> 'removed'
      ORDER BY (m.role = 'owner') DESC, m.created_at ASC`,
    [shopId]
  );
  return {
    v: core.VERSION,
    catalog: core.keys().map((k) => ({
      key: k,
      edit: SERVER_EDITABLE.includes(k) ? core.CATALOG[k].edit : 'none',
      caps: SERVER_EDITABLE.includes(k) ? core.CATALOG[k].caps.slice() : [],
      approvable: core.CATALOG[k].approvable,
      display: core.CATALOG[k].display,
      defaults: { ...core.CATALOG[k].defaults },
    })),
    roles,
    members: members.map((m) => {
      const layer = parsePerms(m.perms);
      const layers = [roles[m.role] || {}, layer];
      return {
        id: m.id, userId: m.user_id, name: m.name || '', role: m.role, status: m.status,
        layer, effective: core.effective(m.role, layers),
      };
    }),
  };
}

module.exports = {
  SERVER_EDITABLE, EDITABLE_ROLES, MESSAGES,
  layersFor, layersOfReq, isLayered, memberAllows, legacyPermissions, policyPayload, policyOf,
  cleanLayer, setRoleLayer, setMemberLayer, overview,
};
