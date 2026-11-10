package ir.vil3ntec.tohid.data

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 *  مدلِ داده — دقیقاً همان چیزی که نسخهٔ وب می‌نویسد.
 *
 *  نام‌ها و شکلِ فیلدها عمداً عوض نشده‌اند. دلیلش این است که فایلِ داده و
 *  فایلِ پشتیبان بین دو نسخه باید یکی بماند: کسی که تا دیروز از نسخهٔ وب
 *  استفاده می‌کرده، همان داده را اینجا می‌بیند، و بکاپی که اینجا می‌گیرد در
 *  آنجا هم باز می‌شود.
 *
 *  هر فیلدِ تازه‌ای که نسخهٔ وب بعداً اضافه کرده مقدارِ پیش‌فرض دارد، چون
 *  داده‌های قدیمی آن فیلد را ندارند و بدونِ پیش‌فرض خواندنشان می‌شکند.
 */

@Serializable
data class Product(
  val id: String,
  val name: String = "",
  val category: String = "",
  val unit: String = "",
  val purchasePrice: Double = 0.0,
  val salePrice: Double = 0.0,
  val wholesalePrice: Double = 0.0,
  val minStock: Double = 0.0,
  val notes: String = "",
  val barcodes: List<String> = emptyList(),
  /**
   * فقط یک نشانه است، نه خودِ عکس: عکس‌ها جای دیگری نگهداری می‌شدند.
   * نگه داشته می‌شود تا با نوشتنِ دوبارهٔ دفتر پاک نشود.
   */
  val photo: Boolean = false,
  val createdAt: Long = 0,
  /** زیردسته (اختیاری) — زیرِ `category` */
  val subcategory: String = "",
  /** تأمین‌کنندهٔ اصلیِ کالا (اختیاری) — شناسهٔ `Supplier` */
  val supplierId: String = "",
  /** کالای غیرفعال در فروش و جست‌وجوی فروش نمی‌آید؛ سابقه‌اش می‌ماند */
  val active: Boolean = true,
  /**
   *  واحدِ فروشِ دوم (اختیاری) و ضریبِ آن: یک «واحدِ فروش» چند «واحدِ
   *  موجودی» است. مثلاً موجودی کیلوگرم، فروش گرم ⇒ `unitFactor = 0.001`.
   *  موجودی و قیمت همیشه بر حسبِ `unit` می‌مانند؛ فقط ورودیِ مقدار تبدیل
   *  می‌شود (`SalesEngine.toStockQty`). خالی ⇒ فقط همان `unit`.
   */
  val saleUnit: String = "",
  val unitFactor: Double = 1.0,
  /**
   *  PRD-02 — نامِ پشتو و انگلیسی (نامِ دری همان [name])، مترادف‌ها (نام‌های دیگرِ همین کالا) و
   *  واژه‌های جست‌وجو؛ PRD-26 — محلِ قفسه (راهرو، قفسه، طبقه) برای جمع‌آوریِ سفارش.
   *  شکلِ تمیز و جورِ جست‌وجو: `core/product/ProductInfo` (و `ProductInfoMap`). خالی ⇒ `null` و
   *  **نوشته نمی‌شود**، پس ردیفِ کالایی که این‌ها را ندارد بایت‌به‌بایت همان پیشین است. فرم و
   *  جست‌وجو فقط با تنظیمِ همین دستگاه (`ProductInfoPrefs`، پیش‌فرض خاموش)؛ ویرایشِ بی آن فرم
   *  این فیلدها را دست نمی‌زند (`ProductDraft.info = null`).
   */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val namePs: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val nameEn: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val synonyms: List<String>? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val keywords: List<String>? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val aisle: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val shelf: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val shelfLevel: String? = null,
  /**
   *  PRD-24 — شناسهٔ کالاهای جایگزین به ترتیبِ ترجیح، برای پیشنهادِ صندوق هنگامِ ناموجودی؛ PRD-25 —
   *  فیلدهای سفارشیِ «نام: مقدار» به ترتیبِ ورود (`core/product/ProductExtras`). همان قاعدهٔ بالا: خالی ⇒
   *  `null` و نوشته نمی‌شود؛ فرم و پیشنهاد فقط با `ProductInfoPrefs`.
   */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val alternatives: List<String>? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val customFields: Map<String, String>? = null,
  /** PRD-03 کدِ کالا (`ProductSku`)؛ PRD-09 `true` = خدماتی/بی موجودی (`ProductInfoMap.sellable`) — همان قاعده و همان تنظیم */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val sku: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val stockless: Boolean? = null,
  /*
   *  PRD-06 — برند و برچسب‌ها (فقط با `ShopPolicy.productTools` نوشته می‌شوند). همان کلیدِ `brand` که
   *  کاتالوگِ سرور (`lib/catalog.js`) و نمای عمومیِ بازار می‌خوانند. `null` نوشته نمی‌شود ⇒ کالای
   *  بی برند همان JSON ِ پیشین است و op ِ تازه‌ای نمی‌سازد.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val brand: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val tags: List<String>? = null,
  /*  INV-12 — نقطهٔ سفارش و بیشینهٔ موجودی (به همان `unit`). افزودنی: `null` = تعریف نشده و نوشته نمی‌شود (`NEVER`) تا ردیفِ قدیمی بایت‌به‌بایت
   *  همان بماند. فقط با «نقطهٔ سفارش و بیشینه» در تنظیمات (`ShopPolicy.stockLevels`)؛ قاعده‌ها در `core` (`StockLevelRules`). «حد کم بودن» همان `minStock`. */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val reorderPoint: Double? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val maxStock: Double? = null,
  /*  PRD-04 — بارکدِ هر واحد: بسته‌ها (کارتنِ ۲۴ دانه‌ای با بارکدِ خودش؛ `PackBarcodes` ِ core). فقط با `ShopPolicy.productTools` نوشته می‌شود؛ اسکنِ بارکدِ بسته
   *  «اندازهٔ بسته» از `unit` به سبد می‌برد. `null` نوشته نمی‌شود ⇒ کالای بی بسته همان JSON ِ پیشین است و op ِ تازه‌ای نمی‌سازد. */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val packs: List<ProductPack>? = null,
  /** PRD-10 اجزای کیت؛ PRD-13 بازه‌های قیمتِ زمان‌بندی‌شده (`ProductPlansMap`) — خالی ⇒ `null` و نوشته نمی‌شود؛ فرم فقط با `ProductInfoPrefs` */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val kit: List<KitPart>? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val priceWindows: List<PriceWindow>? = null,
  /** PRD-11 ویژگی‌های مدل؛ تنوع: شناسهٔ مدل و ترکیبش (`ProductVariantsMap`) — همان قاعده: خالی ⇒ `null` و نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val variantAxes: List<VariantAxis>? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val variantOf: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val variant: Map<String, String>? = null,
  /** POS-28 — `true` = کالای سریال‌دار: هر دانه سریال می‌خواهد و فاکتور بی سریالِ کامل بسته نمی‌شود (`SerialSale`) */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val serialTracked: Boolean? = null,
  /** MKT-06 «چرخهٔ مصرف» (روزِ ۱ تا ۳۶۵، `ProductCycle`) برای یادآوریِ «دوباره خریدن» ِ برنامهٔ خرید؛ `null` نوشته نمی‌شود ⇒ ردیفِ پیشین همان */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val consumptionDays: Int? = null,
  /*  PRD-07 — واحدهای خریدِ چندگانه (کارتنِ ۱۲ از یک تأمین‌کننده، کارتنِ ۲۴ از دیگری) با ضریب به همین `unit`؛ قاعده‌ها و حساب در
   *  `core/product/PurchaseUnits`. فرم فقط با `ShopPolicy.productTools`؛ سرور شکلش را می‌سنجد (`lib/purchase-units.js`). همان راهِ
   *  همگام‌سازی و ادغامِ `packs` (یک فیلدِ کالا). `null` نوشته نمی‌شود ⇒ کالای بی واحدِ خرید همان JSON ِ پیشین است و op ِ تازه‌ای نمی‌سازد. */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val purchaseUnits: List<PurchaseUnit>? = null,
)

