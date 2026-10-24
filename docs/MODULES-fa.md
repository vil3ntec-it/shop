# ماژول‌ها — بخشِ تازه یا سیستمِ تازه چطور اضافه می‌شود

دستورِ صاحب محصول: «برنامهٔ فروشگاه قابلِ توسعه باشه که اگه خواستم سیستم‌های دیگه‌ای درست کنم، بتونم یک
بخشِ جدید براش بدم یا توی همون فروشِ آنلاین اضافه کنم».

هر بخشِ برنامه (فروش، کالا، انبار، پول، گزارش، حساب، فروشِ آنلاین، تنظیمات، پیک، خرید، …) یک **ماژول** است:
یک `AppModule` در پوشهٔ خودش، و **یک خط** در فهرستِ `V3Modules.ALL`
(`ui/v3/nav/V3Modules.kt`). پوسته، مسیریاب، «بیشتر»، تنظیمات، فهرستِ نوارِ پایین و صفحهٔ دکان در حالتِ
خرید همه از همین فهرست می‌خوانند؛ پس بخشِ تازه بی دست زدن به `AppRoot` یا «بیشتر» یا تنظیمات همه‌جا می‌آید.

## قرارداد (`ui/v3/nav/AppModule.kt`)

```kotlin
AppModule(
  id = "notes",                              // یکتا
  title = { StrNotes.title },                // نوشته از جدولِ i18n
  icon = Icons.Outlined.StickyNote2,
  modes = setOf(AppMode.SHOP),               // دکان / پیک / خرید (یک یا چند)
  needs = setOf(Need.MANAGE),                // نقش یا پرچمِ سرور (MANAGE، AUDIT، ORDERS_LIVE، STOREFRONT، CHAT، DELIVERY)
  flag = "module.notes",                     // پرچمِ ماژول؛ پیش‌فرض خاموش (`ModuleFlags`)
  routes = listOf(V3Route("notes", RouteKind.PAGE, { StrNotes.title }, chrome = Chrome.OWN) { NotesScreen() }),
  entries = listOf(ModuleEntry("notes", { StrNotes.title }, "notes", Icons.Outlined.StickyNote2,
    setOf(Place.MORE), MoreGroup.SHOP, V3Hue.PURPLE)),          // ردیفِ «بیشتر» / کاشیِ خانه / موردِ نوار
  settings = listOf(SettingsEntry({ StrNotes.settings }, "settings/notes", Icons.Outlined.Tune, SettingsLayout.GENERAL)),
  shopper = listOf(ShopperSection("notes", { StrNotes.forCustomers }, "shopper/shop/{slug}/notes", Icons.Outlined.StickyNote2)),
)
```

- `routes`: مسیرهای نوع‌دار با پیوندِ `vill3n://app/<مسیر>`؛ گونه (TAB/PAGE/SHEET/GATE)، `parent` برای پشتهٔ درستِ
  برگشت، `aliases` برای نامِ قدیمی، `needs`/`flag` برای هر مسیر.
- `entries`: کجا دیده شود — `Place.MORE` (زیرِ `MoreGroup`)، `Place.HOME` (کاشیِ خانه)، `Place.NAV` (فهرستِ
  ویرایشگرِ نوارِ پایین؛ شناسه ماندگار است، عوضش نکنید).
- `settings`: صفحه‌هایش در «تنظیمات».
- `shopper`: بخشی که روی صفحهٔ هر دکان در **فروشگاهِ آنلاین** (حالتِ خرید) می‌گذارد.

## بخشِ تازه در برنامهٔ دکان — سه قدم

1. پوشهٔ `ui/v3/<بخش>/` با `<Group>Routes.kt` (مثلِ بالا) و صفحه‌ها (`XScreen.kt` + `XModel.kt` با آزمون) روی
   کیتِ نسخهٔ ۳ (`docs/V3-KIT-fa.md`). نوشته‌ها در `core/i18n/Str<بخش>.kt` و یک خط در `I18nTables`.
