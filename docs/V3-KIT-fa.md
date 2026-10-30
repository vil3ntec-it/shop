# کیتِ نسخهٔ ۳ (VILL3N) — راهنمای کوتاه

هر صفحهٔ گوشی و کامپیوتر فقط از همین اجزا ساخته می‌شود. رنگ فقط از `PhoneV3` / `PhoneV3Roles` / `PhoneV3Palette`
(سه تمِ THEME-V2 — پایین)، حرکت فقط از `Motion.kt` و `ui/v3/V3Motion.kt`، نوشته فقط از جدول‌های `core/i18n`
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
| `V3Sheet(onDismiss, title?, dismissible = true) { closer -> }`، `V3ConfirmSheet(...)`، `V3PickerSheet(...)` | برگه | بستن با حرکت: `closer.close { بعد }`. کارِ پاک‌کننده = `V3ConfirmSheet` با متنِ هشدارِ موتور. `dismissible = false` (نمایشِ یک‌بارهٔ کد): زدنِ بیرون، کشیدن و «برگشت» نمی‌بندند، فقط `closer`. محتوا پیمایش می‌شود (`scroll = true`)؛ فهرستِ Lazy درونِ برگه `heightIn(max = …)` می‌خواهد، یا `scroll = false`. |
| `V3FormSheet(title, dirty, onClose, scroll = true) { sheet -> }` | فرم در برگه (ورود، اصلاح، پرداخت) | همان `V3Sheet` با نگهبانِ «دور ریخته شود؟»: اگر `dirty` است، کشیدن، زدنِ پس‌زمینه و «برگشت» اول `V3ConfirmSheet` (`StrKit.discardTitle/Body/discard`) می‌آورد؛ «ادامهٔ ویرایش» برگه را با همان وضع دوباره باز می‌کند (حالتِ فرم را بیرونِ برگه نگه دارید). کارِ تمام‌شده: `sheet.finish { بعد }` — بستن بی پرسش. `scroll = false` وقتی فرم خودش بخشِ پیمایش‌پذیر و دکمهٔ چسبان دارد. |
| `V3Stat(label, value, format)` + `V3StatGrid` | آمار و پول | شمارندهٔ عدد، عددِ پایانی دقیق. |
| `V3StatusPill(V3Status, label)` | وضعیت (§۸) | نقطه + برچسب. `AWAITING` فقط «در انتظارِ تأیید» است (بنفش، همان بنفشِ `REVIEW`)؛ هشدار — کم‌موجود، نزدیکِ انقضا، سقف، اختلاف، رو به پایان — `WARNING` (کهربایی). در صفحه‌های قدیم همین جدایی `PillTone.AWAITING` / `WARNING` است. |
| `V3HeroCard(title, value, label?, caption?, chips: List<V3HeroChip>, pages, page, onBell?, bellDot, onTitle?, live, valueForSize)` | کادرِ بزرگِ خانه و کادرِ کشیدنی | **یک قالب** برای «فروشِ امروز»، «صندوق»، «گاوصندوق» و کادری که پایین کشیده می‌شود (خانهٔ G10 همین را به کار می‌برد): گرادیانِ تم (۱۳۵°، آینه در RTL)، دو دایرهٔ نرمِ رنگِ همراه، متنِ سفیدِ کامل، سرخطِ نامِ دکان (با `onTitle` زدنی) + زنگ، عددِ ۳۶sp (۳۰sp وقتی `valueForSize` بلند است — برای عددِ در حالِ شمارش عددِ پایانی را بدهید)، زیرنویس (با `live` آیکنِ «زنده»)، تا ۳ تراشهٔ شیشه‌ای `V3HeroChip(label, icon?, haptic, onClick?)` (بی `onClick` = فقط نوشته) و نشانگرِ صفحه در همان ردیف. شعاع ۲۸، بلندیِ ثابتِ ۲۲۸dp (با درشتیِ نوشته تا ×۱٫۳)؛ حاشیهٔ ۱۶ از لبه را ورق‌زن می‌دهد. زیرِ شیشهٔ تراشه سایهٔ تم (`heroChipShade`) تا AA. |
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

