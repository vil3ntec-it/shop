package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.BulkPrice
import ir.vil3ntec.tohid.core.product.PackBarcodes
import ir.vil3ntec.tohid.core.money.Qty
import ir.vil3ntec.tohid.core.product.ProductBarcodes
import ir.vil3ntec.tohid.core.product.ProductImportPlan
import ir.vil3ntec.tohid.core.product.ProductLabels
import ir.vil3ntec.tohid.core.product.SimilarNames
import ir.vil3ntec.tohid.core.stock.StockLevelRules
import ir.vil3ntec.tohid.core.stock.StockLevels
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.plain
import kotlin.math.max

/**
 *  انبار — ثبتِ کالا، ورودِ جنس، اصلاحِ موجودی.
 *
 *  مثل حسابِ فروش، اینجا هم هیچ چیز به صفحه یا فایل دست نمی‌زند: ورودی
 *  «دفترِ فعلی»، خروجی «دفترِ تازه». همان قاعده‌ها و همان پیام‌های نسخهٔ
 *  وب، چون فروشنده به همان‌ها عادت کرده.
 */
object WarehouseEngine {

  data class ProductDraft(
    val name: String = "",
    val category: String = "",
    val unit: String = "",
    val purchasePrice: Double = 0.0,
    val salePrice: Double = 0.0,
    val minStock: Double = 0.0,
    val barcode: String = "",
    /** null ⇒ در ویرایش دست نمی‌خورد (فرم‌هایی که این فیلدها را ندارند، مثلِ ثبتِ گروهی) */
    val subcategory: String? = null,
    val supplierId: String? = null,
    val active: Boolean? = null,
    val saleUnit: String? = null,
    val unitFactor: Double? = null,
    /**
     *  PRD-04: همهٔ بارکدهای کالا (کادرِ «بارکدها» پشتِ `ShopPolicy.productTools`). null ⇒ فرمِ
     *  تک‌بارکدی: `barcode` فقط بارکدِ نخست است و بقیهٔ بارکدهای کالای ویرایشی سرِ جایشان می‌مانند.
     */
    val barcodes: List<String>? = null,
    /**
     *  PRD-14 (پشتِ `ShopPolicy.productTools`): کاربر و دلیلِ تغییرِ قیمت در سابقه و `priceHistory`.
     *  null ⇒ همان ردیف‌های پیشین، بی این دو فیلد.
     */
    val changedBy: String? = null,
    val reason: String? = null,
    /**
     *  PRD-02/PRD-26 — نام‌های دیگر، واژه‌ها و محلِ قفسه. `null` ⇒ دست نمی‌خورد (فرمی که این بخش را
     *  نشان نمی‌دهد، یعنی تنظیمِ خاموش، ورود از فایل و ثبتِ گروهی)؛ وگرنه همه از همین.
     */
    val info: ir.vil3ntec.tohid.core.product.ProductInfo? = null,
    /** PRD-06 (پشتِ `ShopPolicy.productTools`): برند و برچسب‌ها؛ null ⇒ در ویرایش دست نمی‌خورد، خالی ⇒ پاک */
    val brand: String? = null,
    val tags: List<String>? = null,
    /**
     *  INV-12: `true` ⇒ نقطهٔ سفارش و بیشینه دقیقاً همین دو مقدار می‌شوند (`null` = پاک)، با سنجشِ
     *  ترتیبِ «کمینه ≤ نقطهٔ سفارش < بیشینه» در `core`. `false` (پیش‌فرض؛ فرم‌هایی که این دو را
     *  ندارند یا تنظیمِ «نقطهٔ سفارش و بیشینه» خاموش است) ⇒ همان مقدارِ پیشین دست نمی‌خورد.
     */
    val editLevels: Boolean = false,
    val reorderPoint: Double? = null,
    val maxStock: Double? = null,
    /**
     *  PRD-04 (همان پرچم): بسته‌ها با بارکدِ خودشان (کارتنِ ۲۴ دانه‌ای)، به شکلِ فرم؛ موتور می‌سنجد
     *  (`PackBarcodes.check`). null ⇒ در ویرایش دست نمی‌خورد؛ فهرستِ خالی ⇒ همه برداشته.
     */
    val packs: List<PackBarcodes.Draft>? = null,
  )

  data class EntryDraft(
    val productId: String = "",
    val cartons: Double = 0.0,
    val perCarton: Double = 0.0,
    val units: Double = 0.0,
    val unit: String = "",
    val price: Double = 0.0,
    val date: String = "",
    val notes: String = "",
    /** خالی = بی انقضا؛ وگرنه `yyyy-MM-dd` */
    val expiryDate: String = "",
    val batch: String = "",
    /** خالی = ثبت نشده؛ وگرنه `yyyy-MM-dd` و نه بعد از انقضا */
    val productionDate: String = "",
    /*
     *  موتورِ تازهٔ موجودی (`StockLedger`) — `null` یعنی فرم این‌ها را ندارد (خاموش)
     *  و ردیف دقیقاً مثلِ امروز ساخته/ویرایش می‌شود.
     */
    /** هزینهٔ جانبیِ کلِ همین ردیف (کرایه، گمرک) به افغانی؛ ۰ = ندارد — PUR-06 */
    val landedCost: Double? = null,
    /** محلِ نگه‌داری؛ "" = محلِ اصلیِ دکان — INV-20 */
    val locationId: String? = null,
    /** کاربرِ ثبت‌کننده (INV-02) — فقط در ردیفِ تازه */
    val userId: String? = null,
  )

  /** هزینهٔ جانبیِ نادرست — `null` یعنی درست یا نیامده */
  private fun landedProblem(draft: EntryDraft): String? =
    draft.landedCost?.takeIf { it.isNaN() || it < 0 }?.let { "هزینهٔ جانبی درست نیست" }

  /** تاریخِ تولید درست است و از انقضا جلوتر نیست — `null` یعنی درست */
  private fun datesProblem(draft: EntryDraft): String? {
    if (!ExpiryEngine.validDate(draft.expiryDate)) return "تاریخِ انقضا درست نیست"
    if (!ExpiryEngine.validDate(draft.productionDate)) return "تاریخِ تولید درست نیست"
    val made = draft.productionDate.trim()
    val exp = draft.expiryDate.trim()
    if (made.isNotEmpty() && exp.isNotEmpty() && made > exp) return "تاریخِ تولید بعد از انقضا است"
    return null
  }

  /** اصلاح موجودی | برگشت به تأمین‌کننده — همان دو حالتِ نسخهٔ وب */
  enum class AdjustKind { ADJUSTMENT, SUPPLIER_RETURN }

  /* ------------------------------ انبارگردانی ------------------------------ */

  /** یک ردیفِ اختلاف: آنچه برنامه می‌گوید، آنچه شمرده شد، و ارزشِ فرقشان */
  data class CountLine(val product: Product, val system: Double, val counted: Double) {
    val diff: Double get() = counted - system
    val value: Double get() = diff * product.purchasePrice
  }

  /**
   *  فقط کالاهایی که شمرده شده‌اند و با موجودیِ برنامه فرق دارند.
   *  فرقِ کمتر از یک‌هزارم (گرد شدنِ اعشار) اختلاف نیست.
   */
  fun countLines(d: ShopData, counted: Map<String, Double>): List<CountLine> =
    d.products.mapNotNull { p ->
      val c = counted[p.id] ?: return@mapNotNull null
      val sys = ShopStore.stock(d, p.id)
      if (kotlin.math.abs(c - sys) < 0.0005) null else CountLine(p, sys, c)
    }

