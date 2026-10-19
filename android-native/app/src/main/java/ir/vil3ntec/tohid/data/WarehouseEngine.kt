package ir.vil3ntec.tohid.data

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

  /** نامِ کالا برای سنجشِ تکرار: فاصله‌های اضافه و بزرگی/کوچکیِ حروف حساب نمی‌شود */
  private fun nameKey(name: String): String =
    name.trim().replace(Regex("\\s+"), " ").replace('ي', 'ی').replace('ك', 'ک').lowercase()

  /** فهرستِ خوانای تغییرها — قیمتِ خرید جدا در `price_change` ثبت می‌شود */
  internal fun productChanges(a: Product, b: Product): List<String> = buildList {
    if (a.name != b.name) add("نام «${a.name}» ⇐ «${b.name}»")
    if (a.salePrice != b.salePrice) add("قیمت فروش ${money(a.salePrice)} ⇐ ${money(b.salePrice)}")
    if (a.category != b.category) add("دسته «${a.category}» ⇐ «${b.category}»")
    if (a.unit != b.unit) add("واحد «${a.unit}» ⇐ «${b.unit}»")
    if (a.minStock != b.minStock) add("حداقل موجودی ${money(a.minStock)} ⇐ ${money(b.minStock)}")
    if (a.active != b.active) add(if (b.active) "فعال شد" else "غیرفعال شد")
    if (a.barcodes != b.barcodes) add("بارکد عوض شد")
  }

  /** تاریخچهٔ یک کالا: ویرایش‌ها و تغییرِ قیمتِ خرید، تازه‌ترین اول */
  fun productHistory(d: ShopData, productId: String): List<AuditEntry> =
    d.auditLog.filter { it.refId == productId && (it.type == "product_edit" || it.type == "price_change") }
      .sortedByDescending { it.createdAt }

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

    val barcode = draft.barcode.trim()
    // بارکدِ تکراری، کالای قبلی را از دسترسِ اسکنر بیرون می‌برد
    if (barcode.isNotEmpty() && ShopStore.barcodeIndex(d).containsKey(barcode)) {
      return Result.Failed("این بارکد قبلاً برای کالای دیگری ثبت شده")
    }

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
      barcodes = if (barcode.isEmpty()) emptyList() else listOf(barcode),
      createdAt = now,
      subcategory = draft.subcategory?.trim().orEmpty(),
      supplierId = draft.supplierId.orEmpty(),
      active = draft.active ?: true,
      saleUnit = draft.saleUnit?.trim().orEmpty(),
      unitFactor = if (draft.saleUnit.isNullOrBlank()) 1.0 else draft.unitFactor ?: 1.0,
    )

    return Result.Ok(
      d.copy(
        products = d.products + product,
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

    val barcode = draft.barcode.trim()
    // بارکدِ تکراری فقط وقتی ایراد دارد که مالِ کالای دیگری باشد
    val owner = if (barcode.isEmpty()) null else ShopStore.barcodeIndex(d)[barcode]
    if (owner != null && owner != id) return Result.Failed("این بارکد قبلاً برای کالای دیگری ثبت شده")

    val updated = existing.copy(
      name = name,
      category = draft.category,
      unit = draft.unit,
      purchasePrice = draft.purchasePrice,
      salePrice = draft.salePrice,
      minStock = if (draft.minStock.isNaN()) 0.0 else draft.minStock,
      barcodes = if (barcode.isEmpty()) emptyList() else listOf(barcode),
      subcategory = draft.subcategory?.trim() ?: existing.subcategory,
      supplierId = draft.supplierId ?: existing.supplierId,
      active = draft.active ?: existing.active,
      saleUnit = draft.saleUnit?.trim() ?: existing.saleUnit,
      unitFactor = when {
        (draft.saleUnit ?: existing.saleUnit).isBlank() -> 1.0
        else -> draft.unitFactor ?: existing.unitFactor
      },
    )

    // تغییرِ قیمتِ خرید در تاریخچه می‌ماند — سودِ گذشته نباید عوض شود
    var history = d.priceHistory
    var audit = d.auditLog
    if (existing.purchasePrice != draft.purchasePrice) {
      history = history + PriceChange(
        id = newId(),
        productId = id,
        oldPrice = existing.purchasePrice,
        newPrice = draft.purchasePrice,
        date = today,
        createdAt = now,
      )
      audit = audit + AuditEntry(
        id = newId(),
        type = "price_change",
        date = today,
        refId = id,
        notes = "تغییر قیمت خرید از ${money(existing.purchasePrice)} به ${money(draft.purchasePrice)} افغانی",
        createdAt = now,
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
        notes = "ویرایش «${existing.name}»: ${changes.joinToString("، ")}",
        createdAt = now,
      )
    }

    return Result.Ok(
      d.copy(
        products = d.products.map { if (it.id == id) updated else it },
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