/** یک واحدِ خرید (PRD-07): نام، ضریب به هزارمِ `Product.unit` (کارتنِ ۲۴ ⇒ 24000)، تأمین‌کنندهٔ پیش‌فرض و بارکدِ اختیاری */
@Serializable
data class PurchaseUnit(
  val name: String = "",
  val factorMilli: Long = 0,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val supplierId: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val barcode: String? = null,
)

/** یک بسته از کالا: نام، اندازه به هزارمِ `Product.unit` (کارتنِ ۲۴ دانه‌ای ⇒ 24000) و بارکدهایش — PRD-04 */
@Serializable
data class ProductPack(
  val name: String = "",
  val sizeMilli: Long = 0,
  val barcodes: List<String> = emptyList(),
  /** قیمتِ یک بسته در لیستِ قیمت (S05، Minor)؛ `null` تعریف نشده و نوشته نمی‌شود ⇒ JSON ِ بستهٔ پیشین همان */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val priceMinor: Long? = null,
)

@Serializable
data class WarehouseEntry(
  val id: String,
  val productId: String = "",
  val cartons: Double = 0.0,
  val perCarton: Double = 0.0,
  val units: Double = 0.0,
  val unit: String = "",
  val price: Double = 0.0,
  val date: String = "",
  val notes: String = "",
  /** ردیفِ «اصلاح موجودی»، نه ورودِ واقعیِ کالا */
  val isAdjustment: Boolean = false,
  /** اگر این ورودی از ثبتِ یک خرید آمده باشد، شناسهٔ همان خرید */
  val purchaseId: String? = null,
  /**
   *  ردیفِ ساختهٔ سیستم از یک سند (مثلاً اصلاحِ انبارِ برگشتِ ضایعات یا بی رسید): شناسهٔ
   *  همان سند (`docId` ِ برگشت). سرور با آن می‌داند اگر سند رد شد این ردیف هم با آن برگردد
   *  (`staff-approval.js`، `companionOf`). ورودِ دستیِ کالا `null` است و نوشته نمی‌شود —
   *  ردیفِ قدیمی بایت‌به‌بایت همان می‌ماند.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val refId: String? = null,
  val createdAt: Long = 0,
  /**
   *  تاریخِ انقضای همین محموله (`yyyy-MM-dd`)؛ خالی یعنی «انقضا ندارد یا
   *  ثبت نشده» — هرگز تاریخِ ساختگی جایش نمی‌نشیند. شرح سرِ `ExpiryEngine`.
   */
  val expiryDate: String = "",
  /** شمارهٔ سری ساخت (اختیاری) */
  val batch: String = "",
  /** تاریخِ تولید (اختیاری، `yyyy-MM-dd`) — خالی یعنی ثبت نشده */
  val productionDate: String = "",
  /*
   *  ── موتورِ تازهٔ موجودی و بها (`StockLedger`، پشتِ تنظیمِ دکان) ──────
   *  همه افزودنی، `null` = رفتارِ امروز. `null` نوشته نمی‌شود (`NEVER`)، وگرنه
   *  نخستین تفاضلِ Sync v1 برای هر ردیفِ قدیمی یک op می‌ساخت.
   */
  /** هزینهٔ جانبیِ همین ردیف (کرایه، گمرک) به افغانی، کلِ ردیف — PUR-06 */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val landedCost: Double? = null,
  /**
   *  همان هزینهٔ جانبی، **دقیق** به پولِ خرد (صدمِ افغانی) — منبعِ حساب در `StockLedger`.
   *  `landedCost` فقط برای خواندنِ آدم و ابزارِ قدیمی است؛ هر دو با هم نوشته می‌شوند.
   *  نامِ `…Minor` در Sync v3 بی تبدیل و به‌صورتِ عددِ صحیح می‌رود و برمی‌گردد
   *  (`landedCost` در `V3Schema.money` نیست — ⛔ اضافه نشود، وگرنه این دو نام با هم می‌خورند).
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val landedCostMinor: Long? = null,
  /** محلِ نگه‌داری (INV-20)؛ `null`/خالی = محلِ اصلیِ دکان */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val locationId: String? = null,
  /** کاربری که ثبت کرد (INV-02)؛ `null` = ثبتِ قدیمی */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val userId: String? = null,
  /**
   *  نوعِ اصلاح (INV-04): count · damage · expired · lost · other · supplier_return ·
   *  customer_return (برگشتِ بی‌رسیدِ کالای سالم، #318). اصلاحِ برگشت (damage یا
   *  customer_return با `refId` = برگشت) تنها ردیفِ رسیدِ انبار است که صندوق‌دار می‌نویسد (A16).
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val adjKind: String? = null,
  /** سری‌ای که اصلاحِ کاهشی از آن برداشته شود (اختیاری) */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val lotId: String? = null,
  /** INV-10 (`CountAbsorbWiring`): فقط ردیفِ «انبارگردانی» — لحظهٔ کالا و دانستهٔ شمارنده؛ `correctedAt` فقط از سرورِ v3 */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val countedAt: Long? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val seenMilli: Long? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val correctedAt: Long? = null,
  /**
   *  PRD-07 — ارزشِ **دقیقِ** کالای همین ردیف به صدمِ افغانی (بی هزینهٔ جانبی): خریدی که به واحدِ خرید ثبت شد
   *  (۱ کارتنِ ۲۴ به ۱٬۰۰۰ ⇒ 100000)، تا میانگینِ موزون (`StockLedger`، `ReportCore`) ارزش را از `units × price` ِ گردشده
   *  (۲۴ × ۴۱٫۶۷ = ۱٬۰۰۰٫۰۸) نسازد. `null` = همان `units × price` ِ پیشین؛ نوشته نمی‌شود ⇒ ردیفِ قدیمی همان.
   *  INV-21 — همین فیلد بهای دقیقِ کلِ ردیفِ خروجیِ تولید (`Repack` ِ `lots`، جمعِ بهای اجزا) هم هست؛ همان قاعده.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val costMinor: Long? = null,
)

@Serializable
data class Sale(
  val id: String,
  val invoiceNumber: Int? = null,
  val total: Double = 0.0,
  val discountType: String = "amount",
  val discountValue: Double = 0.0,
  val discount: Double = 0.0,
  val finalTotal: Double = 0.0,
  val paymentMethod: String = "cash",
  val debtorId: String? = null,
  val paidAmount: Double = 0.0,
  val remaining: Double = 0.0,
  val status: String = "completed",
  val debtGiven: Double = 0.0,
  val debtSettled: Double = 0.0,
  val createdAt: Long = 0,
  val date: String = "",
  val syncStatus: String = "pending",
  /** دلیلِ لغو — همان `cancelReason`ِ وب */
  val cancelReason: String = "",
  /** فروشنده — نامِ حسابی که فاکتور را زد (برای گزارشِ فروشِ هر کاربر)؛ خالی یعنی نامعلوم */
  val seller: String = "",
  /**
   *  سفارشِ آنلاینی که این فروش تحویلِ آن است (`OrderFulfillment`) — شناسه و شمارهٔ سفارش.
   *  فروشِ صندوق `null` است و `null` نوشته نمی‌شود (`NEVER`)، مثلِ `series`.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val orderId: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val orderNo: Long? = null,
  /**
   *  «سری سند»ِ دستگاهی که فاکتور را زد (F0-03)، مثلاً `B1`؛ شمارهٔ نمایشی
   *  `{سری}-{شماره}` می‌شود (`invoiceLabel`). فاکتورِ پیش از سری و فاکتورِ
   *  وب (که بازهٔ خودش را دارد) `null`اند و همان عدد را نشان می‌دهند.
   *
   *  ⛔ `null` نوشته نمی‌شود (`EncodeDefault.NEVER`): وگرنه نخستین تفاضلِ
   *  Sync v1 پس از به‌روزرسانی برای **هر** فاکتورِ قدیمی یک opِ
   *  `{series: null}` می‌ساخت.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val series: String? = null,
  /**
   *  «برگهٔ امضاشده»ی تأییدِ تخفیفِ بیش از سقف (STF-02، بخشِ ۱۰.۱؛
   *  `core/perm/ApprovalSlip`). سرور با آن تخفیف را می‌پذیرد و سابقه نامِ
   *  تأییدکننده و دلیل را دارد. فقط وقتی هست که تأیید لازم بوده؛ `null`
   *  نوشته نمی‌شود (همان دلیلِ `series`).
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val approval: kotlinx.serialization.json.JsonObject? = null,
  /** STF-02 — برگهٔ تأییدِ **لغوِ** همین فاکتور (فقط Sync v3؛ شرح سرِ `core/perm/CancelApproval`)؛ `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val cancelApproval: kotlinx.serialization.json.JsonObject? = null,
  /**
   *  STF-03 — برگهٔ تأییدِ **قرضِ** همین فاکتور بیش از سقفِ کارمند (`sales.credit`، `SaleCaps`)؛ سرور آن را در
   *  `fields.creditApproval` می‌خواند (`credit-cap.slipOf`، پرچمِ `shop.credit_cap`). ارجاعِ «پرسیدن از مدیر» یا برگهٔ PIN؛
   *  فقط وقتی هست که صندوق پیش‌سنجی کرد و تأیید لازم بود؛ `null` نوشته نمی‌شود.
   */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val creditApproval: kotlinx.serialization.json.JsonObject? = null,
  /** STF-03 — برگهٔ تأییدِ **تغییرِ قیمتِ ردیف** بیش از سقف (`sales.price`، `price-cap.slipOf`، پرچمِ `shop.price_cap`)؛ `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val priceApproval: kotlinx.serialization.json.JsonObject? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val freeLineApproval: kotlinx.serialization.json.JsonObject? = null, // POS-10 (۱-۱۱)
  /**
   *  PAY (v3 A4) — ردیف‌های پرداختِ ترکیبی (نقد، کیف پول، کارت، حواله، به حساب)، به پول.
   *  فقط فروشی که با پرچمِ `ShopPolicy.payV2` ثبت شده دارد؛ معنای فیلدهای بالا
   *  (`paidAmount`، `remaining`، `debtGiven`) همان امروز است، پس برنامهٔ کهنه همان عدد
   *  را می‌خواند؛ `paymentMethod` برای فروشِ غیرنقد `wallet`/`card`/`transfer`/`mixed` است
   *  (`PayFlow.legacyMethod`). `null` نوشته نمی‌شود — مثلِ `series`.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val payments: List<SalePayment>? = null,
  /** دریافتیِ کل (نقد + غیرنقد)، به پول — فقط با `payments` */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val tenderedMinor: Long? = null,
  /** پولِ پس (فقط از نقد)، به پول — فقط با `payments` */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val changeMinor: Long? = null,
  /**
   *  PAY-09 — ردیفِ جدای «گردکردنِ نقدی» (نوعِ `rounding`، `core/pay/TillRounding`)، به پول و علامت‌دار؛ فقط فروشی
   *  که نقد بستش و پلهٔ صندوق (`CashRoundPref`) روشن بود. `finalTotal` = `total − discount` + همین؛ پس پرداختی،
   *  صندوق، گزارش و زنجیره همان مبلغِ گرفته‌شده را می‌بینند و قیمت/تخفیف دست نمی‌خورند. ⛔ `null` نوشته نمی‌شود.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val roundingMinor: Long? = null,
  /**
   *  کانالِ فروش (ORD-20): `online` برای فاکتورِ سریِ `W` که سرور هنگامِ
   *  تحویلِ سفارشِ اینترنتی در دفترِ دکان می‌گذارد؛ فاکتورِ صندوق `null`
   *  است (گزارش آن را «حضوری» می‌خواند — `ReportLedger`). مثلِ `series`
   *  هرگز `null` نوشته نمی‌شود تا فاکتورهای قدیمی opِ تازه نسازند.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val channel: String? = null,
  /**
   *  توکنِ QRِ فاکتور (فاکتورِ نسخهٔ ۲): ۱۲۸ بیتِ تصادفی، ۲۶ نویسه (`ReceiptToken`)، فقط
   *  هنگامِ **ثبتِ** فروش (`SalesEngine`). نشانیِ QR = `<سرور>/r/<نشانِ دکان>/<توکن>` و
   *  صفحهٔ عمومیِ سرور فروش را فقط در همین دکان با آن پیدا می‌کند. فروشِ پیش از نسخهٔ ۲
   *  `null` است و QR ندارد (v3 فروش را فقط‌افزودنی می‌داند؛ توکنِ دیرتر ممکن نیست).
   *  مثلِ `series` هرگز `null` نوشته نمی‌شود: فروشِ کهنه opِ تازه نمی‌سازد و برنامهٔ
   *  کهنه‌ای که این را نمی‌شناسد آن را روی سرور پاک نمی‌کند (A00، `newer-fields.js`).
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val receiptToken: String? = null,
  /**
   *  جمعِ تخفیف‌های خودکارِ این فاکتور به **واحدِ کوچک** (یک‌صدمِ افغانی) — همان
   *  جمعِ `SaleItem.promo`ِ قلم‌هایش (PRM، `PromoEngine.stamp`). `null` یعنی صندوق
   *  تخفیفِ خودکار را نسنجید (کلیدِ دکان خاموش، یا ساختِ پیشین)؛ `0` یعنی سنجید و
   *  چیزی نشد. پول نیست که جمع شود: `total` همین حالا پس از آن است.
   *  ⛔ مثلِ `series` هرگز `null` نوشته نمی‌شود — فاکتورِ قدیمی بایت‌به‌بایت همان.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val promoDiscountMinor: Long? = null,
  /** کدهای تخفیفی که صندوقدار زد (نرمال‌شده) — خالی نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val coupons: List<String> = emptyList(),
  /**
   *  یادداشتِ فاکتور (POS-11، `SaleNotes.clean`: یک خط، حداکثر ۱۲۰ نویسه) — روی رسید چاپ
   *  می‌شود. فقط با تنظیمِ «یادداشت» ِ صندوق (`PosNotesPrefs`)؛ ⛔ `null` نوشته نمی‌شود، پس
   *  فاکتورِ بی یادداشت بایت‌به‌بایت همان. محتوای مالی نیست (`finalized-sales.js`).
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val note: String? = null,
  /**
   *  «تاریخِ تصحیح‌شده» (S28b، بخشِ ۳.۸.۱ بندِ ۸) — فقط سرورِ Sync v3 می‌نویسد، وقتی ساعتِ همین فاکتور
   *  (`createdAt`) بیش از ۵ دقیقه با زمانِ تصحیح‌شده فرق داشت؛ `createdAt` و `date` ِ دستگاه دست نمی‌خورند.
   *  برنامه هرگز خودش نمی‌گذارد؛ ⛔ `null` نوشته نمی‌شود، پس فاکتورِ با ساعتِ درست بایت‌به‌بایت همان.
   */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val correctedAt: Long? = null,
  /** PAY-17 — مهرِ شیفتِ کشوی نام‌داری که دستگاهِ ثبت‌کننده رویش بود (`TillDrawer`)؛ کشوی اصلی `null` و `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val shiftId: String? = null,
)

/**
 *  سهمِ یک قاعدهٔ تخفیفِ خودکار از یک قلمِ فاکتور — `id` و `rev`ِ قاعده در سرور
 *  (`/api/shop/promos`)، نامش همان لحظه (برای چاپِ دوباره؛ قاعده بعداً عوض یا پاک
 *  می‌شود) و تخفیفش روی همین قلم به واحدِ کوچک.
 */
@Serializable
data class SalePromo(
  val id: String = "",
  val rev: Long = 0,
  val name: String = "",
  val discountMinor: Long = 0,
)

/**
 *  یک ردیفِ پرداختِ فاکتور (`Sale.payments`) — به پول، نام‌ها همان `PaymentRow`ِ هسته.
 *  غیرنقد «ثبتِ دستی، در انتظار» است (`status = pending`، `verification = manual`) تا تأییدِ قابلِ اتکا برسد (PAY-08)؛ نقد و «به حساب» تأییدشده‌اند.
 *
 *  روی سیم مبلغ `amount` است (به پول) — همان `PaymentRow`ِ هسته و ردیفی که سرور روی فاکتورِ
 *  W می‌گذارد (`order-ledger.js`، #321)؛ `amountMinor`ِ نسخهٔ پیشِ این شاخه هم خوانده می‌شود.
 *  بی این، فاکتورِ W ِ سرور خواندنِ کلِ دفتر را می‌شکست (فیلدِ اجباریِ گم).
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SalePayment(
  val method: String,
  val kind: String,
  @SerialName("amount")
  @JsonNames("amountMinor")
  val amountMinor: Long,
  val status: String,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val ref: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val verification: String? = null,
  /** امانتِ کیست، اگر پول هنوز دستِ دکان نیست — `courier` برای «نقد هنگامِ تحویل»ِ فاکتورِ W */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val custody: String? = null,
  /** حساب‌پیِ دکان: اثرِ انگشتِ حسابِ گیرنده (همان `PaymentRow.payee`) ⇐ گزارشِ «پول به کدام حساب رفت» ِ صاحب */
  @EncodeDefault(EncodeDefault.Mode.NEVER) val payee: String? = null,
  @EncodeDefault(EncodeDefault.Mode.NEVER) val fx: SaleFx? = null, // PAY-10: فقط ردیفِ `fx` — رکوردِ تبادلِ ثابت (`FxTillFlow`)
)

