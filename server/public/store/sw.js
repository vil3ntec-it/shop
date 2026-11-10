/*
 * ویترین — کارگرِ سرویس **فقط** برای پوشِ وب ِ مشتری (`lib/store-push.js`، CHK-20، NTF-01؛ «خبرم کن» ِ کالا، WEB-10):
 * نمایشِ اعلان و بازکردنِ صفحهٔ پیگیری یا برگهٔ کالا با لمس. فقط `store/push-client.js` (از `order-push.js` یا `alert-push.js`)
 * پس از لمسِ خودِ مشتری آن را ثبت می‌کند (دامنه = همین ویترین).
 *
 * ⛔ هیچ fetch، هیچ کش و هیچ پیامی از صفحه: رفتارِ شبکه‌ایِ ویترین همان پیشین است.
 * ⛔ متن فقط از پیامِ رمزشدهٔ همین سرور (عنوان ≤ ۸۰، متن ≤ ۳۰۰)؛ نشانیِ لمس فقط مسیرِ همین مبدأ (`/…`، نه `//…`) — هر
 *    چیزِ دیگر ⇒ هیچ پنجره‌ای باز نمی‌شود.
 */
'use strict';

function safePath(u) {
  return typeof u === 'string' && u.length < 600 && /^\/(?![/\\])[^\s\\]*$/.test(u) ? u : '';
}

self.addEventListener('install', function () { self.skipWaiting(); });

self.addEventListener('push', function (e) {
  var d = {};
  try { d = e.data ? e.data.json() : {}; } catch (x) { d = {}; }
  var title = typeof d.title === 'string' ? d.title.slice(0, 80) : '';
  var body = typeof d.body === 'string' ? d.body.slice(0, 300) : '';
  //  o- سفارش (store-push)، a- «خبرم کن» ِ کالا (store-alert-push)
  var tag = typeof d.tag === 'string' && /^[oa]-[A-Za-z0-9_-]{1,20}$/.test(d.tag) ? d.tag : undefined;
  var lang = d.lang === 'en' || d.lang === 'ps' ? d.lang : 'fa';
  //  userVisibleOnly: هر پوش یک اعلانِ دیدنی دارد (پیامِ خراب ⇒ همان نامِ ویترین بی متن)
  e.waitUntil(self.registration.showNotification(title || body || '…', {
    body: title ? body : '', tag: tag, renotify: Boolean(tag), lang: lang, dir: lang === 'en' ? 'ltr' : 'rtl',
    icon: '/kit/icon-192.png', data: { url: safePath(d.url) },
  }));
});

self.addEventListener('notificationclick', function (e) {
  e.notification.close();
  var path = safePath(e.notification.data && e.notification.data.url);
  if (!path) return;
  var url = new URL(path, self.location.origin).href;
  e.waitUntil(self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (list) {
    for (var i = 0; i < list.length; i++) if (list[i].url === url && 'focus' in list[i]) return list[i].focus();
    return self.clients.openWindow(url);
  }));
});
