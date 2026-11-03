# طرحِ برنامهٔ بومیِ آیفون — برنامهٔ مدیریت (صندوق + حالتِ پیک)

> بررسی: ۱۴۰۵/۰۸/۰۸ (۳۰ اکتبر ۲۰۲۶)، شاخهٔ `claude/research-ios-readiness`، روی کامیتِ `f557986`.
> پایه: §۴۰.۳ِ `docs/spec/40-owner-decisions.md` (KMP + Compose Multiplatform، همان کد، بی بازنویسی).
> در این بررسی هیچ کدی عوض نشد. عددها با `grep` شمرده شده‌اند (روشِ شمارش در پیوستِ الف). برآوردِ زمان تخمین است، اندازه‌گیری نیست.

## ۰. گامِ ۱ِ برنامهٔ آیفون از همان رابط (۱۴۰۵/۰۸/۱۲، شاخهٔ `claude/ios-app-phase1`)

> تصمیمِ تازهٔ صاحب محصول: برنامهٔ آیفون **از همین حالا**، کنارِ اندروید و کامپیوتر، **از همان کدِ رابط و منطق** ساخته شود تا
> بعداً از صفر نوشته نشود. این بخش وضعِ واقعیِ همین PR است؛ بخش‌های ۱ تا ۶ (بررسیِ ۱۴۰۵/۰۸/۰۸) تاریخچه‌اند.
> **تصحیحِ بندِ ۱.۲:** ترفندِ کامپیوتر برای آیفون **کار می‌کند**. Kotlin/Native بسته‌ای به نامِ `java.io` یا `java.util` را
> از کدِ خودِ ما می‌پذیرد (فقط `kotlin.*` بسته است)، پس همان shimِ نام‌به‌نام ِ `desktop/src/shim` برای JDK هم شدنی است.

### ۰.۱ معماری (آینهٔ `desktop/`)

```
android-native/app/src/main/java   ← تنها کدِ رابط و منطق (دست‌نخورده)
        │  کپی هنگامِ ساخت (کارِ `iosSources`)، بی فایل‌های ios/shared/ios-exclude.txt،
        │  با یک خطِ `import ir.vil3ntec.tohid.ios.jvm.*` سرِ هر فایل و `@Synchronized` ⇐ `synchronized(Unit) {…}`
        ▼
ios/shared/build/generated/ios-src  ──┐
ios/shared/src/iosMain/kotlin         ├─ Kotlin/Native + Compose Multiplatform (iosArm64، iosSimulatorArm64؛ فقط با -Pios=true)
  ├ java/** ، javax/**  shimِ JDK      │   ⇒ چارچوبِ ایستای `TohidShared`
  ├ android/** ، androidx/** shimِ اندروید (همان نام‌های desktop/src/shim)
  ├ ir/…/ios/jvm  System، Math، Character، Charsets، synchronized، @Volatile، String.format، Class
  ├ ir/…/*Ios.kt  جایگزین‌ها (همان API ِ فایلِ اندروید؛ مثلِ «replaced» ِ کامپیوتر)
  └ ir/…/ios/IosRoot.kt + MainViewController.kt  ریشهٔ آیفون
core/  ← همان includeBuild (هدف‌های iOS ِ آن هم با -Pios=true)
        ▼
ios/iosApp (SwiftUI)  `MainViewControllerKt.MainViewController()` در `UIViewControllerRepresentable`؛ قلمِ Vazirmatn از res/font
```

- نسخه‌ها همان کامپیوترند: Kotlin ‎2.2.20، Compose Multiplatform ‎1.9.3، material3 ‎1.9.0، lifecycle ‎2.9.4، coroutines ‎1.10.2.
- `ios/shared` ساختِ جداست (مثلِ `core`): اندروید و کامپیوتر آن را نمی‌خوانند ⇐ رفتار و خروجیِ آن دو **بایت‌به‌بایت همان** است
  (این PR هیچ فایلی در `android-native/`، `desktop/` و `core/` عوض نکرد).
- فایلِ تازه‌ای در `android-native` که در `ios-exclude.txt` نیست خودبه‌خود به iOS می‌رسد؛ اگر به JVM تکیه کند کارِ `ios-ui-klib` سرخ (گزارش) می‌شود.

### ۰.۲ شمارشِ JVM/اندروید در منابعِ ساخته‌شده (همان `androidSources` ِ کامپیوتر)

روی `desktop/build/generated/android-src` (کپیِ کامپیوتر، بی ۲۰ فایلِ `replaced`): **۱۰۷۶ فایل، ۲۰۲٬۹۱۳ خط**. **۵۳۸ فایل**
(۵۰٪) دست‌کم یک API ِ فقط-JVM یا فقط-اندروید دارند؛ ۵۳۸ فایل (۷۳٬۱۳۰ خط) هیچ. شمارش با `grep` روی متنِ بی توضیح (روش: پیوستِ ب).

| API | فایل | رخداد |
|---|---:|---:|
| `Context` / `LocalContext` | ۳۸۶ | ۱٬۷۸۴ |
| `android.*` (import یا نامِ کامل) | ۲۶۳ | ۵۷۴ |
| `System.*` (`currentTimeMillis` ۳۵۳، `nanoTime` ۱۲…) | ۱۷۲ | ۳۶۷ |
| `SharedPreferences` | ۱۴۱ | ۱۹۱ |
| androidx ِ غیرِ Compose (`activity`، `work`، `core`…) | ۷۰ | ۱۸۶ |
| `Math.*` / `Character.*` | ۶۸ | ۱۳۳ |
| `Dispatchers.IO` (در Native import می‌خواهد) | ۶۳ | ۱۴۵ |
| Compose ِ فقط-اندروید (`BackHandler`، `LocalConfiguration`، launcher، `LocalView`…) | ۶۳ | ۱۹۳ |
| `java.util.*` (`Calendar`، `Locale`، `UUID`، `Date`…) | ۵۱ | ۱۶۶ |
| `java.io.File` | ۴۶ | ۱۶۱ |
| `Charsets.*` | ۴۶ | ۷۵ |
| `@Volatile` | ۴۱ | ۹۱ |
| `java.io.*` ِ دیگر (جریان‌ها، `IOException`) | ۳۸ | ۷۳ |
| `synchronized` / `@Synchronized` | ۳۶ | ۱۵۱ |
| `String.format` / `java.text` | ۳۴ | ۳۸ |
| `java.net` / `HttpURLConnection` | ۱۸ | ۴۳ |
| `Locale` | ۱۸ | ۲۳ |
| `java.security` / `javax.crypto` | ۱۷ | ۳۹ |
| `java.util.concurrent` / `Atomic*` | ۱۳ | ۳۵ |
| `org.json` | ۱۰ | ۱۰۷ |
| `Thread` | ۹ | ۱۱ |
| بازتاب (`::class.java`، `javaClass`) | ۸ | ۸ |
| منابعِ `R.*` | ۶ | ۱۴ |
| `java.nio` | ۴ | ۹ |
| `javax.*` ِ دیگر | ۳ | ۸ |
| `okhttp3` | ۲ | ۶ |
| `java.lang.*` | ۲ | ۵ |
| `java.time` / `com.google.*` / `@JvmStatic`… | ۰ | ۰ |

بیشترین رخداد در یک فایل: `print/PrintSpooler` (۷۷)، `platform/ServerSwitchAndroid` (۶۶)، `data/ShopStore` (۵۸)، `print/ThermalPrinter` (۵۷)،
`data/ImageBank` (۵۱)، `print/PrintJob` (۴۶)، `ui/screens/Settings` (۴۴). به تفکیکِ پوشه (فایل / بی وابستگی): `ui` ۷۰۴/۳۷۱،
`data` ۱۸۲/۶۷، `core` ۱۱۲/۸۷، `sync` ۳۴/۶، `print` ۱۹/۰، `scan` ۹/۵.

### ۰.۳ آنچه در گامِ ۱ برای iOS کامپایل شد (گامِ ۲ ⇐ ۰.۷)

همهٔ ۱۰۹۶ فایلِ `android-native/app/src/main/java` (همان ۱۰۷۶ + ۲۰ فایلِ `replaced` ِ کامپیوتر):

| | فایل |
|---|---:|
| **کامپایل‌شده برای iOS** (klib ِ `iosSimulatorArm64`) | **۳۰۸** (۳۹٬۱۷۶ خط): `ui` ۱۴۸، `core` ۹۷، `data` ۴۱، `scan` ۷، `sync` ۵، `print` ۳، `pay` ۲، ریشه ۵ |
| بیرون: API ِ بی shim در خودِ فایل | ۱۲۷ (`android.graphics` ۵۳، androidx ۴۳، `android.content.*` ِ دیگر ۳۸، `java.io` ِ جریان ۲۶، `Manifest` ۲۶، `android.app` ۱۸، `java.security` ۱۳، `org.json` ۱۱…) |
| بیرون: خودش پاک، ولی به فایلِ بیرون‌مانده تکیه دارد | ۶۵۸ |
| جایگزینِ iOS (همان API) | ۳ (`ui/Platform`، `ui/screens/Haptics`، `sync/v1/SyncV1Worker`) |

shimها و جایگزین‌ها: ۳۵ فایل، ۱٬۸۱۴ خط در `ios/shared/src/iosMain/kotlin` — `Context` و `SharedPreferences` (روی
NSUserDefaults، همان نامِ پرونده‌ها و کلیدها)، `java.io.File` (NSFileManager، نوشتنِ اتمی)، `Calendar`/`TimeZone`/`GregorianCalendar`
(NSCalendar)، `SimpleDateFormat` (فقط الگوهای عددی)، `Locale`، `String.format` (`%d %s %f %x %c` با پرچم/پهنا/دقت؛ گردِ نیمه‌بالا مثلِ JDK)،
`URLEncoder`/`URLDecoder`، رده‌های خطای شبکه، `SecureRandom` (`SecRandomCopyBytes`)، `BigInteger` (فقط برای مسیرِ سرریزِ `StockCore`)،
`AtomicReference`/`Executors` (صفِ سریالِ GCD)، `Uri`، `Intent` (نشانی‌ها با `openURL`)، `BackHandler`، launcherهای انتخابِ فایل،
`Build` (`SDK_INT = 0`)، `Settings.Global` («کاهشِ حرکت» ِ iOS)، `LocalConfiguration`، `Font(R.font…)`.

**ریشهٔ آیفون** (`IosRoot`): `TohidTheme` و قلمِ Vazirmatn ⇐ نخستین صفحهٔ برنامه (`ModeChooser`، «دکان/پیک») ⇐ کیتِ نسخهٔ ۳
(`KitGallery`، چهار بخش) با `V3TabBar`. همه composable ِ مشترک؛ هیچ صفحه‌ای دوباره نوشته نشد.
**ورود، فروش و فهرستِ کالا هنوز نه** — زنجیره‌ای که جلویشان را گرفته (از خطای کامپایل):

| صفحه | زنجیره | گره |
|---|---|---|
| فروش (`SaleScreen`) و هر موتورِ دفتر (`SalesEngine`، `ReportEngine`، `DebtorEngine`…) | `ShopStore` ⇐ `AtRestException` | `core/atrest/AtRestEnvelope` (AES-GCM ِ `javax.crypto`) |
| ورود (`EmailCodeSheet`) و هر چیزِ سروری | `Backend`/`ApiClient` ⇐ `HttpEngine` | `HttpURLConnection` + `platform/ServerSwitchAndroid` |
| فهرستِ کالا (`ProductsScreen`) و کاشیِ کالا (`PhoneCatalog`) | `PhotoStore` / `rememberProductImage` | `android.graphics.Bitmap`/`BitmapFactory` |
| تنظیمات و `ProvideLang` | `SettingsParts` ⇐ `SettingsLayout` ⇐ ثابت‌های `StockBook` | چند فایلِ androidx/`android.*` در همان زنجیره |

### ۰.۴ گام‌های بعد (هر برش یک PR؛ اندروید و کامپیوتر دست نمی‌خورند)