## رنگ و تم (THEME-V2، ۱۴۰۵/۰۸/۰۲)

سه تم × روز و شب در `ui/theme/ShopThemes.kt`: «آسمان» (`SKY`، پیش‌فرض)، «اقیانوس» (`OCEAN`)، «غروب» (`DUSK`).
صفحه‌ها کاری نمی‌کنند: `PhoneV3.colors`، `PhoneV3.ui`، `PhoneV3Roles`، `PhoneV3Palette` و `PhoneV3Debt` همه از
`Shop.colors` ِ تمِ جاری ساخته می‌شوند و عوضِ تم بی راه‌اندازیِ دوباره در ۲۵۰ms نرم جابه‌جا می‌شود.

| چه | API | نکته |
|---|---|---|
| تمِ جاری | `ThemeStore.theme` (فقط خواندن، `State`) | فقط روی همین دستگاه (`tohid-theme`)، همگام نمی‌شود؛ `load(context)` در `MainActivity` و `Main.kt`. |
| ردیفِ «تم» در تنظیمات (`ThemePaletteRow` ِ G9، وصل در `settings/general/appearance`) | `ShopTheme.entries` + `ThemeStore.set(context, t)` | نام‌ها: آسمان / اقیانوس / غروب؛ جدا از «روشن / تیره / خودکار» (`ThemeChoice`). پیش‌نمایش بی ذخیره: `ThemeStore.preview(t)`. |
| پالتِ یک تم | `shopColors(theme, dark)` | `LightColors`/`DarkColors` همان «آسمان»‌اند. |
| گرادیانِ کادرِ بزرگ | `Shop.colors.heroGradient` (سه رنگ) + `heroScrim` | فقط برای `V3HeroCard` و ستونِ انتخاب‌شدهٔ نمودار؛ متن رویش همیشه سفید. |
| رنگِ همراه | `Shop.colors.accent(i)` / `accentSoft(i)` (۱۲٪ روز، ۲۰٪ شب)؛ در کیت `V3Hue` | هفت رنگ به ترتیب: آسمانی، بنفش، مرجانی، کهربایی، نعنایی، صورتی، لیمویی؛ در هر سه تم یکی. سری‌های نمودار (`chart.series`) همین ترتیب‌اند. |
| رنگِ هر بخش | `V3Modules.hueOf(id)` / `hueOf(entry)` (`HUE_BY_ID`) | ثابت: فروش برند، دریافت نعنایی، ورودِ کالا کهربایی، تاریخچه بنفش، مشتری‌ها/قرض‌دارها مرجانی، مصارف صورتی، گزارش آسمانی، خرید لیمویی. `V3IconTile(icon, hue)` آیکنِ رنگی روی زمینهٔ نرمِ همان رنگ می‌کشد. |
| گوشه‌ها | `PhoneV3.groupRadius` ۲۰، `PhoneV3.tileRadius` ۱۴، کاشیِ آیکن ۱۲، کادرِ بزرگ ۲۸ | همهٔ کارت‌ها و برگه‌ها از همین؛ عددِ دستی نه. |

رنگ‌های معنایی (موفق، خطر، هشدار، وضعیت‌ها) به تم بستگی ندارند. انحراف‌های ثبت‌شده (کنتراستِ AA) سرِ `ShopThemes.kt`
و در `Vill3nPaletteTest`؛ عددهای سه تم در `ThemeV2PaletteTest`. **وبِ منجمد** (`index.html`) پالتِ VILL3N ِ خودش را نگه می‌دارد —
تصمیمِ صاحب‌کار: وب فقط درمانِ ایمنیِ داده می‌گیرد، نه رنگ (`WebPaletteParityTest` همان را ثابت نگه می‌دارد).
نمونهٔ زنده: `ui/v3/gallery/ThemeGallery.kt` (عکس‌های `groupshots:foundation` ⇐ `theme-sky|ocean|dusk`).

## حرکت (مثلِ اپل، سبک — همه پشتِ `Motion.enabled`)

