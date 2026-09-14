'use strict';
/**
 * تصمیم نهایی درباره‌ی «این دکان (یا پمپ) الان به چه چیزی دسترسی دارد».
 *
 * ترتیب بررسی: اشتراک فعال ← دوره‌ی آزمایشی ← قابلیت‌های رایگان.
 * همه با ساعت سرور؛ ساعت گوشی هیچ نقشی ندارد.
 *
 * دو بخشِ سامانه همین یک منطق را دارند و فقط کاتالوگ و جدولشان فرق
 * می‌کند — `build(T)` همان را روی جدول‌های هر بخش می‌بندد. صادراتِ
 * پیش‌فرض بخشِ دکان است، پس `entitlementOf(shopId)` مثل قبل کار می‌کند.
 */
const { one, now } = require('../db');
const { catalogOf } = require('./features');
const subs = require('./subscriptions');
const plans = require('./plans');
const tenancy = require('./tenancy');

const DAY = 24 * 60 * 60 * 1000;

function uniq(arr) { return [...new Set(arr)]; }

function build(T) {
  const cat = catalogOf(T.app);
  const ledger = subs.forApp(T.app);
  //  دوره‌ی آزمایشی هر بخش کلید خودش را دارد، وگرنه عوض کردنِ آن برای
  //  دکان‌ها ناخواسته پمپ‌ها را هم عوض می‌کرد.
  const trialKey = T.app === 'shop' ? 'trial_days' : `${T.app}_trial_days`;

  async function trialState(tenant, at = now()) {
    const days = Number(await plans.getConfig(trialKey, '14')) || 0;
    if (!days) return { enabled: false, active: false, used: true, endsAt: 0, daysLeft: 0 };
    const startedAt = Number(tenant.created_at);
    const endsAt = startedAt + days * DAY;
    const active = at < endsAt;
    return {
      enabled: true,
      active,
      used: !active,
      startsAt: startedAt,
      endsAt,
      daysLeft: Math.max(0, Math.ceil((endsAt - at) / DAY)),
    };
  }

  /**
   * @param {string} tenantId شناسه‌ی دکان یا پمپ
   * @returns {{source:string, features:string[], subscription:object, trial:object}}
   */
  async function entitlementOf(tenantId, at = now()) {
    const tenant = await one(`SELECT * FROM ${T.tenantTable} WHERE id=$1`, [tenantId]);
    if (!tenant) {
      return {
        app: T.app,
        source: 'none',
        features: uniq([...cat.CORE_KEYS]),
        subscription: subs.stateOf(null, at),
        trial: { enabled: false, active: false, used: true, endsAt: 0, daysLeft: 0 },
      };
    }

    const sub = await ledger.latestOf(tenantId);
    const state = subs.stateOf(sub, at);
    const trial = await trialState(tenant, at);

    if (state.active) {
      const granted = state.features.length ? state.features : cat.PAID_KEYS;
      return {
        app: T.app,
        source: 'subscription',
        features: uniq([...cat.CORE_KEYS, ...cat.FREE_KEYS, ...granted]),
        subscription: state,
        trial,
      };
    }

    if (trial.active) {
      return {
        app: T.app,
        source: 'trial',
        features: uniq([...cat.CORE_KEYS, ...cat.FREE_KEYS, ...cat.PAID_KEYS]),
        subscription: state,
        trial,
      };
    }

    return {
      app: T.app,
      source: 'free',
      features: uniq([...cat.CORE_KEYS, ...cat.FREE_KEYS]),
      subscription: state,
      trial,
    };
  }

  return { entitlementOf, trialState, tenancy: T };
}

const shop = build(tenancy.SHOP);
const pump = build(tenancy.PUMP);

module.exports = {
  //  بخشِ دکان، همان‌جا که همیشه بود
  entitlementOf: shop.entitlementOf,
  trialState: shop.trialState,
  //  و بخش‌های دیگر، با نام
  build,
  shop,
  pump,
  forApp: (app) => (app === 'pump' ? pump : shop),
};