/**
 *  فاکتورِ سفارشِ اینترنتی (سریِ `W`، ORD-20) — سرور آن را هنگامِ تحویل
 *  می‌سازد. صندوق آن را لغو یا «اصلاح در سبد» نمی‌کند (یعنی دوباره نمی‌زند)؛
 *  فروشی که برنامه هنگامِ تحویلِ سفارش می‌زند (`OrderFulfillment`) هم همین کانال را دارد.
 *  مرجوعیِ قلم‌به‌قلم در دکان مثلِ هر فاکتورِ دیگر مجاز است.
 */
fun Sale.isOnlineOrder(): Boolean = channel == "online" || series?.trim() == ONLINE_SERIES

/** سریِ سرور برای سفارشِ اینترنتی — هیچ دستگاهی آن را نمی‌گیرد (`server/src/lib/doc-series.js`) */
const val ONLINE_SERIES = "W"

/**
 *  شمارهٔ نمایشیِ فاکتور — `B1-154` (F0-03)، یا همان `154` برای فاکتورِ بی سری؛
 *  `null` اگر فاکتور شماره ندارد. شماره‌های قبلی همان‌طور می‌مانند.
 */
fun Sale.invoiceLabel(): String? =
  invoiceNumber?.let { n -> series?.let(ir.vil3ntec.tohid.core.text.JsText::trim)?.takeIf { it.isNotEmpty() }?.let { "$it-$n" } ?: n.toString() }  // trim ِ JS (saleLabel)