| # | برش | کار | باز می‌کند |
|---|---|---|---|
| ۲-الف | **شبکه** | shim ِ `java.net.URL`/`HttpURLConnection` روی NSURLSession (همگام، درونِ `Dispatchers.IO`) و جریان‌های `java.io`؛ جایگزینِ iOS ِ `ServerSwitchAndroid` (پین/دامنه) | `HttpEngine`، `ApiClient`، `Backend` ⇐ **ورود** |
| ۲-ب | **دفترِ رمزشده** | AES-GCM روی iOS (CryptoKit از راهِ Swift یا `cryptography-kotlin`) و کلید در Keychain پشتِ همان `AtRest`/`AtRestKeys`؛ نه دفترِ بی‌رمز | `ShopStore` ⇐ **فروش**، گزارش، بدهی، انبار |
| ۲-پ | **تصویر** | shim ِ `Bitmap`/`BitmapFactory`/`asImageBitmap` روی Skia (`Image.makeFromEncoded`) | `PhotoStore` ⇐ **فهرستِ کالا**، کاشیِ صندوق |
| ۲-ت | ریشهٔ کامل | `Manifest`/`ContextCompat`/`PackageManager`، `StockBook` و `SettingsLayout`، `ProvideLang` ⇐ `AppRoot`؛ سپس `AppRootLegacy` | همهٔ مسیرها |
| ۲-ث | همگام‌سازی | `SyncV1Worker` ِ iOS: هنگامِ باز شدن + `BGAppRefreshTask` | دفترِ مشترک با سرور |
| ۲-ج | بقیه | انتخابگرِ فایل/عکس (UIDocumentPicker/PHPicker پشتِ همان launcherها)، دوربین و بارکد (VisionKit)، اشتراک (UIActivityViewController)، اعلان، چاپ (شبکه/AirPrint؛ بلوتوثِ کلاسیک نه — بندِ ۵.۱) | — |

**وضع:** ۲-الف، ۲-ب، ۲-پ و ۲-ت در گامِ ۲ انجام شد (⇐ ۰.۷؛ ۲-ب با CommonCrypto + GCM ِ خالص، نه CryptoKit)؛ ۲-ث و بیشترِ ۲-ج در گامِ ۳ (⇐ ۰.۸؛
بارکد با AVFoundation، نه VisionKit)؛ چاپ (AirPrint/PDF) در گامِ ۴ (⇐ ۰.۹)؛ از ۲-ج فقط تشخیصِ گفتار مانده است.
گامِ ۶ (⇐ ۰.۱۱): ورود با اپل و قراردادِ مشترکِ «فراهم‌کننده»، و شش فایلِ دیگرِ اندروید خودشان روی آیفون (`ios-exclude.txt`: ۱۹ ⇐ ۱۳).
گامِ ۷ (⇐ ۰.۱۲): هیچ سطرِ بی‌دلیل در `ios-exclude.txt` (۱۳ ⇐ ۱۲)؛ `DeviceLocation` ِ اندروید روی آیفون، پوستهٔ برنامه، `ScanGate` و قاعدهٔ نسخه مشترک.

ترتیبِ صفحه‌ها همان خواستِ صاحب‌کار است: **۲-الف + ۲-ب + ۲-پ ⇐ ورود، صندوق/فروش، فهرستِ کالا**؛ سپس بقیه. هر برش با همان
چرخه سنجیده می‌شود: `compileKotlinIosSimulatorArm64` روی لینوکس، سپس CI ِ macOS.

**تفاوت‌های دانستهٔ shimها (عمدی، تا گامِ ۲):** قفلِ `synchronized` یک قفلِ بازگشتیِ سراسری است (درست ولی درشت)؛
`String.format` ِ بی Locale رقمِ لاتین می‌نویسد (اندروید با زبانِ دستگاه)؛ NSUserDefaults رمز ندارد ⇒ `TokenStore` و کلیدها بیرون‌اند؛
انتخابِ فایل/عکس/دوربین «لغو» و اجازه‌ها «نه» برمی‌گردانند؛ «نت هست» همیشه بله است؛ `Thread.start` خطا می‌دهد.
(گامِ ۲: توکن و کلیدِ دفتر در Keychain‌اند و `Thread.start` روی صفِ GCD اجرا می‌شود؛ بقیه همان.)

### ۰.۵ ساخت و CI

- **لینوکس، محلی:** Kotlin ‎2.2.20 klib ِ Apple را روی لینوکس می‌سازد (پلتفرم‌لایبرریِ iOS در توزیعِ لینوکسِ Kotlin/Native هست؛
  `core` بی پرچم هم ساخته شد، `ios/shared/gradle.properties` پرچمِ `kotlin.native.enableKlibsCrossCompilation=true` را روشن نوشته). فرمان:
  `desktop/gradlew -p ios/shared -Pios=true compileKotlinIosSimulatorArm64`. پیوندِ چارچوب و xcodebuild فقط روی macOS.
  در محیطِ این PR مخزنِ گوگل (androidx ِ iOS) و download.jetbrains.com بسته بودند: بررسیِ محلی با init-script ِ جدا (در مخزن نیست)
  روی Compose ‎1.7.3 ِ Maven Central و بی گروه‌های `androidx.*` انجام شد؛ CI همان ‎1.9.3 را با همهٔ وابستگی‌ها می‌سازد.
- **CI** (`.github/workflows/ios-spike.yml`، هر دو «گزارش» و `continue-on-error`، روی `core/**` و `ios/**`):
  - `ios-ui-klib` (ubuntu): همان `compileKotlinIosSimulatorArm64` و `jvmTest` (GCM در برابرِ JDK) — نگهبانِ ارزان.
  - `ios-app` (macos-latest): `linkDebugFrameworkIosSimulatorArm64` ⇐ `xcodebuild -target iosApp -sdk iphonesimulator -arch arm64`
    بی امضا ⇐ نصب و اجرا روی شبیه‌ساز، عکس پس از ۲۰ ثانیه و گزارشِ «زنده است؟» (مصنوعِ `ios-app-shots`)؛ از گامِ ۲
    `iosSimulatorArm64Test` هم (بردارهای رمز با CommonCrypto؛ از گامِ ۳ WorkManager، بارکد و اعلان).
  - اجرای این کارها روی هر تغییرِ `android-native/**` تصمیمِ هزینه‌ای صاحب‌کار است (دقیقهٔ macOS گران است)؛ فعلاً نه.

### ۰.۶ ⛔ BLOCKED-EXTERNAL — فقط صاحب‌کار

هیچ‌کدام با کد درست نمی‌شود؛ تا صاحب‌کار نداده، فقط ساختِ شبیه‌ساز بی امضا شدنی است:

1. **حسابِ Apple Developer Program** (۹۹ دلار در سال؛ حسابِ سازمانی D-U-N-S می‌خواهد) و راهِ قانونیِ آن با وضعِ تحریم (بندِ ۵.۷).
2. **Team ID** ⇐ `TEAM_ID` در `ios/iosApp/Configuration/Config.xcconfig` (اکنون خالی).
3. **شناسهٔ برنامه (bundle id)**: پیشنهادِ `ir.vil3ntec.tohid` (همان `APPLICATION_ID` ِ اندروید) — باید در حسابِ اپل ثبت شود.
4. **گواهیِ Apple Distribution و profile ِ ارائه**، و رکوردِ برنامه در App Store Connect (نام، نماد، عکس‌ها، ردهٔ سنی).
5. **فرم‌های اپل**: Privacy Manifest و برچسب‌های حریمِ خصوصی، پاسخِ «رمزنگاری/صادرات»، و — اگر ورود با گوگل روشن شود — Sign in with Apple (قاعدهٔ ۴.۸؛
   امروز `GOOGLE_LOGIN = false` است و ورود فقط ایمیل/رمز/کدِ خودِ برنامه ⇐ ۴.۸ هنوز پیش نمی‌آید، ⇐ ۰.۱۰).
   **ورود با اپل از گامِ ۶ در کد هست (⇐ ۰.۱۱)** و فقط این‌ها را می‌خواهد: قابلیتِ «Sign in with Apple» در شناسهٔ برنامه و profile
   (پروندهٔ entitlements با `com.apple.developer.applesignin` = `Default`، کنارِ `aps-environment` ِ بندِ ۶) و متغیرِ سرورِ
   `APPLE_CLIENT_IDS` = همان bundle id. تا آن روز دکمه ساخته نمی‌شود.
6. اعلانِ سرور (APNs): کلیدِ p8 ِ APNs با Key ID و Team ID ⇐ پنج متغیرِ محیطِ سرور (`APNS_KEY_ID`، `APNS_TEAM_ID`، `APNS_KEY_P8`،
   `APNS_BUNDLE_ID`، `APNS_ENV`؛ فرستنده از گامِ ۵ در سرور هست ⇐ ۰.۱۰)، و قابلیتِ «Push Notifications» (`aps-environment`) در شناسه و
   profile ِ برنامه — بی آن iOS توکن نمی‌دهد (سمتِ برنامه از گامِ ۴ آماده است).
7. از گامِ ۳: متنِ اجازه‌های `ios/iosApp/iosApp/Info.plist` (دوربین، Face ID، موقعیت، ذخیرهٔ تصویر؛ از گامِ ۴ «شبکهٔ محلی») و «Background App Refresh» باید با
   برچسب‌های حریمِ خصوصیِ App Store Connect (بندِ ۵) یکی باشند — پرکردنِ آن فرم‌ها فقط با حسابِ صاحب‌کار.

### ۰.۷ گامِ ۲ — شبکه، دفترِ رمزدار، تصویر؛ ریشهٔ واقعیِ برنامه (۱۴۰۵/۰۸/۱۲، شاخهٔ `claude/ios-app-phase2`)

