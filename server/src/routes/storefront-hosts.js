'use strict';
/**
 * نشانی‌های دیگرِ ویترینِ دکان — `/api/shop/storefront/hosts` (`lib/storefront-hosts.js`، docs/SERVERS-fa.md §۳).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچم‌های ویترین (`shop.orders` + `shop.storefront`) و `shop.storefront.host_alias` ⇒ `GET /`
 * فقط `{enabled:false}` و `PUT /` ۴۰۳ (`storefront_disabled` / `storefront_host_alias_disabled`). سرورِ بی
 * `STOREFRONT_ALIAS_DOMAINS` ⇒ `PUT /` ۴۰۳ ِ `host_alias_unavailable` (گرداننده هنوز دامنه‌ای وصل نکرده).
 * ⛔ نوشتن کلیدهای اشتراکِ ویترین را می‌خواهد (SUB-10: دستهٔ PAID).
 * ⛔ فقط صاحبِ دکان (`owner_only`) — نشانیِ عمومیِ دکان همان «سرورها» ِ برنامه است: فقط صاحب. shop_id فقط از توکن.
 * سقف: سطلِ IP پیش از احراز، و ۲۰ نوشتن در ساعت برای هر دکان.
 *
 *   GET /   `{enabled, hosts:[{host, createdAt}], domains, max, permissions:{manage}}`
 *   PUT /   `{hosts: ["shop.example.af", …]}` ⇒ همان شکلِ GET به‌علاوهٔ `added` و `removed`
 */
const express = require('express');
const config = require('../config');
const sf = require('../lib/storefront');
const hosts = require('../lib/storefront-hosts');
const audit = require('../lib/audit');
const { flagOf } = require('../lib/orders');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-hosts-ip' }));
router.use(requireUser, requireShop);
const shopLimit = rateLimit({ max: 20, windowMs: 60 * 60 * 1000, keyPrefix: 'store-hosts', key: (req) => (req.shopId ? `shop:${req.shopId}` : '') });

async function stateOf(shopId) {
  const [st, flag] = await Promise.all([sf.stateOf(shopId), flagOf(shopId, hosts.FLAG)]);
  return { st, flag: st.flag && flag };
}

const needFlag = async (req, res, next) => {
  const { st, flag } = await stateOf(req.shopId);
  if (!st.flag) return next(forbidden('ویترین برای این دکان روشن نیست', 'storefront_disabled'));
  if (!flag) return next(forbidden('نشانیِ دیگرِ ویترین برای این دکان روشن نیست', 'storefront_host_alias_disabled'));
  req.storeState = st;
  next();
};

const needWrite = (req, res, next) => {
  const { entitled, ent } = req.storeState;
  if (entitled) return next();
  return next(ent.trial.used && !ent.trial.active
    ? forbidden('اشتراکِ این دکان به پایان رسیده است.', 'subscription_expired')
    : forbidden('این قابلیت نیازمندِ اشتراک است', 'subscription_required'));
};

const view = async (req) => ({
  enabled: true,
  hosts: await hosts.list(req.shopId),
  domains: hosts.aliasDomains(),
  max: hosts.MAX_PER_SHOP,
  permissions: { manage: req.role === 'owner' },
});

router.get('/', requirePermission('data.read'), async (req, res) => {
  const { flag } = await stateOf(req.shopId);
  res.set('Cache-Control', 'no-store');
  if (!flag) return res.json({ enabled: false });
  res.json(await view(req));
});

router.put('/', shopLimit, needFlag, needWrite, async (req, res) => {
  if (req.role !== 'owner') throw forbidden('نشانیِ ویترین فقط کارِ صاحبِ دکان است', 'owner_only');
  if (!hosts.enabled()) throw forbidden('گردانندهٔ سرور هنوز دامنه‌ای برای ویترین‌ها وصل نکرده است', 'host_alias_unavailable');
  const b = req.body && typeof req.body === 'object' ? req.body : {};
  const out = await hosts.replace(req.shopId, b.hosts, { userId: req.user.id });
  if (out.added.length || out.removed.length) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'storefront.hosts',
      targetType: 'storefront', targetId: req.shopId,
      detail: { added: out.added, removed: out.removed }, ip: clientIp(req),
    });
  }
  res.set('Cache-Control', 'no-store').json({ ...(await view(req)), added: out.added, removed: out.removed });
});

module.exports = router;
