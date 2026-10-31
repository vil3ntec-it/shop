# طرحِ برنامهٔ بومیِ آیفون — برنامهٔ مدیریت (صندوق + حالتِ پیک)

> بررسی: ۱۴۰۵/۰۸/۰۸ (۳۰ اکتبر ۲۰۲۶)، شاخهٔ `claude/research-ios-readiness`، روی کامیتِ `f557986`.
> پایه: §۴۰.۳ِ `docs/spec/40-owner-decisions.md` (KMP + Compose Multiplatform، همان کد، بی بازنویسی).
> در این بررسی هیچ کدی عوض نشد. عددها با `grep` شمرده شده‌اند (روشِ شمارش در پیوستِ الف). برآوردِ زمان تخمین است، اندازه‌گیری نیست.

## ۱. خلاصه در پنج خط

1. کدِ برنامه **۳۴۲ فایلِ Kotlin و ۸۹٬۸۷۲ خط** است. از این‌ها **۱۲۰ فایل** فقط Kotlin/Compose/kotlinx دارند، **۵۸ فایل** فقط به JDK تکیه دارند (`java.*`، `org.json`، `System.currentTimeMillis`، `String.format`، `synchronized`…) و **۱۶۴ فایل** به اندروید وابسته‌اند (`android.*`، androidxِ غیرِ Compose، `Context`، `SharedPreferences`، `R.`، کتابخانه‌های گوگل، OkHttp).
2. ترفندِ کامپیوتر (`desktop/`: کپیِ سورس + ۴۲ فایلِ shim که نام‌های `android.*` را روی JVM می‌سازند + ۱۷ فایلِ جایگزین) **برای آیفون کار نمی‌کند**، چون آیفون JVM ندارد: روی Kotlin/Native هیچ `java.*` و `javax.*`ی نیست. پس هم ۱۶۴ فایلِ اندرویدی و هم ۵۸ فایلِ JDKی جلوی کامپایل‌اند (روی‌هم **۲۲۲ فایل، ۶۵٪**).
3. پایگاه‌دادهٔ SQL در کار نیست (JDBC/SQLite/Room = **۰**)؛ ذخیره با `SharedPreferences` (**۶۰ فایل**، ۱۵ نامِ پرونده) و فایل‌های JSON (`java.io.File` در **۴۲ فایل**) است. این خبرِ خوبی است: جایگزینِ iOS ساده‌تر است.
4. خودِ `core` تقریباً آماده است: از ۱۴۰۵/۰۸/۰۸ (PR #426) سیزده فایل از ۱۵ فایلِ jvmMain (`Money`، `Ulid`، تقویم‌ها، متن، پرداخت…) در commonMainاند؛ فقط `PinCrypto` و `ApproverVault` مانده‌اند (بخشِ ۲.۳).
5. بزرگ‌ترین مانع‌ها ترتیب: `Context`/`LocalContext` (۱۰۷ فایل)، `SharedPreferences` (۶۰)، `java.io.File` (۴۲)، زمان و قالب‌بندی (۸۶ + ۲۸)، رمزنگاری (۱۳ + `PinCrypto`ِ core)، شبکه (۴ + ۱)، و چاپِ حرارتی (۱۱ فایلِ `print/`، همه وابسته).

## ۲. شمارش (فقط `android-native/app/src/main/java`)

### ۲.۱ به تفکیکِ پوشه

| پوشه | فایل | خط | وابسته به اندروید | فقط JDK | خالص |
|---|---:|---:|---:|---:|---:|
| `ui/` | ۱۳۱ | ۴۸٬۳۲۱ | ۷۷ | ۶ | ۴۸ |
| `data/` | ۹۵ | ۱۸٬۴۶۰ | ۴۱ | ۲۳ | ۳۱ |
| `core/` (درونِ برنامه) | ۵۱ | ۹٬۲۶۴ | ۴ | ۱۵ | ۳۲ |
| `sync/` | ۳۳ | ۶٬۵۹۴ | ۲۳ | ۵ | ۵ |
| `print/` | ۱۱ | ۲٬۵۶۳ | ۱۱ | ۰ | ۰ |
| `scan/` | ۱۰ | ۲٬۶۹۵ | ۳ | ۳ | ۴ |
| `update/` | ۵ | ۱٬۲۲۵ | ۲ | ۳ | ۰ |
| `hw/` | ۱ | ۸۴ | ۰ | ۱ | ۰ |
| ریشه (`MainActivity`، `Format`، `Jalali`…) | ۵ | — | ۳ | ۲ | ۰ |
| **جمع** | **۳۴۲** | **۸۹٬۸۷۲** | **۱۶۴** | **۵۸** | **۱۲۰** |

- از ۷۷ فایلِ وابستهٔ `ui/`، **۴۴ فایل** هیچ import ِ اندرویدی ندارند و فقط به‌خاطرِ `LocalContext`/`Context` وابسته‌اند (برای صدا زدنِ `SyncStore(context)`، `Backend.api(context)`، `ShopRole.*(context)` و…). این‌ها با یک «جعبهٔ سرویس» به‌جای `Context` آزاد می‌شوند.
- «خالص» در `ui/` یعنی فقط `androidx.compose.*`. این نام‌ها در Compose Multiplatform همان‌اند، پس بی تغییر کامپایل می‌شوند (به شرطِ وابستگی‌های چندسکویی: material3، icons-extended، lifecycle-viewmodel).

### ۲.۲ به تفکیکِ نوعِ وابستگی (شمارِ فایل)

| وابستگی | فایل | یادداشت |
|---|---:|---|
| هر `import android.*` | ۹۷ | پرتکرارها: `android.content` (۸۸ import)، `android.graphics` (۳۷)، `android.os` (۲۱)، `android.net` (۱۳)، `android.text` (۱۲)، `android.print` (۱۰)، `android.content.pm` (۱۰) |
| androidxِ غیرِ Compose | ۳۰ | `work` (۲۹ import)، `activity.result` (۱۵)، `camera` (۲۲)، `core.content` (۱۳)، `biometric`، `credentials`، `security.crypto`، `fragment` |
| `Context` (هر جا) | ۱۰۷ | `LocalContext` در ۷۱ فایل (۶۹ در `ui/`) |
| `SharedPreferences` | ۶۰ | data ۲۱، ui ۱۹، sync ۱۴، print ۵، core ۲ |
| `java.io` | ۴۴ | `File` در ۳۴ import، `FileOutputStream`، `ByteArrayOutputStream`… |
| `java.time` | ۰ | — |
| `java.util.concurrent` | ۱۱ | `TimeUnit`، `Executors`، `ConcurrentHashMap` |
| `java.util` دیگر | ۱۱ | `Locale`، `Date`، `Calendar`، `UUID`، `Properties`، `zip` |
| `java.text` | ۶ | `SimpleDateFormat` |
| `SimpleDateFormat`/`Calendar`/`TimeZone` | ۲۷ | (با نامِ کامل هم شمرده شد) |
| `java.net` | ۸ | `URL`، `HttpURLConnection`، `URLEncoder`، `Socket` |
| `java.security`/`javax.crypto` | ۱۳ | `MessageDigest` (۱۰ فایل)، `Signature`/`KeyFactory`، `Cipher` AES-GCM، `KeyStore` |
| `java.nio` | ۳ | `FileChannel` (قفلِ فایل)، `Files` |
| JDBC / SQLite / Room | **۰** | پایگاه‌دادهٔ محلی نیست |
| OkHttp | ۱ | `sync/v1/LiveSocket.kt` (سوکتِ زنده) |
| `HttpURLConnection` | ۴ | `core/net/HttpEngine.kt`، `update/*`، `LiveSocket` |
| `org.json` | ۹ | ۱۶ import؛ بقیه `kotlinx.serialization` (۷۸ فایل) |
| `System.currentTimeMillis` | ۸۶ | |
| `String.format` / `.format(` | ۲۸ | شمارش با الگوی گسترده؛ کمی بیش‌شماری دارد |
| `synchronized` / `@Volatile` / `Thread(` | ۳۶ | |
| منابعِ `R.` | ۱۰ | فونت، صدا، تصویر |
| `expect`/`actual` | **۰** | هنوز هیچ مرزِ پلتفرمِ Kotlin-ی نیست؛ مرزها `object`های دستی‌اند (`ui/Platform.kt`، `hw/HwPlatform.kt`) |

### ۲.۳ خودِ `core` (KMP)

| بخش | فایل |
|---|---:|
| `commonMain` (روی iOS کامپایل می‌شود) | ۴۸ (۳۳ + ۱۳ ِ منتقل‌شده + `MathRound` + `platform/Platform`) |
| `jvmMain` (هنوز نه) | ۲ (`PinCrypto`، `ApproverVault`) + actual ِ JVM ِ `Platform` |
| `iosMain` / `linuxX64Main` | ۱ / ۱ (actual ِ `Platform`؛ linuxX64 فقط نگهبانِ کامپایل) |
| `commonTest` / `jvmTest` / `iosTest` | ۱ / ۱ / ۱ |

وابستگیِ فایل‌های `jvmMain`: `Ulid` و `ReceiptTokens` (`SecureRandom`، `MessageDigest`)، `PinCrypto` (P-256، ECDSA، AES-GCM، HMAC، Base64)، `TextNormalizer` و `LocaleFormat` (`java.text.Normalizer`، `Locale`)، `Money` و `Rounding` (`Math.round`)، `LunarHijri` و `BusinessDay` (`Math.floorDiv`)، `CivilDays` (`Calendar`). پنج فایلِ دیگر (`ApproverVault`، `Payment`، `Shift`، `BackupRetention`، `SolarHijri`) ارجاعِ مستقیمِ JDK ندارند و احتمالاً فقط به‌خاطرِ تکیه بر فایل‌های بالا آن‌جایند.

**وضعِ ۱۴۰۵/۰۸/۰۸ (PR #426):** سیزده فایل به commonMain رفتند. `Math.floorDiv` ⇐ `floorDiv` ِ کاتلین؛ `Math.round`/`roundToLong` ⇐ `mathRound` (برگردانِ بیت‌به‌بیتِ `Math.round`، با آزمونِ میلیون‌ها عدد در برابرِ خودش؛ `roundToLong` ِ Native در لبه‌ها فرق دارد)؛ `SecureRandom`، `MessageDigest`، `System.currentTimeMillis` و `@Synchronized` ⇐ `core/platform/Platform.kt` (JVM همان JDK؛ iOS: `SecRandomCopyBytes`، `CC_SHA256`، `NSDate`، `NSLock`). `TextNormalizer`، `LocaleFormat` و `CivilDays` در عمل به `Normalizer`/`Locale`/`Calendar` تکیه نداشتند (فقط در توضیح). `PinCrypto` و `ApproverVault` (که به آن تکیه دارد) ماندند: AES-GCM ِ خام و ECDSA P-256 روی iOS در API ِ عمومیِ C نیستند (CryptoKit فقط Swift است) و قالبِ کلید (PKCS#8/SPKI ⇄ X9.63 ِ `SecKey`) باید جدا و با بردار ساخته شود؛ این کارِ جداگانه‌ای است و رمزنگاریِ PIN برای جابه‌جایی ضعیف نمی‌شود.
آزمون‌ها هنوز بیشتر در `android-native/app/src/test` (۱۵۸ فایل) هستند؛ از PR #426 بردارهای مرجعِ فایل‌های منتقل‌شده در `commonTest/CoreVectorsCommonTest` روی JVM و شبیه‌سازِ iOS هم اجرا می‌شوند.

### ۲.۴ مرزهایی که کامپیوتر از پیش پیدا کرده

`desktop/build.gradle.kts` (فهرستِ `replaced`) این ۱۷ فایل را با نسخهٔ کامپیوتری عوض می‌کند. این‌ها دقیقاً همان مرزهای پلتفرم‌اند و آیفون هم برای هر کدام یک پیاده‌سازی لازم دارد:
`MainActivity`، `BuildConfig`، `core/net/TokenStore`، `data/Fingerprint`، `data/AtRestKeys`، `data/TohidBackupAgent`، `data/GoogleSignIn`، `data/Migration`، `data/FileName`، `scan/BarcodeScanner`، `scan/GuidedCapture`، `sync/Integrity`، `update/Updater`، `print/ReportPrint`، `print/LabelSheetPrint`، `ui/PressMenu`، `ui/Platform`.
به‌علاوهٔ ۴۲ shim در `desktop/src/shim` (بلوتوث، USB، اعلان، WorkManager، `SharedPreferences`، `Bitmap`/`Canvas`، `StaticLayout`، دوربین/نتیجهٔ Activity، `FileProvider`…) که روی آیفون باید به رابطِ واقعی تبدیل شوند، نه shim.

## ۳. سرویس‌های پلتفرم و جایگزینِ iOS

| سرویس | امروز در اندروید (شمارِ فایل) | جایگزین در iOS / KMP | سختی |
|---|---|---|---|
| ذخیرهٔ کلید-مقدار | `SharedPreferences` (۶۰، ۱۵ پرونده) | رابطِ `KeyValueStore` در commonMain؛ اندروید همان پرونده‌ها و کلیدها را نگه می‌دارد (بی مهاجرتِ داده)، iOS با `NSUserDefaults` (یا کتابخانهٔ `multiplatform-settings`) | متوسط، پرشمار |
| فایل و دفترِ محلی | `java.io.File` (۴۲)، `FileChannel` قفل (۲)، `nio.Files` | `okio` (چندسکویی: `FileSystem.SYSTEM`، نوشتنِ اتمی با rename)؛ قفلِ فایل روی iOS لازم نیست (یک فرایند) ولی رابطش بماند | متوسط |
| پایگاه‌داده | ندارد | لازم نیست. اگر بعداً شد: SQLDelight (چندسکویی) | — |
| HTTP | `HttpURLConnection` (۴)، `core/net/HttpEngine` | Ktor Client؛ موتورِ OkHttp روی اندروید، Darwin روی iOS؛ دانلودِ ازسرگیری‌پذیر با `Range` | متوسط |
| سوکتِ زنده | OkHttp WebSocket (`LiveSocket`) | Ktor WebSockets؛ روی iOS در پس‌زمینه قطع می‌شود ⇐ هنگامِ بازگشت به برنامه دوباره وصل و همگام شود | متوسط |
| JSON | `org.json` (۹) + kotlinx.serialization (۷۸) | فقط kotlinx.serialization | کم |
| زمان و تاریخ | `currentTimeMillis` (۸۶)، `SimpleDateFormat`/`Calendar` (۲۷) | `kotlin.time.Clock` یا `kotlinx-datetime`؛ تقویمِ شمسی/قمری همان کدِ `core` | کم، پرشمار |
| قالب‌بندیِ متن و عدد | `String.format` (~۲۸)، `Locale`، `Normalizer` | قالب‌گرهای `core` (`LocaleFormat`)؛ برای `Normalizer` جدولِ نویسهٔ خودمان (فارسی/عربی) یا `expect` با `NSString` | کم تا متوسط |
| هم‌زمانی | `synchronized`/`@Volatile` (۳۶)، `Executors` | `kotlinx.coroutines.sync.Mutex`، `atomicfu` | کم |
| رمزنگاری | `MessageDigest` (۱۰)، `Signature`/`KeyFactory` (۳)، AES-GCM (۲)، `PinCrypto` در core | `expect/actual` یا کتابخانهٔ `cryptography-kotlin` (فراهم‌کنندهٔ Apple)؛ SHA-256 با `kotlincrypto`؛ تصادفیِ امن با `SecRandomCopyBytes` | متوسط، **حساس** |
| کلیدِ امن / Keystore | `AndroidKeyStore` (۲)، `EncryptedSharedPreferences` (۱)، `TokenStore` | Keychain (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`)؛ Secure Enclave فقط P-256 | متوسط، **حساس** |
| اثرِ انگشت/چهره | `BiometricPrompt` (۳) | `LocalAuthentication` (Face ID / Touch ID) | کم |
| ورود با گوگل | Credential Manager + GoogleID (۱) | Google Sign-In iOS SDK؛ **قاعدهٔ ۴.۸ِ App Store**: با ورودِ شخصِ ثالث، یک گزینهٔ هم‌ارزِ حریم‌دار (مثلِ Sign in with Apple) هم لازم است | متوسط |
| شناسهٔ دستگاه | `data/Fingerprint`، `Settings.Secure` | `identifierForVendor` + ذخیره در Keychain (پس از حذف و نصب می‌ماند) | کم |
| سلامتِ برنامه | `sync/Integrity` | App Attest / DeviceCheck | متوسط |
| دوربین و بارکد | CameraX (۲) + ML Kit (۱)، `GuidedCapture` | AVFoundation (`AVCaptureMetadataOutput`) یا VisionKit `DataScannerViewController` (iOS 16+)، با `UIKitView` در Compose | متوسط |
| بارکدخوانِ دستی (HID) | `scan/HidScan`، `ScanKeyEvent` | بارکدخوانِ بلوتوثی روی iOS مثلِ صفحه‌کلید کار می‌کند؛ رخدادِ کلیدِ سخت‌افزاری در Compose iOS باید آزموده شود | متوسط، **نامعلوم** |
| چاپِ فاکتور (حرارتی) | `ThermalPrinter`: بلوتوثِ کلاسیک SPP، USB، TCP (`Socket`) | **SPP و USB روی آیفون ممکن نیست** (بخشِ ۵). شدنی: چاپگرِ BLE (CoreBluetooth)، چاپگرِ شبکه TCP 9100 (Network.framework یا سوکتِ Ktor) | زیاد |
| چاپِ گزارش/برچسب | `android.print` + `PdfDocument` (۲) | AirPrint با `UIPrintInteractionController`؛ PDF با `UIGraphicsPDFRenderer` | متوسط |
| نقاشیِ فاکتور | `Bitmap`/`Canvas` (۲۱)، `StaticLayout` (۴) | Canvas و `TextMeasurer`ِ Compose (Skia، چندسکویی)؛ یعنی `ReceiptPainter` می‌تواند مشترک شود | متوسط |
| اعلانِ محلی | `NotificationCompat` (۲: `Watchman`، `Reminders`) | `UNUserNotificationCenter` | کم |
| اعلانِ فوری از سرور | ندارد (FCM نیست) | APNs: کلیدِ p8 از حسابِ اپل + ارسال از سرور | متوسط (کارِ سرور) |
| کارِ پس‌زمینه | WorkManager (۵: `AutoBackup`، `LocalSnapshots`، `Reminders`، `Watchman`، `SyncV1Worker`) | `BGTaskScheduler`؛ **زمانش دستِ سیستم است** (بخشِ ۵) | زیاد |
| موقعیت (پیک) | `DeviceLocation`، `LocationPing`، `VisitPing` | CoreLocation؛ پس‌زمینه فقط با مجوزِ «همیشه» و `UIBackgroundModes=location` | متوسط، **بازبینیِ اپل** |
| پشتیبان | `BackupAgent` (۴)، `BackupMirror` | پشتیبانِ iCloud ِ خودکارِ پوشهٔ Documents؛ خروجی با برگهٔ اشتراک (`UIActivityViewController`) | کم |
| فایل/اشتراک | `FileProvider` (۳)، `contentResolver`/`Uri` (۲۲)، `ActivityResult` (۱۵) | `UIDocumentPickerViewController`، `UIActivityViewController`، `PHPicker` برای عکس | متوسط |
| به‌روزرسانیِ خودکار | `update/*` (APK) | **ممنوع روی iOS** (قاعدهٔ ۲.۵.۲)؛ فقط App Store / TestFlight. `update/` روی iOS ساخته نمی‌شود | — |
| `PackageManager`/نصب | ۱۳ فایل | روی iOS حذف | — |
| صدا و لرزش | `android.media` (۲)، `Vibrator` (۳) | `AVAudioPlayer`، `UIImpactFeedbackGenerator` | کم |
| گفتار | `RecognizerIntent` (۱) | `SFSpeechRecognizer`؛ پشتیبانیِ فارسی باید آزموده شود | **نامعلوم** |
| کلیپ‌بورد | `LocalClipboardManager` (۶) | همان در Compose Multiplatform | — |
| ترازو، نمایشگرِ مشتری، درگاهِ سریال | `hw/HwPlatform` (`SerialPorts`، `ScaleReader`، `CustomerFacing`) | روی آیفون نیست؛ فقط ترازوی BLE اگر پروتکلش معلوم باشد | زیاد / ناممکن |
| WebView | ۲ فایل | `WKWebView`؛ ولی قاعدهٔ «صفر درصد WebView» برای برنامهٔ بومی همین‌جا هم باید بماند | — |
| منابع (فونت، صدا، تصویر) | `R.` (۱۰)، `painterResource` | `composeResources` (`Res.font`، `Res.drawable`) | کم |
| برگشت | `BackHandler` (۶) | ژستِ برگشتِ iOS؛ در ناوبریِ چندسکویی | کم |

## ۴. طرحِ مرحله‌ای

### گامِ ۰ — نگهبان: جلوی بدتر شدن را بگیر (۱ تا ۲ روز)

**قاعده‌های کدِ تازه** (پیشنهاد برای `docs/RULES-fa.md`، بخشِ «هسته‌های مشترک»؛ در این PR نوشته نشد):
1. منطقِ تازه (موتور، قاعده، محاسبه، تبدیلِ داده) در `core/src/commonMain`؛ نه در `android-native/app`.
2. در منطق ممنوع: `android.*`، androidxِ غیرِ Compose، `Context`، `SharedPreferences`، `java.*`، `javax.*`، `org.json`، `System.currentTimeMillis`، `String.format`، `synchronized`. به‌جایش: `Clock` تزریق‌شده، `KeyValueStore`، `okio`، kotlinx.serialization، `Mutex`.
3. هر نیازِ پلتفرمی پشتِ یک رابط در commonMain (`interface` یا `expect`) و پیاده‌سازی فقط در پوشه‌های مرزی (`.../platform/` یا فایل‌های `*Android.kt`/`*Desk.kt`).
4. صفحهٔ تازه `LocalContext` نمی‌گیرد؛ سرویس‌ها را از یک `CompositionLocal` (مثلاً `LocalAppServices`) می‌گیرد.
5. وابستگیِ تازهٔ Gradle فقط وقتی نسخهٔ چندسکویی (iOS) دارد، یا پشتِ رابط می‌ماند.

**بررسیِ خودکار در CI — شدنی است.** پیشنهاد:
- یک سنجهٔ تازه در `tools/quality/checks/` (مثلاً `platformDeps.mjs`) کنارِ سنجه‌های «فقط‌کاهشیِ» موجود (`ratchet` در `tools/quality/quality.mjs`، سقف‌ها در `baseline.json`).
- برای هر فایلِ `android-native/app/src/main/java/**/*.kt` شمارِ الگوهای ممنوع (فهرستِ بندِ ۲ بالا) را بشمارد. **فایلِ تازه** با هر الگوی ممنوع ⇐ قرمز، مگر در پوشهٔ مرزی باشد. **فایلِ موجود** فقط می‌تواند کمتر شود (سقفِ هر فایل در `baseline.json`؛ با `--lower-baseline` پایین می‌آید، هرگز بالا نمی‌رود).
- بر پایهٔ همان سازوکارِ `cause: 'introduced'`، فقط آنچه همین تغییر ساخته قرمز می‌کند؛ بدهیِ قدیمی گزارش می‌شود ولی جلوی کار را نمی‌گیرد.
- برای `core`: اکنون `ios-spike` فقط روی macOS و فقط وقتی `core/**` عوض شود اجرا می‌شود. نگهبانِ ارزان‌تر: یک هدفِ `linuxX64()` پشتِ پرچم (مثلاً `-Pnative=true`) که روی ubuntu بی Xcode کامپایل می‌شود و هر `java.*` ِ تازه در commonMain را همان لحظه می‌گیرد؛ چون با یک هدفِ jvm تنها، کامپایلر ممکن است `java.*` را در commonMain نبیند.
- آغاز به‌صورتِ «گزارش» (`continue-on-error` مثلِ بقیهٔ سنجه‌ها)، پس از یک هفته بی‌خطا: سد برای فایلِ تازه.

### گامِ ۱ — جدا کردنِ منطق و رابطِ کاربریِ مشترک از پلتفرم (۱۰ تا ۱۶ هفته)

ساختارِ هدف:

```
core/          KMP — پول، متن، تقویم، قاعده‌ها (هست)؛ jvmMain ⇐ expect/actual با iosMain
shared/        KMP تازه — موتورها و انبارها و همگام‌سازی، روی رابط‌ها:
               KeyValueStore، FileStore، Http، Clock، Crypto، SecureStore،
               Notifier، Scheduler، PrinterTransport، Scanner، Location
shared-ui/     Compose Multiplatform — صفحه‌های ui/، با LocalAppServices به‌جای LocalContext
android-native/app   پوستهٔ اندروید + پیاده‌سازیِ رابط‌ها (همین کدِ امروز، جابه‌جاشده)
desktop/       پوستهٔ JVM؛ کم‌کم به shared-ui وصل می‌شود و shimها کوچک می‌شوند
iosApp/        پروژهٔ Xcode + iosMain (گامِ ۲)
```

ترتیبِ برش‌ها (هر برش یک PRِ کوچک، اندروید و کامپیوتر باید سبز بمانند):

| # | برش | دامنه (از شمارش) | برآورد |
|---|---|---|---|
| ۱-الف | `core/jvmMain` ⇐ commonMain + `expect/actual` (iosMain با `SecRandomCopyBytes`، CommonCrypto/`cryptography-kotlin`)؛ انتقالِ آزمون‌های core به `commonTest` | ۱۵ فایل + آزمون‌ها | ۱ تا ۲ هفته |
| ۱-ب | زمان، قالب، هم‌زمانی، `org.json` | ۵۸ فایلِ «فقط JDK» + بخشی از بقیه | ۱ تا ۲ هفته |
| ۱-پ | `KeyValueStore` به‌جای `SharedPreferences`، **با همان ۱۵ نامِ پرونده و همان کلیدها** در اندروید | ۶۰ فایل | ۲ هفته |
| ۱-ت | `FileStore` روی okio به‌جای `java.io.File`/`nio`؛ همان مسیرها | ۴۲ فایل | ۱ تا ۲ هفته |
| ۱-ث | شبکه: Ktor به‌جای `HttpEngine` و OkHttp (موتورِ OkHttp روی اندروید ⇐ رفتار همان) | ۵ فایل، ولی همهٔ همگام‌سازی به آن تکیه دارد | ۱ تا ۲ هفته |
| ۱-ج | انتقالِ موتورها/انبارهای آزادشدهٔ `data/`، `sync/`، `core/` به `shared/` | از ۱۷۹ فایلِ این سه پوشه، هر چه آزاد شده | ۱ تا ۲ هفته |
| ۱-چ | `LocalContext` ⇐ `LocalAppServices`؛ منابع ⇐ `composeResources`؛ انتقالِ `ui/` به `shared-ui` | ۱۳۱ فایل (۴۴ فقط Context) | ۲ تا ۳ هفته |
| ۱-ح | نقاشیِ فاکتور با Canvasِ Compose؛ `PrinterTransport` جدا از قالبِ ESC/POS | ۱۱ فایلِ `print/` | ۱ هفته |

**خطرهای گامِ ۱:**
- **صندوق و پول**: هر جابه‌جایی ممکن است رفتارِ فروش را عوض کند. با ۱۵۸ فایلِ آزمونِ موجود + آزمون‌های بردارِ `core/test-vectors` پیش و پس از هر برش سنجیده شود؛ هیچ برشی منطق را «بهتر» نمی‌کند، فقط جابه‌جا می‌کند.
- **دادهٔ مشتری‌ها**: اگر نامِ پرونده یا کلیدِ `SharedPreferences` یا مسیرِ فایل عوض شود، دادهٔ نصب‌های موجود گم می‌شود. قاعده: رابطِ تازه روی همان نام‌ها؛ بی مهاجرت.
- **ساختِ کامپیوتر** امروز با کپیِ سورس و جایگزینیِ عبارت‌ها (`androidSources`) کار می‌کند؛ با چندماژولی شدن باید هم‌زمان بازآرایی شود.
- **PRهای باز** (#359، #390، #391، #393، #400، #412 روی `ui/`): جابه‌جاییِ بزرگِ پوشه‌ها با آن‌ها برخورد می‌کند. برش‌های ۱-ج و ۱-چ پس از تعیینِ تکلیفِ آن‌ها.

### گامِ ۲ — پوستهٔ iOS (۸ تا ۱۲ هفته، پس از گامِ ۱)

پیشنهادِ ترتیب: **اول حالتِ پیک، بعد صندوق.** پیک طبقِ §۴۰.۲ فقط با سرور کار می‌کند و به صندوق و انبار وابسته نیست؛ پس کوچک‌ترین سطح است و برای آزمودنِ زنجیرهٔ کامل (ورود، Keychain، شبکه، موقعیت، اعلان، انتشار) بهترین است.

| # | کار | برآورد |
|---|---|---|
| ۲-الف | پروژهٔ Xcode، `MainViewController` ِ Compose، امضا، CI روی macOS (گسترشِ `ios-spike`) | ۱ هفته |
| ۲-ب | iosMain برای رابط‌ها: Keychain، `NSUserDefaults`، okio، Ktor Darwin، Clock، رمزنگاری | ۲ هفته |
| ۲-پ | ورود (گوگل + Sign in with Apple)، Face ID، شناسهٔ دستگاه، App Attest | ۱ تا ۲ هفته |
| ۲-ت | **حالتِ پیک**: CoreLocation، اعلانِ محلی، بازگشت و همگام‌سازی هنگامِ باز شدن | ۱ تا ۲ هفته |
| ۲-ث | صندوق: دوربین/بارکد (VisionKit)، چاپگرِ BLE و شبکه، AirPrint برای گزارش، برگهٔ اشتراک | ۲ تا ۳ هفته |
| ۲-ج | فونت و راست‌به‌چپ، صفحه‌کلیدِ فارسی، VoiceOver، اندازه‌های آیفون و آیپد | ۱ هفته |
| ۲-چ | TestFlight، فرمِ حریمِ خصوصی (Privacy Manifest)، بازبینیِ App Store | ۱ هفته + زمانِ اپل |

**جمعِ برآورد:** حدودِ **۴ تا ۷ ماه** کارِ یک برنامه‌نویسِ آشنا با KMP (گامِ ۰ + ۱ + ۲)، به شرطِ این‌که هم‌زمان کارِ بزرگِ دیگری روی همان پوشه‌ها نباشد. نخستین نسخهٔ پیک روی TestFlight حدودِ هفتهٔ ۴ تا ۶ ِ گامِ ۲.

## ۵. محدودیت‌های واقعیِ آیفون (صادقانه)

1. **چاپِ بلوتوث**: بیشترِ چاپگرهای حرارتیِ ارزان «بلوتوثِ کلاسیک» (SPP) دارند. آیفون SPP را فقط برای لوازمِ دارای گواهیِ MFi (چارچوبِ External Accessory) باز می‌کند؛ چاپگرهای معمولیِ بازار این گواهی را ندارند. **این با کد درست نمی‌شود.** راه‌های شدنی: چاپگرِ BLE (باید مدل‌به‌مدل آزموده شود؛ سرعتِ BLE برای تصویرِ فاکتور کم است)، چاپگرِ شبکه (Wi-Fi/LAN، درگاهِ 9100)، یا AirPrint (چاپگرِ حرارتیِ AirPrint کم و گران است). USB ِ مستقیم هم نیست.
2. **ترازو، نمایشگرِ مشتری، درگاهِ سریال، بارکدخوانِ USB**: روی آیفون نیست. صندوقِ آیفون صندوقِ «سبک» است؛ صندوقِ کامل همان اندروید و کامپیوتر می‌ماند.
3. **اجرای پس‌زمینه**: iOS برنامه را چند ثانیه پس از رفتن به پس‌زمینه متوقف می‌کند. `BGAppRefreshTask` زمانش دستِ سیستم است (ممکن است ساعت‌ها اجرا نشود) و `BGProcessingTask` معمولاً فقط هنگامِ شارژ. پس پشتیبانِ خودکار، یادآورِ بدهی (`Reminders`)، `Watchman` و همگام‌سازیِ دوره‌ای روی آیفون **تضمینی نیستند**؛ باید هنگامِ باز شدنِ برنامه انجام شوند و کارهای مهم به سرور برود.
4. **اعلانِ فوری**: اعلانِ محلی شدنی است، ولی اعلانی که «همان لحظه» از رویدادِ سرور بیاید (سفارشِ تازه، پیکِ تازه) فقط با APNs شدنی است: کلید از حسابِ اپل و فرستادن از سرور. امروز سرور هیچ سامانهٔ فشاری (FCM هم) ندارد؛ این کارِ تازهٔ سرور است. سوکتِ زنده در پس‌زمینه کار نمی‌کند.
5. **موقعیتِ پیک در پس‌زمینه**: با مجوزِ «همیشه» و حالتِ پس‌زمینهٔ location شدنی است، ولی اپل در بازبینی دلیلِ روشن می‌خواهد، کاربر هر وقت بخواهد خاموشش می‌کند و iOS گاه‌به‌گاه به کاربر یادآوری می‌کند. ردیابی فقط در «زمانِ تحویل» پیشنهاد می‌شود.
6. **به‌روزرسانی**: به‌روزرسانیِ درون‌برنامه‌ای (مثلِ APK) ممنوع است؛ هر نسخه باید از بازبینیِ اپل بگذرد (معمولاً ۱ تا ۳ روز). رفعِ فوریِ خطا کندتر از اندروید است.
7. **حسابِ توسعه‌دهندهٔ اپل**: بی آن نه TestFlight هست، نه App Store، نه APNs، نه نصبِ پایدار (نصب با Xcode و حسابِ رایگان هر ۷ روز باطل می‌شود). هزینه ۹۹ دلار در سال؛ حسابِ سازمانی شمارهٔ D-U-N-S می‌خواهد.
   **خطرِ جدی برای ایران**: اپل به‌خاطرِ تحریم‌ها برنامهٔ توسعه‌دهنده را برای ساکنانِ ایران ارائه نمی‌کند و App Store در ایران رسماً نیست؛ برنامه‌های ایرانی بارها از App Store برداشته شده‌اند. حسابی به نامِ شخص یا شرکتی خارج از ایران ممکن است، ولی خطرِ بسته شدن دارد و پرداخت/مالکیتِ حساب باید واقعی و قانونی باشد. راه‌های جایگزین (فروشگاه‌های ایرانیِ نصب با گواهیِ سازمانی) خلافِ قوانینِ اپل‌اند و گواهی‌شان مدام باطل می‌شود. **پیش از گامِ ۲ صاحب محصول باید راهِ انتشار را تعیین کند**؛ تا آن روز نسخهٔ وب (طبقِ §۴۰.۳) برای آیفون می‌ماند. (این بند از دانسته‌های عمومی است و باید با وضعِ روزِ تحریم و قوانینِ اپل دوباره سنجیده شود.)
8. **قاعده‌های بازبینیِ App Store که به ما می‌خورند**: ۴.۸ (ورود با گوگل ⇐ Sign in with Apple هم)، ۲.۵.۲ (بی دانلودِ کد)، ۵.۱.۱ (حذفِ حساب از درونِ برنامه لازم است)، Privacy Manifest برای APIهای حساس (`UserDefaults`، زمانِ فایل)، و توضیحِ روشنِ هر مجوز (دوربین، موقعیت، بلوتوث).
9. **پختگیِ Compose Multiplatform روی iOS**: نسخهٔ پایدار است، ولی ورودیِ متنِ فارسی و راست‌به‌چپ، رخدادِ کلیدِ سخت‌افزاری (بارکدخوان)، VoiceOver و حسِ اسکرول باید روی دستگاهِ واقعی آزموده شوند. اندازهٔ برنامه چند ده مگابایت بیشتر از برنامهٔ Swift است.
10. **ساخت فقط روی مک**: کامپایل و امضای iOS فقط روی macOS (رانرِ macOS ِ GitHub گران‌تر و کندتر است؛ کش ِ `~/.konan` همین حالا در `ios-spike` هست).

## ۶. آنچه صاحب محصول باید تصمیم بگیرد

1. راهِ انتشار در آیفون و صاحبِ حسابِ اپل (بندِ ۵.۷) — **پیش از گامِ ۲**.
2. آیا صندوقِ آیفون «سبک» (بی ترازو و نمایشگرِ مشتری و چاپگرِ کلاسیک) پذیرفته است؟
3. ترتیب: اول پیک، بعد صندوق (پیشنهادِ این طرح).
4. آیا گامِ ۰ (قاعده‌ها + سنجهٔ CI) همین حالا ساخته شود؟ کم‌هزینه است و جلوی بزرگ‌تر شدنِ کار را می‌گیرد.

## پیوستِ الف — روشِ شمارش

همه در `android-native/app/src/main/java`، روی فایل‌های `.kt`، با `grep -l` (شمارِ فایل، نه شمارِ خط):

- «وابسته به اندروید»: `^import (android\.|androidx\.(activity|core|work|fragment|biometric|camera|lifecycle|credentials|security|webkit)|com\.google|okhttp3)`، یا نامِ کاملِ `android.x.Y`، یا `\bContext\b`، `SharedPreferences`، `LocalContext`، `\bR\.`.
- «فقط JDK»: هیچ‌کدامِ بالا، ولی یکی از `^import (java\.|javax\.|org\.json)`، نامِ کاملِ `java.x.Y`، `System.currentTimeMillis|nanoTime`، `String.format`/`.format(`، `synchronized(`، `@Volatile`، `@Synchronized`، `Thread(`.
- «خالص»: هیچ‌کدامِ بالا.
- `androidx.compose.*` وابستگی شمرده نشد، چون در Compose Multiplatform همان نام‌هاست.
- شمارِ خط با `wc -l`.
