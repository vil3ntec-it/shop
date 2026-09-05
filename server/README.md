# سرور فروشگاه

سرور اصلی برنامه: حساب کاربری، دکان چندکاربره، اشتراک، همگام‌سازی و پشتیبان‌گیری.

```
برنامه اندروید  →  REST API (HTTPS)  →  Node.js  →  PostgreSQL
```

منبع اصلی اطلاعات، همین سرور است. گوشی فقط یک نسخه‌ی محلی برای سرعت و
کار در نبود اینترنت نگه می‌دارد.

---

## پیش‌نیاز

- Node.js نسخه ۲۰ یا بالاتر
- PostgreSQL نسخه ۱۴ یا بالاتر
- `pg_dump` و `psql` (برای پشتیبان‌گیری و بازیابی)

هیچ چیز به یک کامپیوتر، مسیر یا IP خاص وابسته نیست. همین کد روی
کامپیوتر خانگی، VPS، سرور اختصاصی، سرور ابری یا هاست دارای Node.js
اجرا می‌شود.

---

## راه‌اندازی سریع با Docker

```bash
cp .env.example .env
# رمز دیتابیس و رازها را در .env عوض کنید
docker compose up -d
docker compose exec api node scripts/create-admin.js admin
```

`docker compose up -d` هم دیتابیس و هم سرور را بالا می‌آورد.
پنل مدیریت: `http://آدرس-سرور:3000/admin/`

## به‌روزرسانی

```bash
cd shop/server
./update.sh
```

همین. اسکریپت خودش پشتیبان می‌گیرد، کد تازه را می‌کشد، ایمیج را دوباره
می‌سازد و آخرش نسخه‌ی سرور را نشان می‌دهد تا مطمئن شوید واقعاً عوض شده.

Migrationها هنگام بالا آمدن خودشان اجرا می‌شوند و هر کدام یک بار و فقط
یک بار — پس این دستور را می‌شود هر بار بی‌خطر دوباره زد.

**تله‌ای که باید بشناسید:** `docker compose up -d` بدونِ `--build`،
همان ایمیجِ قدیمی را دوباره بالا می‌آورد. یعنی «به‌روز کردم ولی فرقی
نکرد». `update.sh` این را می‌گیرد: اگر نسخه‌ی جواب‌داده با
`package.json` یکی نباشد، می‌گوید و دستورِ درست را می‌دهد.

اگر Docker ندارید و دستی بالا آورده‌اید:

```bash
cd shop && git pull
cd server && npm ci --omit=dev
# سرویس را دوباره راه بیندازید (systemctl restart … یا pm2 restart …)
curl -s localhost:3000/health   # نسخه را ببینید
```

### از کجا بدانم سرورم قدیمی است

```bash
curl -s https://api.YOURDOMAIN.com/health
```

`version` در پاسخ باید با `version` در `server/package.json` یکی باشد.
اگر نبود، برنامه‌ها هم همین را می‌گویند: «سرورِ شما قدیمی است و این
بخش را ندارد.»

---

## راه‌اندازی دستی

```bash
npm install
cp .env.example .env          # DATABASE_URL و رازها را پر کنید
npm run migrate               # ساخت جدول‌ها
node scripts/create-admin.js admin
npm start
```

برای اجرای ۲۴ ساعته روی کامپیوتر خانگی، سرور را زیر `systemd` یا `pm2`
بگذارید تا بعد از خاموش/روشن شدن خودکار بالا بیاید:

```bash
pm2 start src/index.js --name shop-server && pm2 save && pm2 startup
```

---

## تنظیمات

همه چیز از متغیرهای محیطی خوانده می‌شود؛ فهرست کامل با توضیح در
`.env.example` است. مهم‌ترین‌ها:

| متغیر | کار |
|---|---|
| `DATABASE_URL` | آدرس PostgreSQL |
| `PORT` | پورت سرور (پیش‌فرض ۳۰۰۰) |
| `SERVER_URL` | آدرسی که برنامه‌ها با آن به سرور می‌رسند (IP هم قبول است) |
| `API_SECRET` / `OTP_SECRET` | رازهای سرور؛ حتماً عوض شوند |
| `OTP_PROVIDER` | `log` \| `sms` \| `webhook` \| `whatsapp` — راه‌اندازی در [`SMS.md`](SMS.md) |
| `GOOGLE_CLIENT_ID` | برای ورود با گوگل — راه‌اندازی در [`GOOGLE-LOGIN.md`](GOOGLE-LOGIN.md) |
| `BACKUP_PATH` | مسیر ذخیره‌ی پشتیبان‌ها |
| `BACKUP_PASSPHRASE` | اگر پر باشد، پشتیبان‌ها رمز می‌شوند |
| `TRIAL_DAYS` | مدت دوره‌ی آزمایشی هر دکان |

---

## مسیرهای API

همه‌ی مسیرها هم زیر `/api` و هم زیر `/api/v1` در دسترس‌اند.

**حساب**
```
POST /api/auth/register/start     ثبت‌نام، پلهٔ ۱: نام و ایمیل و رمز → کد به ایمیل
POST /api/auth/register/verify    ثبت‌نام، پلهٔ ۲: کد شش‌رقمی → بلیت
POST /api/auth/register/complete  ثبت‌نام، پلهٔ ۳: بلیت + لوکیشن + پذیرش شرایط → حساب
POST /api/auth/register        ثبت‌نام یک‌مرحله‌ای (قدیمی، برای نسخه‌های پیشین)
POST /api/auth/login           ورود با رمز
POST /api/auth/otp/request     خواستن کد یک‌بارمصرف
POST /api/auth/otp/verify      ورود با کد
POST /api/auth/google          ورود با حساب گوگل
POST /api/auth/refresh         تازه‌سازی نشست
POST /api/auth/logout          خروج
GET  /api/terms                متن شرایط و ضوابط (بدون نیاز به حساب)
POST /api/location             ثبت لوکیشن دستگاه — با حساب یا بدون حساب
GET  /api/location/mine        لوکیشن‌های همین حساب
GET  /api/me                   حساب، دکان، نقش، دسترسی‌ها
GET  /api/me/subscription      وضعیت اشتراک (با ساعت سرور)
```

**دکان و اعضا**
```
GET    /api/shop               دکان من
POST   /api/shop               ساخت دکان
PUT    /api/shop               ویرایش نام دکان
GET    /api/shop/members       اعضا
POST   /api/shop/staff-code    ساخت کد شاگرد
DELETE /api/shop/staff-codes/:id   باطل کردن کد
POST   /api/shop/staff/join    ورود شاگرد با کد
DELETE /api/shop/members/:id   حذف عضو
```

**داده و همگام‌سازی**
```
GET  /api/sync?since=<rev>     گرفتن تغییرات تازه
POST /api/sync                 فرستادن تغییرات (با operationId)
GET/POST/PUT/DELETE /api/products  /api/debtors  /api/sales
                    /api/inventory /api/expenses /api/payments
POST /api/sales/full           ثبت فروش + اقلام + انبار + پرداخت در یک تراکنش
```

**مدیریت**
```
POST /api/admin/login
GET  /api/admin/users  /api/admin/shops  /api/admin/subscriptions
POST /api/admin/subscriptions          فعال‌سازی یا تمدید
PUT  /api/admin/subscriptions/:id      ویرایش
POST /api/admin/subscriptions/:id/status   تعلیق / لغو / فعال
GET  /api/admin/backups   POST /api/admin/backups
```

**سلامت**
```
GET /api/health   →  {"ok":true,"server":"online","database":"connected"}
```

---

## چند نفر روی یک دکان

```
دکان
 ├── صاحب دکان (owner)   همه‌کاره
 ├── مدیر (manager)      کارهای روزمره و مدیریتی
 └── شاگرد (staff)       فروش و ثبت روزمره
```

صاحب دکان از برنامه یک **کد شاگرد** می‌سازد (مثل `SHG-8F29-KD72-PL51`).
شاگرد در صفحه‌ی ورود همین کد را می‌زند و عضو همان دکان می‌شود. اشتراک
به خود دکان وصل است، پس برای هر شاگرد جدا اشتراک لازم نیست.

خودِ کد در دیتابیس ذخیره نمی‌شود؛ فقط HMAC آن. کد می‌تواند مهلت و
سقف تعداد استفاده داشته باشد و هر لحظه باطل شود.

