# کیتِ نسخهٔ ۳ (VILL3N) — راهنمای کوتاه

هر صفحهٔ گوشی و کامپیوتر فقط از همین اجزا ساخته می‌شود. رنگ فقط از `PhoneV3` / `PhoneV3Roles`
(سیستمِ رنگِ VILL3N)، حرکت فقط از `Motion.kt` و `ui/v3/V3Motion.kt`، نوشته فقط از جدول‌های `core/i18n`
(`Str*`؛ واژه‌های کیت در `StrKit`). هیچ HEX، هیچ `Shop.colors`، هیچ `Tohid*`، هیچ `AlertDialog(` در صفحه.

کد: `android-native/app/src/main/java/ir/vil3ntec/tohid/ui/v3/` (کیت) و `ui/v3/nav/` (مسیریاب).

## اجزا (`ir.vil3ntec.tohid.ui.v3`)

| جزء | کی | نکته |
|---|---|---|
| `V3Scaffold(title, subtitle?, onBack?, actions≤2, overflow, search, chips, notice, bottomReserve, largeTitle, fab, bottomBar, backdrop?) { pad -> }` | قابِ **هر** صفحه | عنوانِ درشتِ ۳۰sp که با پیمایش جمع می‌شود؛ `pad.calculateBottomPadding()` را به فهرست بدهید. `notice` را پوسته خودش می‌گذارد (`ui/v3/shell/ShellNotice()`، مالِ G10: اشتراک/نسخهٔ تازه). `backdrop` (مثلاً `Modifier.frosted(PhoneV3.colors)`) به‌جای `bg`؛ سربرگ و عنوانِ درشت آن‌وقت شفاف‌اند. |
| `V3Section(title?, footer?, action?)` + `V3Card` + `V3Divider(start)` | گروهِ محتوا | سرخط بیرونِ کارت؛ کارت همیشه مرزدار. |
| `V3ListRow(title, subtitle?, icon?, hue?, leading?, value?, trailing?, startActions, endActions, selected?, onLongClick?, onClick?)` | **هر** ردیفِ فهرست | کاشیِ رنگیِ `V3Hue`؛ کشیدن ⇐ کارها (آینه در RTL، لرزش در آستانه، کشیدنِ کامل = کارِ اول). |
| `V3IconTile`، `V3Chevron`، `V3ProductThumb(productId, name)` | آیکن/عکس | عکسِ کالا یا کاشیِ حرفِ اول. |
| `V3Button(text, onClick, kind, icon?, enabled, loading, haptic)`، `V3TextButton`، `V3IconButton` | کار | `V3ButtonKind`: PRIMARY / SECONDARY / NEUTRAL / DANGER؛ غیرفعال و «در حالِ کار» داخلی‌اند. |
| `V3Form(title, primary, onPrimary, onBack, primaryEnabled, loading, secondary?, summary?, dirty)` + `V3FormBar` | **هر** فرم | دکمهٔ اصلیِ چسبان بالای صفحه‌کلید؛ `dirty` ⇒ برگشت اول «دور ریخته شود؟». |
| `V3TextField(value, onValueChange, label, kind, placeholder?, helper?, error?, required, suffix?, onScan?)` | کادر | `V3FieldKind`: TEXT، NUMBER، MONEY (هزارگان در نمایش، «؋»)، BARCODE (دکمهٔ اسکن)، PHONE، EMAIL، PASSWORD، DATE. خطای درجا زیرِ همان کادر. |
| `V3SearchField`، `V3ToggleRow`، `V3PickerRow`، `V3DateField`، `V3Toggle` | ورودی‌های دیگر | یک جست‌وجو در هر صفحه. |
| `V3Chip`، `V3AddChip`، `V3ChipRow`، `V3Segmented(options, selected, onSelect)` | فیلتر و تب | نشانگرِ لغزانِ فنری. |
| `V3Sheet(onDismiss, title?, dismissible = true) { closer -> }`، `V3ConfirmSheet(...)`، `V3PickerSheet(...)` | برگه | بستن با حرکت: `closer.close { بعد }`. کارِ پاک‌کننده = `V3ConfirmSheet` با متنِ هشدارِ موتور. `dismissible = false` (نمایشِ یک‌بارهٔ کد): زدنِ بیرون، کشیدن و «برگشت» نمی‌بندند، فقط `closer`. |
| `V3Stat(label, value, format)` + `V3StatGrid` | آمار و پول | شمارندهٔ عدد، عددِ پایانی دقیق. |
| `V3StatusPill(V3Status, label)` | وضعیتِ سفارش (§۸) | نقطه + برچسب، هفت وضعیت. |
| `V3InvoiceItems(items: List<V3InvoiceLine>, title?, footer?)` | اقلامِ فاکتور | `V3InvoiceLine(name, detail, total, note?)` با متنِ از پیش قالب‌شده (پول را موتورِ صدازننده می‌سازد)؛ همان کارتِ اقلامِ برگهٔ تراکنشِ قرض‌دار. |
| `V3Message(V3Tone, title, body?, action?)` | پیام (§۹) | |
| `V3EmptyState(icon, title, body?, action?)`، `V3Skeleton(rows)` | خالی / در حالِ بار | به‌جای چرخنده، اسکلت. |
| `V3Fab(icon, label, expanded, onClick)` | کارِ اصلیِ فهرست | با پیمایش جمع می‌شود؛ Ctrl+N کامپیوتر همین. |
| `V3ActionBar(count, actions, onClear)` | انتخابِ گروهی | |
| `LocalV3Toasts.current?.show(text)` / `.undo(text) { }`، `V3SnackbarHost` | پیام کوتاه | «برگرداندن» فقط جایی که موتور کارِ وارونه دارد. |
| `V3TabBar(tabs, current, onPick)`، `V3TwoPane(list, detail)` | پوسته‌های دیگر، کامپیوتر | |
| `V3Type.*` | نوشته | largeTitle، title، headline، body، callout، subhead، footnote، caption، figure. |