  /**
   *  ثبتِ انبارگردانی: هر اختلاف یک «اصلاحِ موجودی» با دلیلِ «انبارگردانی»
   *  می‌شود — همان ردیفِ انبار، گردشِ کالا و سابقه‌ای که اصلاحِ دستی می‌سازد،
   *  پس گزارش‌ها و همگام‌سازی چیزِ تازه‌ای نمی‌خواهند. شمارشِ منفی رد می‌شود.
   */
  fun stockCount(
    d: ShopData,
    counted: Map<String, Double>,
    note: String,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    if (counted.values.any { it.isNaN() || it < 0 }) return Result.Failed("مقدارِ شمرده‌شده نمی‌تواند منفی باشد")
    val lines = countLines(d, counted)
    if (lines.isEmpty()) return Result.Failed("اختلافی نیست — موجودیِ شمرده‌شده با برنامه یکی است")
    val why = listOf("انبارگردانی", note.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
    var data = d
    var lastId = ""
    for (l in lines) {
      when (val r = adjustStock(
        data, l.product.id, kotlin.math.abs(l.diff), l.diff > 0, why, AdjustKind.ADJUSTMENT, today, now, newId,
      )) {
        is Result.Failed -> return Result.Failed("«${l.product.name}»: ${r.message}")
        is Result.Ok -> { data = r.data; lastId = r.id }
      }
    }
    return Result.Ok(data, lastId)
  }

  sealed interface Result {
    data class Ok(val data: ShopData, val id: String) : Result
    data class Failed(val message: String) : Result
  }

  data class Summary(
    val products: Int,
    val cartons: Double,
    val units: Double,
    val value: Double,
    val low: Int,
    val out: Int,
  )

  /* --------------------------- خلاصهٔ انبار --------------------------- */

  fun summary(d: ShopData) = Summary(
    products = d.products.size,
    cartons = d.warehouseEntries.sumOf { it.cartons },
    units = d.warehouseEntries.sumOf { it.units },
    // ارزشِ تقریبی: همان جمعِ «واحد × قیمتِ خرید» نسخهٔ وب، نه موجودیِ فعلی
    value = d.warehouseEntries.sumOf { it.units * it.price },
    low = d.products.count { ShopStore.stockStatus(d, it) == "low" },
    out = d.products.count { ShopStore.stockStatus(d, it) == "out" },
  )

  /* ---------------------------- ثبتِ کالا ---------------------------- */

  /** نامِ کالا برای سنجشِ تکرار: فاصله‌های اضافه و بزرگی/کوچکیِ حروف حساب نمی‌شود (همان کلیدِ core) */
  private fun nameKey(name: String): String = SimilarNames.exactKey(name)

  /** فهرستِ خوانای تغییرها — قیمتِ خرید جدا در `price_change` ثبت می‌شود */
  internal fun productChanges(a: Product, b: Product): List<String> = buildList {
    if (a.name != b.name) add("نام «${a.name}» ⇐ «${b.name}»")
    if (a.salePrice != b.salePrice) add("قیمت فروش ${money(a.salePrice)} ⇐ ${money(b.salePrice)}")
    if (a.category != b.category) add("دسته «${a.category}» ⇐ «${b.category}»")
    if (a.unit != b.unit) add("واحد «${a.unit}» ⇐ «${b.unit}»")
    if (a.minStock != b.minStock) add("حداقل موجودی ${money(a.minStock)} ⇐ ${money(b.minStock)}")
    if (a.reorderPoint != b.reorderPoint) add("نقطهٔ سفارش ${levelText(a.reorderPoint)} ⇐ ${levelText(b.reorderPoint)}")
    if (a.maxStock != b.maxStock) add("حداکثر موجودی ${levelText(a.maxStock)} ⇐ ${levelText(b.maxStock)}")
    if (a.active != b.active) add(if (b.active) "فعال شد" else "غیرفعال شد")
    if (a.barcodes != b.barcodes) add("بارکد عوض شد")
    //  PRD-02/PRD-26 — فقط وقتی فرمِ همین بخش چیزی را عوض کرد (خاموش ⇒ هرگز)
    if (a.namePs != b.namePs || a.nameEn != b.nameEn || a.synonyms != b.synonyms || a.keywords != b.keywords) {
      add("نام‌های دیگر یا واژه‌های جست‌وجو عوض شد")
    }
    addAll(ProductInfoMap.changes(a, b)) // محلِ قفسه، جایگزین، فیلدِ سفارشی؛ PRD-03/09: کد و خدماتی
    if (a.brand.orEmpty() != b.brand.orEmpty()) add("برند «${a.brand.orEmpty()}» ⇐ «${b.brand.orEmpty()}»")
    if (a.tags.orEmpty() != b.tags.orEmpty()) add("برچسب‌ها «${a.tags.orEmpty().joinToString("، ")}» ⇐ «${b.tags.orEmpty().joinToString("، ")}»")
    if (a.brand.orEmpty() != b.brand.orEmpty()) add("برند «${a.brand.orEmpty()}» ⇐ «${b.brand.orEmpty()}»")
    if (a.tags.orEmpty() != b.tags.orEmpty()) add("برچسب‌ها «${a.tags.orEmpty().joinToString("، ")}» ⇐ «${b.tags.orEmpty().joinToString("، ")}»")
    if (a.packs.orEmpty() != b.packs.orEmpty()) {
      add(if (b.packs.isNullOrEmpty()) "بسته‌ها برداشته شد"
        else "بسته‌ها «${b.packs.joinToString("، ") { PackBarcodes.label(ShopStore.coreOf(it), b.unit) }}»")
    }
  }

  /**
   *  PRD-04: بسته‌های فرم ⇒ بسته‌های کالا، یا پیامِ رد. `null` ⇒ همان `keep`؛ خالی ⇒ null (همان JSON ِ بی بسته).
   *  کالای شمارشی اندازهٔ اعشاری نمی‌گیرد، مگر واحدِ فروشِ دوم داشته باشد (همان قاعدهٔ `SalesEngine.quantityProblem`).
   */
  private fun packsOf(
    drafts: List<PackBarcodes.Draft>?, keep: List<ProductPack>?, unit: String, saleUnit: String,
    codes: List<String>, selfId: String?, owners: Map<String, String>,
  ): Pair<List<ProductPack>?, String?> {
    if (drafts == null) return keep to null
    val fractional = SalesEngine.isFractionalUnit(unit) || saleUnit.isNotBlank()
    return when (val c = PackBarcodes.check(drafts, unit, fractional, codes, selfId) { owners[it] }) {
      is PackBarcodes.Checked.Failed -> null to c.message
      is PackBarcodes.Checked.Ok -> c.packs.map { ProductPack(it.name, it.sizeMilli, it.barcodes) }.ifEmpty { null } to null
    }
  }

  /** برند/برچسبِ فرم ⇒ مقدارِ ذخیره؛ خالی ⇒ null تا JSON ِ کالای بی برند همان پیشین بماند */
  private fun brandOf(raw: String?, keep: String?): String? = if (raw == null) keep else ProductLabels.brand(raw).ifEmpty { null }
  private fun tagsOf(raw: List<String>?, keep: List<String>?): List<String>? =
    if (raw == null) keep else ProductLabels.tags(raw).ifEmpty { null }

  /** تاریخچهٔ یک کالا: ویرایش‌ها و تغییرِ قیمتِ خرید، تازه‌ترین اول */
  fun productHistory(d: ShopData, productId: String): List<AuditEntry> =
    d.auditLog.filter { it.refId == productId && (it.type == "product_edit" || it.type == "price_change") }
      .sortedByDescending { it.createdAt }

  /**
   *  PRD-19: کالاهایی با نامِ **مشابه** (نه یکسان) — هشدار، نه رد: فاصله/نیم‌فاصله/اعراب، ترتیبِ
   *  کلمه‌ها یا غلطِ تایپیِ کوچک؛ رقمِ متفاوت هرگز (`SimilarNames` ِ core). `exceptId` خودِ کالا.
   */
  fun similarNames(d: ShopData, name: String, exceptId: String? = null, limit: Int = 3): List<Product> {
    if (name.isBlank()) return emptyList()
    val except = if (exceptId == null) -1 else d.products.indexOfFirst { it.id == exceptId }
    return SimilarNames.Index(d.products.map { it.name }).find(name, except, limit).map { d.products[it.index] }
  }

  /** کالای دیگری با همین نام هست؟ (`exceptId` خودِ کالای در حالِ ویرایش) */
  fun duplicateName(d: ShopData, name: String, exceptId: String? = null): Product? {
    val key = nameKey(name)
    return d.products.firstOrNull { it.id != exceptId && nameKey(it.name) == key }
  }

  /** واحدِ فروش و ضریب با هم معنی دارند؛ ضریب باید عددِ مثبتِ معتبر باشد */
  private fun unitProblem(unit: String, saleUnit: String?, factor: Double?): String? {
    val su = saleUnit?.trim().orEmpty()
    if (su.isEmpty()) return null
    if (su == unit.trim()) return "واحدِ فروش همان واحدِ موجودی است — خالی بگذارید"
    val f = factor ?: return "ضریبِ تبدیل را بنویسید"
    if (f.isNaN() || f.isInfinite() || f <= 0) return "ضریبِ تبدیل باید عددی بزرگ‌تر از صفر باشد"
    return null
  }

  fun addProduct(d: ShopData, draft: ProductDraft, now: Long, newId: () -> String): Result {
    val name = draft.name.trim()
    if (name.isEmpty()) return Result.Failed("نام کالا را بنویسید")
    //  کالای هم‌نام، موجودی و گزارش را دو تکه می‌کند و فروشنده نمی‌داند کدام را بزند
    duplicateName(d, name)?.let { return Result.Failed("کالایی به نامِ «${it.name}» از قبل هست") }
    unitProblem(draft.unit, draft.saleUnit, draft.unitFactor)?.let { return Result.Failed(it) }
    if (draft.salePrice.isNaN() || draft.salePrice < 0) return Result.Failed("قیمت فروش درست نیست")
    if (draft.purchasePrice.isNaN() || draft.purchasePrice < 0) return Result.Failed("قیمت خرید درست نیست")
    val minStock = if (draft.minStock.isNaN()) 0.0 else draft.minStock
    if (draft.editLevels) levelsProblem(minStock, draft.reorderPoint, draft.maxStock)?.let { return Result.Failed(it) }

    val barcode = draft.barcode.trim()
    val codes = draft.barcodes?.let { it.flatMap(ProductBarcodes::parse).distinct() }
      ?: (if (barcode.isEmpty()) emptyList() else listOf(barcode))
    if (codes.size > ProductBarcodes.MAX) return Result.Failed("حداکثر ${plain(ProductBarcodes.MAX)} بارکد برای یک کالا")
    // بارکدِ تکراری، کالای قبلی را از دسترسِ اسکنر بیرون می‌برد — بارکدِ بستهٔ کالای دیگر هم (PRD-04)
    val index = ShopStore.codeOwners(d)
    ProductBarcodes.conflict(codes, null) { index[it] }?.let {
      return Result.Failed(if (draft.barcodes == null || codes.size == 1) "این بارکد قبلاً برای کالای دیگری ثبت شده" else "بارکدِ $it قبلاً برای کالای دیگری ثبت شده")
    }
    val (packs, packProblem) = packsOf(draft.packs, null, draft.unit, draft.saleUnit.orEmpty(), codes, null, index)
    (packProblem ?: draft.info?.let { ProductInfoMap.skuProblem(d, it, null, null) })?.let { return Result.Failed(it) } // PRD-03: کد یکتا

    val id = newId()
    val product = Product(
      id = id,
      name = name,
      category = draft.category,
      unit = draft.unit,
      purchasePrice = draft.purchasePrice,
      salePrice = draft.salePrice,
      wholesalePrice = 0.0,
      minStock = if (draft.minStock.isNaN()) 0.0 else draft.minStock,
      notes = "",
      barcodes = codes,
      createdAt = now,
      subcategory = draft.subcategory?.trim().orEmpty(),
      supplierId = draft.supplierId.orEmpty(),
      active = draft.active ?: true,
      saleUnit = draft.saleUnit?.trim().orEmpty(),
      unitFactor = if (draft.saleUnit.isNullOrBlank()) 1.0 else draft.unitFactor ?: 1.0,
      reorderPoint = if (draft.editLevels) draft.reorderPoint else null,
      maxStock = if (draft.editLevels) draft.maxStock else null,
      brand = brandOf(draft.brand, null),
      tags = tagsOf(draft.tags, null),
      packs = packs,
    ).let { p -> draft.info?.let { ProductInfoMap.apply(p, it) } ?: p }

    return Result.Ok(
      d.copy(
        products = ProductVariantsMap.withChildren(d.products + product, product, newId, now), // PRD-11: تنوع‌های تازه
        productCategories = withCategory(d.productCategories, draft.category),
        productUnits = withUnit(d.productUnits, draft.unit),
      ),
      id = id,
    )
  }

  fun editProduct(
    d: ShopData,
    id: String,
    draft: ProductDraft,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    val existing = d.products.find { it.id == id } ?: return Result.Failed("کالا پیدا نشد")
    val name = draft.name.trim()
    if (name.isEmpty()) return Result.Failed("نام کالا را بنویسید")
    duplicateName(d, name, exceptId = id)?.let { return Result.Failed("کالایی به نامِ «${it.name}» از قبل هست") }
    unitProblem(draft.unit, draft.saleUnit ?: existing.saleUnit, draft.unitFactor ?: existing.unitFactor)
      ?.let { return Result.Failed(it) }
    if (draft.salePrice.isNaN() || draft.salePrice < 0) return Result.Failed("قیمت فروش درست نیست")
    if (draft.purchasePrice.isNaN() || draft.purchasePrice < 0) return Result.Failed("قیمت خرید درست نیست")
    //  حدِ کم بودن همیشه از فرم می‌آید؛ نقطهٔ سفارش و بیشینه فقط وقتی فرم آن‌ها را دارد — ترتیبِ هر سه با هم سنجیده می‌شود
    val minStock = if (draft.minStock.isNaN()) 0.0 else draft.minStock
    val reorderPoint = if (draft.editLevels) draft.reorderPoint else existing.reorderPoint
    val maxStock = if (draft.editLevels) draft.maxStock else existing.maxStock
    if (draft.editLevels || existing.reorderPoint != null || existing.maxStock != null) {
      levelsProblem(minStock, reorderPoint, maxStock)?.let { return Result.Failed(it) }
    }

    val barcode = draft.barcode.trim()
    //  فرمِ تک‌بارکدی فقط بارکدِ نخست را عوض می‌کند؛ بقیه نمی‌افتند (رفعِ از دست رفتنِ داده، #476؛ PRD-04)
    val codes = draft.barcodes?.let { it.flatMap(ProductBarcodes::parse).distinct() }
      ?: ProductBarcodes.mergePrimary(barcode, existing.barcodes)
    if (codes.size > ProductBarcodes.MAX) return Result.Failed("حداکثر ${plain(ProductBarcodes.MAX)} بارکد برای یک کالا")
    // بارکدِ تکراری فقط وقتی ایراد دارد که مالِ کالای دیگری باشد — و فقط بارکدِ **تازه** سنجیده می‌شود:
    // تعارضِ آفلاینِ قدیمی (§۳.۴) ویرایشِ نام یا قیمت را قفل نمی‌کند
    val index = ShopStore.codeOwners(d)
    //  فرمِ تک‌بارکدی (`barcodes == null`) همان پیامِ همیشگی را می‌گیرد، هر چند بارکدِ دیگر هم کالا داشته باشد (#476)
    ProductBarcodes.conflict(codes.filter { it !in existing.barcodes }, id) { index[it] }?.let {
      return Result.Failed(if (draft.barcodes == null || codes.size == 1) "این بارکد قبلاً برای کالای دیگری ثبت شده" else "بارکدِ $it قبلاً برای کالای دیگری ثبت شده")
    }
    val unitNow = draft.unit
    val (packs, packProblem) = packsOf(draft.packs, existing.packs, unitNow, draft.saleUnit ?: existing.saleUnit, codes, id, index)
    //  PRD-03: فقط کدِ تازه سنجیده می‌شود — تعارضِ آفلاینِ قدیمی ویرایشِ نام یا قیمت را قفل نمی‌کند
    (packProblem ?: draft.info?.let { ProductInfoMap.skuProblem(d, it, id, existing.sku) })?.let { return Result.Failed(it) }
    //  PRD-04: بارکدِ تازهٔ کالا بارکدِ بستهٔ خودش نباشد — وگرنه یک اسکن هم «۱» بود هم «۲۴»
    packs.orEmpty().firstNotNullOfOrNull { pk -> codes.firstOrNull { it in pk.barcodes && it !in existing.barcodes }?.let { it to pk } }?.let { (code, pk) ->
      return Result.Failed("بارکدِ $code بارکدِ بستهٔ «${pk.name}» همین کالاست")
    }
    //  واحدِ موجودی عوض شد ⇒ اندازهٔ بسته‌ها (به همان واحد) دیگر معنا ندارد
    if (packs != null && draft.packs == null && unitNow != existing.unit) {
      return Result.Failed("این کالا بسته دارد؛ پیش از عوض کردنِ واحد، بسته‌ها را بردارید یا دوباره بنویسید")
    }

    val updated = existing.copy(
      name = name,
      category = draft.category,
      unit = draft.unit,
      purchasePrice = draft.purchasePrice,
      salePrice = draft.salePrice,
      minStock = if (draft.minStock.isNaN()) 0.0 else draft.minStock,
      barcodes = codes,
      subcategory = draft.subcategory?.trim() ?: existing.subcategory,
      supplierId = draft.supplierId ?: existing.supplierId,
      active = draft.active ?: existing.active,
      saleUnit = draft.saleUnit?.trim() ?: existing.saleUnit,
      unitFactor = when {
        (draft.saleUnit ?: existing.saleUnit).isBlank() -> 1.0
        else -> draft.unitFactor ?: existing.unitFactor
      },
      reorderPoint = reorderPoint,
      maxStock = maxStock,
      brand = brandOf(draft.brand, existing.brand),
      tags = tagsOf(draft.tags, existing.tags),
      packs = packs,
    ).let { u -> draft.info?.let { ProductInfoMap.apply(u, it) } ?: u }

    // تغییرِ قیمتِ خرید در تاریخچه می‌ماند — سودِ گذشته نباید عوض شود
    var history = d.priceHistory
    var audit = d.auditLog
    val by = draft.changedBy?.trim()?.ifEmpty { null }
    val why = draft.reason?.trim()?.ifEmpty { null }
    val whySuffix = why?.let { " — دلیل: $it" }.orEmpty()
    if (existing.purchasePrice != draft.purchasePrice) {
      history = history + PriceChange(
        id = newId(),
        productId = id,
        oldPrice = existing.purchasePrice,
        newPrice = draft.purchasePrice,
        date = today,
        createdAt = now,
        by = by,
        reason = why,
      )
      audit = audit + AuditEntry(
        id = newId(),
        type = "price_change",
        date = today,
        refId = id,
        notes = "تغییر قیمت خرید از ${money(existing.purchasePrice)} به ${money(draft.purchasePrice)} افغانی$whySuffix",
        createdAt = now,
        by = by,
      )
    }
    //  بقیهٔ تغییرهای کالا هم ردپا دارند — «چه کسی نام یا قیمتِ فروش را عوض کرد؟»
    val changes = productChanges(existing, updated)
    if (changes.isNotEmpty()) {
      audit = audit + AuditEntry(
        id = newId(),
        type = "product_edit",
        date = today,
        refId = id,
        notes = "ویرایش «${existing.name}»: ${changes.joinToString("، ")}" + if (updated.salePrice != existing.salePrice) whySuffix else "",
        createdAt = now,
        by = by,
      )
    }

    return Result.Ok(
      d.copy(
        products = ProductVariantsMap.withChildren(d.products.map { if (it.id == id) updated else it }, updated, newId, now),
        productCategories = withCategory(d.productCategories, draft.category),
        productUnits = withUnit(d.productUnits, draft.unit),
        priceHistory = history,
        auditLog = audit,
      ),
      id = id,
    )
  }

  /**
   * حذفِ کالا.
   *
   * فاکتورهای گذشته دست نمی‌خورند — همان‌طور که در نسخهٔ وب. سابقهٔ فروش
   * حقیقتی است که افتاده، و پاک کردنش گزارشِ سودِ ماه‌های قبل را عوض
   * می‌کند. فقط خودِ کالا و ورودی‌های انبارش می‌روند.
   */
  fun deleteProduct(d: ShopData, id: String, today: String, now: Long, newId: () -> String): Result {
    val product = d.products.find { it.id == id } ?: return Result.Failed("کالا پیدا نشد")
    return Result.Ok(
      d.copy(
        products = d.products.filter { it.id != id },
        warehouseEntries = d.warehouseEntries.filter { it.productId != id },
        /*
         *  ردیف‌های یتیم هم با کالا می‌روند.
         *
         *  تا دیروز فقط کالا و ورودی‌های انبارش برداشته می‌شدند و
         *  `stockMovements` و `priceHistory` که به همان شناسه اشاره
         *  می‌کنند سرِ جایشان می‌ماندند — برای همیشه، و با همگام‌سازی به
         *  همهٔ گوشی‌ها هم می‌رفتند. در «گردشِ موجودی» به شکلِ ردیف‌های
         *  «(حذف‌شده)» دیده می‌شدند و بی‌صدا وزنِ دفتر را زیاد می‌کردند.
         *
         *  اقلامِ فروش (`saleItems`) عمداً می‌مانند: فاکتورِ گذشته سند
         *  است و دست‌خوردنی نیست. همان هشداری که پیش از حذف نشان داده
         *  می‌شود هم همین را می‌گوید.
         */
        stockMovements = d.stockMovements.filter { it.productId != id },
        priceHistory = d.priceHistory.filter { it.productId != id },
        auditLog = d.auditLog + AuditEntry(
          id = newId(),
          type = "delete_product",
          date = today,
          refId = id,
          notes = "حذف محصول «${product.name}»",
          createdAt = now,
        ),
      ),
      id = id,
    )
  }

  /** جمله‌ای که پیش از حذف به کاربر نشان داده می‌شود */
  /* ------------------------------ کارِ گروهی ------------------------------ */

  /**
   *  چند کالا با هم: فعال/غیرفعال یا دستهٔ تازه. هر کالا ردیفِ «ویرایش
   *  کالا»ی خودش را در سابقه می‌گیرد — همان ردپای ویرایشِ تکی.
   */
  fun bulkUpdate(
    d: ShopData,
    ids: Set<String>,
    today: String,
    now: Long,
    newId: () -> String,
    change: (Product) -> Product,
  ): Result {
    if (ids.isEmpty()) return Result.Failed("کالایی انتخاب نشده")
    val audit = ArrayList<AuditEntry>()
    val products = d.products.map { p ->
      if (p.id !in ids) return@map p
      val next = change(p)
      val changes = productChanges(p, next)
      if (changes.isNotEmpty()) {
        audit += AuditEntry(
          id = newId(), type = "product_edit", date = today, refId = p.id,
          notes = "ویرایشِ گروهیِ «${p.name}»: ${changes.joinToString("، ")}", createdAt = now,
        )
      }
      next
    }
    if (audit.isEmpty()) return Result.Failed("چیزی عوض نشد")
    val categories = products.filter { it.id in ids }.map { it.category }.fold(d.productCategories) { acc, c -> withCategory(acc, c) }
    return Result.Ok(d.copy(products = products, productCategories = categories, auditLog = d.auditLog + audit), id = ids.first())
  }

  /** کالاهای انتخاب‌شده به زبانِ `BulkPrice` ِ core (پول به یک‌صدمِ افغانی) */
  private fun minorOf(x: Double): Long = Math.round(x * 100)

  /**
   *  PRD-21: پیش‌نمایشِ قیمتِ گروهی — همان که رابط رنگی نشان می‌دهد و همان که `bulkPrice` اعمال می‌کند.
   */
  fun bulkPricePreview(
    d: ShopData, ids: Set<String>, field: BulkPrice.Field, op: BulkPrice.Op, roundToMinor: Long = 100,
  ): BulkPrice.Preview = BulkPrice.preview(
    d.products.filter { it.id in ids }.map { BulkPrice.Item(it.id, it.name, minorOf(it.salePrice), minorOf(it.purchasePrice)) },
    field, op, roundToMinor,
  )

  /**
   *  PRD-21 و PRD-14: قیمتِ گروهی. فقط ردیف‌های `applies` ِ پیش‌نمایش؛ ردیفِ «رد» (منفی) دست نمی‌خورد.
   *  ⛔ پیش‌نمایشِ هشدارداده (زیرِ بها، صفر، تغییرِ ۵۰٪) بی `confirmed` اعمال نمی‌شود؛ دلیل اجباری است.
   *  هر کالا ردیفِ سابقه با کاربر و دلیل دارد؛ تغییرِ قیمتِ خرید در `priceHistory` هم می‌نشیند.
   */
  fun bulkPrice(
    d: ShopData, ids: Set<String>, field: BulkPrice.Field, op: BulkPrice.Op, roundToMinor: Long,
    confirmed: Boolean, by: String, reason: String, today: String, now: Long, newId: () -> String,
  ): Result {
    if (ids.isEmpty()) return Result.Failed("کالایی انتخاب نشده")
    if (!BulkPrice.valid(op)) return Result.Failed("مقدار درست نیست")
    val why = reason.trim()
    if (why.isEmpty()) return Result.Failed("دلیلِ تغییرِ قیمت را بنویسید")
    val preview = bulkPricePreview(d, ids, field, op, roundToMinor)
    if (preview.changed == 0) return Result.Failed("چیزی عوض نشد")
    if (preview.needsConfirm && !confirmed) return Result.Failed("هشدارهای پیش‌نمایش را ببینید و تأیید کنید")
    val who = by.trim().ifEmpty { null }
    val rows = preview.rows.filter { it.applies }.associateBy { it.id }
    val audit = ArrayList<AuditEntry>()
    val history = ArrayList<PriceChange>()
    val label = if (field == BulkPrice.Field.SALE) "قیمت فروش" else "قیمت خرید"
    val products = d.products.map { p ->
      val r = rows[p.id] ?: return@map p
      val value = r.newMinor / 100.0
      val old = r.oldMinor / 100.0
      if (field == BulkPrice.Field.PURCHASE) {
        history += PriceChange(id = newId(), productId = p.id, oldPrice = p.purchasePrice, newPrice = value,
          date = today, createdAt = now, by = who, reason = why)
      }
      audit += AuditEntry(
        id = newId(), type = if (field == BulkPrice.Field.PURCHASE) "price_change" else "product_edit", date = today, refId = p.id,
        notes = "قیمتِ گروهیِ «${p.name}»: $label ${money(old)} ⇐ ${money(value)} — دلیل: $why", createdAt = now, by = who,
      )
      if (field == BulkPrice.Field.SALE) p.copy(salePrice = value) else p.copy(purchasePrice = value)
    }
    return Result.Ok(
      d.copy(products = products, priceHistory = d.priceHistory + history, auditLog = d.auditLog + audit),
      id = rows.keys.first(),
    )
  }

  /* ------------- حدهای موجودی: کمینه، نقطهٔ سفارش، بیشینه (INV-12) ------------- */

  /** «۱۲» یا «—» برای ردِ تغییر در سابقه */
  private fun levelText(v: Double?): String = v?.let { ir.vil3ntec.tohid.qty(it) } ?: "—"

  /** مقدار ⇒ هزارم با همان `Qty.fromLegacy`؛ بیرون از بازهٔ دقیق ⇒ سقفِ Long تا سنجش «بیش از اندازه» بگوید */
  private fun milliOrNull(v: Double?): Long? =
    v?.takeIf { it.isFinite() }?.let { runCatching { Qty.fromLegacy(it).milli }.getOrDefault(if (it < 0) Long.MIN_VALUE else Long.MAX_VALUE) }

  /**
   *  حدهای کالا به زبانِ `core`. `minStock = 0` همان «بی هشدار» ِ امروز است ⇒ `null`.
   *  عددِ نامعتبر (NaN) ⇒ `null`؛ سنجش در `levelsProblem` جلویش را می‌گیرد.
   */
  fun levelsOf(p: Product): StockLevels = StockLevels(
    minMilli = p.minStock.takeIf { it != 0.0 }?.let { milliOrNull(it) },
    reorderMilli = milliOrNull(p.reorderPoint),
    maxMilli = milliOrNull(p.maxStock),
  )

  /**
   *  کالا با حدهای تازه؛ کمینهٔ تعریف‌نشده ⇒ `minStock = 0` (همان قالبِ امروز). حدی که عوض نشده
   *  همان عددِ پیشینِ کالا می‌ماند (گردِ هزارم آن را دست نمی‌زند و ردِ بی‌جا در سابقه نمی‌سازد).
   */
  fun withLevels(p: Product, l: StockLevels): Product {
    val before = levelsOf(p)
    return p.copy(
      minStock = if (l.minMilli == before.minMilli) p.minStock else (l.minMilli ?: 0L) / 1000.0,
      reorderPoint = if (l.reorderMilli == before.reorderMilli) p.reorderPoint else l.reorderMilli?.let { it / 1000.0 },
      maxStock = if (l.maxMilli == before.maxMilli) p.maxStock else l.maxMilli?.let { it / 1000.0 },
    )
  }

  /** پیامِ نخستین ایرادِ حدها (فارسی) یا `null` */
  fun levelsProblem(minStock: Double, reorderPoint: Double?, maxStock: Double?): String? {
    if (listOfNotNull(minStock, reorderPoint, maxStock).any { !it.isFinite() }) return "عددِ حدِ موجودی درست نیست"
    val l = StockLevels(
      minMilli = minStock.takeIf { it != 0.0 }?.let { milliOrNull(it) },
      reorderMilli = milliOrNull(reorderPoint),
      maxMilli = milliOrNull(maxStock),
    )
    return StockLevelRules.problems(l).firstOrNull()?.fa
  }

  /** پیش‌نمایشِ ویرایشِ گروهیِ حدها برای کالاهای انتخاب‌شده — ناموفق‌ها اول (`StockLevelRules.plan`) */
  fun previewLevels(d: ShopData, ids: Set<String>, patch: StockLevelRules.Patch): List<StockLevelRules.Planned> =
    StockLevelRules.plan(d.products.filter { it.id in ids }.map { it.id to levelsOf(it) }, patch)

  /**
   *  ویرایشِ گروهیِ حدها: فقط ردیف‌هایی که پس از تغییر ترتیبشان درست است عوض می‌شوند (بقیه دست
   *  نمی‌خورند و در پیش‌نمایش/نتیجه با دلیل می‌آیند). هر کالای عوض‌شده یک ردِ `product_edit` دارد.
   *  هیچ ردیفی عوض نشود ⇒ `Failed`.
   */
  fun bulkLevels(
    d: ShopData,
    ids: Set<String>,
    patch: StockLevelRules.Patch,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    val apply = previewLevels(d, ids, patch).filter { it.changes }.associate { it.id to it.after }
    if (apply.isEmpty()) return Result.Failed("چیزی عوض نشد")
    return bulkUpdate(d, apply.keys, today, now, newId) { p -> apply[p.id]?.let { withLevels(p, it) } ?: p }
  }

  /** یک ردیفِ «نیاز به سفارش»: وضعیت نسبت به حدها و پیشنهادِ مقدار تا بیشینه (`null` ⇒ بیشینه تعریف نشده) */
  data class ReorderRow(val product: Product, val onHand: Double, val state: StockLevelRules.State, val suggest: Double?)

  /**
   *  کالاهای فعالی که حدی دارند و به نقطهٔ سفارش، کمینه یا صفر رسیده‌اند (INV-12)، با پیشنهادِ «تا
   *  بیشینه». ترتیب: تمام‌شده، زیرِ کمینه، وقتِ سفارش؛ سپس نام. فقط خواندن — سفارشی ثبت نمی‌شود.
   */
  fun reorderList(d: ShopData): List<ReorderRow> {
    val stock = ShopStore.index(d)
    return d.products.asSequence()
      .filter { it.active }
      .mapNotNull { p ->
        val l = levelsOf(p)
        if (l.minMilli == null && l.reorderMilli == null && l.maxMilli == null) return@mapNotNull null
        val onHand = stock.stock(p.id)
        val have = milliOrNull(onHand) ?: 0L
        val st = StockLevelRules.state(have, l)
        if (st !in StockLevelRules.NEEDS_ORDER) return@mapNotNull null
        ReorderRow(p, onHand, st, StockLevelRules.suggestOrderMilli(have, l)?.let { it / 1000.0 })
      }
      .sortedWith(compareBy<ReorderRow> { it.state.ordinal }.thenBy { it.product.name })
      .toList()
  }

  /** حذفِ چند کالا — همان `deleteProduct` برای هر کدام، یا همه یا هیچ */
  fun bulkDelete(d: ShopData, ids: Set<String>, today: String, now: Long, newId: () -> String): Result {
    if (ids.isEmpty()) return Result.Failed("کالایی انتخاب نشده")
    var data = d
    for (id in ids) {
      when (val r = deleteProduct(data, id, today, now, newId)) {
        is Result.Failed -> return r
        is Result.Ok -> data = r.data
      }
    }
    return Result.Ok(data, id = ids.first())
  }

  fun deleteWarning(d: ShopData, id: String): String {
    val product = d.products.find { it.id == id } ?: return ""
    val sold = ShopStore.soldQty(d, id)
    return if (sold > 0) {
      "از «${product.name}» تا کنون ${money(sold)} واحد فروخته شده است. فاکتورهای قبلی حفظ می‌شوند ولی نام این کالا در آن‌ها «حذف‌شده» نشان داده می‌شود و از گزارش سود محصولات کنار می‌رود."
    } else {
      "محصول «${product.name}» و سوابق ورود آن از انبار حذف خواهد شد."
    }
  }

  /* --------------------------- ورودِ کالا --------------------------- */

  fun addEntry(d: ShopData, draft: EntryDraft, today: String, now: Long, newId: () -> String): Result {
    if (d.products.none { it.id == draft.productId }) return Result.Failed("ابتدا یک محصول انتخاب یا اضافه کنید")
    if (draft.units.isNaN() || draft.units <= 0) return Result.Failed("تعداد واحد معتبر وارد کنید")
    if (draft.price.isNaN() || draft.price < 0) return Result.Failed("قیمت خرید معتبر وارد کنید")
    datesProblem(draft)?.let { return Result.Failed(it) }
    landedProblem(draft)?.let { return Result.Failed(it) }

    val id = newId()
    val date = draft.date.ifBlank { today }
    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries + WarehouseEntry(
          id = id,
          productId = draft.productId,
          cartons = draft.cartons,
          perCarton = draft.perCarton,
          units = draft.units,
          unit = draft.unit,
          price = draft.price,
          date = date,
          notes = draft.notes,
          createdAt = now,
          expiryDate = draft.expiryDate.trim(),
          batch = draft.batch.trim(),
          productionDate = draft.productionDate.trim(),
          landedCost = draft.landedCost?.takeIf { it > 0 },
          landedCostMinor = draft.landedCost?.takeIf { it > 0 }?.let(StockLedger::minor),
          locationId = draft.locationId?.trim()?.ifEmpty { null },
          userId = draft.userId?.trim()?.ifEmpty { null },
        ),
        stockMovements = d.stockMovements + StockMovement(
          id = newId(),
          productId = draft.productId,
          type = "purchase_in",
          qty = draft.units,
          date = date,
          notes = draft.notes.ifBlank { "ثبت ورود کالا" },
          refId = id,
          createdAt = now,
        ),
        productUnits = withUnit(d.productUnits, draft.unit),
      ),
      id = id,
    )
  }

  /**
   * ویرایشِ ورودی. حرکتِ انبارِ قبلی سرِ جایش می‌ماند — عیناً مثل نسخهٔ وب.
   */
  fun editEntry(d: ShopData, id: String, draft: EntryDraft, today: String): Result {
    val existing = d.warehouseEntries.find { it.id == id } ?: return Result.Failed("ورودی پیدا نشد")
    if (d.products.none { it.id == draft.productId }) return Result.Failed("ابتدا یک محصول انتخاب یا اضافه کنید")
    if (draft.units.isNaN() || draft.units <= 0) return Result.Failed("تعداد واحد معتبر وارد کنید")
    if (draft.price.isNaN() || draft.price < 0) return Result.Failed("قیمت خرید معتبر وارد کنید")
    datesProblem(draft)?.let { return Result.Failed(it) }
    landedProblem(draft)?.let { return Result.Failed(it) }
    //  کم کردنِ ورودی‌ای که جنسش فروخته شده، موجودی را منفی می‌کرد —
    //  همان نگهبانِ `deleteEntry`
    val left = ShopStore.stock(d, existing.productId) - existing.units +
      (if (draft.productId == existing.productId) draft.units else 0.0)
    if (left < 0) {
      return Result.Failed("این تغییر موجودی را منفی می‌کند: ${money(-left)} واحد از این ورودی فروخته شده است")
    }

    val updated = existing.copy(
      productId = draft.productId,
      cartons = draft.cartons,
      perCarton = draft.perCarton,
      units = draft.units,
      unit = draft.unit,
      price = draft.price,
      date = draft.date.ifBlank { today },
      notes = draft.notes,
      expiryDate = draft.expiryDate.trim(),
      batch = draft.batch.trim(),
      productionDate = draft.productionDate.trim(),
      //  فرمی که این دو را ندارد (خاموش) ⇒ همان مقدارِ قبلی
      landedCost = if (draft.landedCost != null) draft.landedCost.takeIf { it > 0 } else existing.landedCost,
      landedCostMinor = if (draft.landedCost != null) draft.landedCost.takeIf { it > 0 }?.let(StockLedger::minor) else existing.landedCostMinor,
      locationId = if (draft.locationId != null) draft.locationId.trim().ifEmpty { null } else existing.locationId,
    )
    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries.map { if (it.id == id) updated else it },
        productUnits = withUnit(d.productUnits, draft.unit),
      ),
      id = id,
    )
  }

  /**
   *  حذفِ یک ورودِ انبار.
   *
   *  اگر بخشی از همان جنس قبلاً فروخته شده باشد، حذفِ ورودی موجودی را
   *  **منفی** می‌کند — و موجودیِ منفی یعنی ارزشِ انبار و گزارشِ سود هر دو
   *  غلط می‌شوند، بی‌آنکه کسی متوجه شود. پس جلویش گرفته می‌شود، با همان
   *  جمله‌ای که نسخهٔ وب می‌گوید.
   *
   *  حرکتِ انبارِ همان ورودی هم می‌رود: نگه‌داشتنِ خطِ «ورود خرید» برای
   *  جنسی که هرگز نیامده، دفترِ گردشِ موجودی را دروغ می‌کند. به‌جایش یک
   *  ردیفِ سابقه ثبت می‌شود تا معلوم بماند چه کسی چه چیزی را برداشت.
   */
  fun deleteEntry(d: ShopData, id: String, today: String, now: Long, newId: () -> String): Result {
    val entry = d.warehouseEntries.find { it.id == id } ?: return Result.Failed("ورودی پیدا نشد")

    val after = ShopStore.stock(d, entry.productId) - entry.units
    if (after < 0) {
      val shortage = -after
      return Result.Failed(
        "این ورودی قابل حذف نیست: ${money(shortage)} واحد از آن فروخته شده است. " +
          "ابتدا فروش‌های مربوطه را لغو یا مرجوع کنید."
      )
    }

    val product = d.products.find { it.id == entry.productId }
    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries.filter { it.id != id },
        stockMovements = d.stockMovements.filter { it.refId != id },
        auditLog = d.auditLog + AuditEntry(
          id = newId(),
          type = "delete_entry",
          date = today,
          refId = id,
          notes = "حذف ورود انبار «${product?.name ?: "محصول حذف‌شده"}» به مقدار ${money(entry.units)}",
          createdAt = now,
        ),
      ),
      id = id,
    )
  }

  /** جملهٔ تأییدِ پیش از حذف — همان چیزی که وب می‌گوید */
  fun deleteEntryWarning(d: ShopData, id: String): String {
    val entry = d.warehouseEntries.find { it.id == id } ?: return ""
    val product = d.products.find { it.id == entry.productId }
    val now = ShopStore.stock(d, entry.productId)
    return "موجودی «${product?.name ?: "محصول"}» از ${money(now)} به ${money(now - entry.units)} کاهش می‌یابد."
  }

  /* ------------------------- اصلاحِ موجودی ------------------------- */

  /**
   * موجودی را کم یا زیاد می‌کند و دلیلش را ثبت می‌کند.
   *
   * دلیل اجباری است. موجودیِ بی‌دلیل که عوض شود، فردا هیچ‌کس نمی‌فهمد
   * جنس کجا رفته.
   */
  fun adjustStock(
    d: ShopData,
    productId: String,
    quantity: Double,
    increase: Boolean,
    reason: String,
    kind: AdjustKind,
    today: String,
    now: Long,
    newId: () -> String,
    /**
     *  موتورِ تازهٔ موجودی (INV-04): count · damage · expired · lost · other؛ برگشت به
     *  تأمین‌کننده خودش `supplier_return` می‌شود. `null` (خاموش) ⇒ ردیف مثلِ امروز.
     */
    adjKind: String? = null,
    userId: String? = null,
  ): Result {
    val product = d.products.find { it.id == productId } ?: return Result.Failed("ابتدا یک محصول اضافه کنید")
    if (quantity.isNaN() || quantity <= 0) return Result.Failed("مقدار معتبر وارد کنید")
    val why = reason.trim()
    if (why.isEmpty()) return Result.Failed("دلیل اصلاح را بنویسید")

    val delta = if (increase) quantity else -quantity
    //  کاهش بیش از موجودی، موجودی را منفی و گزارشِ انبار را دروغ می‌کند —
    //  همان نگهبانِ `deleteEntry`
    val stockNow = ShopStore.stock(d, productId)
    if (delta < 0 && stockNow + delta < 0) {
      return Result.Failed("فقط ${ir.vil3ntec.tohid.qty(max(0.0, stockNow))} ${product.unit} موجود است")
    }
    val id = newId()
    val date = today
    val label = if (kind == AdjustKind.SUPPLIER_RETURN) "برگشت به تأمین‌کننده" else "اصلاح موجودی"
    val stockKind: String? = when {
      adjKind == null -> null
      kind == AdjustKind.SUPPLIER_RETURN -> "supplier_return"
      //  خراب/گم‌شده/منقضی فقط کاهش است؛ افزایش ⇒ اصلاحِ شمارش
      increase && adjKind in setOf("damage", "lost", "expired") -> "count"
      adjKind in StockLedger.ADJ_KINDS -> adjKind
      else -> "other"
    }
    val user = userId?.trim()?.ifEmpty { null }.takeIf { stockKind != null }

    return Result.Ok(
      d.copy(
        warehouseEntries = d.warehouseEntries + WarehouseEntry(
          id = id,
          productId = productId,
          cartons = 0.0,
          perCarton = 0.0,
          units = delta,
          unit = product.unit,
          price = 0.0,
          date = date,
          notes = why,
          isAdjustment = true,
          createdAt = now,
          adjKind = stockKind,
          userId = user,
        ),
        stockMovements = d.stockMovements + StockMovement(
          id = newId(),
          productId = productId,
          type = if (kind == AdjustKind.SUPPLIER_RETURN) "supplier_return" else "adjustment",
          qty = delta,
          date = date,
          notes = why,
          refId = id,
          createdAt = now,
          userId = user,
          reason = if (stockKind != null) why else null,
        ),
        auditLog = d.auditLog + AuditEntry(
          id = newId(),
          type = if (kind == AdjustKind.SUPPLIER_RETURN) "supplier_return" else "stock_adjustment",
          date = date,
          refId = id,
          notes = "$label «${product.name}» به مقدار ${if (increase) "+" else "−"}${money(quantity)} — دلیل: $why",
          createdAt = now,
        ),
      ),
      id = id,
    )
  }

  /* =========================== ثبتِ دسته‌جمعی =========================== */

  /**
   *  یک ردیفِ «ورود کالا به انبار» در حالتِ دسته‌جمعی.
   *
   *  ردیف یا به کالای موجود اشاره می‌کند، یا خودش کالای تازه‌ای می‌سازد —
   *  همان دو حالتی که فهرستِ کشویی نسخهٔ وب دارد.
   */
  data class BulkEntry(val entry: EntryDraft, val newProduct: ProductDraft? = null)

  /**
   *  چند کالا، یک‌جا.
   *
   *  قاعده‌ی مهم: **یا همه ثبت می‌شوند یا هیچ‌کدام.** اگر ردیفِ پنجم ایراد
   *  داشته باشد، چهار ردیفِ اول هم ثبت نمی‌شوند. نیمه‌کاره ثبت شدن،
   *  بدترین حالت است: کاربر نمی‌داند کدام رفت و کدام نرفت و دوباره که
   *  می‌زند، نصفشان تکراری می‌شود.
   *
   *  چون هر ردیف روی خروجیِ ردیفِ قبل سوار می‌شود، بارکدِ تکراری *داخلِ
   *  همین دسته* هم گرفته می‌شود، نه فقط تکرار با کالاهای قدیمی.
   */
  fun addProducts(
    d: ShopData,
    drafts: List<ProductDraft>,
    now: Long,
    newId: () -> String,
  ): Result {
    if (drafts.isEmpty()) return Result.Failed("حداقل یک ردیف کامل لازم است")
    var data = d
    var lastId = ""
    drafts.forEachIndexed { index, draft ->
      when (val step = addProduct(data, draft, now, newId)) {
        is Result.Failed -> return Result.Failed("ردیف ${plain(index + 1)}: ${step.message}")
        is Result.Ok -> { data = step.data; lastId = step.id }
      }
    }
    return Result.Ok(data, lastId)
  }

  /**
   *  PRD-20: ورودِ کالا از Excel/CSV با پیش‌نمایش (فقط با `ShopPolicy.productTools`).
   *
   *  • پیش‌نمایش دوباره روی **همین** دفتر ساخته و با آنچه کاربر دید (`previewed`) سنجیده می‌شود؛ هر فرقی
   *    (همگام‌سازی در این فاصله کالایی آورد، یا دکمه دو بار زده شد) ⇒ رد، بی هیچ نوشتن. پس همان پیش‌نمایش
   *    هرگز دو بار نوشته نمی‌شود و ورودِ دوبارهٔ همان فایل چیزی برای نوشتن ندارد (همه SAME).
   *  • NEW با همان `addProduct` ِ فرم؛ UPDATE با همان `editProduct` و **فقط** `changes` ِ پیش‌نمایش (خانهٔ
   *    خالی، قیمتِ گردشدهٔ خروجیِ خودِ برنامه و واحد دست نمی‌خورند؛ بارکد فقط افزوده)، با کاربر و دلیلِ
   *    «ورود از فایلِ …» در سابقه و `priceHistory` (PRD-14). یا همه یا هیچ.
   *  • یک ردیفِ `product_import` در سابقه (سابقهٔ واردسازی): نامِ فایل، شمارها و اثرِ انگشت در `refId`.
   */
  fun importProducts(
    d: ShopData,
    rows: List<List<String>>,
    previewed: ProductImportPlan.Plan,
    fileName: String,
    by: String,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    val plan = ProductImport.planOf(d, rows, previewed.update)
    if (plan != previewed) return Result.Failed("کالاهای دکان در این فاصله عوض شد — پیش‌نمایش را دوباره ببینید")
    plan.problem?.let { return Result.Failed(it) }
    if (plan.writes == 0) return Result.Failed("چیزی برای ثبت نیست — کالاهای این فایل از قبل در دکان‌اند")
    val who = by.trim().ifEmpty { null }
    val file = fileName.trim().ifEmpty { "بی‌نام" }
    var data = d
    var lastId = ""
    for (r in plan.rows) {
      val step = when (r.status) {
        ProductImportPlan.Status.NEW -> addProduct(data, ProductDraft(
          name = r.name,
          category = r.category.orEmpty(),
          unit = r.unit.orEmpty(),
          purchasePrice = (r.purchaseMinor ?: 0L) / 100.0,
          salePrice = (r.saleMinor ?: 0L) / 100.0,
          minStock = (r.minMilli ?: 0L) / 1000.0,
          barcodes = r.barcodes,
        ), now, newId)
        ProductImportPlan.Status.UPDATE -> {
          val p = data.products.firstOrNull { it.id == r.existingId }
            ?: return Result.Failed("ردیفِ ${plain(r.line)}: کالا پیدا نشد")
          var category = p.category
          var buy = p.purchasePrice
          var sell = p.salePrice
          var min = p.minStock
          var codes = p.barcodes
          for (c in r.changes) when (c) {
            is ProductImportPlan.Change.Text -> if (c.col == ProductImportPlan.Col.CATEGORY) category = c.new
            is ProductImportPlan.Change.Price ->
              if (c.col == ProductImportPlan.Col.PURCHASE) buy = c.newMinor / 100.0 else sell = c.newMinor / 100.0
            is ProductImportPlan.Change.MinStock -> min = c.newMilli / 1000.0
            is ProductImportPlan.Change.AddBarcodes -> codes = codes + c.codes
          }
          editProduct(data, p.id, ProductDraft(
            name = p.name, category = category, unit = p.unit, purchasePrice = buy, salePrice = sell, minStock = min,
            barcodes = codes, changedBy = who, reason = "ورود از فایلِ «$file»",
          ), today, now, newId)
        }
        else -> continue
      }
      when (step) {
        is Result.Failed -> return Result.Failed("ردیفِ ${plain(r.line)}: ${step.message}")
        is Result.Ok -> { data = step.data; lastId = step.id }
      }
    }
    val n = { s: ProductImportPlan.Status -> plain(plan.count(s)) }
    val notes = "ورود از فایلِ «$file»: ${n(ProductImportPlan.Status.NEW)} کالای تازه، ${n(ProductImportPlan.Status.UPDATE)} به‌روز، " +
      "${plain(plan.count(ProductImportPlan.Status.SAME) + plan.count(ProductImportPlan.Status.KEPT))} از پیش در دکان، " +
      "${n(ProductImportPlan.Status.ERROR)} ردیفِ رد"
    return Result.Ok(
      data.copy(auditLog = data.auditLog + AuditEntry(
        id = newId(), type = ProductImport.AUDIT_TYPE, date = today, refId = ProductImport.refOf(plan.fingerprint),
        notes = notes, createdAt = now, by = who,
      )),
      id = lastId,
    )
  }

  /**
   *  چند ورودِ انبار، یک‌جا — با همان قاعده‌ی «یا همه یا هیچ‌کدام».
   *
   *  ردیفی که کالای تازه می‌خواهد، اول کالا را می‌سازد و بعد ورودی را به
   *  نامِ همان می‌نویسد؛ پس دقیقاً همان زنجیره‌ی فرمِ تک‌ردیفی طی می‌شود:
   *  کالا → ورودی → حرکتِ انبار.
   */
  fun addEntries(
    d: ShopData,
    rows: List<BulkEntry>,
    today: String,
    now: Long,
    newId: () -> String,
  ): Result {
    if (rows.isEmpty()) return Result.Failed("هیچ ردیف کاملی برای ثبت نیست")
    var data = d
    var lastId = ""
    rows.forEachIndexed { index, row ->
      val no = plain(index + 1)
      var productId = row.entry.productId
      if (row.newProduct != null) {
        when (val made = addProduct(data, row.newProduct, now, newId)) {
          is Result.Failed -> return Result.Failed("ردیف $no: ${made.message}")
          is Result.Ok -> { data = made.data; productId = made.id }
        }
      }
      when (val step = addEntry(data, row.entry.copy(productId = productId), today, now, newId)) {
        is Result.Failed -> return Result.Failed("ردیف $no: ${step.message}")
        is Result.Ok -> { data = step.data; lastId = step.id }
      }
    }
    return Result.Ok(data, lastId)
  }

  /* ---------------------------- دسته‌بندی‌ها ---------------------------- */

  /**
   *  دسته‌بندیِ تازه. تکراری اضافه نمی‌شود و فاصله‌های اضافه می‌روند.
   */
  fun addCategory(d: ShopData, name: String): Result {
    val v = name.trim()
    if (v.isEmpty()) return Result.Failed("نام دسته‌بندی را بنویسید")
    if (d.productCategories.contains(v)) return Result.Ok(d, v)
    return Result.Ok(d.copy(productCategories = d.productCategories + v), v)
  }

  /**
   *  حذفِ دسته‌بندی.
   *
   *  کالاهای همان دسته حذف نمی‌شوند — فقط دیگر با این دسته فیلتر نمی‌شوند،
   *  عیناً همان کاری که نسخهٔ وب می‌کند. نامِ دسته روی خودِ کالا می‌ماند تا
   *  اگر کاربر دوباره همان دسته را ساخت، کالاها سرِ جایشان برگردند.
   */
  fun removeCategory(d: ShopData, name: String): Result {
    if (!d.productCategories.contains(name)) return Result.Failed("این دسته‌بندی وجود ندارد")
    return Result.Ok(d.copy(productCategories = d.productCategories.filterNot { it == name }), name)
  }

  /* ------------------------------ ریزه‌کاری ------------------------------ */

  private fun withCategory(list: List<String>, value: String): List<String> {
    val v = value.trim()
    return if (v.isEmpty() || list.contains(v)) list else list + v
  }

  private fun withUnit(list: List<String>, value: String): List<String> {
    val v = value.trim()
    return if (v.isEmpty() || list.contains(v)) list else list + v
  }
}
