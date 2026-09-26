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
  //  ⛔ کلید و پیش‌فرض از `plans.trialConfig` می‌آیند، نه از این‌جا —
  //  شرحش آن‌جا. سه جا نوشته شدنشان یعنی فهرستِ مدیر و خودِ برنامه
  //  می‌توانستند دو حرفِ جدا بزنند.
  async function trialState(tenant, at = now()) {
    const days = await plans.trialDaysOf(T.app);
    if (!days) return { enabled: false, active: false, used: true, endsAt: 0, daysLeft: 0 };
    //  ⛔ پمپ آغازِ دوره‌اش را در ستونِ خودش دارد (`trial_started_at`، مهاجرتِ
    //  ۰۳۲ — همهٔ حساب‌های پیشین یک بار بررسی شدند)؛ دکان همان `created_at`.
    //  ⚠️ فقط از خودِ سرور — هیچ برنامه‌ای این تاریخ را نمی‌فرستد.
    const startedAt = Number(tenant.trial_started_at ?? tenant.created_at);
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
    let trial = await trialState(tenant, at);
    /*
     *  ⛔ **دورهٔ آزمایشی مالِ حسابی است که هنوز هیچ اشتراکی نداشته.**
     *
     *  گزارشِ صاحبِ سامانه (۱۴۰۵/۰۷/۱۳): «نمی‌توانم اشتراکِ حسابی را حذف
     *  کنم.» سنجیده شد، حدس زده نشد (پنلِ واقعی + همین سرور + برنامهٔ
     *  پمپ): مدیر اشتراکِ حسابِ **تازه** را لغو می‌کرد، پنل «لغو» نشان
     *  می‌داد — و برنامه شصت ثانیه بعد «دورهٔ آزمایشی · ۲۹ روز» با **همهٔ**
     *  قابلیت‌ها می‌گرفت، چون حساب هنوز در سی روزِ نخستش بود. یعنی لغو،
     *  تعلیق و تمام شدنِ اشتراک در ماهِ اول هیچ اثری نداشت، و همان راهِ
     *  پولی هم بود: استاندارد بدهی، تمام شود، و وی‌آی‌پیِ رایگان بگیرد.
     *
     *  پس هر اشتراکی که روزی نشسته (فعال، تعلیق، لغو، منقضی) دوره را
     *  مصرف‌شده می‌کند. ⚠️ `pending` نه: درخواستِ خریدی که هنوز تایید
     *  نشده نباید مشتری را همان لحظه از دورهٔ آزمایشی‌اش بیرون بیندازد.
     */
    if (sub && sub.status !== 'pending' && trial.active) {
      trial = { ...trial, active: false, used: true, daysLeft: 0, consumed: true };
    }

    if (state.active) {
      //  ⛔ قاعدهٔ «فهرستِ خالی = پلنِ کامل» دست نمی‌خورد؛ افزونه فقط
      //  **به** فهرست اضافه می‌شود (Feature Flag روی همین اشتراک).
      const granted = state.features.length ? state.features : cat.PAID_KEYS;
      const addons = await require('./discounts').addonKeysOf(T.app, sub.id);
      return {
        app: T.app,
        source: 'subscription',
        features: uniq([...cat.CORE_KEYS, ...cat.FREE_KEYS, ...granted, ...addons]),
        addons,
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
