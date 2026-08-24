# توحید | برنامه اندروید

برنامه‌ی بومی (Kotlin + Jetpack Compose). نه WebView، نه شورت‌کات.

## چرا بومی

| | WebView | این برنامه |
|---|---|---|
| داده | localStorage، سیستم‌عامل می‌تواند پاکش کند | SQLite واقعی (Room) |
| حجم داده | حدود ۵ تا ۱۰ مگابایت | محدودیت عملی ندارد |
| همگام‌سازی وقتی برنامه بسته است | ندارد | WorkManager، خودکار |
| اسکن بارکد | دوربین مرورگر، کند | CameraX + ML Kit |
| سرعت | وابسته به موتور مرورگر | بومی |

## ساخت

```bash
cd android
./gradlew assembleDebug      # خروجی: app/build/outputs/apk/debug/
./gradlew testDebugUnitTest  # تست‌ها
```

روی گیت‌هاب هم با هر push خودکار ساخته و تست می‌شود
(`.github/workflows/android.yml`).

## انتشار نسخه و به‌روزرسانی از گیت‌هاب

```bash
git tag v1.0.1
git push origin v1.0.1
```

گیت‌هاب APK را می‌سازد و در Releases می‌گذارد. برنامه‌ی نصب‌شده روی گوشی
آخرین Release را می‌بیند و به‌روزرسانی را پیشنهاد می‌دهد.

### امضای رسمی (یک بار)

بدون کلید رسمی، بیلد با امضای debug ادامه می‌دهد تا خط لوله نشکند — ولی
برای انتشار واقعی کلید لازم است، وگرنه کاربر نمی‌تواند نسخه‌ی بعدی را
روی نسخه‌ی قبلی نصب کند.

```bash
keytool -genkey -v -keystore tohid.keystore -alias tohid \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 tohid.keystore > keystore.b64
```

سپس در `Settings → Secrets and variables → Actions` این‌ها را بساز:

| Secret | مقدار |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | محتوای `keystore.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | رمز keystore |
| `ANDROID_KEY_ALIAS` | `tohid` |
| `ANDROID_KEY_PASSWORD` | رمز کلید |

**فایل keystore را گم نکن.** بدون آن نمی‌توانی نسخه‌ی جدید منتشر کنی و
کاربران باید برنامه را پاک و از نو نصب کنند (یعنی داده‌شان می‌رود).

## اتصال از هر جای دنیا

سرور خانگی پشت مودم است و از بیرون دیده نمی‌شود. یکی از این راه‌ها لازم است:

**۱) Cloudflare Tunnel — پیشنهاد من**
رایگان، بدون port forward، پشت CGNAT هم کار می‌کند، و https واقعی می‌دهد:

```bash
cloudflared tunnel login
cloudflared tunnel create tohid
cloudflared tunnel route dns tohid shop.example.com
cloudflared tunnel run --url http://localhost:4700 tohid
```

بعد در برنامه آدرس `https://shop.example.com` را وارد کن.

**۲) DDNS + port forward**
پورت ۴۷۰۰ روی مودم به سرور forward شود و یک آدرس DDNS بگیری. ساده‌تر
ولی شکننده‌تر: اگر اینترنت خانه IP عمومی نداشته باشد (CGNAT) کار نمی‌کند.

**۳) VPS ارزان**
سرور را روی یک VPS بگذار. مطمئن‌ترین، ولی هزینه‌ی ماهانه دارد.

بدون یکی از این‌ها، همگام‌سازی فقط داخل شبکه‌ی خانه کار می‌کند.

## وضعیت

**آماده:** پایگاه‌داده، موتور همگام‌سازی، همگام‌سازی پس‌زمینه، حساب و دکان،
تشخیص کسری موجودی، به‌روزرسانی از گیت‌هاب، تم و راست‌به‌چپ.

**در دست انتقال:** صفحه‌های فروش، محصولات، قرض‌داران، انبار، مصارف، خرید،
گزارشات و تنظیمات — پایه‌شان آماده است و یکی‌یکی منتقل می‌شوند.