> روی `claude/ios-app-phase1` (#822) و دستهٔ نهم (#826). اندروید، کامپیوتر و `core` دست نخوردند؛ همه در `ios/` است.

**نتیجه:** ۱۱۰۹ از ۱۱۳۴ فایلِ `android-native/app/src/main/java` برای iOS کامپایل می‌شوند (گامِ ۱: ۳۰۸ از ۱۰۹۶)، به‌علاوهٔ
دو فایلِ `core/src/jvmMain` (`PinCrypto`، `ApproverVault`) که core برای iOS ندارد. ریشهٔ آیفون حالا **همان بدنهٔ `MainActivity`**
است: انتخابِ حالت ⇐ ورود و خوش‌آمد (`WelcomeScreen`) ⇐ `AppRoot` (صندوق و فروش، کالاها، مشتری‌ها، گزارش…) یا حالتِ پیک.

| برش | چه شد | فایل‌ها |
|---|---|---|
| ۲-الف شبکه | `URL`/`HttpURLConnection` روی NSURLSession (همگام، خطاها همان ردهٔ JDK)، جریان‌های `java.io`، `Socket` ِ POSIX (چاپگرِ شبکه)؛ «سرورِ خودِ دکان» و هابِ محلی همان اندرویدِ پرچم‌خاموش | `java/net/*`، `java/io/*`، `platform/ServerSwitchIos`، `platform/HubRelayIos` |
| ۲-ب دفترِ رمزدار | `javax.crypto` ِ AES-GCM = AES ِ CommonCrypto (ECB، سخت‌افزاری) + حالتِ GCM ِ خالص در `commonMain`؛ `MessageDigest`/`Mac`/PBKDF2 (CommonCrypto)؛ ECDSA P-256 (Security.framework)؛ KEK و توکنِ ورود در Keychain («فقط همین دستگاه») | `javax/crypto/*`، `java/security/*`، `ios/crypto/Gcm.kt`، `data/AtRestKeysIos`، `core/net/TokenStoreIos` |
| ۲-پ تصویر | `Bitmap`/`BitmapFactory`/`Canvas`/`Matrix` روی Skia؛ `StaticLayout` روی پاراگرافِ Skia با Vazirmatn (رسید) | `android/graphics/*`، `android/text/StaticLayout.kt` |
| بقیه | `org.json` (رفتارِ نسخهٔ اندروید)، WorkManager ِ بی‌اجرا، `ZipInputStream`/`ZipOutputStream` (zlib)، تقویم/تاریخ، `TreeMap`/`TreeSet`، تخته‌برش (UIPasteboard)، launcher و اجازه‌ها («لغو»/«نه»)، اعلان (خاموش) | `org/json`، `androidx/work`، `java/util/*` |

**آزمونِ تازه** (همان بایت‌های JDK، پس پاکتِ اندروید/کامپیوتر در آیفون باز می‌شود و برعکس):
- `src/jvmTest/…/GcmJvmTest` — حالتِ GCM ِ آیفون در برابرِ `AES/GCM/NoPadding` ِ JDK روی ورودیِ تصادفی (کلید ۱۲۸/۱۹۲/۲۵۶، IV ِ ۸/۱۲/۱۶/۶۰ بایت، تا ۷۰ کیلوبایت)، برچسبِ کوتاه، دست‌خوردگی. روی لینوکس (`jvmTest`، کارِ `ios-ui-klib`).
- `src/iosTest/…/CryptoIosTest` و `AtRestIosTest` — همان بردارها روی شبیه‌ساز با CommonCrypto؛ پاکتِ دفتری که JDK ساخته باز می‌شود و با همان nonce عینِ همان بایت‌ها ساخته می‌شود (کارِ `ios-app`).

**جایگزین‌های iOS ِ هم‌API** (مثلِ «replaced» ِ کامپیوتر، فهرستِ کامل در `ios-exclude.txt`): ۲۲ فایل —
`ui/Platform`، `ui/screens/Haptics`، `core/net/TokenStore`، `data/AtRestKeys`، `data/DiskWrite`، `data/DeviceLocation`،
`data/Fingerprint`، `data/Migration`، `data/GoogleSignIn`، `data/FileName`، `platform/ServerSwitchAndroid`، `platform/HubRelayAndroid`،
`pay/platform/PayDevice`، `print/ReportPrint`، `print/LabelSheetPrint`، `hw/PrinterFinderAndroid`، `scan/BarcodeScanner`،
`scan/GuidedCapture`، `sync/Integrity`، `sync/v1/SyncV1Worker`، `sync/v1/LiveSocket` و `update/Updater` (به‌روزرسانی فقط از App Store؛
نه پرسش، نه APK). فقط **سه** فایل مستقیم بیرون‌اند: `MainActivity` (کارش را `ios/IosRoot.kt` می‌کند)، `data/TohidBackupAgent`
(پشتیبانِ اندروید) و `pay/platform/HesabPayHceService` (NFC ِ HCE). سطرِ «غیرِمستقیم» دیگر نیست: هیچ فایلی فقط به‌خاطرِ وابستگی بیرون نمانده.

**صفحه‌های ریشه:** همان انتخابِ حالت (`ModeChooser`)، ورود و خوش‌آمد (`WelcomeScreen` ⇐ `AuthGate` یا `WelcomeScreenLegacy`، درخواست‌ها
روی NSURLSession)، و `AppRoot` با صندوق و فروش (`v3/sales/cart/SaleScreen`، `QuickSaleScreen`، `DeskSaleScreen`؛ رابطِ قدیم `Sale.kt`)،
فهرستِ کالا (`v3/products/ProductsListScreen`) و بقیهٔ مسیرهای اندروید؛ حالتِ پیک (`CourierHome`) هم. رابط فقط نسخهٔ ۳ است (رابطِ قدیم و پرچمِ `UiV3` ۱۴۰۵/۰۸/۱۱ پاک شدند). ترتیبِ بارگذاری همان `onCreate` است (گزارشِ خطا، «سرورِ بعدی»، دفتر، شستنِ توکن‌های کهنه، تنظیم‌ها، نقشِ پیک).

**دستهٔ نهم (#826):** ۲۷ فایلِ تازهٔ main (`TillUserAndroid`، `QuickRegister`، `LateApologyScreen`، `CustomerErasurePanel`، `OrderEvidencePanel`،
`VisualSearchUi`، `LoyaltyShopAndroid`، `ExpiryPrefAndroid`، `CountReview`…) که دو کارِ آیفونِ #826 را سرخ کرده بودند، حالا بی هیچ سطرِ تازه
در `ios-exclude.txt` برای iOS کامپایل می‌شوند. قاعده همان است: فایلِ تازه خودبه‌خود iOS است و اگر نشد `ios-ui-klib` نامش را می‌گوید.

**هنوز نه (گامِ ۳؛ وضعِ امروز ⇐ ۰.۸)** — هر کدام در iOS «خاموش/نیست» گزارش می‌شود، نه خطای بی‌صدا:
- همگام‌سازی با سرور (`SyncV1Worker`) و سوکتِ زنده (`LiveSocket`) — دفترِ آیفون فعلاً محلی است؛ `BGAppRefreshTask`.
- دوربین و بارکدِ دوربین (VisionKit)، انتخابِ فایل/عکس (PHPicker/UIDocumentPicker)، اشتراک‌گذاری، Face ID، اعلانِ محلی، موقعیت، تشخیصِ گفتار.
- چاپ: چاپگرِ شبکه کامپایل می‌شود ولی روی دستگاهِ واقعی آزموده نشده؛ بلوتوثِ کلاسیک در iOS نیست (§۵.۱)؛ PDF/AirPrint ِ گزارش و برگهٔ برچسب.
- «سرورِ خودِ دکان» با گواهیِ سنجاق‌شده (نمایندهٔ NSURLSession).
- کلیدِ تأییدکننده‌ای که JDK ِ کامپیوتر ساخته (PKCS#8 ِ بی کلیدِ عمومی) در آیفون امضا نمی‌کند.
- بازگشایی با پیوندِ `vill3n://`، لمسِ اعلانِ سفارش و پنجرهٔ «برنامه بسته شد» (`CrashNotice`) — هر سه خصوصیِ `MainActivity`‌اند.
- آزمونِ دستیِ صفحه‌ها روی شبیه‌ساز/گوشی (این PR فقط ساخت، اجرا و عکسِ نخستین صفحه را در CI دارد).

**بررسیِ محلی:** همان klib روی لینوکس، با Compose 1.7.3 و بدل‌های محلیِ `androidx.lifecycle` (مخزنِ گوگل این‌جا بسته است؛ init-script و بدل‌ها در مخزن نیستند). CI همان 1.9.3 را با کتابخانه‌های واقعی می‌سازد.

### ۰.۸ گامِ ۳ — همگام‌سازی و سوکتِ زنده، دوربین/عکس/پرونده/اشتراک، Face ID، اعلان، موقعیت، پیوند (۱۴۰۵/۰۸/۱۲، شاخهٔ `claude/ios-app-phase3`)

> روی `claude/ios-app-phase2` (#834). اندروید، کامپیوتر و `core` دست نخوردند: هیچ فایلی بیرونِ `ios/` و `docs/` عوض نشد
> (بی seam ِ تازهٔ expect/actual). هر چه اضافه شد shim/جایگزینِ iOS است تا **همان فایل‌های اندروید** روی آیفون کار کنند.

**نتیجه:** ۱۱۱۱ از ۱۱۳۴ فایلِ `android-native/app/src/main/java` برای iOS کامپایل می‌شوند (گامِ ۲: ۱۱۰۹). دو جایگزینِ گامِ ۲
(`SyncV1WorkerIos`، `LiveSocketIos`) **حذف شدند**: خودِ `sync/v1/SyncV1Worker.kt` و `sync/v1/LiveSocket.kt` ِ اندروید، دست‌نخورده، روی آیفون می‌دوند.
`ios-exclude.txt`: ۲۰ جایگزین + همان سه فایلِ مستقیم (`MainActivity`، `TohidBackupAgent`، `HesabPayHceService`).

| برش | چه شد (همه پشتِ همان API ِ اندروید که کدِ مشترک صدا می‌زند) | فایل‌ها (زیرِ `ios/shared/src`) |
|---|---|---|
| همگام‌سازی | WorkManager ِ **داخلِ فرآیند** (همان شکلِ کامپیوتر): نامِ یکتا، `KEEP`/`UPDATE`/`REPLACE`، مکثِ آغازین، `retry` ِ نمایی/خطی، زمانِ آخرین اجرای هر کارِ دوره‌ای در NSUserDefaults (باز کردنِ دوباره کارِ نارسیده را زود اجرا نمی‌کند). Kotlin/Native بازتاب ندارد ⇐ سازندهٔ پنج کارگرِ اندروید یک بار ثبت می‌شود (`IosWork`). `BGAppRefreshTask` (iOS به صلاحدیدِ خودش، کمینه ۱۵ دقیقه) یک دور همگام‌سازی؛ بازگشت به برنامه ⇐ یک دور و وصلِ دوبارهٔ سوکت | `iosMain/…/androidx/work/Work.kt`، `commonMain/…/ios/work/WorkTiming.kt`، `iosMain/…/ios/IosApp.kt` |
| سوکتِ زنده | shim ِ `okhttp3` (`OkHttpClient`، `Request`، `WebSocket`، `WebSocketListener`) روی `NSURLSessionWebSocketTask`: ping هر ۳۰ ثانیه (pong ِ نرسیده ⇐ شکست، مثلِ OkHttp)، بستنِ سرور با کد (`onClosing` ⇐ `onClosed`، مثلِ 4403)، هر سوکت دقیقاً یک پایان؛ `Handler.postDelayed` روی صفِ اصلیِ GCD | `iosMain/…/okhttp3/WebSocket.kt`، `android/os/Handler.kt`، `ServerSwitchIos.okHttp` |
| عکس، دوربین، پرونده | `PickVisualMedia`/`GetContent("image/…")` ⇐ PHPicker (بی اجازهٔ گالری)؛ `TakePicture` ⇐ دوربینِ UIImagePickerController؛ `GetContent`/`GetMultipleContents` ِ دیگر ⇐ UIDocumentPicker (رونوشت در cache با همان نام)؛ `CreateDocument` ⇐ پرونده در cache که کدِ مشترک در آن می‌نویسد و با بستنِ جریان «ذخیره در Files». عکس‌ها JPEG ِ راست‌شده (HEIC و چرخشِ EXIF حل) | `ios/IosPickers.kt`، `ios/IosUi.kt`، `androidx/activity/result/contract/Contracts.kt`، `android/content/Context.kt` |
| اشتراک | `startActivity(createChooser(ACTION_SEND))` ⇐ UIActivityViewController (متن، `EXTRA_STREAM`، موضوع؛ لنگرِ iPad) — رسید، صورت‌حساب، گزارشِ PDF | `ios/IosPickers.kt`، `android/content/Context.kt` |
| بارکدِ دوربین | `CameraScanner` روی `AVCaptureMetadataOutput`: همان قالب‌های ML Kit (EAN-13/8، UPC-A/E، Code 128/39، ITF؛ QR با `qr`/`qrOnly`)، UPC-A ِ iOS (EAN-13 با صفر) ⇐ همان ۱۲ رقم، و هر خوانش از همان `BarcodeGuard` ِ مشترک؛ چند بارکد در کادر ⇐ رد (مثلِ `Aim.bestOf`) | `scan/BarcodeScannerIos.kt` |
| Face ID / Touch ID | `Fingerprint` روی LocalAuthentication (فقط زیست‌سنجی؛ لغو و «با رمز وارد می‌شوم» ⇐ `onFail(null)`). `LocalContext` ِ ریشه حالا `IosActivity` (یک `FragmentActivity`) است، مثلِ اندروید ⇐ `LockGate` و `AppLockLegacy` ِ مشترک خودشان می‌پرسند | `data/FingerprintIos.kt`، `ios/IosApp.kt`، `androidx/fragment/app/FragmentActivity.kt` |
| اعلانِ محلی | `NotificationManagerCompat`/`NotificationManager` ⇐ UNUserNotificationCenter (شناسهٔ `tohid-<id>`، جایگزینی و `cancel`)؛ `Intent` ِ `setContentIntent` در userInfo ⇐ لمسِ اعلان همان `openNotice` ِ `MainActivity` (سفارشِ تازه). اجازه هنگامِ نخستین نمایشِ دکان (همان لحظهٔ اندرویدِ ۱۳+) | `android/app/Notification.kt`، `PendingIntent.kt`، `androidx/core/app/Notifications.kt`، `ios/IosNotifications.kt` |
| اجازه‌ها و موقعیت | `checkSelfPermission` و `RequestPermission`/`RequestMultiplePermissions` ⇐ اجازهٔ واقعیِ iOS (دوربین، موقعیت، اعلان؛ گالری همیشه بله؛ بقیه نه). `DeviceLocation` روی CoreLocation («هنگامِ استفاده»، یک‌باره، مهلتِ ۱۲ ثانیه، همان پروندهٔ `tohid-location`) — فقط جایی که کدِ مشترک از پیش می‌خواهد (F0-07، پیک، ویترین) | `ios/IosPermissions.kt`، `ios/IosLocation.kt`، `data/DeviceLocationIos.kt` |
| پیوند و «برنامه بسته شد» | `vill3n://app/…` (`CFBundleURLTypes` + `.onOpenURL`) ⇐ همان `handleLink` (`DeepLinks.parse`)؛ `CrashNotice` همان پنجرهٔ `MainActivity` با `StrShell` و `CrashReporter` | `ios/IosApp.kt`، `ios/IosRoot.kt`، `ios/iosApp/iosApp/iOSApp.swift` |

**پوستهٔ Xcode:** `Info.plist` — `CFBundleURLTypes` (vill3n)، `BGTaskSchedulerPermittedIdentifiers` (`ir.vil3ntec.tohid.refresh`) و
`UIBackgroundModes=fetch`، و متنِ اجازه‌ها (`NSCameraUsageDescription`، `NSFaceIDUsageDescription`، `NSLocationWhenInUseUsageDescription`،
`NSPhotoLibraryAddUsageDescription` برای «ذخیرهٔ تصویر» ِ برگهٔ اشتراک). `OTHER_LDFLAGS`: چارچوب‌های سیستمی که چارچوبِ ایستای
`TohidShared` می‌خواهد (Security، AVFoundation، BackgroundTasks، CoreLocation، LocalAuthentication، PhotosUI، UniformTypeIdentifiers،
UserNotifications). `iOSApp.init` ⇐ `appDidLaunch()` (پیش از پایانِ راه‌اندازی: BGTaskScheduler، نمایندهٔ اعلان، چرخهٔ برنامه).

**رفعِ CI ِ `ios-app` (از گامِ ۱ سرخ):** xcodebuild در سرآیندِ ObjC ِ چارچوب می‌افتاد — سه نامِ Kotlin هم‌نامِ ماکروهای C‌اند
(`BuildConfig.DEBUG` با ماکروی `DEBUG` ِ پیکربندیِ Debug، `NAME_MAX` ِ `<limits.h>` در `DeliveryForms`/`NewOrderModel`/`CampaignLinks`،
`JSONObject.NULL`). نام‌ها عوض نشدند؛ `ios/shared/build.gradle.kts` پس از پیوندِ چارچوب همان ماکروها را فقط درونِ `TohidShared.h`
با `push_macro`/`undef`/`pop_macro` کنار می‌گذارد (`guardHeaderMacros`). مرحلهٔ پیوندِ برنامه و اجرا روی شبیه‌ساز تا امروز هرگز
به CI نرسیده بود؛ نتیجه‌اش همین PR است.

**آزمونِ تازه:**
- `src/jvmTest/…/ios/work/WorkTimingJvmTest` (۸) — زمان‌بندیِ کارهای دوره‌ای در برابرِ قاعده‌های WorkManager ِ اندروید (کفِ ۱۵ دقیقه، سررسید، ساعتِ عقب‌رفته، عقب‌نشینیِ نمایی/خطی). روی لینوکس (`jvmTest`، کارِ `ios-ui-klib`).
- `src/iosTest/…/ios/Phase3IosTest` (۳) — اجرای کارگرِ ثبت‌شده با WorkManager ِ آیفون، نگاشتِ بارکدِ AVFoundation به قاعدهٔ ML Kit، رفت‌وبرگشتِ `Intent` ِ اعلان از راهِ userInfo. فقط روی شبیه‌ساز (کارِ `ios-app`).

**تفاوت‌های دانسته (عمدی):**
- iOS برنامهٔ بسته را نگه نمی‌دارد: همگام‌سازیِ پس‌زمینه فقط وقتی iOS `BGAppRefreshTask` بدهد (و نه پس از بستنِ دستیِ برنامه)؛ سوکت در پس‌زمینه می‌افتد و با بازگشت دوباره وصل می‌شود. قیدِ «نت هست» سنجیده نمی‌شود (مثلِ کامپیوتر؛ کارِ بی‌نت `retry` می‌دهد).
- `CreateDocument`: جای ذخیره **پس از** نوشتن انتخاب می‌شود (پنجرهٔ Files)؛ لغوِ آن پرونده را فقط در cache می‌گذارد.
- اسکنِ دوربین: فوکوس/بزرگ‌نمایی/چراغِ خودکارِ اندروید (`FocusPilot`، `LightPilot`) نیست؛ فوکوسِ پیوستهٔ iOS. دوربینِ راهنمای عکسِ کالا (`GuidedCapture`) نیست؛ عکس با `TakePicture`.
- شبیه‌ساز دوربین ندارد ⇐ مسیرهای دوربین فقط روی گوشیِ واقعی آزموده می‌شوند (هنوز نشده).

**هنوز نه (گامِ ۴؛ وضعِ امروز ⇐ ۰.۹):** چاپ (PDF/AirPrint ِ گزارش و برگهٔ برچسب؛ چاپگرِ شبکه روی دستگاه آزموده نشده؛ بلوتوثِ کلاسیک در iOS نیست — §۵.۱)؛
تشخیصِ گفتار (Speech)؛ ورود با گوگل/اپل و گواهیِ «همین برنامه» (`Integrity`) — هر دو حسابِ اپل می‌خواهند (۰.۶)؛ «سرورِ خودِ دکان» با
گواهیِ سنجاق‌شده (نمایندهٔ NSURLSession، برای HTTP و همین سوکت)؛ اعلانِ سرور (APNs، ۰.۶ بندِ ۶)؛ صدای اسکن (`MediaPlayer`)؛ آزمونِ دستیِ
صفحه‌ها و دوربین روی گوشیِ واقعی.

**بررسیِ محلی (لینوکس):** همان klib با Compose 1.7.3 و بدل‌های محلیِ `androidx.lifecycle` (‎۰.۷‎)، `jvmTest` ِ `ios/shared`،
`core` ِ `jvmTest` و آزمونِ کامپیوتر. پیوندِ چارچوب، xcodebuild، اجرا روی شبیه‌ساز و `iosSimulatorArm64Test` فقط در CI ِ macOS.

### ۰.۹ گامِ ۴ — چاپ و PDF، «سرورِ خودِ دکان» با گواهیِ سنجاق‌شده، APNs، بوقِ اسکن (۱۴۰۵/۰۸/۱۲، شاخهٔ `claude/ios-app-phase4`)

> روی دستهٔ یازدهم (#837 = گامِ ۲ و ۳ + `devices-hub` + `hesabpay-per-shop`). اندروید، کامپیوتر و `core` دست نخوردند؛ سرور یک
> تغییرِ کوچک دارد (APNs، پایین). باز هم **همان فایل‌های اندروید** روی آیفون‌اند و هر چه تازه است shim ِ iOS است.

**نتیجه:** ۱۱۳۷ از ۱۱۵۷ فایلِ `android-native/app/src/main/java` برای iOS کامپایل می‌شوند (گامِ ۳: ۱۱۱۱ از ۱۱۳۴؛ ۲۳ فایلِ تازهٔ
دستهٔ یازدهم همه هستند). سه جایگزینِ دیگر **حذف شدند** و خودِ فایلِ اندروید جایشان است: `print/ReportPrint.kt`،
`print/LabelSheetPrint.kt` و `platform/ServerSwitchAndroid.kt`. `ios-exclude.txt`: ۱۷ جایگزین + همان سه فایلِ مستقیم.

| برش | چه شد | فایل‌ها (زیرِ `ios/shared/src`) |
|---|---|---|
| چاپ و PDF | `PrintManager.print` ⇐ همان `onLayout`/`onWrite` ِ سازگارکنندهٔ کدِ مشترک یک بار، PDF در `cache/print` ⇐ پنجرهٔ AirPrint (`UIPrintInteractionController`: چاپگر، شمار، صفحه‌ها؛ iPad با لنگر)؛ دستگاهِ بی چاپ ⇐ برگهٔ اشتراکِ همان PDF؛ خرابی ⇐ «چاپ نشد: …» (همان `StrPick.printFailed`). `PrintedPdfDocument`/`PdfDocument` روی همان `Canvas` ِ Skia با واحدِ پوینت (A4 = ۵۹۵ × ۸۴۱، همان حسابِ اندروید) و ۳۰۰ نقطه در اینچ؛ هر صفحه یک تصویرِ خاکستری (تک‌رنگ) یا RGB با zlib (FlateDecode). همهٔ «چاپ» ِ گزارش، صورت‌حساب، فاکتورِ خرید، برگهٔ برچسب و QR ِ کمپین؛ «ذخیرهٔ PDF» ِ گزارشِ تازه (`savePdf`) همان PDF را به برگهٔ اشتراک می‌دهد. رسیدِ فروش: «فرستادنِ PDF» (`PrintJob.sharePdf`، `ReceiptPdf` ِ core) برگهٔ اشتراک است و «چاپ» ِ همان برگه AirPrint است | `iosMain/…/android/print/*`، `android/graphics/pdf/PdfDocument.kt`، `android/os/PrintSupport.kt`، `ios/IosPrint.kt`، `commonMain/…/ios/print/RasterPdf.kt` |
| «سرورِ خودِ دکان» | خودِ `ServerSwitchAndroid.kt` (پروفایل‌ها، «سرورِ بعدی» در شروعِ فرآیند، سنجشِ نشانی‌ها، `ServersController` ِ صفحهٔ «سرورها»). سنجاقِ گواهی **همان `PinnedTls` ِ اندروید** است روی shimِ `javax.net.ssl` (`SSLContext`، `X509TrustManager`، `HostnameVerifier`، `HttpsURLConnection`) و `java.security.cert`: پرسشِ اعتمادِ NSURLSession (`ServerTrust`) ⇐ زنجیرهٔ `SecTrust` (DER، برگ نخست) ⇐ `checkServerTrusted` ِ همان مدیرِ اعتماد ⇐ `verify` ِ همان verifier؛ هر دو ⇐ پذیرش، وگرنه دست‌دادن لغو و `SSLHandshakeException` (همان که `probe` می‌گیرد: «گواهیِ این سرور با گواهیِ سنجاق‌شده نمی‌خواند»). اعتمادِ پیش‌فرضِ iOS جایش را نمی‌گیرد (مثلِ JDK)؛ بی verifier ⇐ رد. «اعتماد در نخستین استفاده» (`PinnedTls.capture`) هم همان است. سوکتِ زنده با همان قاعده (`OkHttpClient.sslSocketFactory`/`hostnameVerifier`) | `iosMain/…/javax/net/ssl/Ssl.kt`، `javax/net/ssl/IosTls.kt`، `java/security/cert/Certificate.kt`، `java/net/Http.kt`، `okhttp3/WebSocket.kt` |
| اعلانِ سرور (APNs) | سمتِ برنامه: `registerForRemoteNotifications` در `appDidLaunch`؛ `AppDelegate` ِ SwiftUI (`@UIApplicationDelegateAdaptor`) توکن را به `IosPush` می‌دهد ⇐ `POST /api/me/push` با `provider = apns`، `platform = ios`، پشتِ همان ورود (`Backend.api`)؛ پس از ورود، روزی یک بار برای هر «توکن + حساب». سرور: `apns` در فهرستِ سرویس‌های `/api/me/push`؛ فرستندهٔ FCM آن را نه می‌فرستد نه «کهنه» می‌کند (فقط apns ⇐ `apns_only`). فرستادن به APNs نیست (کلید و قابلیت ⇐ ۰.۶ بندِ ۶) | `iosMain/…/ios/IosPush.kt`، `ios/iosApp/iosApp/iOSApp.swift`، `server/src/routes/me.js`، `server/src/lib/push.js` |
| بوقِ اسکن | `ScanFeedback.ok` ⇐ همان `res/raw/scan_beep.mp3` (در Resources ِ برنامه) با AVAudioPlayer ِ نگه‌داشته، از اول (نشستِ صدای Ambient: آهنگِ برنامهٔ دیگر قطع نمی‌شود و کلیدِ بی‌صدا رعایت می‌شود) + لرزشِ «درست»؛ «ناشناخته» ⇐ لرزشِ «هشدار» (بوقِ `ToneGenerator` در iOS نیست). لرزش‌های دیگر (`Haptics`) از گامِ ۲ بودند | `iosMain/…/scan/BarcodeScannerIos.kt`، `ir/vil3ntec/tohid/R.kt` |
| دستهٔ یازدهم | چهار فایلِ تازه (`hw/DeviceLinks`، `hw/DeviceSignalsAndroid`، `scan/ScanServiceAndroid`، `pay/platform/HesabPayNoticeAndroid`) که `ios-ui-klib` ِ #837 را سرخ کرده بودند، بی سطرِ تازه در `ios-exclude.txt`: `Service`، `BroadcastReceiver`/`IntentFilter`، `NotificationListenerService`/`StatusBarNotification`، `ServiceCompat`، `Intent(Context, Class)`، `ComponentName.unflattenFromString`، `Thread.interrupt`/`InterruptedException`، `BluetoothDevice.type/uuids/bluetoothClass`. در iOS هیچ‌کدام اجرا نمی‌شوند: سرویسِ پیش‌رو، پخشِ سیستمی و خواندنِ اعلانِ برنامهٔ دیگر در iOS نیست و «بلوتوث نیست» ِ همیشگی است | `iosMain/…/android/app/Service.kt`، `android/content/BroadcastReceiver.kt`، `android/service/notification/*`، `androidx/core/app/ServiceCompat.kt` |

**پوستهٔ Xcode:** `Info.plist` — `NSLocalNetworkUsageDescription` و `NSAppTransportSecurity`/`NSAllowsLocalNetworking` (سرورِ دکان و
چاپگرِ شبکه در شبکهٔ محلی). `scan_beep.mp3` در Resources. `OTHER_LDFLAGS`: `AVFAudio` و `-lz` (zlib ِ PDF و xlsx؛ چارچوبِ ایستای
Kotlin کتابخانه‌های سیستمی را خودش به برنامه نمی‌دهد). **رفعِ CI ِ `ios-app`:** xcodebuild ِ گامِ ۳ از سرآیندِ ObjC گذشت و در Swift
افتاد: چارچوب رده‌های هم‌نامِ SwiftUI دارد (`View` ِ shimِ `android.view`، `Context` ِ shimِ `android.content`) ⇐ «'View' is
ambiguous» و `ComposeView` بی `Context`. حالا `ContentView.swift` و `iOSApp.swift` نام‌های SwiftUI/UIKit/Foundation را با نامِ ماژول
می‌نویسند (`SwiftUI.View`، `UIViewControllerRepresentableContext`، …).

**آزمونِ تازه:**
- `src/jvmTest/…/ios/print/RasterPdfJvmTest` (۶) — اندازهٔ A4 ِ اندروید (۵۹۵ × ۸۴۱)، جدولِ xref و `/Count`، بایت‌های تصویرِ فشرده
  برابرِ پیکسل‌ها، RGB، PDF ِ بی صفحه ⇐ خطا؛ پروندهٔ نمونه با `pdfinfo` (۲ صفحه، A4، 1.4) و `pdftoppm` دستی هم باز شد. روی لینوکس.
- `src/iosTest/…/ios/Phase4IosTest` (۵) — سنجاقِ گواهی با **همان** `PinnedTls` روی `SecTrust` ِ یک گواهیِ خودامضای P-256 (همان DER،
  پذیرشِ سنجاقِ درست، ردِ سنجاقِ دیگر، ردِ بی verifier/بی مدیرِ اعتماد)، PDF ِ A4 ِ دوصفحه‌ای با zlib ِ iOS، `ComponentName`، هگزِ
  توکنِ APNs. فقط روی شبیه‌ساز (کارِ `ios-app`).
- سرور: `test/push-delivery.test.js` ۲۲/۲۲ روی PGlite (دو آزمونِ تازه: ثبتِ `apns` و ردِ سرویسِ ناشناخته؛ با FCM ِ ساختگی، توکنِ APNs
  نه فرستاده می‌شود نه کهنه، و «فقط apns» گوگل را صدا نمی‌زند — همین آزمون بی تغییرِ `push.js` سرخ است).

**تفاوت‌های دانسته (عمدی):**
- PDF ِ آیفون تصویری است (۳۰۰ نقطه)، نه برداری مثلِ اندروید: برای چاپ و خواندن یکی است، ولی متنش جست‌وجوپذیر نیست و حجمش بیشتر است.
- پنجرهٔ چاپ پس از ساختنِ PDF باز می‌شود؛ اندازهٔ کاغذ همان A4 ِ کدِ مشترک است و عوض کردنِ کاغذ در پنجرهٔ iOS صفحه را از نو نمی‌چیند.
- «سرورِ بعدی» مثلِ اندروید برنامه را می‌بندد (`exitProcess`)؛ iOS خودش دوباره بازش نمی‌کند — کاربر باز می‌کند.
- هابِ محلی (SYN-09) روی آیفون هنوز نه (`HubRelayIos` ⇐ null)؛ درخواست مستقیم به سرورِ فعال می‌رود.
- ATS: نشانیِ IP و نامِ `.local` آزاد است؛ نامِ دامنهٔ عمومی با گواهیِ خودامضا را شاید ATS پیش از نماینده ببندد (آزموده نشده).
- `Thread.interrupt` فقط نشان است (کارِ GCD بریدنی نیست)؛ کارگرهای بارکدخوانِ SPP در iOS هرگز بالا نمی‌آیند.

**هنوز نه:**
- **ورود با گوگل/اپل** — کدِ مشترک انتزاعِ «فراهم‌کننده» ندارد: فقط `data/GoogleSignIn` (Credential Manager) و سرور فقط `/auth/google`
  (توکنِ ID ِ گوگل). ورود با گوگل در iOS شناسهٔ کلاینتِ iOS ِ گوگل (کنسولِ صاحب‌کار) و Sign in with Apple مسیرِ تازهٔ سرور + حسابِ اپل
  (قاعدهٔ ۴.۸، ۰.۶) می‌خواهد؛ تا آن روز ایمیل و کد (همان پیامِ روشنِ `GoogleSignInIos`).
- **تشخیصِ گفتار** — کدِ مشترک از برنامهٔ دیگرِ اندروید می‌پرسد (`RecognizerIntent`)؛ در iOS «برنامه‌ای نیست» ⇐ دکمهٔ میکروفون پنهان.
  SFSpeechRecognizer صفحهٔ شنیدنِ خودش و دو اجازهٔ تازه (میکروفون، گفتار) می‌خواهد.
- فرستندهٔ APNs در سرور (HTTP/2 + JWT ِ ES256 با کلیدِ p8؛ محیطِ sandbox/production) — بی کلیدِ صاحب‌کار ساختنی نیست.
- `Integrity` (گواهیِ «همین برنامه»، App Attest) — حسابِ اپل.
- آزمونِ دستی روی گوشیِ واقعی: AirPrint روی چاپگرِ واقعی، سرورِ دکان با گواهیِ خودامضا روی شبکهٔ دکان، بوق، APNs، و صفحه‌ها.

**بررسیِ محلی (لینوکس):** klib ِ iOS و klib ِ آزمونِ iOS (`compileKotlinIosSimulatorArm64`، `compileTestKotlinIosSimulatorArm64`) با
Compose 1.7.3 (‎۰.۷‎)؛ `jvmTest` ِ `ios/shared` ۱۸/۱۸ (Gcm ۴، RasterPdf ۶، WorkTiming ۸)؛ سرور `push-delivery` ۲۲/۲۲ روی PGlite. فقط
CI ِ macOS: پیوندِ چارچوب، xcodebuild، اجرا روی شبیه‌ساز و `iosSimulatorArm64Test` (۹ + ۵).

### ۰.۱۰ گامِ ۵ — فرستندهٔ APNs در سرور، هابِ محلی، لمسِ اعلانِ سرور، CI ِ آیفون (۱۴۰۵/۰۸/۱۲، شاخهٔ `claude/ios-app-phase5`)

> روی دستهٔ دوازدهم (#845، با گامِ ۴ = #841). اندروید، کامپیوتر و `core` دست نخوردند؛ سرور یک فرستندهٔ تازه دارد (بی مسیرِ تازه).
> باز هم **همان فایل‌های اندروید** روی آیفون‌اند؛ یک جایگزینِ دیگر حذف شد.

**نتیجه:** ۱۱۵۱ از ۱۱۷۰ فایلِ `android-native/app/src/main/java` برای iOS کامپایل می‌شوند (گامِ ۴: ۱۱۳۷ از ۱۱۵۷؛ `ios-exclude.txt`: ۱۶
جایگزین + همان سه فایلِ مستقیم). `platform/HubRelayIos.kt` حذف شد و خودِ `platform/HubRelayAndroid.kt` جایش است.

| برش | چه شد | فایل‌ها |
|---|---|---|
| CI ِ آیفون سرخ بود | دستهٔ دوازدهم (#845) و هر PR ِ پس از آن در `ios-ui-klib` و `ios-app` با «Redeclaration» افتاده بودند: ادغامِ main سه جایگزینی را که گامِ ۴ حذف کرده بود برگردانده بود (`ServerSwitchIos.kt`، `ReportPrintIos.kt`، `LabelSheetPrintIos.kt`) و خودِ فایلِ اندروید هم کامپایل می‌شد. حذف شدند. کارِ `ios-app` ِ گامِ ۴ هر چهار مرحله را سبز گذراند (پیوند، xcodebuild، اجرا روی شبیه‌ساز، `iosSimulatorArm64Test`) ولی در ذخیرهٔ کشِ پایانی به سقفِ ۷۵ دقیقه خورد و «لغو» شد ⇐ سقف ۱۰۰ دقیقه (همان کار، بی سرخِ دروغ). هر دو کار هنوز «گزارش» و `continue-on-error`اند | `ios/shared/src/iosMain/…`، `.github/workflows/ios-spike.yml` |
| فرستندهٔ APNs (سرور) | `lib/apns.js` کنارِ FCM ِ `push.js`: HTTP/2 ِ `node:http2` به `api.push.apple.com` (یا sandbox) با یک نشستِ ماندگار؛ JWT ِ ES256 با `node:crypto` (امضای خامِ r‖s، `kid` = Key ID، `iss` = Team ID)، تازه هر ۴۰ دقیقه (اپل: نه زیرِ ۲۰، نه بالای ۶۰)؛ سرآیندهای `apns-topic`/`apns-push-type: alert`/`apns-priority: 10`/`apns-expiration` (۲۴ ساعت)/`apns-collapse-id` (= notifId)؛ بدنه `aps` + دادهٔ پیام در سطحِ بالا، بیش از ۴KB ⇐ متن کوتاه می‌شود. ۴۲۹/۵xx/قطعِ اتصال ⇐ دوباره با عقب‌نشینیِ نمایی (۳ بار، `retry-after` ِ اپل)؛ `ExpiredProviderToken` ⇐ یک بار JWT ِ تازه (فقط اگر ≥ ۲۰ دقیقه)؛ ۴۰۳ ِ دیگر ⇐ بقیهٔ دسته فرستاده نمی‌شود؛ فقط ۴۱۰ و `BadDeviceToken` ⇐ ردیف «کهنه» (برنامه روزی یک بار دوباره ثبت می‌کند و زنده می‌شود؛ `DeviceTokenNotForTopic` نه — شناسهٔ نادرستِ سرور همهٔ توکن‌ها را نکشد). توکنِ غیرهگز بی درخواست کنار می‌رود. تنظیم فقط از محیط (⇐ ۰.۶ بندِ ۶)؛ هیچ ⇐ خاموشِ بی‌صدا و همان رفتارِ گامِ ۴؛ ناقص/خراب ⇐ خاموش با هشدارِ «نامِ متغیر/دلیل». کلیدِ «پوش روشن» ِ پنل کلیدِ اصلیِ هر دو است؛ `push.masked()` فقط `apns: {configured, env, reason}`. توکن، کلید و JWT هرگز در لاگ، `last_error` («apns ۴۱۰ Unregistered») یا پاسخ نیستند | `server/src/lib/apns.js`، `server/src/lib/push.js`، `server/src/routes/me.js` (فقط توضیح) |
| لمسِ اعلانِ سرور | userInfo ِ اعلانِ APNs (`aps` + `type`/`notifId`/`orderId`) ⇐ `IosPush.remoteIntent` ⇐ همان دو extra ِ `OrderNotify` ⇐ همان `openNotice` (سفارش باز می‌شود، رسیدِ «دیده شد» همان راه). اعلانِ محلیِ دیدبان برای همان notifId اعلانِ APNs ِ رسیده را برمی‌دارد (شناسهٔ آن = `apns-collapse-id`) تا یک خبر دو بار دیده نشود؛ روی گوشیِ واقعی هنوز آزموده نشده | `ios/IosPush.kt`، `ios/IosNotifications.kt` |
| هابِ محلی (SYN-09) | خودِ `HubRelayAndroid` ِ اندروید: `URL.openConnection(Proxy.NO_PROXY)` ⇐ نشستِ NSURLSession ِ **بی پراکسی** (`connectionProxyDictionary` خالی؛ پراکسیِ Wi-Fi ِ iOS نشانیِ محلی را هم می‌گیرد) و `instanceFollowRedirects = false` ⇐ نمایندهٔ نشست ریدایرکت را دنبال نمی‌کند و ۳xx همان‌جا برمی‌گردد (مثلِ JDK) — این دومی «سنجشِ سرور» ِ `ServerSwitchAndroid` را هم درست کرد که از گامِ ۴ ریدایرکت را بی‌صدا دنبال می‌کرد. نشست‌های shim حالا چهارتا‌اند ([پراکسیِ سیستم/بی پراکسی] × [اعتمادِ iOS/سنجاق])، هر کدام با نمایندهٔ خودش (`ShimHttp` به‌جای `PinnedHttp`). پراکسیِ دستیِ HTTP/SOCKS ⇐ `UnsupportedOperationException` (کدِ مشترک نمی‌خواهد) | `java/net/Net.kt` (`Proxy`)، `java/net/Http.kt`، `javax/net/ssl/IosTls.kt` |

**آزمونِ تازه:**
- سرور: `test/apns.test.js` ۱۱/۱۱ — سرورِ HTTP/2 ِ ساختگی روی 127.0.0.1 (بی TLS، بی شبکه) و کلیدِ P-256 ِ همین‌جا ساخته: تنظیمِ ناقص/خراب (فقط نامِ متغیر)، سه شکلِ کلید در `.env`، JWT (امضا با کلیدِ عمومی، ۴۰ دقیقه)، مسیر/سرآیند/بدنه، ۴۱۰/BadDeviceToken/DeviceTokenNotForTopic، عقب‌نشینی و `retry-after` و سقف، قطعِ اتصال ⇐ نشستِ تازه، `ExpiredProviderToken`، `InvalidProviderToken`، توکنِ غیرهگز، بدنهٔ بلند، و «هیچ توکن/کلید/JWT در لاگ و نتیجه». `test/push-delivery.test.js` ۲۳/۲۳ روی PGlite (آزمونِ تازه: رسیده ⇐ `last_ok_at`، ۴۱۰ ⇐ کهنه با `last_error` ِ بی توکن، ردیفِ FCM دست‌نخورده، ۴۰۳ ِ کلید ⇐ ایست، کلیدِ اصلیِ پنل خاموش ⇐ APNs هم خاموش). جهش‌ها: برداشتنِ BadDeviceToken، «کهنه»، امضای DER، کلیدِ اصلی، ایستِ ۴۰۳ و سنجشِ توکن ⇐ هر کدام دست‌کم ۱ سرخ.
- `src/iosTest/…/ios/Phase5IosTest` (۳) — نگاشتِ userInfo ِ APNs به `Intent` ِ سفارش، `Proxy.NO_PROXY` ⇐ `HttpsURLConnection` و ردِ پراکسیِ دیگر، چهار نشستِ جدا، `HubRelayAndroid.of` روی آیفون (پرچم خاموش ⇐ null). فقط روی شبیه‌ساز (کارِ `ios-app`).

**هنوز نه (و چرا):**
- **ورود با اپل / انتزاعِ «فراهم‌کننده»:** ساخته نشد. ورود با گوگل در کدِ مشترک **خاموش** است (`GOOGLE_LOGIN = false` در `ui/v3/onboarding/GoogleLinkSheet.kt`؛ دکمه هیچ‌جا دیده نمی‌شود) ⇒ امروز ورودِ شخصِ ثالثی نیست که انتزاع بخواهد، و قاعدهٔ ۴.۸ ِ اپل (Sign in with Apple فقط وقتی ورودِ شخصِ ثالث هست) پیش نمی‌آید. انتزاعِ امروز کدِ مرده در سه سکو (اندروید، کامپیوتر، آیفون) بود. طرحِ آماده برای روزی که گوگل روشن شود: (۱) سرور `POST /auth/apple` کنارِ `/auth/google` با `lib/apple.js` ِ هم‌شکلِ `lib/google.js` (JWKS ِ `https://appleid.apple.com/auth/keys` با کش، RS256، `iss = https://appleid.apple.com`، `aud` ∈ `APPLE_CLIENT_IDS` = bundle id، `exp`/`iat`، و `nonce` = SHA-256 ِ nonce ِ خامی که سرور یک‌بار‌مصرف و ۱۰ دقیقه‌ای داده)، پیوند با `user_identities` (`provider = 'apple'`، بی مهاجرت) و همان قاعدهٔ «ایمیلِ دارای حساب ⇐ اثباتِ روشِ قبلی» (۴۰۹ `apple_link_required` + `/auth/apple/link-code`؛ هرگز پیوندِ خودکار با ایمیل، رلهٔ خصوصیِ اپل حسابِ تازه)؛ (۲) کدِ مشترک: `ExternalSignIn` (نام، مسیرِ سرور، کدِ ۴۰۹) به‌جای `GoogleSignIn`/`GoogleLinkAsk` ِ تک‌منظوره، و `AppleSignIn` فقط در shim ِ iOS (`ASAuthorizationAppleIDProvider`). پیش‌نیاز: قابلیتِ «Sign in with Apple» در حسابِ اپل (۰.۶).
- **تشخیصِ گفتار:** کدِ مشترک `StartActivityForResult(ACTION_RECOGNIZE_SPEECH)` را به برنامهٔ دیگرِ اندروید می‌سپارد؛ shim ِ کوچک نیست — SFSpeechRecognizer صفحهٔ شنیدنِ خودش (AVAudioEngine)، دو اجازهٔ تازه (میکروفون، گفتار) و برچسبِ حریمِ خصوصی می‌خواهد، و پشتیبانیِ زبانِ فارسی روی دستگاه باید اول روی گوشیِ واقعی دیده شود. تا آن روز دکمهٔ میکروفون پنهان است (همان «برنامه‌ای نیست» ِ اندروید).
- **App Attest (`Integrity`):** shim ِ کوچک نیست — کلید در Secure Enclave، گواهیِ اپل و وارسیِ CBOR/زنجیرهٔ گواهی در سرور، و قابلیتِ App Attest در حسابِ اپل. `IntegrityIos.isGenuine` همان «درست» ِ گامِ ۲ است (امضای برنامه را خودِ iOS وارسی می‌کند).
- هابِ محلی: صفحه، mDNS و جفت‌سازی همان «مانده» ِ SYN-09 برای همهٔ سکوهاست؛ روی آیفونِ واقعی با هابِ کامپیوتر آزموده نشده.
- آزمونِ دستی روی گوشیِ واقعی: APNs (کلید + قابلیتِ Push)، AirPrint، سرورِ دکان و هاب در شبکهٔ دکان، بوق و صفحه‌ها.

**بررسیِ محلی (لینوکس):** klib ِ iOS و klib ِ آزمونِ iOS (`compileKotlinIosSimulatorArm64`، `compileTestKotlinIosSimulatorArm64`) با
Compose 1.7.3 (‎۰.۷‎) بی خطا و بی هشدار در فایل‌های این گام، و `jvmTest` ِ `ios/shared` ۱۸/۱۸؛ سرور `apns` ۱۱/۱۱ و `push-delivery` ۲۳/۲۳ روی PGlite. فقط CI ِ macOS: پیوندِ چارچوب،
xcodebuild، اجرا روی شبیه‌ساز و `iosSimulatorArm64Test` (۹ + ۵ + ۳).

### ۰.۱۱ گامِ ۶ — ورود با اپل و قراردادِ مشترکِ «فراهم‌کننده»، شش فایلِ دیگرِ اندروید خودشان روی آیفون (۱۴۰۵/۰۸/۱۲، شاخهٔ `claude/ios-app-phase6`)

> روی `claude/ios-app-phase5` (#852) و دستهٔ سیزدهم (#855: رابطِ قدیم و `UiV3` پاک شد). باز هم **همان فایل‌های اندروید**؛ هیچ صفحه‌ای
> دوباره نوشته نشد. وابستگیِ سکو فقط در لایهٔ آیفون (`ios/shared/src/iosMain`)؛ رفتارِ اندروید و کامپیوتر همان است (دکمهٔ اپل آن‌جا
> هرگز ساخته نمی‌شود و درخواستِ تازه‌ای هم نمی‌رود).

**سنجش (فایل‌های `android-native/app/src/main/java` که برای iOS کامپایل می‌شوند):**

| | کامپایل برای iOS | همه | `ios-exclude.txt` |
|---|---:|---:|---|
| گامِ ۴ (#841) | ۱۱۳۷ | ۱۱۵۷ | ۲۰ |
| پیش از این گام: گامِ ۵ (`ac52c8ee`) | ۱۱۵۱ | ۱۱۷۰ | ۱۹ (۱۶ جایگزین + ۳ مستقیم) |
| همان، پس از ادغامِ دستهٔ ۱۳ (‎−۱۷ فایلِ `ui/legacy` و `UiV3`) | ۱۱۳۴ | ۱۱۵۳ | ۱۹ |
| **پس از این گام** | **۱۱۴۲** (۹۸٫۹٪) | ۱۱۵۵ (+۲ فایلِ مشترکِ تازه) | **۱۳** (۱۲ جایگزین + `MainActivity.kt`) |

شش سطر از `ios-exclude.txt` رفت و خودِ فایلِ اندروید جایش است (چهار جایگزینِ iOS پاک شد):

| فایلِ اندروید | چه شد (shim ِ iOS) |
|---|---|
| `core/net/TokenStore.kt` | `androidx.security.crypto.EncryptedSharedPreferences` روی کلیددان: یک پرونده = یک قلمِ Keychain (JSON)، «پس از نخستین بازگشایی، فقط همین دستگاه». حالا «سرورِ بعدی» (`stashTo`/`openFrom`) و انتقالِ پروندهٔ قدیمی همان کدِ اندروید است. `TokenStoreIos` پاک شد (برنامهٔ آیفون هنوز منتشر نشده، پس انتقالِ قلم‌های پیشین لازم نیست) |
| `data/FileName.kt` | `ContentResolver.query` ِ نامِ پروندهٔ `file://` (`OpenableColumns.DISPLAY_NAME`، `Cursor` ِ یک‌ردیفه)؛ نشانیِ دیگر ⇐ null ⇐ همان «تکهٔ آخر» |
| `data/Fingerprint.kt` | `androidx.biometric` روی LocalAuthentication: «دلیل» = `title` ِ اندروید، دکمهٔ دوم = `negativeButtonText`؛ لغو ⇐ بی پیام، «با رمز» ⇐ `ERROR_NEGATIVE_BUTTON` (رفتارِ همان `FingerprintIos` ِ پیشین) |
| `sync/Integrity.kt` | `PackageManager.getPackageInfo` ⇐ بسته‌ای بی گواهیِ APK ⇐ اثرِ انگشت «» و `isGenuine` با `SIGNING_SHA256` ِ خالیِ آیفون «درست» (امضا را خودِ iOS وارسی می‌کند؛ App Attest هنوز نه) |
| `data/TohidBackupAgent.kt`، `pay/platform/HesabPayHceService.kt` | `BackupAgent`/`HostApduService` ِ بی رفتار: در iOS هرگز ساخته نمی‌شوند (نه عاملِ پشتیبان، نه HCE)، ولی هر تغییرشان حالا روی آیفون هم وارسی می‌شود؛ `File.walkTopDown().onEnter` (همان `FileTreeWalk` ِ JVM) |

**ورود با اپل** (مانده از گامِ ۵؛ طرح همان §۰.۱۰ «هنوز نه»):

| لایه | چه شد | فایل |
|---|---|---|
| سرور | `lib/apple.js`: JWKS ِ اپل با کش (کلیدِ ناشناخته ⇐ یک بار تازه)، RS256، `iss`، `aud` ∈ `APPLE_CLIENT_IDS` (رشته، نه آرایه)، `exp`/`iat`، و **nonce ِ همین سرور**: `POST /api/auth/apple/nonce` بی نوشتن در پایگاه (`v1.<مهلت>.<تصادفی>.<HMAC>`، ده دقیقه)؛ ادعای `nonce` ِ توکن = SHA-256 ِ آن؛ **یک‌بار مصرف** با صدورِ نشست (`idempotency_keys`، بی مهاجرت) ⇐ همان توکن + nonce بارِ دوم ۴۰۱ `apple_nonce_used`. مسیرهای گوگل و اپل یک جدول‌اند (`PROVIDERS` در `lib/external-login.js`؛ مسیرها همان `routes/auth.js`): یافتن با شناسه، ساختن/وصل فقط با ایمیلِ تأییدشده، ایمیلِ دارای حساب ⇐ ۴۰۹ `apple_link_required` + `POST /api/auth/apple/link-code` (هرگز وصلِ خودکار؛ ۴۰۹ و کدِ وصل nonce را مصرف نمی‌کنند). نام از بدنه (اپل در توکن نمی‌دهد؛ بی نویسهٔ کنترلی، ۸۰ نویسه). بی `APPLE_CLIENT_IDS` همه ۴۰۳ `apple_not_configured` و `/config` ⇐ `appleLogin: false` | `server/src/lib/apple.js`، `lib/external-login.js`، `routes/auth.js`، `config.js`، `app.js` |
| کدِ مشترک | `data/ExternalSignIn.kt`: `SignInProvider` (کدِ ۴۰۹)، `ExternalLinkAsk` به‌جای `GoogleLinkAsk` ِ تک‌منظوره، `AppleSignInUi` و `ExternalSignIn.apple` (فقط لایهٔ آیفون می‌گذارد). `AuthController.apple`: nonce ِ تازه ⇐ پنجره ⇐ `/auth/apple`؛ ۴۰۹ ⇐ همان برگهٔ اثبات (`ExternalLinkSheet`، متنِ اپل). دکمهٔ «ورود با اپل» (`AppleButton`، رهنمودِ اپل: زمینهٔ `ink`، نوشته و نشانِ `bg`) فقط وقتی سکو پنجره دارد **و** سرور بازش کرده؛ اندروید و کامپیوتر نه پرسشِ `/config` ِ تازه‌ای دارند نه دکمه‌ای | `data/ExternalSignIn.kt`، `ui/v3/onboarding/*`، `data/repo/AuthRepository.kt` |
| آیفون | `ios/AppleSignInIos.kt`: `ASAuthorizationAppleIDProvider` (نام + ایمیل)، SHA-256 ِ هگزِ nonce، ایمیل از خودِ توکن (اپل فقط بارِ نخست می‌دهد؛ فقط برای نمایش)، لغو ⇐ `null` (بی پیام)؛ در `appDidLaunch` گذاشته می‌شود. Xcode: `-framework AuthenticationServices` | `ios/shared/src/iosMain/…/ios/AppleSignInIos.kt`، `project.pbxproj` |

**آزمونِ تازه:**
- سرور: `test/apple-signin.test.js` ۶/۶ روی PGlite — JWKS ِ محلی و کلیدِ RSA ِ همین آزمون (بی شبکه): خاموشی بی `APPLE_CLIENT_IDS`؛ حسابِ تازه با رلهٔ خصوصی و نام؛ nonce ِ یک‌بارمصرف؛ AUTH-04 برای اپل (۴۰۹، رمزِ غلط، کدِ وصل، سابقهٔ `auth.identity_linked` با `provider: apple`)؛ ایمیلِ تأییدنشده؛ nonce ِ دیگر/ساختگی/کهنه/بی‌nonce، امضای بیگانه، aud/iss/alg، منقضی؛ چرخشِ کلید.
- `src/iosTest/…/ios/Phase6IosTest` (۴) — SHA-256 ِ nonce هم‌شکلِ سرور و ایمیل از توکن، خودِ `FileName` و `Integrity` ِ اندروید روی shim، رمزگذارِ پروندهٔ کلیددان (همهٔ نوع‌ها). فقط روی شبیه‌ساز (کارِ `ios-app`).

**هنوز نه (و چرا):**
- ورود با اپل روی گوشیِ واقعی — قابلیتِ «Sign in with Apple» و `APPLE_CLIENT_IDS` (§۰.۶ بندِ ۵)؛ بی آن‌ها دکمه پنهان است. روی شبیه‌ساز هم بی امضا اپل خطای ۱۰۰۰ می‌دهد.
- ۱۲ جایگزینِ مانده رفتارِ iOS ِ واقعی دارند (Keychain ِ کلیدِ دفتر، CoreLocation، fsync/rename ِ POSIX، دوربین، به‌روزرسانی از App Store، …) یا برای iOS معنا ندارند (`Migration` ِ WebView، `Platform`): پاک کردنشان رفتار را بدتر می‌کرد. `Haptics` (الگوی لرزشِ اندروید ⇐ بازخوردِ لمسیِ iOS) و `DiskWrite` (با `SDK_INT = 0` ِ shim شاخهٔ بی fsync ِ پوشه را می‌گرفت) عمداً ماندند.
- تشخیصِ گفتار و App Attest — همان دلیلِ §۰.۱۰.

**بررسیِ محلی (لینوکس):** klib ِ iOS و klib ِ آزمونِ iOS با Compose 1.7.3 و بدل‌های محلیِ `androidx.lifecycle` (‎۰.۷‎؛ مخزنِ گوگل بسته، Maven Central ِ مستقیم ۴۲۹ ⇐ آینهٔ GCS ِ آن؛ init-script در مخزن نیست؛ تنها دگرگونیِ محلیِ دیگر: `HapticFeedbackType.GestureThresholdActivate` ِ ۱.۸ در کپیِ ساخته‌شده ⇐ `LongPress`): هر دو klib سبز (`compileKotlinIosSimulatorArm64`، `compileTestKotlinIosSimulatorArm64`؛ ۱۱۴۴ فایل = ۱۱۴۲ + دو فایلِ `core/src/jvmMain`)، `jvmTest` ِ `ios/shared` ۱۸/۱۸. سرور روی PGlite: `apple-signin` ۶/۶ و با `google-link`، `auth`، `sec-auth`، `log-secrets`، `route-authz-inventory`، `tenant-matrix`، `app-compat`، `platform`، `trial-single-source` روی‌هم ۸۶/۸۶؛ `codeql-models` ۴/۴. هسته `jvmTest` ۱۱۵۱/۱۱۵۱؛ کامپیوتر ۲۷۳۴/۲۷۴۷ (همان ۱۳ آزمونِ `ui.theme` که فقط محلی سرخ‌اند). فقط CI ِ macOS: پیوندِ چارچوب، xcodebuild، اجرا روی شبیه‌ساز و `iosSimulatorArm64Test`.

### ۰.۱۲ گامِ ۷ — هیچ سطرِ بی‌دلیل در `ios-exclude.txt`: موقعیتِ اندروید روی آیفون، پوستهٔ مشترکِ برنامه، منطقِ خالصِ جایگزین‌ها در کدِ مشترک (۱۴۰۵/۰۸/۱۲، شاخهٔ `claude/ios-app-phase7`)

> روی main پس از #861 (گامِ ۶) و دستهٔ ۱۵ (#866). باز هم **همان فایل‌های اندروید**؛ هیچ صفحه‌ای دوباره نوشته نشد. رفتارِ اندروید و
> کامپیوتر همان است: کدی که از `MainActivity`، `BarcodeScanner`/`BarcodeScannerDesk` و `Updater`/`UpdaterDesk` بیرون آمد خط‌به‌خط
> همان است و همان‌جا صدا زده می‌شود (ترتیبِ `onCreate` دست نخورد).

**سنجش (فایل‌های `android-native/app/src/main/java` که برای iOS کامپایل می‌شوند؛ از گزارشِ `iosSources` ِ klib):**

| | کامپایل برای iOS | همه | `ios-exclude.txt` | خطِ بیرون‌مانده |
|---|---:|---:|---:|---:|
| main پس از #861 (`3fccb68e`) | ۱۱۴۶ | ۱۱۵۹ | ۱۳ | — |
| **پیش از این گام:** main پس از دستهٔ ۱۵ (`bb9a4332`) | ۱۱۵۱ | ۱۱۶۴ | ۱۳ (۱۲ جایگزین + `MainActivity.kt`) | ۳٬۲۳۹ |
| **پس از این گام** | **۱۱۵۴** (۹۹٫۰٪) | ۱۱۶۶ (+۲ فایلِ مشترکِ تازه) | **۱۲** (۴ فقط-اندروید + ۸ همتا) | **۲٬۸۶۰** |

جایگزین‌های iOS (`*Ios.kt` ِ هم‌API): ۱۲ فایل، ۷۷۴ خط ⇐ ۱۱ فایل، ۶۴۸ خط. ریشهٔ آیفون (`IosRoot.kt` + `IosApp.kt`): ۳۷۵ ⇐ ۲۴۰ خط.

**تصمیم برای هر ۱۳ سطر** (خواستِ صاحب‌کار: یا منطق به کدِ مشترک، یا shim ِ iOS، یا دلیلِ «فقط اندروید»؛ هیچ سطرِ بی‌دلیل):

| فایلِ اندروید | تصمیم | چه شد / چرا |
|---|---|---|
| `data/DeviceLocation.kt` | **shim** ⇐ از فهرست رفت | `android.location` ِ iOS روی CoreLocation (‎`android/location/Location.kt`‎): یک فراهم‌کننده (CoreLocation خودش GPS/Wi-Fi/دکل را می‌آمیزد)، `getLastKnownLocation` ⇐ `CLLocationManager.location` (از هر رشته؛ مدیر فقط روی رشتهٔ اصلی)، `requestLocationUpdates` ⇐ یک `requestLocation()`، برچسبِ نقطه از دقت (تا ۵۰ متر `gps`). با `java.lang.Double` و `java.util.function.Consumer` ِ shim خودِ فایلِ اندروید (تازگیِ ده‌دقیقه‌ای، پروندهٔ `tohid-location`، مهلتِ ۱۲ ثانیه، دقیق‌ترینِ تازه‌ها) روی آیفون است؛ `DeviceLocationIos.kt` پاک شد |
| `MainActivity.kt` | فقط اندروید + منطق به کدِ مشترک | بدنهٔ `setContent`، `CrashNotice`، تنظیم‌های پیش از صفحه، پیوند، لمسِ اعلان و همگام‌سازی به `AppShellAndroid.kt` (`AppShell`، `AppStart`) رفت و **هم** `MainActivity` **هم** ریشهٔ آیفون همان را صدا می‌زنند (پیش‌تر ریشهٔ آیفون رونوشتِ آن‌ها بود و هر تغییرِ `MainActivity` بی‌صدا به آیفون نمی‌رسید). خودِ Activity (چرخهٔ عمر، `enableEdgeToEdge`، نامِ مستعارِ پیوند در مانیفست، سرویسِ اسکنِ پس‌زمینه) نقطهٔ ورودِ اندروید است؛ ورودِ آیفون `MainViewController` ⇐ `IosRoot` |
| `data/Migration.kt` | فقط اندروید | کوچِ دفتر از برنامهٔ وبیِ قدیمیِ اندروید (WebView ِ همان بسته)؛ آیفون آن برنامه را هرگز نداشت |
| `update/Updater.kt` | فقط اندروید + منطق به کدِ مشترک | دانلود/امضا/نصبِ APK؛ آیفون فقط App Store (۲.۵.۲). `versionOf`/`isNewer` که در اندروید، کامپیوتر و آیفون سه بار نوشته شده بود ⇐ `update/ReleaseVersion.kt` (هر سه همان API را نگه داشتند) |
| `pay/platform/PayDevice.kt` | فقط اندروید | NFC ِ «شبیه‌سازیِ کارت» (HCE) و نمایشگرِ دومِ مشتری (`Presentation`)؛ iOS HCE را به برنامه‌ها نمی‌دهد (جز با مجوزِ ویژهٔ اپل در چند کشور) |
| `data/AtRestKeys.kt` | همتا (`AtRestKeysIos`) | KEK ِ Android Keystore و متنِ صفحهٔ امنیت («سخت‌افزاری»، چرخشِ نام)؛ shim ِ `AndroidKeyStore` روی Keychain همان متن را روی آیفون دروغ می‌کرد |
| `data/DiskWrite.kt` | همتا (`DiskWriteIos`) | شاخهٔ `java.nio` با سطحِ API است؛ با `SDK_INT = 0` ِ shim شاخهٔ بی fsync ِ پوشه گرفته می‌شد. همتا همان تضمین را با `fsync` + `rename(2)` دارد |
| `data/GoogleSignIn.kt` | همتا (`GoogleSignInIos`) | Credential Manager؛ پیام‌های خطایش (SHA-1، «حسابِ گوگل به گوشی بیفزایید») روی آیفون غلط است. ورود با گوگل در همهٔ سکوها خاموش است؛ ورودِ آیفون اپل است (§۰.۱۱). روزی که گوگل روشن شود همتایش GoogleSignIn ِ iOS است، نه shim |
| `hw/PrinterFinderAndroid.kt` | همتا (`PrinterFinderIos`) | از پیش فقط لایهٔ سکو (کارتِ شبکه، بلوتوثِ کلاسیک)؛ جست‌وجوی چاپگرِ شبکه مشترک است |
| `scan/BarcodeScanner.kt` | همتا + منطق به کدِ مشترک | CameraX + ML Kit + MediaPlayer ⇐ AVFoundation. `ScanGate` (سدِ تکرارِ ۱۲۰۰ میلی‌ثانیه‌ای) که سه بار نوشته شده بود ⇐ `core/…/scan/ScanGate.kt` (همان بسته؛ صفحه‌های `Sale`/`SaleRoute` دست نخوردند) |
| `scan/GuidedCapture.kt` | همتا (`GuidedCaptureIos`) | دوربینِ راهنمای CameraX؛ آیفون عکسِ کالا را با دوربینِ خودِ iOS (`TakePicture`) می‌گیرد |
| `ui/Platform.kt` | همتا (`PlatformIos`) | «کدام سکو» (شناسهٔ دستگاه، پوشهٔ پشتیبان) برای هر سکو جداست، مثلِ `PlatformDesk` |
| `ui/screens/Haptics.kt` | همتا (`HapticsIos`) | الگوی `Vibrator`؛ آیفون بازخوردِ لمسیِ خودِ iOS را دارد (تصمیمِ گامِ ۶). یکی کردنِ الگو با Core Haptics تصمیمِ صاحب‌کار و آزمونِ گوشیِ واقعی می‌خواهد |

**کارهای دستهٔ ۱۴ و ۱۵ روی آیفون:** امنیتِ حساب‌های پرداخت (اثرِ انگشتِ حسابِ گیرنده در حالتِ سادهٔ حساب‌پی، `HesabPayChangeNotice`)،
دری (`I18n.pick` ⇐ `DariText`)، صندوقِ گوشی با امتیاز و یادداشت (`TillSale.record`)، راهِ نسخهٔ ۳ برای کارهای بی‌راه و
فایل‌های تازهٔ `*Android.kt` ِ دستهٔ ۱۵ (`StockExtrasAndroid`، `WebcamPrefAndroid`، …) همه بی سطرِ تازه در `ios-exclude.txt` برای
iOS کامپایل می‌شوند (klib ِ دستهٔ ۱۵ پیش از این گام هم سبز بود: ۱۱۵۱ فایل).

**آزمونِ تازه:**
- `src/iosTest/…/ios/Phase7IosTest` (۶) — نقطهٔ CoreLocation ⇐ `Location` ِ اندروید (برچسبِ gps/network از دقت، زمان، دقتِ نامعلوم)؛
  `LocationManager` ِ `IosContext` و `DeviceLocation.remembered` ِ خودِ فایلِ اندروید روی پروندهٔ `tohid-location`؛ `AppStart.openLink`
  (همان پیوندِ `MainActivity`)؛ `ScanGate` و `ReleaseVersion`/`Updater` ِ آیفون؛ حساب‌پیِ ساده با گیرنده و دری (دستهٔ ۱۴)؛ یادداشتِ
  فاکتورِ `TillSale.record` (دستهٔ ۱۵). فقط روی شبیه‌ساز (کارِ `ios-app`).
- هسته: `ScanGateTest` (۳).

**هنوز نه (و چرا):**
- شکستِ گرفتنِ موقعیت: جایگزینِ پیشین همان دم null می‌داد؛ حالا همان رفتارِ اندروید است (فراهم‌کنندهٔ بی‌جواب ⇐ مهلتِ ۱۲ ثانیه ⇐ null).
  `granted` پیش از آن سنجیده می‌شود، پس فقط وقتی پیش می‌آید که اجازه هست ولی نقطه‌ای نمی‌آید.
- `desktop/…/Main.kt` هنوز رونوشتِ خودش از پوسته را دارد؛ رفتنش به `AppShell` تغییرِ رفتارِ کامپیوتر (پنجره، سینی) را می‌سنجد و این گام دست نزد.
- تشخیصِ گفتار و App Attest — همان دلیلِ §۰.۱۰؛ ۸ همتا و ۴ فقط-اندروید بالا.

**بررسیِ محلی (لینوکس):** klib ِ iOS و klib ِ آزمونِ iOS با Compose 1.7.3 و همان بدل‌های محلیِ گامِ ۶ (‎۰.۱۱؛ init-script در مخزن نیست):
پیش از گام ۱۱۵۱ + ۲ فایلِ `core/src/jvmMain` سبز؛ پس از گام هر دو klib سبز (`compileKotlinIosSimulatorArm64`،
`compileTestKotlinIosSimulatorArm64`؛ ۱۱۵۴ + ۲). `jvmTest` ِ `ios/shared` ۱۸/۱۸؛ هسته `jvmTest` ۱۱۷۲/۱۱۷۲ (با `ScanGateTest`)؛ کامپیوتر ۲۷۷۴/۲۷۸۷ (همان ۱۳ آزمونِ `ui.theme` که فقط محلی سرخ‌اند؛ `UpdaterTest` سبز).
فقط CI ِ macOS: پیوندِ چارچوب، xcodebuild، اجرا روی شبیه‌ساز و `iosSimulatorArm64Test`. اندروید روی این ماشین ساخته نمی‌شود (SDK نیست):
`MainActivity` و `AppShellAndroid.kt` را CI ِ اندروید می‌سازد؛ خودِ `AppShellAndroid.kt` با کامپیوتر و آیفون هم کامپایل شد.

**رفعِ پیوندِ چارچوب (پس از #869/#871):** کارِ `ios-app` از این گام در `linkDebugFrameworkIosSimulatorArm64` با
`Linking globals named 'OBJC_CLASS_$_TohidSharedDouble': symbol multiply defined!` می‌افتاد (klib سبز بود). ریشه: shim ِ تازهٔ
`java.lang.Double` (`object`، عمومی) در سرآیندِ ObjC نامِ `TohidSharedDouble` می‌گرفت (پیشوندِ چارچوب + نامِ ساده، بی بسته) و
Kotlin/Native همین نام را برای جعبهٔ `kotlin.Double` در زمانِ اجرای خودش دارد — نامگذار فقط `Base`/`MutableSet`/`MutableDictionary`
را رزرو می‌کند، نه `Number` و جعبه‌های عدد. یک چارچوب است و `export()` ِ دوباره‌ای نیست. رفع: shim `internal` (فقط برای کامپایلِ همین
ماژول، نه API ِ Swift؛ بی پرچمِ پیوند). نگهبان: `src/jvmTest/…/ios/ObjcNamesJvmTest` (کارِ ارزانِ `ios-ui-klib`) هر ردهٔ عمومیِ سطحِ
بالا به نامِ `Number`، `Byte`…`Double`، `Boolean` در shimها، `commonMain` و کدِ اندروید را رد می‌کند.

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
| به‌روزرسانیِ خودکار | `update/*` (APK) | **ممنوع روی iOS** (قاعدهٔ ۲.۵.۲)؛ فقط App Store / TestFlight. روی iOS `Updater` جایگزینِ بی‌APK دارد (`update/UpdaterIos.kt`): نه می‌پرسد، نه می‌گیرد، نه نصب می‌کند | — |
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

## پیوستِ ب — روشِ شمارشِ بخشِ ۰

- منبع: خروجیِ `androidSources` ِ `desktop/build.gradle.kts` (`gradle -p desktop androidSources`)؛ توضیح‌ها (`//`، `/* */`) پیش از شمارش پاک شدند.
- هر سطرِ جدولِ ۰.۲ یک الگوی `grep -P` است (مثلاً `(?<![\w.])android\.[a-z]\w*\.`، `\b(LocalContext|Context)\b`، `(?<![\w.])System\.\w+`،
  `Dispatchers\.IO\b`، `\bsynchronized\s*\(|@Synchronized\b`)؛ «فایل» = فایل‌هایی که دست‌کم یک بار دارند، «رخداد» = جمعِ همه.
- «کامپایل‌شده برای iOS» شمارش نیست، نتیجهٔ کامپایل است: فایل‌های با API ِ بی shim کنار رفتند، سپس `compileKotlinIosSimulatorArm64`
  تکرار شد و هر فایلِ خطادار با نخستین نامِ ناپیدایش به `ios-exclude.txt` رفت، تا کامپایل سبز شد (۷ دور).