---

## دامنه و HTTPS

بدون گواهی، رمز و توکن کاربر روی HTTP ساده رد و بدل می‌شود. برای سروری که
روی اینترنت است، این مرحله اختیاری نیست.

۱) **دامنه بخرید** و در DNS یک رکورد `A` بسازید:

```
نوع    نام                 مقدار
A      api.example.com     IP سرور شما
```

اگر Cloudflare دارید، ابر را خاکستری (DNS only) بگذارید تا Caddy بتواند
گواهی بگیرد؛ بعد از گرفتن گواهی می‌توانید نارنجی‌اش کنید.

۲) در `.env` بنویسید:

```
DOMAIN=api.example.com
TRUST_PROXY=true
CORS_ORIGIN=https://vil3ntec-it.github.io
```

۳) پورت‌های ۸۰ و ۴۴۳ روی سرور باز باشند و بالا بیاورید:

```bash
docker compose --profile tls up -d
```

Caddy خودش از Let's Encrypt گواهی می‌گیرد و پیش از تمام شدن تازه می‌کند.
گواهی‌ها در volume می‌مانند، پس بالا و پایین کردن سرور دوباره گواهی
نمی‌گیرد (Let's Encrypt سقف روزانه دارد).

۴) بررسی کنید:

```bash
curl https://api.example.com/health
```

بعد از این، در برنامه فقط همین نشانی را بدهید: `https://api.example.com`

> **بدون دامنه، روی شبکه‌ی محلی:** `BIND=0.0.0.0` بگذارید و بدون
> `--profile tls` بالا بیاورید. آن‌وقت API روی `http://IP:3000` است.
> این فقط برای آزمایش است.

## امنیت

- رمزها با `scrypt` هش می‌شوند.
- توکن نشست تصادفی است و فقط هش آن در دیتابیس می‌ماند؛ پس قابل باطل کردن است.
- `shop_id` هرگز از بدنه‌ی درخواست خوانده نمی‌شود — از روی عضویت کاربرِ توکن پیدا می‌شود.
  به همین دلیل کسی با عوض کردن شناسه در درخواست به دکان دیگری نمی‌رسد.
- دسترسی‌ها (نقش‌ها) سمت سرور بررسی می‌شوند، نه با پنهان کردن دکمه در گوشی.
- همه‌ی کوئری‌ها پارامتری‌اند؛ SQL Injection ممکن نیست.
- محدودیت نرخ روی ورود، کد یک‌بارمصرف، کد شاگرد و پنل مدیریت.
- رمز، کد و توکن هرگز در لاگ یا سابقه نوشته نمی‌شوند.

---

## پشتیبان‌گیری

پشتیبان روزانه/هفتگی/ماهانه خودکار گرفته می‌شود (`BACKUP_ENABLED`).
خروجی، فایل SQL فشرده است — نه یک قالب مخصوص این سرور.

```bash
npm run backup                       # پشتیبان دستی
node scripts/restore.js <فایل>       # بازگرداندن
```

اگر `BACKUP_PASSPHRASE` را پر کنید فایل با AES-256-GCM رمز می‌شود.
**عبارت عبور را گم نکنید؛ بدون آن پشتیبان باز نمی‌شود.**

## انتقال سرور (خانگی ← VPS)

```bash
# روی سرور قدیمی
npm run backup

# فایل را به سرور تازه ببرید، دیتابیس خالی بسازید و:
node scripts/restore.js shop-2026....sql.gz
```

تاریخچه‌ی Migrationها هم منتقل می‌شود، پس دیتابیس دقیقاً همان‌جایی است
که بود. در برنامه‌ی اندروید فقط آدرس سرور را عوض کنید — نصب دوباره لازم نیست.

---

## تست

```bash
createdb shop_test
npm test
```

تست‌ها روی PostgreSQL واقعی اجرا می‌شوند و همان سناریوهای مهم را می‌سنجند:
جدا بودن دو حساب، جدا بودن دو دکان، کد شاگرد، همگام‌سازی دوطرفه، نوشتن
همزمان، ثبت نشدن رکورد تکراری، کار آفلاین و بازگشت اطلاعات بعد از نصب دوباره.
