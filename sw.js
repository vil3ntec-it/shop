/* ==========================================================
   فروشگاه من — سرویس‌ورکرِ خداحافظی (۱۴۰۵/۰۸/۲۰)

   ⛔ وبِ مدیریتِ فروشگاه به تصمیمِ صاحبِ مخزن برداشته شد (docs/RULES-fa.md، بخشِ نخست). نصب‌های پیشین (PWA ِ
   صفحهٔ اصلی و برگه‌های باز) هنوز همین نشانی را می‌پرسند و تا این فایل نرسد پوستهٔ کهنه را از کش نشان می‌دهند.
   این کارگر:
     ۱) بی انتظار جای کارگرِ کهنه را می‌گیرد (skipWaiting؛ کارگرِ کهنه منتظرِ دکمهٔ «به‌روزرسانی» می‌ماند)؛
     ۲) فقط کش‌های پوستهٔ **همین کانال** را پاک می‌کند: `tohid-shop-…` در پایدار و `beta-tohid-shop-…` در `/beta/`
        — نه کشِ کانالِ دیگر و نه کشِ برنامهٔ دیگری روی همین دامنه؛ و هرگز localStorage یا IndexedDB (دفترِ دکان)
        را، که صفحهٔ تازه (`index.html`) از آن فایلِ پشتیبان می‌سازد؛
     ۳) خودش را برمی‌دارد و برگه‌های بازِ خودش را دوباره بار می‌کند تا صفحهٔ «برنامه را نصب کنید» از شبکه بیاید.
   fetch ندارد: از این پس هیچ پاسخی از کش داده نمی‌شود.

   ⛔ نگه داشته می‌شود تا دیرترین نصبِ کهنه هم یک بار آنلاین شود (دست‌کم تا ۱۴۰۶/۰۲/۰۱)؛ برداشتنِ زودترش
      نصب‌های کهنه را روی پوستهٔ کش‌شده نگه می‌دارد.
   ⛔ همین فایل در `/beta/` هم منتشر می‌شود و هر کانال فقط کارِ خودش را می‌کند (`BETA_PATH`؛ ورک‌فلوی
      «وب — GitHub Pages» همین نام را در هر دو می‌جوید).
   آزمون: `server/test/web-shop-gone.mjs`.
   ========================================================== */
'use strict';

const BETA = /\/beta\/sw\.js$/.test(self.location.pathname);
const BETA_PATH = new URL('./beta/', self.location).pathname;
const OWN_CACHE = BETA ? 'beta-tohid-shop-' : 'tohid-shop-';

self.addEventListener('install', (event) => {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener('activate', (event) => {
  event.waitUntil((async () => {
    const keys = await caches.keys();
    await Promise.all(keys.filter((k) => k.startsWith(OWN_CACHE)).map((k) => caches.delete(k)));
    await self.registration.unregister();
    const open = await self.clients.matchAll({ type: 'window' });
    await Promise.all(open.map((c) => {
      //  برگهٔ `/beta/` کارِ کارگرِ آزمایشیِ خودش است
      if (!BETA && new URL(c.url).pathname.startsWith(BETA_PATH)) return null;
      return c.navigate(c.url).catch(() => null);
    }));
  })());
});