@Serializable
data class SaleItem(
  val id: String,
  val saleId: String = "",
  /**
   * برای قلمِ آزاد خالی است — قلمی که کالای ثبت‌شده‌ای پشتش نیست و روی
   * موجودی هم اثر نمی‌گذارد. شرحش سرِ `SalesEngine.CartLine`.
   */
  val productId: String = "",
  /**
   * نامِ قلم، فقط برای قلمِ آزاد.
   *
   * قلمِ معمولی نامش را از خودِ کالا می‌گیرد و اینجا خالی می‌ماند — تا
   * تغییرِ نامِ کالا در فاکتورهای قدیمی هم دیده شود، همان‌طور که تا امروز
   * بوده.
   *
   * فیلدِ تازه‌ای است که نسخهٔ وب نمی‌شناسد. افزودنی و بی‌خطر است (پیش‌فرض
   * خالی)، ولی اگر همان دفتر در نسخهٔ وب باز و دوباره نوشته شود، ممکن است
   * این نام بیفتد و آن قلم بی‌نام بماند. فاکتور از بین نمی‌رود، فقط
   * برچسبش.
   */
  val name: String = "",
  val quantity: Double = 0.0,
  val unitPrice: Double = 0.0,
  val purchasePrice: Double = 0.0,
  val totalPrice: Double = 0.0,
  val returnedQty: Double = 0.0,
  /** سریِ اسکن‌شده سرِ صندوق — اول از همین برداشته می‌شود (`StockLedger`، افزودنی) */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val lotId: String? = null,
  /** محلِ فروش (INV-20)؛ `null` = محلِ اصلیِ دکان */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val locationId: String? = null,
  /** PRD-10 — رونوشتِ اجزای کیت هنگامِ فروش (`ProductPlansMap.parts`)؛ کالای عادی `null` و نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val parts: List<KitPart>? = null,
  /** POS-28 — سریال‌های همین قلم به ترتیبِ ثبت؛ کالای بی سریال `null` و نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val serials: List<String>? = null,
  /**
   *  قیمتِ فهرستِ کالا هنگامِ فروش، پیش از تخفیفِ خودکار و قیمتِ دستی، به **واحدِ
   *  کوچک** — تا سرور تخفیفِ خودکار (`promo`) را از پایین آوردنِ دستیِ قیمت جدا
   *  کند. فقط وقتی صندوق تخفیفِ خودکار را سنجید؛ قلمِ آزاد ندارد.
   *  ⛔ `null` و فهرستِ خالی نوشته نمی‌شوند (`EncodeDefault.NEVER`) — قلمِ قدیمی همان.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val listPriceMinor: Long? = null,
  /** تخفیف‌های خودکارِ همین قلم، به ترتیبِ اعمالِ هسته؛ جمعش = تخفیفِ خودکارِ قلم */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val promo: List<SalePromo> = emptyList(),
  /** یادداشتِ همین قلم (POS-11) — مثلِ `Sale.note`، `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val note: String? = null,
  /**
   *  منبعِ قیمتِ همین قلم برای رسید و سابقه (POS-18): فقط `"special"` (قیمتِ خاصِ مشتری، `CustomerPrice.stamp`).
   *  `null` = لیست/دستی، همان رفتارِ همیشه، و نوشته نمی‌شود — پرچمِ خاموش ⇒ قلم بایت‌به‌بایت همان.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val priceSource: String? = null,
  /** POS-23 — فروشندهٔ قلمِ جدا از فاکتور (`line_seller`، `LineSeller.stored`)؛ `null` = `Sale.seller`، نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val seller: String? = null,
)

@Serializable
data class SaleReturn(
  val id: String,
  val saleId: String = "",
  val saleItemId: String = "",
  val productId: String = "",
  val quantity: Double = 0.0,
  val amount: Double = 0.0,
  val reason: String = "",
  val date: String = "",
  val createdAt: Long = 0,
  /*
   *  RET (v3 §۶.۸) — فقط برگشتِ پرچم‌دار (`ReturnSettings.enabled`، `ReturnFlow.post`).
   *  برگشتِ امروز هیچ‌کدام را ندارد و ⛔ `null` نوشته نمی‌شود (`EncodeDefault.NEVER`،
   *  مثلِ `Sale.series`)، پس ردیفِ قدیمی بایت‌به‌بایت همان می‌ماند.
   */
  /** سندِ برگشت: ردیف‌های یک برگشت یک `docId` دارند؛ تکرارِ همان سند دوباره ثبت نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val docId: String? = null,
  /** `restock` (به موجودی) | `damaged` (ضایعات) */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val disposition: String? = null,
  /** حالتِ بازپرداختِ انتخاب‌شده: `original` | `cash` | `store_credit` */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val refund: String? = null,
  /** سهمِ این ردیف که از بدهیِ بازِ همین فاکتور کم شد (افغانی)؛ بقیه نقد یا اعتبار */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val toAccount: Double? = null,
  /** سهمِ این ردیف که اعتبارِ فروشگاه شد (افغانی) — به حسابِ `creditTo` */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val toCredit: Double? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val creditTo: String? = null,
  /**
   *  سهمِ این ردیف که به روشِ غیرنقدِ اصلی (کیف پول، کارت، حواله…) پس رفت: شناسهٔ روش ⇒
   *  افغانی. فقط فاکتورِ با `Sale.payments`؛ این سهم از دخل کم نمی‌شود (`CashShift`).
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val toMethods: Map<String, Double>? = null,
  /** فقط برگشتِ بی رسید: قیمتِ واحدی که پس داده شد */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val unitPrice: Double? = null,
  /**
   *  ثبت‌کننده و تأییدکننده (برای گزارشِ ضدتقلب). دفترِ موجودی (`StockLedger`) کاربرِ ردیفِ
   *  برگشت را هم از همین `by` می‌خواند؛ برگشتِ قدیمی `null` است (⇐ «legacy»). فقط **یک** `by`.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val by: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val approvedBy: String? = null,
  /** تعویض: فاکتورِ تازه‌ای که در همان جریان زده شد */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val exchangeSaleId: String? = null,
  /**
   *  `online` برای مرجوعیِ سرور هنگامِ بازپرداختِ سفارشِ اینترنتی (ORD-20): پولش از راهِ
   *  سفارش برمی‌گردد، نه از دخل (`CashShift`). مرجوعیِ دستگاه `null` است و مثلِ `Sale.channel`
   *  هرگز `null` نوشته نمی‌شود تا ردیف‌های قدیمی opِ تازه نسازند.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val channel: String? = null,
  /**
   *  «برگهٔ امضاشده»ی تأییدِ همین سندِ برگشت (RET-04، RET-10، STF-02؛ `core/perm/ReturnApprovals`):
   *  سند `returns/<docId>`، مبلغ = جمعِ ردیف‌های سند. فقط روی **نخستین** ردیفِ سند و فقط وقتی تأیید
   *  لازم بوده؛ سرور برگه را از هر ردیفِ سند برمی‌دارد (`groupReturns`). `null` نوشته نمی‌شود.
   */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val approval: kotlinx.serialization.json.JsonObject? = null,
  /** PAY-17 — مهرِ شیفتِ کشوی نام‌داری که دستگاهِ ثبت‌کننده رویش بود (`TillDrawer`)؛ کشوی اصلی `null` و `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val shiftId: String? = null,
)

@Serializable
data class Debtor(
  val id: String,
  val name: String = "",
  val phone: String = "",
  val notes: String = "",
  val createdAt: Long = 0,
  /**
   *  سقفِ اعتبار به افغانی (CRM-03) — `null` یعنی بی سقف، همان رفتارِ همیشه.
   *  هم‌واحدِ `DebtTransaction.amount`؛ حساب با `DebtBook.creditCheck` به واحدِ کوچک.
   *
   *  ⛔ `null` نوشته نمی‌شود (`EncodeDefault.NEVER`، مثلِ `Sale.series`): وگرنه
   *  هر قرض‌دارِ قدیمی یک کلیدِ تازه می‌گرفت و نخستین تفاضلِ Sync v1 برای همه op
   *  می‌ساخت. قرض‌دارِ قدیمی بایت‌به‌بایت همان می‌ماند.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val creditLimit: Double? = null,
  /*
   *  پروندهٔ مشتری (CRM-02، ۰۸، ۱۰، ۱۱، ۱۲) — همه اختیاری و همه `null` به معنای «همان رفتارِ همیشه».
   *  ⛔ هیچ‌کدام `null` نوشته نمی‌شود (`EncodeDefault.NEVER`، مثلِ `creditLimit`): قرض‌دارِ قدیمی
   *  بایت‌به‌بایت همان می‌ماند و نخستین تفاضلِ Sync v1 برایش op نمی‌سازد. نوشتن فقط از `CustomerCrm`
   *  و `CustomerMerge`.
   */
  /** شمارهٔ دوم (CRM-02) — همان قالبِ `phone` (`LocaleText.phoneForSave`) */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val phone2: String? = null,
  /** برچسب‌ها (CRM-11) — بی تکرار (با `TextNormalizer.compareKey`)، بی «#» */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val tags: List<String>? = null,
  /** گروهِ مشتری (CRM-08)، مثلاً «همکار» یا «عمده‌فروش» */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val group: String? = null,
  /** لیستِ قیمتِ پیش‌فرض (CRM-08): `wholesale` = قیمتِ عمده؛ `null` = خرده (همیشه) */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val priceList: String? = null,
  /** رضایت برای پیامِ تبلیغاتی (CRM-12): `null` = نپرسیده (یعنی نه)، `false` = صریحاً نه */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val marketingConsent: Boolean? = null,
  /**
   *  ادغام‌شده در (CRM-10): شناسهٔ مشتریِ ماندگار. ردیف پاک نمی‌شود (سابقه و شناسه می‌مانند)،
   *  ولی دیگر «فعال» نیست: `CustomerCrm.active` آن را بیرون می‌گذارد.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val mergedInto: String? = null,
  /**
   *  کد/بارکدِ کارتِ مشتری (CRM-16) — فقط رقم، یکتا در دکان؛ نوشتن فقط از `CustomerCards`.
   *  `null` = بی کارت، همان رفتارِ همیشه، و ⛔ نوشته نمی‌شود (`EncodeDefault.NEVER`).
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val cardCode: String? = null,
  /** نشانیِ تحویل (CRM-09) — شرح سرِ `DeliveryAddress`؛ `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val deliveryAddress: DeliveryAddress? = null,
  /**
   *  قیمت‌های خاصِ همین مشتری (POS-18) — هر کالا یک ردیف؛ نوشتن فقط از `CustomerPrices` (پشتِ `CustomerPricePrefs`).
   *  یک فیلدِ قرض‌دار است، پس همان راهِ همگام‌سازی و همان ادغامِ `Debtor.priceList` را دارد (v1 ردیف، v3 ‹master›
   *  فیلدبه‌فیلد). `null` = بی قیمتِ خاص، همان رفتارِ همیشه، و ⛔ نوشته نمی‌شود (`EncodeDefault.NEVER`).
   */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val specialPrices: List<SpecialPrice>? = null,
)

