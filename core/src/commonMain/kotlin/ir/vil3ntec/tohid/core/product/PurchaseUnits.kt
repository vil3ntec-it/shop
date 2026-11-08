package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import ir.vil3ntec.tohid.core.text.LocaleFormat

/*
 *  واحدهای خریدِ چندگانه — PRD-07 (§۶.۳): «واحدِ پایه، واحدهای خرید و فروش با ضریبِ تبدیل».
 *
 *  یک کالا با واحدِ پایهٔ «دانه» از تأمین‌کنندهٔ «الف» در «کارتنِ ۱۲» و از «ب» در «کارتنِ ۲۴» خریده می‌شود. هر
 *  واحدِ خرید نام، ضریب به **واحدِ پایه** (به هزارم: ۲۴ دانه ⇒ 24000؛ برای واحدِ وزنی اعشار: کیسهٔ ۲٫۵ کیلو ⇒ 2500)،
 *  و به اختیار تأمین‌کنندهٔ پیش‌فرض و یک بارکد دارد. موجودی، بها و قیمت همیشه به واحدِ پایه می‌مانند؛ فقط ورودیِ
 *  ردیفِ خرید/سفارش تبدیل می‌شود. خالص (commonMain)؛ همتای سرور: `server/src/lib/purchase-units.js` (همان کدها).
 *
 *  ── حساب (همان واحدهای #231) ─────────────────────────────────────
 *  مقدار = عددِ صحیحِ هزارم (`…Milli`)، پول = عددِ صحیحِ یک‌صدمِ افغانی (`…Minor`).
 *  • مقدارِ پایه = `mulDivRound(مقدار، ضریب، ۱۰۰۰)` ِ `PurchaseOrderCore` (نیمه به +∞).
 *  • بهای هر واحدِ پایه = round(جمعِ ردیف × ۱۰۰۰ ÷ مقدارِ پایه) با **همان** قاعدهٔ نیمه به +∞ ([roundDiv] =
 *    floor(n/d + ½))؛ فقط برای نمایش و «قیمتِ خریدِ کالا».
 *  • **جمعِ ردیف هرگز از روی بهای واحد دوباره ساخته نمی‌شود**: ارزشی که به انبار و میانگینِ موزون می‌رود خودِ
 *    جمعِ ردیف است (`WarehouseEntry.costMinor`)، و بدهیِ تأمین‌کننده هم همان. باقی‌ماندهٔ گرد کردن
 *    ([BaseCost.remainderMinor] = جمع − مقدارِ پایه × بهای واحد، مثبت یا منفی) جایی گم نمی‌شود: در ارزشِ همان
 *    ردیف می‌ماند. مثال: ۱ کارتنِ ۲۴ به ۱٬۰۰۰ افغانی (100000) ⇒ بهای واحد 4167 (۴۱٫۶۶۶… ⇒ ۴۱٫۶۷)، ۲۴ × 4167 =
 *    100008، باقی −8 و ارزشِ انبار دقیقاً 100000.
 */
object PurchaseUnits {

  const val MAX_UNITS = 10
  const val MAX_NAME = 30
  /** یک میلیون واحدِ پایه — همان سقفِ اندازهٔ بسته (`PackBarcodes.MAX_SIZE_MILLI`) */
  const val MAX_FACTOR_MILLI = PackBarcodes.MAX_SIZE_MILLI
  const val MAX_REF = 128
  const val MAX_CODE = 64
  const val MILLI = 1000L
  private const val MAX_SAFE = PurchaseOrderCore.MAX_SAFE

  /** یک واحدِ خریدِ ذخیره‌شده (`supplierId`/`barcode` ِ نبوده ⇒ `null`) */
  data class Item(val name: String, val factorMilli: Long, val supplierId: String? = null, val barcode: String? = null)

  /** ورودیِ فرم، همان‌طور که تایپ می‌شود؛ ردیفِ تمامِ خالی نادیده */
  data class Draft(val name: String = "", val factor: String = "", val supplierId: String = "", val barcode: String = "")

  /** کدهای رد — همان رشته‌ها در سرور (`purchase-units.js`) و برنامه (`StrPurchaseUnits.problem`) */
  object Code {
    const val NOT_A_LIST = "pu_not_list"
    const val TOO_MANY = "pu_too_many"
    const val NAME_REQUIRED = "pu_name_required"
    const val NAME_TOO_LONG = "pu_name_too_long"
    const val NAME_IS_BASE = "pu_name_is_base"
    const val DUPLICATE_NAME = "pu_duplicate_name"
    const val BAD_FACTOR = "pu_bad_factor"
    const val FACTOR_TOO_BIG = "pu_factor_too_big"
    const val WHOLE_ONLY = "pu_whole_only"
    const val BAD_SUPPLIER = "pu_bad_supplier"
    const val BAD_BARCODE = "pu_bad_barcode"
    const val DUPLICATE_BARCODE = "pu_duplicate_barcode"
    const val BARCODE_IS_PRODUCT = "pu_barcode_is_product"
    const val BARCODE_TAKEN = "pu_barcode_taken"
    const val BARCODE_IS_OTHER_PACK = "pu_barcode_is_other_pack"
  }