قاعده (`MOTION-APPLE.md`): فقط APIهای خودِ Compose، فقط `graphicsLayer` (بی چیدمانِ دوباره در هر فریم)، بی Lottie و
کتابخانهٔ تازه، ۶۰ فریم روی گوشیِ ضعیف: در رسم چیزی نسازید (`remember`/`drawWithCache`)، کلید روی ردیف‌های Lazy،
`derivedStateOf` برای پیمایش. «کاهشِ حرکت» ⇐ همان حالتِ پایانی، بی‌درنگ. صفحه‌ها کدِ حرکتِ خودشان را نمی‌نویسند؛ فقط این‌ها:

| خواسته | API |
|---|---|
| فنرهای قطع‌شدنی (هدفِ تازه از همان سرعت) | `V3Springs.ui()` (۰٫۸۵، جا و اندازه)، `V3Springs.playful()` (۰٫۷، فقط تأییدها)؛ `MotionSpec.sheet/indicator/morph/press`؛ با `animate*AsState`/`Animatable` |
| واژه‌ها | `MotionTokens` (SHORT 150، MEDIUM 220، LONG 280، EMPHASIZED 350، پلکان ۳۰ms × ۱۰، شمارنده ۶۰۰)؛ عددِ تازه نسازید |
| رفتن/برگشتِ صفحه | پوسته (`V3Transitions.page`): صفحهٔ تازه تمام‌عرض با فنر از لبهٔ پایان (RTL: چپ)، پیشین ۳۰٪ کنار و کم‌نور (۰٫۷۲)؛ برگشت برعکس؛ تب و کامپیوتر = محو. زیرصفحهٔ درونی: `V3PageSwitch(target, depth)` |
| برگه پا‌به‌پای انگشت | `V3Sheet` (کشیدن و رهاکردن با سرعتِ همان دست)؛ صفحهٔ پشت خودکار تا ۰٫۹۶ (`v3BehindSheet` در پوسته، `V3Depth`) |
| ورقِ کارت‌ها | `HorizontalPager` + `Modifier.v3PagerItem(state, page)` (کناری کوچک و کم‌رنگ، با انگشت بزرگ می‌شود) |
| فشار روی **هر** زدنی | `Modifier.v3Pressable(scale = 0.97f ردیف/کارت، 0.92f آیکن, haptic = کارِ اصلی)` |
| ورودِ پلکانی | `rememberV3Stagger()` + `Modifier.v3Stagger(s, key, index)` (بارِ اول، ≤۱۰) |
| در حالِ بار | `V3Skeleton(rows)` (درخششِ فقط‌رسم) |
| عدد و نمودار | `rememberCountUp(value)`؛ `val p = rememberV3Grow(data)` و بلندی × `p` یا `Modifier.v3GrowFromBaseline { p }` |
| عنوانِ درشتِ جمع‌شونده | خودِ `V3Scaffold` |
| کارت ⇐ جزئیات، FAB ⇐ برگه | `V3ContainerTransform(selected, list, detail)` + `Modifier.v3Container(key)` روی هر دو سر. FAB: `selected = if (open) "fab" else null`، `V3Fab(..., modifier = Modifier.v3Container("fab"))` در `list` و سطحِ برگه با همان کلید در `detail` |
| موفقیت | `V3SuccessCheck(visible, haptic = true)` (فنرِ شاد + کشیدنِ تیک + لرزشِ نرم) |
| خطا | `Modifier.v3Shake(trigger)` (سه رفت‌وبرگشتِ ۸dp + `Haptics.error`) — `trigger` شمارنده یا پیامِ خطا |
| آمدن/رفتن/جابه‌جاییِ ردیف | Lazy: `Modifier.v3AnimateItem(this)` (کلید لازم)؛ غیرِ Lazy و «کشیدن برای حذف»: `V3Collapse(visible) { … }` + `LocalV3Toasts.current?.undo(...)` |
| لرزش | فقط از کیت: `haptic = true`، `Haptics.success/confirm/error`؛ هرگز روی پیمایش |
| برگشتِ پیش‌نما (اندروید ۱۴+) | خودِ پوسته (`PredictiveBackHandler` در `V3RouteHost`): صفحه با انگشت کنار و کوچک می‌شود؛ قدیم/کامپیوتر برگشتِ ساده |
| ایستگاهِ نیمه/کاملِ برگه | `V3Sheet(detents = true)` (پیش‌فرض)؛ تأیید و منو `false` |
| جابه‌جاییِ تب | پوسته: shared-axis X (۱۰٪ + محو) به ترتیبِ نوار (`V3Nav.tabOrder`)، آینه در RTL |
| «شیشه»ی نوار | `v3GlassColor(solid)` فقط روی نوارها؛ گوشیِ ضعیف/کاهشِ حرکت ⇒ پُر؛ بی blur |
| آیکنِ تبِ فعال | `ModuleEntry.iconSelected` (پُر)؛ نوارها رنگ و جهشِ فنری را خودشان دارند |

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
   - پشتهٔ هر تب نگه داشته می‌شود (برگشت به تب ⇐ همان صفحه‌ای که بودید؛ زدنِ دوباره ⇐ ریشهٔ تب).
   - کامپیوتر/پهن: `nav.open` هم روی پشته می‌نشیند (برگشت جزئیات را جا نمی‌اندازد)؛ پرشِ هم‌سطح فقط
     `nav.openFromRail` (منوی کناری، فهرستِ فرمان، میان‌برها).
   - برگشتِ سربرگ: `backOrNull` (ریشهٔ تب بی برگشت). چیدمانِ کامپیوتری در مسیر: `if (desk)`، نه `Platform.DESKTOP`
     (پیش‌نمایشِ گوشی `LocalDeskLayout = false` می‌دهد تا عکس نسخهٔ گوشی باشد).
   - پایینِ صفحه را پوسته نگه می‌دارد (`ShellFrame`): نوارِ شناور فقط روی ریشهٔ تب‌ها؛ صفحه `bottomReserve`
     نمی‌خواهد و نوارِ سیستم را دوباره نمی‌شمارد (`LocalV3SystemBottom.current ?: navigationBars`).
