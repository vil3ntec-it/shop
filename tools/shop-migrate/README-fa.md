# shop-migrate — ابزارِ مهاجرتِ داده (فاز ۱، قدمِ ۱)

پروژهٔ JVMِ مستقل. **به هیچ برنامه‌ای وصل نیست**: نه APK، نه نصب‌کنندهٔ کامپیوتر، نه سرور.
دو چیز دارد:

1. **طرحِ SQLite (SQLDelight)** برای موجودیت‌های اصلیِ بخشِ ۳.۴ — `src/main/sqldelight/…/db/*.sq`
2. **اجرای آزمایشی (`dry-run`)**: پشتیبانِ امروزِ دکان را فقط می‌خواند، در یک SQLiteِ
   **درون‌حافظه** به مدلِ تازه می‌برد و گزارشِ تطبیقِ بخشِ ۸ را چاپ می‌کند. **هیچ فایلی نمی‌نویسد.**

## اجرا روی پشتیبانِ واقعی (کارِ صاحبِ محصول — بند ۹ پیامِ شروع، بخشِ ۸ قدمِ ۲)

فقط با رضایتِ صاحبِ همان دکان، فقط روی **کپیِ** فایلِ پشتیبان، فقط روی کامپیوترِ صاحبِ محصول.

پیش‌نیاز: Java 17 یا تازه‌تر (`java -version`). Gradle لازم نیست — همان wrapperِ `desktop` به کار می‌رود.

```bat
:: ویندوز، از ریشهٔ مخزن — یک بار
desktop\gradlew.bat -p tools\shop-migrate installDist

:: هر پشتیبان (ZIPِ «پشتیبانِ کامل» یا JSONِ ساده؛ نسخهٔ وب هم همین قالب را دارد)
tools\shop-migrate\build\install\shop-migrate\bin\shop-migrate.bat dry-run "D:\copies\dokan-1.zip" > dokan-1-report.txt
```

```sh
# لینوکس/مک
./desktop/gradlew -p tools/shop-migrate installDist
tools/shop-migrate/build/install/shop-migrate/bin/shop-migrate dry-run /path/to/copy.zip > report.txt
```

- خروجی: `0` = PASS، `2` = اختلافِ بی‌دلیل هست، `1` = خطا (فایل خوانده نشد).
- ورودی فقط خوانده می‌شود (آزمونِ `dryRunWritesNothingAndReadsZipBackups` همین را می‌سنجد). تنها
  نوشتنِ جانبی کارِ خودِ کتابخانهٔ sqlite-jdbc است: کتابخانهٔ بومی‌اش را یک بار در پوشهٔ موقتِ سیستم باز می‌کند.
- **چه چیزی را بفرستید:** همان `report.txt`. پیش‌فرضِ گزارش **بی دادهٔ شخصی** است: نه نام، نه تلفن، نه
  یادداشت، نه نامِ دکان، نه شناسهٔ خام؛ هر کالا/مشتری/تأمین‌کننده یک برچسبِ هش‌شده دارد (`p:…`، `c:…`، `s:…`).
- `--names` نام‌ها را نشان می‌دهد تا خودتان ردیفِ ناهم‌خوان را در دفتر پیدا کنید. **آن خروجی را نفرستید.**
- `--all` همهٔ کالاها/مشتری‌ها را چاپ می‌کند، نه فقط ناهم‌خوان‌ها.

## گزارش چه می‌گوید

شش گروهِ بخشِ ۸ (قدمِ ۳)، هرکدام «محاسبهٔ قدیم» در برابرِ «محاسبهٔ مدلِ تازه»:

| گروه | قدیم (همان فرمولِ برنامه) | تازه (SQL روی طرحِ تازه) |
|---|---|---|
| `rows/*` تعدادِ ردیف | اندازهٔ هر فهرستِ دفتر | `count(*)` هر جدول |
| `sales/*` جمعِ فروش و سود | `ReportEngine.sales` (همه) | `sale`/`saleLine`/`saleReturn`/`expense` |
| `pay/*` جمعِ پرداخت‌ها | نقد/نسیهٔ فاکتور، `give`/`receive`، پرداخت به تأمین‌کننده | `salePayment`، `customerLedgerEntry`، `supplierPayment` |
| `customer/*`، `supplier/*` مانده | `ShopStore.debt` / `supplierDebt` | جمعِ دفتر (هیچ مانده‌ای ذخیره نمی‌شود) |
| `stock/*` موجودی | `ShopStore.StockIndex` | جمعِ `stockMove` (فقط افزودنی) |
| `month/*` فروش و سودِ هر ماهِ خورشیدی | `ReportEngine.sales` روی `jalaliMonth` | همان بازه روی طرحِ تازه |