  /** نخستین ایراد: کد، ردیف (از صفر؛ −۱ = کلِ فهرست) و نامِ همان ردیف برای پیام */
  data class Failure(val code: String, val index: Int = -1, val name: String = "")

  sealed interface Checked {
    data class Ok(val units: List<Item>) : Checked
    data class Failed(val failure: Failure) : Checked
  }

  /** کلیدِ یکتاییِ نام — همان «نامِ عیناً یکسان» ِ فرمِ کالا (`SimilarNames.exactKey`) */
  fun key(name: String): String = SimilarNames.exactKey(name)

  fun draftOf(u: Item): Draft = Draft(u.name, PackBarcodes.sizeText(u.factorMilli), u.supplierId.orEmpty(), u.barcode.orEmpty())

  /** «کارتن (۲۴ دانه)» */
  fun label(u: Item, baseUnit: String): String =
    "${u.name} (${PackBarcodes.sizeText(u.factorMilli)}${if (baseUnit.isBlank()) "" else " $baseUnit"})"

  /** واحدِ همین نام (همان کلید)، یا null */
  fun find(units: List<Item>?, name: String?): Item? {
    if (units.isNullOrEmpty() || name.isNullOrBlank()) return null
    val k = key(name)
    return units.firstOrNull { key(it.name) == k }
  }

  /** پیش‌فرضِ ردیفِ خرید برای یک تأمین‌کننده: واحدی که همین تأمین‌کننده را دارد، وگرنه null (= واحدِ پایه) */
  fun defaultFor(units: List<Item>?, supplierId: String?): Item? =
    if (supplierId.isNullOrBlank()) null else units.orEmpty().firstOrNull { it.supplierId == supplierId }

  /**
   *  شکلِ واحدهای ذخیره‌شده (همان سنجشِ سرور، بی دانستنِ کالاهای دیگر). `baseUnit`/`fractional` فقط وقتی
   *  کالا معلوم است (برنامه)؛ سرور `null` می‌دهد و «اعشار برای واحدِ شمارشی» را نمی‌سنجد.
   */
  fun validate(units: List<Item>, baseUnit: String? = null, fractional: Boolean = true): Failure? {
    if (units.size > MAX_UNITS) return Failure(Code.TOO_MANY)
    val names = HashSet<String>()
    val codes = HashSet<String>()
    units.forEachIndexed { i, u ->
      val name = u.name.trim()
      if (name.isEmpty()) return Failure(Code.NAME_REQUIRED, i)
      if (name.length > MAX_NAME) return Failure(Code.NAME_TOO_LONG, i, name)
      if (!baseUnit.isNullOrBlank() && key(name) == key(baseUnit)) return Failure(Code.NAME_IS_BASE, i, name)
      if (!names.add(key(name))) return Failure(Code.DUPLICATE_NAME, i, name)
      if (u.factorMilli <= 0) return Failure(Code.BAD_FACTOR, i, name)
      if (u.factorMilli > MAX_FACTOR_MILLI) return Failure(Code.FACTOR_TOO_BIG, i, name)
      if (!fractional && u.factorMilli % MILLI != 0L) return Failure(Code.WHOLE_ONLY, i, name)
      val s = u.supplierId
      if (s != null && (s.length > MAX_REF || PurchaseOrderCore.blank(s))) return Failure(Code.BAD_SUPPLIER, i, name)
      val c = u.barcode
      if (c != null) {
        if (c.isEmpty() || c.length > MAX_CODE || c.any { it.isWhitespace() }) return Failure(Code.BAD_BARCODE, i, name)
        if (!codes.add(c)) return Failure(Code.DUPLICATE_BARCODE, i, name)
      }
    }
    return null
  }