/**
 *  یک قیمتِ خاصِ مشتری (POS-18، هسته `core/sale/CustomerPrice.Entry`): کالا، قیمتِ واحدِ پایه به **یک‌صدمِ افغانی** و
 *  بازهٔ اختیاری (`yyyy-MM-dd`، دو سر داخل). سرِ خالی نوشته نمی‌شود.
 */
@Serializable
data class SpecialPrice(
  val productId: String = "",
  val priceMinor: Long = 0,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val from: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val to: String? = null,
)

@Serializable
data class DebtTransaction(
  val id: String,
  val debtorId: String = "",
  /** give = قرض داده شد، receive = پول گرفته شد */
  val type: String = "give",
  val amount: Double = 0.0,
  val date: String = "",
  val notes: String = "",
  val createdAt: Long = 0,
  /**
   *  سررسیدِ یک برد (`yyyy-MM-dd`) — `null` یعنی سررسید ندارد، همان رفتارِ همیشه.
   *  فقط برای `give`؛ رسید سررسید ندارد. مبلغ را عوض نمی‌کند، فقط «کِی باید
   *  پس بیاید» (`DebtBook`: عقب‌افتاده = سررسید گذشته و هنوز باز).
   *
   *  ⛔ `null` نوشته نمی‌شود (`EncodeDefault.NEVER`) — تراکنشِ قدیمی همان می‌ماند.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val dueDate: String? = null,
  /**
   *  ردیفِ ساختهٔ سیستم (نه دستِ کاربر): سندی که آن را ساخت — برای لغو و مرجوعی شناسهٔ
   *  فاکتور. همان نامِ `refId`ِ `StockMovement`. ردیفِ دستی `null` است و نوشته نمی‌شود.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val refId: String? = null,
  /**
   *  چرا سیستم ساختش — همان واژه‌های `StockMovement.type`:
   *  `sale_cancel` (لغوِ فروشِ نسیه؛ بدهی بسته می‌شود، پولی به دخل نیامده)،
   *  `customer_return` (مرجوعیِ فروشِ نسیه؛ کنارِ کمِ کلِ مرجوعی از دخل)،
   *  `store_credit` (برای آینده: پرداخت با اعتبارِ فروشگاه). `null` = ردیفِ دستی.
   *  صندوق `sale_cancel` و `store_credit` را نقد نمی‌شمارد (`CashShift.isCashReceipt`).
   */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val kind: String? = null,
  /** PAY-17 — مهرِ شیفتِ کشوی نام‌داری که دستگاهِ ثبت‌کننده رویش بود (`TillDrawer`)؛ کشوی اصلی `null` و `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val shiftId: String? = null,
)

@Serializable
data class Expense(
  val id: String,
  val title: String = "",
  val category: String = "",
  val amount: Double = 0.0,
  val date: String = "",
  val notes: String = "",
  val createdAt: Long = 0,
)

@Serializable
data class Supplier(
  val id: String,
  val name: String = "",
  val phone: String = "",
  val address: String = "",
  val notes: String = "",
  val createdAt: Long = 0,
  /**
   *  PUR-10 — زمانِ تأمینِ دستی (روز، ۱ تا ۳۶۵) از فرمِ تأمین‌کننده؛ بر زمانِ سنجیده از سفارش‌ها
   *  می‌چربد (`ReorderCore.leadTime`). افزودنی: `null` نوشته نمی‌شود و دفترِ قدیم همان است.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val leadDays: Int? = null,
)

@Serializable
data class Purchase(
  val id: String,
  val productId: String = "",
  val supplierId: String = "",
  val quantity: Double = 0.0,
  val unit: String = "",
  val purchasePrice: Double = 0.0,
  val totalAmount: Double = 0.0,
  val date: String = "",
  val notes: String = "",
  val paidAmount: Double = 0.0,
  val debt: Double = 0.0,
  val warehouseEntryId: String? = null,
  val createdAt: Long = 0,
  /**
   *  شمارهٔ فاکتورِ خریدِ چندردیفه (`PurchaseBook`) — همهٔ ردیف‌های یک فاکتور
   *  همین عدد را دارند. ۰ یعنی خریدِ تک‌ردیفهٔ قدیمی (هر ردیف فاکتورِ خودش).
   *  افزودنی و بی‌خطر؛ نسخهٔ وب نمی‌شناسدش و فقط گروه‌بندی را از دست می‌دهد.
   */
  val invoiceNo: Int = 0,
  /** سهمِ این ردیف از هزینهٔ جانبیِ فاکتور (افغانی) — فقط نمایش؛ بدهیِ تأمین‌کننده نیست (PUR-06، افزودنی) */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val landedCost: Double? = null,
  /** همان سهم، دقیق به صدمِ افغانی (مثلِ `WarehouseEntry.landedCostMinor`) */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val landedCostMinor: Long? = null,
  /** سررسیدِ پرداختِ همین فاکتور (`yyyy-MM-dd`، PUR-07، افزودنی) — یادآورِ «سررسید گذشته» */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val dueDate: String? = null,
  /*  PRD-07 — ردیفی که به واحدِ خرید ثبت شد: نامِ واحد، مقدار به همان واحد و ضریب (هزارم). `quantity` و `purchasePrice` همچنان به
   *  واحدِ پایه‌اند و `totalAmount` جمعِ دقیقِ همان ردیف؛ این سه فقط برای نمایش و برگشت. `null` نوشته نمی‌شود ⇒ خریدِ پیشین همان. */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val unitName: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val unitQtyMilli: Long? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val unitFactorMilli: Long? = null,
)