هر ردیف یکی از چهار حکم را می‌گیرد:

- `EQUAL` — برابر به دقتِ مدلِ تازه (پول یک‌صدم، مقدار هزارم).
- `DOUBLE` — «اعشار ناشی از Double»: همان محاسبهٔ قدیم روی ورودی‌های گردشده دقیقاً عددِ تازه را می‌دهد.
- `EXPLAINED` — اختلاف **دقیقاً** برابرِ اثرِ ناهنجاری‌های فهرست‌شده است (مثلاً قلمِ فروشِ بی فاکتور). دلیل کنارش می‌آید.
- `UNEXPLAINED` — اختلافِ بی‌دلیل ⇒ `RESULT: FAIL`. طبقِ بخشِ ۸ مهاجرت جلو نمی‌رود تا دلیلش پیدا و تصمیمش گرفته شود.

بخشِ `anomalies` هر ناهنجاریِ داده را با شمار و معنی‌اش می‌آورد؛ هیچ ردیفی بی‌صدا نمی‌افتد. زنجیره‌های هش
(فروش و سابقهٔ عملیات) از روی خودِ پایگاه از نو حساب می‌شوند (`chainCheck`).

## طرحِ SQLite

| فایل | جدول‌ها | قاعدهٔ ثابتِ بسته در خودِ پایگاه (ماشه/CHECK) |
|---|---|---|
| `Shop.sq` | `currency`، `shop`، `device`، `warehouse`، `legacyId` | سریِ سند در فروشگاه یکتا؛ دستگاهِ غیرفعال سندِ تازه نمی‌پذیرد |
| `Catalog.sq` | `measureUnit`، `product`، `unitConversion`، `barcode`، `priceList`، `price` | تبدیلِ واحد کسرِ گویاست؛ بارکد در فروشگاه یکتا مگر با پرچمِ «تکراری»؛ بازهٔ قیمت هم‌پوشانی ندارد و فقط بسته می‌شود |
| `Stock.sq` | `stockMove` | فقط افزودنی؛ اصلاح بی دلیل نیست |
| `Sales.sq` | `sale`، `saleLine`، `salePayment`، `saleReturn` | فروش پیش‌نویس به دنیا می‌آید؛ «پایان» حلقهٔ زنجیرهٔ هش است؛ پس از آن فقط لغو با دلیل؛ ردیف و پرداخت منجمد؛ مرجوعی فقط روی فاکتورِ پایان‌یافته و ردیفِ خودش |
| `Customers.sq` | `customer`، `customerLedgerEntry` | مانده ذخیره نمی‌شود؛ دفتر فقط افزودنی |
| `Suppliers.sq` | `supplier`، `supplierInvoice`، `supplierPayment`، `expense` | — |
| `Cash.sq` | `cashShift` | صندوقِ بسته منجمد است؛ اختلاف ثبت می‌شود، اصلاح نه |
| `Audit.sq` | `auditLog` | زنجیرهٔ هش (`seq` بی‌شکاف، `prevHash` = هشِ قبلی)؛ فقط افزودنی |

- پول `…Minor` (عددِ صحیح، AFN یک‌صدم)، مقدار `…Milli` (هزارم)، زمان `…At` (میلی‌ثانیهٔ UTC)، `businessDate` روزِ کاری.
- هش: `SHA-256(prevHash ‖ canonicalJson(doc))` با JCS (RFC 8785)؛ سندِ هش‌شده در `Docs.kt` و دادهٔ شخصی ندارد.
- فقط ویژگی‌های SQLite 3.9 (minSdk 24). گویشِ SQLDelight همان پیش‌فرضِ 3.18 است که طرح را می‌سنجد.
- **تنظیمِ اتصال** (`DbSettings.kt`): `journal_mode=WAL`، `synchronous=FULL`، `foreign_keys=ON`، `busy_timeout=5000`.
  FULL یعنی پس از هر COMMIT، WAL روی دیسک fsync می‌شود — قطعِ برق فروشِ تأییدشده را نمی‌برد. synchronous و
  foreign_keys مالِ هر اتصال‌اند؛ روی اندروید در `onConfigure`ِ `AndroidSqliteDriver` اعمال شوند (شرح در همان فایل).