2. یک خط در `V3Modules.ALL`.
3. اگر منطقِ تازه دارد (پول، موجودی، همگام‌سازی) جایش `core/` است با آزمونِ خودش، نه صفحه. پرچمِ ماژول خاموش
   می‌ماند تا آماده شود؛ برای آزمایش روی یک دستگاه: `ModuleFlags.set(context, "module.<بخش>", true)`.

## بخشِ تازه درونِ فروشگاهِ آنلاین

همان ماژول، با `modes = setOf(AppMode.SHOP, AppMode.SHOPPER)`: مدیریتش در دکان (`routes` با `AppMode.SHOP`) و
نمایش برای مشتری با `shopper = listOf(ShopperSection(..., route = "shopper/shop/{slug}/<بخش>"))` و یک `V3Route`
برای همان مسیر. صفحهٔ دکان در حالتِ خرید فهرستِ `V3Modules.shopperSections(access)` را خودش نشان می‌دهد.
اگر داده باید از سرور بیاید، API ِ تازه زیرِ `/api/store/:slug/<بخش>` و منطقش در `server/src/lib` (مثلِ چت و
اعتماد)، پشتِ پرچمِ سرورِ خودش.

## نمونهٔ زنده

`ui/v3/example/ExampleRoutes.kt` (`ExampleModule`، پرچمِ خاموشِ `module.example`): یک ردیف در «بیشتر»، یک
صفحه در تنظیمات (`settings/example`)، یک بخش روی صفحهٔ دکان در حالتِ خرید (`shopper/shop/{slug}/example`)، و
مسیرِ `example`. آزمونِ `ModuleRegistryTest` نشان می‌دهد با پرچمِ خاموش هیچ‌کدام دیده نمی‌شود و با روشن شدن
همه‌جا می‌آید؛ همان آزمون برای **هر** ماژول می‌سنجد که مسیرها پیدا می‌شوند، پدرها درست‌اند و ورودی‌ها نقش و
پرچم را رعایت می‌کنند.

## گروه‌های بازسازی (فاز ۱)

| گروه | فایلِ ثبت | ماژول |
|---|---|---|
| G1 ورود | `ui/v3/onboarding/OnboardingRoutes.kt` | `OnboardingModule` |
| G2 کالا | `ui/v3/products/ProductsRoutes.kt` | `ProductsModule` |
| G3 انبار | `ui/v3/stock/StockRoutes.kt` | `StockModule` |
| G4 پول | `ui/v3/money/MoneyRoutes.kt` | `MoneyModule` |
| G5 فروش | `ui/v3/sales/SalesRoutes.kt` | `SalesModule` |
| G6 گزارش | `ui/v3/analytics/AnalyticsRoutes.kt` | `AnalyticsModule` |
| G7 حساب | `ui/v3/account/AccountRoutes.kt` | `AccountModule` |
| G8 آنلاین و پیک | `ui/v3/online/OnlineRoutes.kt` | `OnlineModule`، `CourierModule` |
| G9 تنظیمات | `ui/v3/settings/SettingsRoutes.kt` | `SettingsModule` |
| G10 پوسته | `ui/v3/shell/ShellRoutes.kt` | `ShellModule` |
| خرید | `ui/v3/shopper/ShopperRoutes.kt` | `ShopperModule` |
| پایه | `ui/v3/nav/FoundationRoutes.kt`، `ui/v3/nav/ScanRoutes.kt` | `FoundationModule`، `ScanModule` (`scan?kind=qr`، هر سه حالت) |

هر مسیرِ NAV-MAP از پیش ثبت است و امروز به صفحهٔ پیشین اشاره می‌کند (یا با `LandOn` روی نزدیک‌ترین صفحه
می‌نشیند)؛ گروه فقط محتوای مسیرهای خودش را با صفحهٔ تازه عوض می‌کند.
