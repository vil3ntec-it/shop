/* ==========================================================
   توحید | مدیریت فروشگاه — Service Worker
   هدف: برنامه بدون اینترنت هم کامل باز شود.
   - پوسته برنامه (index.html، آیکن‌ها، مانیفست) هنگام نصب کش می‌شود.
   - ناوبری: اول شبکه، در نبود اینترنت از کش (stale-while-revalidate).
   - فونت‌های گوگل: اولین بار از شبکه، سپس همیشه از کش.
   داده‌های فروشگاه در localStorage است و اصلاً از اینجا عبور نمی‌کند.
   ========================================================== */
const VERSION = 'tohid-shop-v7';
const SHELL_CACHE = VERSION + '-shell';
const FONT_CACHE = VERSION + '-fonts';

const SHELL_ASSETS = [
  './',
  './index.html',
  './manifest.webmanifest',
  './icons/icon-192.png',
  './icons/icon-512.png',
  './icons/icon-maskable-512.png',
  './license/license-client.js',
  './license/license-ui.css',
  './license/shop-sync.js',
  './license/shop-ui.js',
  './license/shop-ui.css',
  './license/vip.js',
  './license/vip.css',
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(SHELL_CACHE)
      // هر فایلی که نبود، کل نصب را خراب نکند
      .then((cache) => Promise.allSettled(SHELL_ASSETS.map((url) => cache.add(url))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys.filter((k) => !k.startsWith(VERSION)).map((k) => caches.delete(k))
      ))
      .then(() => self.clients.claim())
  );
});

// پیام از صفحه: برای اعمال فوری نسخه جدید
self.addEventListener('message', (event) => {
  if (event.data === 'skip-waiting') self.skipWaiting();
});

function isFontRequest(url) {
  return url.hostname === 'fonts.googleapis.com' || url.hostname === 'fonts.gstatic.com';
}

self.addEventListener('fetch', (event) => {
  const req = event.request;
  if (req.method !== 'GET') return;

  const url = new URL(req.url);

  // ---- فونت‌ها: اول کش، بعد شبکه (و ذخیره برای دفعات بعد) ----
  if (isFontRequest(url)) {
    event.respondWith(
      caches.match(req).then((hit) => hit || fetch(req).then((res) => {
        const copy = res.clone();
        caches.open(FONT_CACHE).then((c) => c.put(req, copy)).catch(() => {});
        return res;
      }).catch(() => hit))
    );
    return;
  }

  // ---- ناوبری: اول شبکه تا نسخه تازه بیاید، در نبود اینترنت از کش ----
  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then((res) => {
          const copy = res.clone();
          caches.open(SHELL_CACHE).then((c) => c.put('./index.html', copy)).catch(() => {});
          return res;
        })
        .catch(() => caches.match('./index.html').then((hit) => hit || caches.match('./')))
    );
    return;
  }

  // ---- بقیه فایل‌های خودِ برنامه: اول کش، بعد شبکه ----
  if (url.origin === self.location.origin) {
    event.respondWith(
      caches.match(req).then((hit) => hit || fetch(req).then((res) => {
        if (res && res.ok) {
          const copy = res.clone();
          caches.open(SHELL_CACHE).then((c) => c.put(req, copy)).catch(() => {});
        }
        return res;
      }))
    );
  }
});