- هنوز نیست (گام‌های بعدیِ فاز ۱): لات/انقضا، انتقال بین انبار، سفارشِ خرید و رسیدِ جزئی، شمارشِ اسکناس، کاربر و مجوز.

## نگاشتِ دادهٔ قدیم (`Mapper.kt`)

- عددها جابه‌جا می‌شوند، از نو حساب نمی‌شوند؛ هر Double با گردکردنِ «نیمه به سمتِ +∞» روی نمایشِ دهدهی‌اش.
- موجودی از منبعِ واقعیِ امروز ساخته می‌شود: ورودِ انبار − فروشِ لغونشده + ردیف‌های مرجوعی (نه از `stockMovements`).
- کالا/مشتری/تأمین‌کنندهٔ حذف‌شده‌ای که سابقه به آن ارجاع دارد ردیفِ «جانشین»ِ غیرفعال می‌گیرد.
- شناسهٔ ULIDِ قدیم می‌ماند؛ بقیه ULIDِ قطعی (زمان = `createdAt`، باقی از SHA-256) و نگاشت در `legacyId`.
- دادهٔ قدیم یک دستگاه است: سریِ `L`؛ شمارهٔ فاکتورِ قدیم همان‌طور در `displayNo` می‌ماند.

## نگهبان‌ها و آزمون‌ها

```sh
./desktop/gradlew -p tools/shop-migrate test
```

- `SchemaTest` هر قاعدهٔ ثابتِ جدولِ بالا را در خودِ SQLite می‌شکند و رد شدنش را می‌بیند؛ `PragmaTest` WAL/FULL را روی فایل.
- `DryRunTest`: دفترِ ساختگیِ سالم ⇐ همه `EQUAL`؛ دفترِ خراب ⇐ هر خرابی با دلیلِ درست؛ عددِ غلطِ مدلِ تازه ⇐ `UNEXPLAINED`.
- «محاسبهٔ قدیم» کپیِ چند تابعِ برنامه است (موتورها به Context گره خورده‌اند). نگهبانش دو نیمه دارد:
  `LegacyGoldenTest` (کپی ⇐ `golden/legacy-golden.txt`) و `android-native/…/data/MigrationGoldenTest.kt`
  (موتورِ واقعی ⇐ همان فایل، در CIِ اندروید و کامپیوتر). اگر `ShopStore`/`ReportEngine` عوض شود:

  ```sh
  rm tools/shop-migrate/src/test/resources/golden/fixture-seed1.json   # فقط اگر سازنده عوض شده
  bin=tools/shop-migrate/build/install/shop-migrate/bin/shop-migrate
  $bin fixture --seed 1 --anomalies --out tools/shop-migrate/src/test/resources/golden/fixture-seed1.json
  $bin golden tools/shop-migrate/src/test/resources/golden/fixture-seed1.json > tools/shop-migrate/src/test/resources/golden/legacy-golden.txt
  ```

- سازندهٔ دفترِ ساختگی: `shop-migrate fixture --seed 7 --size small|medium|large [--anomalies] --out x.json`.
  `large` ≈ دو سال، ۷ هزار فاکتور، ۱۰ مگابایت؛ اجرای آزمایشی‌اش حدودِ ۱۲ ثانیه.

## قدم‌های بعد (بخشِ ۸)

۲. اجرا روی کپیِ پشتیبانِ چند دکان (همین سند) ⇐ ۳. گزارشِ تطبیقِ بی اختلافِ بی‌دلیل ⇐ ۴. مهاجرتِ خودکار روی
دستگاه با پشتیبانِ پیش از مهاجرت و راهِ برگشت ⇐ ۵. سرور مدتی op‌های v2 و v3 را با هم می‌پذیرد.
ترتیب طبقِ پیشنهادِ D-17: پس از گزارشِ سبز روی کپی‌ها، اول یک دکانِ آزمایشی.