5. پیوندِ بیرونی: `vill3n://app/<مسیر>` (اعلان‌ها) — `DeepLinks.appLink(path)`. پیوندِ سرد پشته را از زنجیرهٔ `parent` می‌سازد.
6. دسترسی: `needs = setOf(Need.MANAGE)` و/یا پرچمِ ماژول؛ بی‌دسترسی خودکار صفحهٔ «دسترسی ندارید» با برگشت. متنِ ویژهٔ
   همان صفحه: `V3Route(..., deniedBody = { StrX.why })` (پیش‌فرض `StrKit.noAccessBody`).
7. عکس: حالت‌های صفحه را در `desktop/.../shots/<Group>Shots.kt` بگذارید؛ روشن/تیره × fa/en.

### اسکن برای نتیجه
- بارکد (فقط دکان، قالب‌های فروش): `nav.openForResult(SalesPaths.scan("stock")) { code -> }`.
- **QR در هر دو حالت** (دکان، پیک): `nav.openForResult(ScanPaths.QR) { raw -> }` — `ScanPaths.QR = "scan?kind=qr"`،
  فقط `FORMAT_QR_CODE`، متنِ خام با `nav.finish(text)`؛ `null` یعنی بسته شد. کامپیوتر دوربین ندارد ⇐ برگهٔ «این‌جا دوربین نیست».
- پرسشِ ثابت در الگو (`scan?kind=qr`) شرط است و الگو را از `scan` دقیق‌تر می‌کند (`RoutePattern.required`).

### درزهای میانِ گروه‌ها (بی import)
- `HomePager.page` (۰ خانه، ۱ آمار؛ `stats?period=` می‌گذارد، ورقِ خانه می‌رود و null می‌کند) و `HomePager.stats`
  (محتوای آمار که G6 ثبت می‌کند و ورقِ خانهٔ G10 صدا می‌زند) — `ui/v3/nav/HomePager.kt`.