نمونهٔ زندهٔ همه: `ui/v3/gallery/KitGallery.kt` (پیش‌نمایشِ `phone:g:foundation:kit-basics|kit-forms|kit-lists|kit-states`؛
پسوندِ `-dark` و `-en`؛ همه با `groupshots:foundation`).

## حرکت (همه پشتِ `Motion.enabled`)

- واژه‌ها: `MotionTokens` (SHORT 150، MEDIUM 220، LONG 280، EMPHASIZED 350، پلکان ۳۰ms × ۱۰، شمارنده ۶۰۰) و
  `MotionSpec` (`tweenOf`، `sheet` فنرِ ۰٫۸۵/۳۸۰، `indicator`، `morph`، `press`). عددِ تازه نسازید.
- جابه‌جاییِ صفحه را پوسته می‌کند (`V3Transitions.page`): رفتن = از سمتِ پایان (RTL: از چپ)، برگشت برعکس، تب = محو.
  زیرصفحهٔ درونی: `V3PageSwitch(target, depth)`.
- فهرست به جزئیات: `V3ContainerTransform(selected, list, detail)` و `Modifier.v3Container(key)`.
- ورودِ پلکانی: `val s = rememberV3Stagger()` و `Modifier.v3Stagger(s, key, index)` (فقط بارِ اول).
- فشار: `Modifier.v3Pressable(scale = 0.97f ردیف / 0.92f دکمهٔ گرد, haptic)`.
- عدد: `rememberCountUp(value)`؛ موفقیت: `V3SuccessCheck(visible)`.
- لرزش: `haptic = true` روی کارِ اصلی/پاک‌کننده؛ `Haptics.success/confirm` برای ثبت و اسکن.

## صفحهٔ تازه + مسیر

1. فایل‌ها در `ui/v3/<گروه>/`: `XScreen.kt` (فقط نمایش) + `XModel.kt` (حالتِ خالص، با آزمون). زیرِ ۴۰۰ خط.
2. مسیر را **فقط** در `ui/v3/<گروه>/<Group>Routes.kt` ثبت کنید (تنها فایلِ مشترکِ گروه با پوسته):
   ```kotlin
   V3Route("stock/{id}", RouteKind.PAGE, { StrStock.item }, parent = StockPaths.LIST, chrome = Chrome.OWN) {
     StockItemScreen(id = args["id"].orEmpty(), data = data, onBack = { back() }, onEntry = { nav.open(StockPaths.entry(product = it)) })
   }
   ```
   `chrome = Chrome.OWN` یعنی صفحه `V3Scaffold` خودش را دارد (صفحه‌های بازسازی‌شده همیشه OWN).
