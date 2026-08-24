# توحید | سرور اشتراک و License

سرور خانگی برای مدیریت اشتراک کاربران برنامه «مدیریت فروشگاه توحید» و صدور
License امضاشده.

## معماری

```
برنامه کاربر
    │  (همیشه برنامه آغازکننده است — سرور هرگز به دستگاه کاربر وصل نمی‌شود)
    ▼
درخواست امن HTTPS  ──►  سرور خانگی
                             │
                             ├─ احراز هویت (توکن مات، هش scrypt)
                             ├─ دیتابیس اشتراک (SQLite)
                             ├─ بررسی وضعیت با ساعت سرور
                             └─ تولید License
                                     │
                             امضا با کلید خصوصی (ECDSA P-256)
                                     ▼
                             License امضاشده  ──►  برنامه
                                                     │
                                          ذخیره محلی + کار آفلاین
```

## راه‌اندازی

```bash
cd server
npm install
cp .env.example .env          # مقادیر را ویرایش کنید
nano .env

npm run generate-keys          # جفت‌کلید امضا (یک بار)
npm run create-admin boss --role superadmin
npm start
```

پنل مدیریت: `http://<آدرس سرور>:4700/admin/`

### تنظیم برنامه کاربر

خروجی `generate-keys` (کلید عمومی base64) را در `license/license-client.js`
داخل `PINNED_PUBLIC_KEY` بگذارید. اگر خالی بماند، کلید در اولین اتصال از سرور
گرفته و ذخیره می‌شود — کار می‌کند ولی امن‌تر آن است که کلید را خودتان بگذارید.

در `.env` هم دامنه‌ی برنامه را در `CORS_ORIGINS` وارد کنید:
```
CORS_ORIGINS=https://vil3ntec-it.github.io
```

## قابلیت‌ها (Permissions)

قابلیت‌های پایه — همیشه باز، حتی پس از پایان اشتراک:
`dashboard` · `products` · `settings`

قابلیت‌های اشتراکی — در پنل برای هر کاربر جداگانه انتخاب می‌شوند:
`sales` · `warehouse` · `debtors` · `expenses` · `purchasing` · `reports`
· `audit_log` · `barcode` · `backup` · `csv_export`

## مدل اشتراک

| فیلد | توضیح |
|---|---|
| `starts_at` / `ends_at` | epoch UTC — سرور تعیین می‌کند، نه برنامه |
| `timezone` | تاریخ‌های وارد‌شده‌ی مدیر در این منطقه تفسیر می‌شوند (پیش‌فرض `Asia/Kabul`) |
| `features` | فهرست قابلیت‌های مجاز |
| `max_devices` | سقف دستگاه فعال |
| `grace_days` | مهلت پس از پایان، پیش از قفل شدن |
| `license_ttl_days` | اعتبار آفلاین License؛ خالی = تا پایان اشتراک |

**اعتبار آفلاین (`license_ttl_days`) یک بده‌بستان است:**
کوتاه‌تر → لغو دسترسی سریع‌تر اثر می‌کند، ولی کاربر باید زودتر آنلاین شود.
خالی → کاربر تا پایان اشتراک بدون اینترنت کار می‌کند، ولی لغو دسترسی تا آن
زمان به دستگاه آفلاین نمی‌رسد.

## ساختار License

قالب فشرده مثل JWS: `base64url(header).base64url(payload).base64url(signature)`
امضا: **ECDSA P-256 + SHA-256** روی بایت‌های `header.payload`.

```json
{
  "lid": "شناسه License",      "ver": 1,
  "uid": "شناسه کاربر",         "did": "شناسه دستگاه",
  "duid": "شناسه دستگاه سمت برنامه",
  "sid": "شناسه اشتراک",        "kid": "شناسه کلید امضا",
  "iss": "tohid-license-server", "aud": "tohid-shop-app",
  "iat": 0, "nbf": 0, "exp": 0, "sub_ends": 0, "grace_ms": 0,
  "tz": "Asia/Kabul", "plan": "…",
  "feat": ["sales", "reports"], "core": ["dashboard","products","settings"],
  "maxdev": 2, "dfp": "هش اثر انگشت دستگاه"
}
```

## API