@Serializable
data class SupplierPayment(
  val id: String,
  val supplierId: String = "",
  val amount: Double = 0.0,
  val date: String = "",
  val notes: String = "",
  val createdAt: Long = 0,
)

@Serializable
data class StockMovement(
  val id: String,
  val productId: String = "",
  /** purchase_in | sale | customer_return | supplier_return | adjustment | transfer */
  val type: String = "",
  val qty: Double = 0.0,
  val date: String = "",
  val notes: String = "",
  val refId: String? = null,
  val createdAt: Long = 0,
  /*  افزودنی — `StockLedger`: کاربر و دلیل (INV-02)، و برای `transfer` مبدأ،
   *  مقصد و سری (INV-20). انتقال جمعِ صفر است و موتورِ امروز نمی‌بیندش. */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val userId: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val reason: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val locationId: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val toLocationId: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val lotId: String? = null,
  /** «تاریخِ تصحیح‌شده» ِ سرورِ v3 (S28b)؛ فقط از سرور — نگهبانِ ساعتِ INV-10 (`CountAbsorb.member`) */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val correctedAt: Long? = null,
)

@Serializable
data class PriceChange(
  val id: String,
  val productId: String = "",
  val oldPrice: Double = 0.0,
  val newPrice: Double = 0.0,
  val date: String = "",
  val createdAt: Long = 0,
  /*
   *  PRD-14 — چه کسی و چرا (فقط با `ShopPolicy.productTools`)؛ `null` نوشته نمی‌شود، پس ردیفِ قدیمی و
   *  ردیفِ پرچمِ خاموش بایت‌به‌بایت همان JSON ِ پیشین‌اند و op ِ تازه‌ای نمی‌سازند.
   */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val by: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val reason: String? = null,
)

