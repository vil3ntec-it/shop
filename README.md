# shop

برنامه‌ی مدیریت فروشگاه — نسخه‌ی وب، برنامه‌ی اندروید، برنامه‌ی کامپیوتر و سرور.

```
برنامه اندروید ──┐
برنامه کامپیوتر ─┼─→  REST API  →  Node.js  →  PostgreSQL
نسخه وب ─────────┘
```

| پوشه | چیست |
|---|---|
| `index.html` و `license/` | نسخه‌ی وب (PWA)، روی GitHub Pages |
| `android-native/` | برنامه‌ی بومی اندروید (Kotlin + Compose) |
| `desktop/` | برنامه‌ی بومی کامپیوتر (ویندوز، مک، لینوکس) — همان کدِ `android-native` روی Compose Desktop |
| `admin-android/` | برنامه‌ی اندرویدِ «توحید مدیریت» برای مدیرِ سامانه (Kotlin + Compose) |
| `android/` | پوسته‌ی WebViewِ قدیمِ اندروید که همان نسخه‌ی وب را از داخلِ بسته اجرا می‌کند؛ ساختش فقط دستی است (`android.yml`) |
| `server/` | سرور: حساب، دکان چندکاربره، اشتراک، همگام‌سازی، پشتیبان |
| `docs/` | سندها، قاعده‌ها (`RULES-fa.md`) و مشخصاتِ محصول (`docs/spec/`) |

- سایت: https://vil3ntec-it.github.io/shop/
- دانلودِ همهٔ نسخه‌ها (یک صفحه، تشخیصِ خودکارِ سیستم): https://vil3ntec-it.github.io/shop/download.html
- راه‌اندازی سرور: [`server/README.md`](server/README.md)
- برنامه‌ی بومی اندروید: [`android-native/README-fa.md`](android-native/README-fa.md) — پوسته‌ی قدیمِ WebView: [`android/README.md`](android/README.md)
- برنامه‌ی کامپیوتر: [`docs/DESKTOP-fa.md`](docs/DESKTOP-fa.md) — دانلود از انتشارِ [`tohid-desktop`](https://github.com/vil3ntec-it/shop/releases/tag/tohid-desktop)

## در یک نگاه

- هر کاربر حساب خودش را دارد؛ ورود با شماره و کد پیامکی، حساب گوگل، یا رمز.
- هر دکان یک شناسه دارد و صاحب دکان با «کد شاگرد» شاگردهایش را روی همان
  دکان می‌آورد. اشتراک به دکان وصل است، نه به یک نفر.
- اطلاعات اصلی روی سرور می‌ماند؛ گوشی یک نسخه‌ی محلی برای کار در نبود
  اینترنت دارد و به محض وصل شدن، تفاوت‌ها همگام می‌شوند.
- پشتیبان‌گیری خودکار روی سرور، با امکان بازگرداندن روی هر سرور دیگر.