3. گونه‌ها: `TAB` (ریشه، در نوار)، `PAGE` (رفتن به عمق)، `SHEET` (روی صفحهٔ زیر)، `GATE` (تمام‌صفحه، بیرونِ پشته).
4. ناوبری فقط با مسیر، هرگز import ِ صفحهٔ گروهِ دیگر: `nav.open(ProductsPaths.item(id))`، `back()` (در مسیر؛ از مسیری که
   دیگر بالای پشته نیست هیچ نمی‌کند — پس `onDismiss` ِ برگه پس از `finish` صفحهٔ صدازننده را نمی‌بندد)، `nav.back()`،
   `nav.replace(...)`، `nav.switchTab(...)`، `nav.openForResult(SalesPaths.scan("stock")) { code -> }` و در مقصد `nav.finish(value)`.
   از هر جا: `LocalV3Nav.current`؛ بیرون از Compose: `AppNav.go(path)`.
5. پیوندِ بیرونی: `vill3n://app/<مسیر>` (اعلان‌ها) — `DeepLinks.appLink(path)`. پیوندِ سرد پشته را از زنجیرهٔ `parent` می‌سازد.
6. دسترسی: `needs = setOf(Need.MANAGE)` و/یا پرچمِ ماژول؛ بی‌دسترسی خودکار صفحهٔ «دسترسی ندارید» با برگشت. متنِ ویژهٔ
   همان صفحه: `V3Route(..., deniedBody = { StrX.why })` (پیش‌فرض `StrKit.noAccessBody`).
7. عکس: حالت‌های صفحه را در `desktop/.../shots/<Group>Shots.kt` بگذارید؛ روشن/تیره × fa/en.

### اسکن برای نتیجه
- بارکد (فقط دکان، قالب‌های فروش): `nav.openForResult(SalesPaths.scan("stock")) { code -> }`.
- **QR در هر سه حالت** (دکان، پیک، خرید): `nav.openForResult(ScanPaths.QR) { raw -> }` — `ScanPaths.QR = "scan?kind=qr"`،
  فقط `FORMAT_QR_CODE`، متنِ خام با `nav.finish(text)`؛ `null` یعنی بسته شد. کامپیوتر دوربین ندارد ⇐ برگهٔ «این‌جا دوربین نیست».
  در حالتِ خرید: `nav.openForResult(ScanPaths.QR) { raw -> raw?.let { ShopperLinks.parse(it, hosts) }?.let(nav::open) }`.
- پرسشِ ثابت در الگو (`scan?kind=qr`) شرط است و الگو را از `scan` دقیق‌تر می‌کند (`RoutePattern.required`).

### درزهای میانِ گروه‌ها (بی import)
- `HomePager.page` (۰ خانه، ۱ آمار؛ `stats?period=` می‌گذارد، ورقِ خانه می‌رود و null می‌کند) و `HomePager.stats`
  (محتوای آمار که G6 ثبت می‌کند و ورقِ خانهٔ G10 صدا می‌زند) — `ui/v3/nav/HomePager.kt`.

### زبان
`LocaleChoice.lang` (فارسی/English/پښتو، `tohid-locale` › `lang`) و `LocaleChoice.setLang(context, Lang)`؛ ریشهٔ گوشی و
کامپیوتر `ProvideLang { }` است: جهت از `lang.rtl` (فارسی و پشتو راست‌به‌چپ، انگلیسی چپ‌به‌راست) و `LocalLang`. نوشته‌ها
(`Str*`) خودشان از `I18n.source = { LocaleChoice.lang }` می‌خوانند. صفحه هیچ‌وقت `LayoutDirection.Rtl` ِ ثابت نمی‌گذارد.

## مسیرهای حالتِ «خرید» (جای‌نگهدار، گروهِ خرید می‌سازد)

`shopper/home` · `shopper/shop/{slug}` (`?product=`) · `shopper/cart` · `shopper/cart/{slug}` · `shopper/checkout` ·
`shopper/orders` · `shopper/order/{id}` · `shopper/order/{slug}/{token}` · `shopper/account` — در
`ui/v3/shopper/ShopperRoutes.kt`. پیوندِ QRِ دکان (`https://<slug>.vill3n.top/`، `https://<میزبان>/store/<slug>`)
برنامه را در حالتِ خرید روی همان دکان باز می‌کند.

## ساختار و قاعده‌ها

