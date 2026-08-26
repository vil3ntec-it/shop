'use strict';
/**
 * تصمیم نهایی درباره‌ی «این دکان الان به چه چیزی دسترسی دارد».
 *
 * ترتیب بررسی: اشتراک فعال ← دوره‌ی آزمایشی ← قابلیت‌های رایگان.
 * همه با ساعت سرور؛ ساعت گوشی هیچ نقشی ندارد.
 */
const { one, now } = require('../db');
const { CORE_KEYS, FREE_KEYS, PAID_KEYS } = require('./features');
const subs = require('./subscriptions');
const plans = require('./plans');

const DAY = 24 * 60 * 60 * 1000;

function uniq(arr) { return [...new Set(arr)]; }

async function trialState(shop, at = now()) {
  const days = Number(await plans.getConfig('trial_days', '14')) || 0;
  if (!days) return { enabled: false, active: false, used: true, endsAt: 0, daysLeft: 0 };
  const startedAt = Number(shop.created_at);
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
 * @param {string} shopId
 * @returns {{source:string, features:string[], subscription:object, trial:object}}
 */
async function entitlementOf(shopId, at = now()) {
  const shop = await one('SELECT * FROM shops WHERE id=$1', [shopId]);
  if (!shop) {
    return {
      source: 'none',
      features: uniq([...CORE_KEYS]),
      subscription: subs.stateOf(null, at),
      trial: { enabled: false, active: false, used: true, endsAt: 0, daysLeft: 0 },
    };
  }

  const sub = await subs.latestOf(shopId);
  const state = subs.stateOf(sub, at);
  const trial = await trialState(shop, at);

  if (state.active) {
    const granted = state.features.length ? state.features : PAID_KEYS;
    return {
      source: 'subscription',
      features: uniq([...CORE_KEYS, ...FREE_KEYS, ...granted]),
      subscription: state,
      trial,
    };
  }

  if (trial.active) {
    return {
      source: 'trial',
      features: uniq([...CORE_KEYS, ...FREE_KEYS, ...PAID_KEYS]),
      subscription: state,
      trial,
    };
  }

  return {
    source: 'free',
    features: uniq([...CORE_KEYS, ...FREE_KEYS]),
    subscription: state,
    trial,
  };
}

module.exports = { entitlementOf, trialState };