### کاربر
| مسیر | کار |
|---|---|
| `POST /api/v1/auth/register` | ثبت‌نام |
| `POST /api/v1/auth/login` | ورود (accessToken + refreshToken) |
| `POST /api/v1/auth/refresh` | تازه‌سازی توکن |
| `POST /api/v1/auth/change-password` | تغییر رمز (همه نشست‌ها باطل می‌شوند) |
| `GET  /api/v1/auth/me` | پروفایل + وضعیت اشتراک |
| `GET  /api/v1/license/public-key` | کلید عمومی بررسی امضا |
| `GET  /api/v1/license/time` | زمان سرور |
| `POST /api/v1/license/activate` | فعال‌سازی دستگاه + صدور License |
| `POST /api/v1/license/sync` | گرفتن License تازه |
| `GET  /api/v1/license/status` | وضعیت اشتراک |
| `GET/DELETE /api/v1/license/devices[/:id]` | دستگاه‌های خودِ کاربر |

### مدیر (همه پشت `requireAdmin`)
| مسیر | کار |
|---|---|
| `POST /api/v1/admin/login` | ورود مدیر |
| `GET  /api/v1/admin/stats` | آمار کلی |
| `GET  /api/v1/admin/users[?q=]` | فهرست/جستجوی کاربران |
| `GET  /api/v1/admin/users/:id` | جزئیات کاربر |
| `POST /api/v1/admin/users/:id/status` | فعال/غیرفعال کردن کاربر |
| `POST /api/v1/admin/users/:id/subscription` | ساخت/جایگزینی اشتراک |
| `PATCH /api/v1/admin/subscriptions/:id` | ویرایش اشتراک |
| `POST /api/v1/admin/subscriptions/:id/renew` | تمدید |
| `POST /api/v1/admin/subscriptions/:id/status` | فعال/تعلیق/لغو |
| `GET  /api/v1/admin/devices` | همه دستگاه‌ها |
| `POST /api/v1/admin/devices/:id/revoke\|restore` | لغو/بازگرداندن دستگاه |
| `POST /api/v1/admin/licenses/:id/revoke` | ابطال License |
| `GET  /api/v1/admin/audit` | سابقه عملیات |
| `GET  /api/v1/admin/timezones` | منطقه‌های زمانی |

## امنیت — چه چیزی تضمین می‌شود و چه چیزی نه

**تضمین می‌شود:**
- کلید خصوصی هرگز از سرور خارج نمی‌شود؛ برنامه فقط کلید عمومی دارد.
- ساختن License تقلبی عملاً ناممکن است — بدون کلید خصوصی امضا معتبر نمی‌شود.
- ویرایش تاریخ یا قابلیت‌های داخل License امضا را می‌شکند و License رد می‌شود.
- شناسه‌ی کاربر همیشه از توکن خوانده می‌شود، نه از پارامتر درخواست؛ پس کاربر
  با تغییر user_id به داده‌ی دیگری نمی‌رسد.
- هر API آنلاینِ وابسته به قابلیت، پشت `requireFeature` دوباره بررسی می‌شود.
- عقب بردن ساعت دستگاه اشتراک را تمدید نمی‌کند.
- هیچ رمز یا کلیدی در کد نیست؛ همه از `.env` و فایل کلید می‌آید.

**تضمین نمی‌شود (و صادقانه باید گفت):**
برنامه‌ی فروشگاه تماماً سمت مرورگر اجرا می‌شود. کسی که با DevTools کار بلد
باشد می‌تواند **قفل نمایشی سمت کلاینت** را دور بزند. امضای دیجیتال جلوی جعل
License را می‌گیرد، ولی جلوی دستکاری کدِ در حال اجرا در مرورگرِ خودِ کاربر را
نمی‌گیرد — این محدودیت ذاتی هر برنامه‌ی کلاینت‌ساید است، نه نقص این پیاده‌سازی.

محافظت واقعی برای هر قابلیتی که ارزش مالی دارد، این است که آن قابلیت به یک
API سرور وابسته باشد و آن API با `requireFeature` محافظت شود. الگوی آن در
`src/app.js` (`/api/v1/protected/*`) پیاده شده است.

## تست

```bash
npm test        # ۳۱ تست: جریان کامل + منطق ساعت
```

## پشتیبان‌گیری

این‌ها را نگه دارید:
- `data/license.db` — کاربران، اشتراک‌ها، دستگاه‌ها
- `data/keys/license-private.pem` — **کلید امضا؛ گم شود همه Licenseها باطل می‌شوند**

`.env`، `data/` و `*.pem` در `.gitignore` هستند و نباید کامیت شوند.