- کیت: `ui/v3/*.kt` (هر جزء یک فایلِ کوچک)؛ نقش‌های رنگ: `ui/theme/PhoneV3Roles.kt`؛ حرکت: `ui/screens/Motion.kt`.
- مسیریاب: موتورِ خالص در `core/nav/` (آزمون: `NavCoreTest`)، فهرست و پوسته در `ui/v3/nav/` (آزمون: `ModuleRegistryTest`).
- جزءِ تازه لازم شد؟ در بستهٔ گروهِ خودتان `internal DraftX` بسازید و در PR بنویسید؛ پایه آن را به کیت می‌برد.

## عکس و کامپایلِ محلی

**کامپایلِ کاملِ کامپیوتر** (همهٔ کدِ `android-native` هم با آن کامپایل می‌شود) بی شبکه، با کلاس‌پثِ گریدلِ همین ماشین:

```sh
cd desktop
export LC_ALL=C.UTF-8 LANG=C.UTF-8   # بی این، نامِ فارسیِ آزمون‌ها در مسیرِ کلاس «Malformed input» می‌دهد
gradle --offline --no-daemon -q -Pdev=true --init-script <scratchpad>/v3f/offline.init.gradle compileKotlin      # کد
gradle --offline --no-daemon -q -Pdev=true --init-script <scratchpad>/v3f/offline.init.gradle compileTestKotlin  # آزمون‌ها
```

نامِ آزمون (`` fun `...`() ``) نباید `:` `.` `;` `/` `<` `>` `[` داشته باشد — JVM نمی‌پذیرد (به‌جایش «—»).

`offline.init.gradle` گروه‌های `androidx.*` را (که در کش نیستند) کنار می‌گذارد، دو jar ِ `runtime-desktop`/`runtime-saveable-desktop`
۱.۷.۳ ِ کش را جایشان می‌گذارد و پوشهٔ `v3f/stubs` (یک `androidx/lifecycle/Lifecycle.kt` ِ ۱۲خطی: `Lifecycle` با `Event`/`State`
و `LifecycleOwner`) را به منبع‌ها می‌افزاید. بی این پوشه همان ۱۲ خطای `androidx.lifecycle` ِ همیشگی می‌آید و چیزِ دیگری
نیست. اگر «Connection to the Kotlin daemon has been unexpectedly lost» آمد (کمبودِ حافظه)، گریدل خودش بی daemon ادامه
می‌دهد؛ فقط `exit 0` و نبودِ `e: file:` را ببینید. اجرای آزمون‌ها (`test`) این‌جا نمی‌شود (jar ِ skiko در کش نیست) —
آزمون را CI می‌راند.

**رسمِ بی‌پنجره (ImageComposeScene)**: پروژهٔ کوچکِ `<scratchpad>/kt-v3render` (‏`build.gradle.kts` + `src/main/kotlin/Render.kt`
و دو جایگزینِ کوچکِ `Shop`/`LocaleText`). فایل‌های کیت، تم، حرکت، `core/i18n|nav|text` و چند shim ِ `desktop/src/shim` را از
همین درخت کپی می‌کند (مسیرِ درخت سرِ `build.gradle.kts`، متغیرِ `wt`) و هر صفحه را روشن/تیره × fa/en در ۳۹۰×۸۴۴ رسم می‌کند:

```sh
cd <scratchpad>/kt-v3render && gradle --no-daemon -q run --args="<پوشهٔ خروجی> [پیشوندِ نام]"
```

راهِ `androidx.collection`: کامپوزِ ۱.۷ در اجرا `androidx.collection` می‌خواهد که فقط روی Google Maven است؛
`dl.google.com` (همان `google()` ِ گریدل) را پراکسی می‌بندد ولی `https://maven.google.com` باز است. پس در این پروژه
`repositories { mavenCentral(); maven("https://maven.google.com") }` است و `androidx.collection`/`androidx.annotation` دیگر
کنار گذاشته یا جایگزین نمی‌شوند (فقط `androidx.lifecycle` و `androidx.arch.core`). صفحهٔ تازه: یک خط در `screens` ِ `Render.kt`.

عکس‌های CI: `desktop-shots` هر `Shot` ِ `desktop/.../shots/<Group>Shots.kt` را با `groupshots:all` می‌گیرد و در شاخهٔ
`desktop-screenshots` زیرِ `pr/<شاخه>/v3/<گروه>/<حالت>-<light|dark>-<fa|en>.png` (با README ِ هر گروه) می‌گذارد.