### زبان
`LocaleChoice.lang` (فارسی/English/پښتو، `tohid-locale` › `lang`) و `LocaleChoice.setLang(context, Lang)`؛ ریشهٔ گوشی و
کامپیوتر `ProvideLang { }` است: جهت از `lang.rtl` (فارسی و پشتو راست‌به‌چپ، انگلیسی چپ‌به‌راست) و `LocalLang`. نوشته‌ها
(`Str*`) خودشان از `I18n.source = { LocaleChoice.lang }` می‌خوانند. صفحه هیچ‌وقت `LayoutDirection.Rtl` ِ ثابت نمی‌گذارد.

## حالتِ «خرید» در برنامهٔ مدیریت نیست

تصمیمِ صاحب محصول (۱۴۰۵/۰۸/۰۸): برنامهٔ مدیریت دو حالت دارد (دکان، پیک)؛ خریدِ مشتری برنامهٔ جدای خودش را دارد.
هیچ مسیرِ `shopper/…`، ماژول یا پیوندِ وب (QRِ دکان، `…/store/<slug>`) این‌جا ثبت نیست — آن پیوندها در مرورگر
می‌مانند. منطقِ خالصِ خرید (`core/shopper`: پیوند، سبد، پرداخت، سفارش‌ها، API، با آزمون‌هایش) از برنامهٔ مدیریت
برداشته شد (§۴۰.۱)؛ بایگانی: کامیتِ 5918b94، مسیرِ `android-native/app/src/{main,test}/java/ir/vil3ntec/tohid/core/shopper`.

## ساختار و قاعده‌ها

- **تاریخ و روز در صفحه:** `displayDate(iso)` و `displayMillis(at)` (به زبان و تقویمِ انتخاب‌شده، ۳۴۸)، شمارِ روز
  `daysText(n)`، نامِ ماه `JALALI_MONTHS`. `formatDate` فقط برای داده، CSV و کادرِ ویرایش.
- کیت: `ui/v3/*.kt` (هر جزء یک فایلِ کوچک)؛ نقش‌های رنگ: `ui/theme/PhoneV3Roles.kt`؛ تم‌ها: `ui/theme/ShopThemes.kt`؛ حرکت: `ui/screens/Motion.kt`.
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

⚠️ `androidx.collection`: کامپوزِ ۱.۷ در **اجرا** `androidx.collection` می‌خواهد که فقط روی Google Maven است؛
`https://maven.google.com` هر فایل را به `dl.google.com` می‌فرستد (۳۰۱) و پراکسیِ این ماشین آن را می‌بندد (۴۰۳).
پس این‌جا رسمِ بی‌پنجره **اجرا نمی‌شود** (کامپایلش می‌شود)؛ عکسِ واقعی را CI می‌گیرد. هر جا jar ِ
`collection-jvm` در کش بود (ماشینِ دیگر)، `repositories { mavenCentral(); google() }` و برداشتنِ exclude ِ
`androidx.collection`/`androidx.annotation` کافی است. صفحهٔ تازه: یک خط در `screens` ِ `Render.kt`.

عکس‌های CI: `desktop-shots` هر `Shot` ِ `desktop/.../shots/<Group>Shots.kt` را می‌گیرد و در شاخهٔ
`desktop-screenshots` زیرِ `pr/<شاخه>/v3/<گروه>/<حالت>-<light|dark>-<fa|en>.png` (با README ِ هر گروه) می‌گذارد.
- **شاخهٔ یک گروه (`…-v3-<گروه>`):** `foundation` + همان گروه + هر گروهِ دیگری که درخواست در `ui/v3/<گروه>/` یا
  `shots/<Group>Shots.kt` ِ آن تغییر داده.
- **پایه (v3-foundation)، phone-v3، هر شاخهٔ دیگر و اجرای دستی:** همهٔ گروه‌ها، سه‌تا‌سه‌تا در تکه‌های موازی (`groupshots:a,b,c`).
- پایانِ هر گروه نشانِ `.done` دارد (`DevShot`). اگر مهلتِ ۳۰ دقیقهٔ تکه تمام شود یا گروهی نیمه بماند، کار قرمز می‌شود؛
  هرچه گرفته شده باز هم منتشر می‌شود.
- محلی: `groupshots:<گروه>` یا چند گروه با ویرگول.