/** شناسهٔ ردیفِ سابقه — ULID، همان `uid()`ِ وب و `newId()`ِ صفحه‌ها (F0-10) */
fun auditId(): String = ir.vil3ntec.tohid.sync.v1.Ulid.next()

@Serializable
data class AuditEntry(
  val id: String,
  val type: String = "",
  val date: String = "",
  val refId: String? = null,
  val notes: String = "",
  val createdAt: Long = 0,
  /** مبلغِ ردیف — فعلاً فقط صندوق (`shift_open`/`shift_close`)؛ بقیه صفر */
  val amount: Double = 0.0,
  /*  PAY (v3 A4) — فقط ردیف‌های صندوقِ پرچم‌دار (`cash_in`، `cash_out`، `no_sale` و `shift_close`ِ با شمارش) و `pay_fix` (POS-40).
   *  همان نام‌های رویدادِ pay-core و `ReportLedger`؛ `null` نوشته نمی‌شود تا ردیف‌های قدیمی op ِ تازه نسازند. */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val by: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val approvedBy: String? = null,
  /** POS-40 — فقط `pay_fix` ِ «نیازمند تأیید»: برگهٔ امضاشدهٔ مدیر (`auditLog/<شناسه>`، همان مبلغ) که سرور با `shop.sync.pay_fix` می‌سنجد */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val approval: kotlinx.serialization.json.JsonObject? = null,
  /** دلیل: `deposit`/`owner_withdrawal`/`petty_expense`/`other`، دلیلِ کشو بی فروش، یا دلیلِ اختلاف */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val reason: String? = null,
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val note: String? = null,
  /** گزارشِ Zِ هسته در لحظهٔ بستن (به پول، همان شکلِ JS) — لغوِ بعدی عوضش نمی‌کند */
  @OptIn(ExperimentalSerializationApi::class)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val z: kotlinx.serialization.json.JsonObject? = null,
  /** ردیف‌هایی که این رد جابه‌جا کرده، هر کدام `جدول/شناسه` (مثلاً `transactions/t1`، `sales/s7`) — فعلاً فقط `customer_merge`
   *  (CRM-10)، تا ادغام برگشت‌پذیر و دنبال‌کردنی بماند. حداکثر `CustomerMerge.REF_CHUNK` در هر رد (سقفِ ۶۴KB ِ هر op). `null` نوشته نمی‌شود. */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val refIds: List<String>? = null,
  /** POS-40 — فقط `pay_fix` (`PayFixFlow`؛ `refId` = فاکتور، `amount` = مبلغ): روشِ پیشین، روشِ تازه و مرجعِ روشِ تازه */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val fromMethod: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val toMethod: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val payRef: String? = null,
  /** POS-26 — فقط `special_order` (سند) و `special_order_status` (وضعیتِ تازه)؛ `SpecialOrders`. `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val specialOrder: SpecialOrderDoc? = null,
  /** POS-27 — فقط `layaway` (سند) و `layaway_status` (وضعیتِ تازه)؛ `Layaways`. `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val layaway: LayawayDoc? = null,
  /** INV-21 — فقط `stock_bom` (`Bom`): دستورِ ساختِ کالای `refId`؛ `null` نوشته نمی‌شود */ @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val bom: BomDoc? = null,
  /** PRM-13 — فقط ردیف‌های `loyalty_*` (`LoyaltyPoints`): شمارِ امتیاز (همیشه مثبت؛ نوع علامت را می‌گوید). `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val points: Long? = null,
  /** PAY-07 — فقط `pay_status` (`PayStatusFlow`؛ `refId` = فاکتور، `amount` = مبلغِ ردیف): جای ردیف در `payments`، روشش،
   *  وضعیتِ پیشین و تازه. `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val payRow: Int? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val payMethod: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val fromStatus: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val toStatus: String? = null,
  /** PAY-17 — فقط `shift_open` ِ کشوی نام‌دار («چند کشو»، `TillDrawer`): نامِ کشو یا صندوقدار. کشوی اصلی `null` و `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val drawer: String? = null,
  /** INV-11 — فقط `stock_transfer` (`StockTransferBook`): گامِ انتقالِ موجودیِ سرور (`StockTransfer.Step.xfer`). `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val xfer: kotlinx.serialization.json.JsonObject? = null,
  /** CRM-15 — فقط `customer_view` («چه کسی پرونده را دید»، `CustomerAccessLog`): شناسهٔ بیننده، نقش، دستگاه و نوع
   *  (`detail`/`phone`/`export`؛ `core/crm/CustomerAccess`). نامِ بیننده همان `by`. بی دادهٔ مشتری. `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val viewer: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val role: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val device: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val view: String? = null,
  /** STF-09 — مُهرِ نشست هنگامِ نوشتن (`AuditSeal`، نه صفحه): کاربرِ واردشده، نقشش و دستگاه (`SyncStore.deviceUid` ِ همگام‌سازی).
   *  پس از ساخت عوض نمی‌شوند؛ ردیفِ کهنه بی این‌ها «نامعلوم» دیده می‌شود. `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val userId: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val userRole: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val deviceId: String? = null,
  /** STF-09 — حلقهٔ زنجیرهٔ هشِ سابقهٔ همین دستگاه (`core/audit/AuditChain`، فقط با پرچمِ `shop.doc_chain`): زنجیره
   *  (`<deviceId>/<دوره>`)، شماره، هشِ پیشین، هشِ همین ردیف و سطحِ مدل (`AuditSeal.LEVEL`؛ ۱ نوشته نمی‌شود). `null` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val chain: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val chainSeq: Long? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val prev: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val hash: String? = null,
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val chainV: Long? = null,
)

/**
 *  کلِ دفترِ دکان — همان کلیدهایی که نسخهٔ وب در
 *  localStorage['tohid-shop-data-v1'] می‌نویسد، با همان نام‌ها.
 */
@Serializable
data class ShopData(
  val debtors: List<Debtor> = emptyList(),
  val transactions: List<DebtTransaction> = emptyList(),
  val expenses: List<Expense> = emptyList(),
  val expenseCategories: List<String> = DEFAULT_EXPENSE_CATEGORIES,
  val products: List<Product> = emptyList(),
  val productCategories: List<String> = DEFAULT_PRODUCT_CATEGORIES,
  val productUnits: List<String> = DEFAULT_UNITS,
  val warehouseEntries: List<WarehouseEntry> = emptyList(),
  val sales: List<Sale> = emptyList(),
  val saleItems: List<SaleItem> = emptyList(),
  @SerialName("returns") val saleReturns: List<SaleReturn> = emptyList(),
  // شمارهٔ فاکتور از یک شروع می‌شود. قبلاً از ۱۰۰۰ بود — عددی که فقط
  // «بزرگ‌تر به نظر رسیدن» را می‌ساخت و برای دکان‌داری هیچ معنایی نداشت.
  // دفترهای موجود شمارهٔ خودشان را نگه می‌دارند: شمارهٔ فاکتور در سابقهٔ
  // عملیات و در حساب قرض‌داران نوشته شده و عوض کردنش آن ارجاع‌ها را می‌شکند.
  val nextInvoiceNo: Int = 1,
  val suppliers: List<Supplier> = emptyList(),
  val purchases: List<Purchase> = emptyList(),
  val supplierPayments: List<SupplierPayment> = emptyList(),
  val stockMovements: List<StockMovement> = emptyList(),
  val priceHistory: List<PriceChange> = emptyList(),
  val auditLog: List<AuditEntry> = emptyList(),
  /** گاوصندوق (PAY-18) — مجموعهٔ پلهٔ ۴ِ Sync v1 */
  val safeEntries: List<SafeEntry> = emptyList(),
  /** سندِ ردشده‌ای که صاحب «کنار گذاشت»/«اصلاح کرد» — کامل و بیرون از همگام‌سازی (`data/SetAsideDoc.kt`)؛ `[]` نوشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class) @EncodeDefault(EncodeDefault.Mode.NEVER) val setAside: List<SetAsideDoc> = emptyList(),
)

val DEFAULT_EXPENSE_CATEGORIES = listOf("کرایه", "برق", "معاش", "ترانسپورت", "خوراک", "متفرقه")  // داده: نامِ پیش‌فرضِ ذخیره‌شدهٔ دستهٔ مصرف
val DEFAULT_PRODUCT_CATEGORIES = emptyList<String>()
val DEFAULT_UNITS = listOf("عدد", "کیلوگرم", "گرم", "لیتر", "متر", "کارتن", "بسته")  // داده: نامِ پیش‌فرضِ ذخیره‌شدهٔ واحد