  /**
   *  فرم ⇒ واحدها، یا نخستین ایراد.
   *  @param baseUnit واحدِ پایهٔ کالا
   *  @param fractional کالا اعشار می‌پذیرد؟ (همان قاعدهٔ بسته‌ها: واحدِ وزنی یا واحدِ فروشِ دوم)
   *  @param ownBarcodes بارکدهای خودِ کالا — بارکدِ واحدِ خرید یکی از آن‌ها نیست (وگرنه یک اسکن هم «۱» بود هم «۲۴»)
   *  @param ownPacks بارکدِ بسته‌های فروشِ همین کالا ⇐ اندازه: همان کارتن (همان اندازه) می‌تواند همان بارکد را داشته باشد
   *  @param ownerOf بارکد ⇐ کالایی که دارد (بارکد، بسته یا واحدِ خرید)؛ `null` = آزاد
   *  @param supplierOk تأمین‌کنندهٔ پیش‌فرض در دفتر هست؟
   */
  fun check(
    drafts: List<Draft>,
    baseUnit: String,
    fractional: Boolean,
    ownBarcodes: List<String>,
    ownPacks: Map<String, Long>,
    selfId: String?,
    ownerOf: (String) -> String?,
    supplierOk: (String) -> Boolean,
  ): Checked {
    val rows = drafts.filter { it.name.isNotBlank() || it.factor.isNotBlank() || it.barcode.isNotBlank() || it.supplierId.isNotBlank() }
    val out = ArrayList<Item>(rows.size)
    rows.forEachIndexed { i, r ->
      val name = r.name.trim().replace(Regex("\\s+"), " ")
      val factor = DecimalText.scaled(r.factor, 3) ?: return Checked.Failed(Failure(Code.BAD_FACTOR, i, name))
      val supplier = r.supplierId.trim().ifEmpty { null }
      if (supplier != null && !supplierOk(supplier)) return Checked.Failed(Failure(Code.BAD_SUPPLIER, i, name))
      val code = ProductBarcodes.parse(r.barcode).let { if (it.size > 1) return Checked.Failed(Failure(Code.BAD_BARCODE, i, name)) else it.firstOrNull() }
      out += Item(name, factor, supplier, code)
    }
    validate(out, baseUnit, fractional)?.let { return Checked.Failed(it) }
    val own = ownBarcodes.toSet()
    out.forEachIndexed { i, u ->
      val c = u.barcode ?: return@forEachIndexed
      if (c in own) return Checked.Failed(Failure(Code.BARCODE_IS_PRODUCT, i, u.name))
      val pack = ownPacks[c]
      if (pack != null && pack != u.factorMilli) return Checked.Failed(Failure(Code.BARCODE_IS_OTHER_PACK, i, u.name))
      val owner = ownerOf(c)
      if (owner != null && owner != selfId) return Checked.Failed(Failure(Code.BARCODE_TAKEN, i, u.name))
    }
    return Checked.Ok(out)
  }

  /* ── حساب ───────────────────────────────────────────────────────── */

  /** round(n ÷ d) با نیمه به +∞ = floor(n/d + ½) — همان قاعدهٔ `mulDivRound`، برای مخرجِ بزرگ‌تر از 2^31 */
  fun roundDiv(n: Long, d: Long): Long {
    require(d > 0) { "purchase-units: roundDiv needs d > 0" }
    val q = n.floorDiv(d)
    val r = n.mod(d)
    return if (r >= d - r) q + 1 else q
  }

  /** مقدار در واحدِ خرید ⇒ مقدارِ پایه (هزارم) */
  fun baseQtyMilli(qtyMilli: Long, factorMilli: Long): Long = PurchaseOrderCore.mulDivRound(qtyMilli, factorMilli, MILLI)

  /** جمعِ یک ردیف: مقدار در واحدِ خرید × قیمتِ یک واحدِ خرید (همان `PurchaseOrderCore.lineTotalMinor`) */
  fun lineTotalMinor(qtyMilli: Long, unitPriceMinor: Long): Long = PurchaseOrderCore.lineTotalMinor(qtyMilli, unitPriceMinor)

  /**
   *  ردیفِ خرید به واحدِ پایه: مقدار، بهای هر واحدِ پایه، جمعِ **دقیق** (همان جمعِ ردیف) و باقی‌ماندهٔ گرد کردن.
   *  همیشه `remainderMinor == totalMinor − mulDivRound(baseQtyMilli, unitCostMinor, 1000)`.
   */
  data class BaseCost(val baseQtyMilli: Long, val unitCostMinor: Long, val totalMinor: Long, val remainderMinor: Long)

  fun baseCost(qtyMilli: Long, factorMilli: Long, totalMinor: Long): BaseCost {
    require(qtyMilli > 0 && factorMilli > 0) { "purchase-units: qty and factor must be > 0" }
    require(totalMinor in 0..MAX_SAFE) { "purchase-units: total out of range" }
    val base = baseQtyMilli(qtyMilli, factorMilli)
    require(base > 0) { "purchase-units: base quantity rounds to zero" }
    //  totalMinor ≤ 2^53 ⇒ × ۱۰۰۰ در Long جا می‌شود (≈ 9.007e18 < 9.22e18)
    val unit = roundDiv(totalMinor * MILLI, base)
    val rebuilt = PurchaseOrderCore.mulDivRound(base, unit, MILLI)
    return BaseCost(base, unit, totalMinor, totalMinor - rebuilt)
  }

  /* ── بارکد ⇒ کالا و واحدِ خرید ───────────────────────────────────── */

  data class Hit(val productId: String, val unit: Item)

  /** فقط جورِ **کامل** (رقمِ فارسی/عربی ⇐ لاتین)؛ کدی که دو کالا دارند هیچ‌کدام نیست */
  class Index(products: List<Pair<String, List<Item>>>) {
    private val hits = HashMap<String, Hit>()
    private val clash = HashSet<String>()

    init {
      for ((pid, units) in products) for (u in units) {
        val c = u.barcode?.let(LocaleFormat::asciiDigits) ?: continue
        val prev = hits[c]
        if (prev != null && prev.productId != pid) clash += c else hits[c] = Hit(pid, u)
      }
      clash.forEach { hits.remove(it) }
    }

    fun find(code: String): Hit? = hits[LocaleFormat.asciiDigits(code.trim())]

    val isEmpty: Boolean get() = hits.isEmpty()
  }
}
