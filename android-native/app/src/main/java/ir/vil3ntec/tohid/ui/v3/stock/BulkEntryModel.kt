package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine

/** یک ردیفِ «ورودِ چند قلم»: کالای موجود یا محصولِ تازه (نام، دسته، بارکد)، مقدار و قیمت */
data class BulkRow(
  val key: Int,
  val productId: String = "",
  val newProduct: Boolean = false,
  val newName: String = "",
  val newCategory: String = "",
  val newBarcode: String = "",
  val qty: String = "",
  val price: String = "",
  val unit: String = "",
  /** بارکدِ اسکن‌شده‌ای که در دکان نبود — راهنمای «نامش را بنویسید» */
  val scanMiss: Boolean = false,
) {
  /** ردیفِ دست‌نخورده نادیده گرفته می‌شود، خطا نیست */
  val untouched: Boolean get() = !newProduct && productId.isBlank() && qty.isBlank() && price.isBlank()

  /** همان شرطِ آمادگیِ صفحهٔ پیشین: کالا (یا نامِ تازه)، مقدارِ مثبت، قیمتِ نامنفی */
  val ready: Boolean
    get() = (if (newProduct) newName.isNotBlank() else productId.isNotBlank()) &&
      (qty.toDoubleOrNull() ?: 0.0) > 0 && (price.toDoubleOrNull() ?: -1.0) >= 0

  val amount: Double get() = qty.toDoubleOrNull() ?: 0.0
  val value: Double get() = amount * (price.toDoubleOrNull() ?: 0.0)

  fun pick(p: Product): BulkRow = copy(
    productId = p.id,
    newProduct = false,
    scanMiss = false,
    unit = p.unit.ifBlank { unit },
    price = if (price.isBlank() && p.purchasePrice > 0) EntryForm.plainNumber(p.purchasePrice) else price,
  )

  fun asNew(barcode: String = newBarcode): BulkRow = copy(productId = "", newProduct = true, newBarcode = barcode)
}

/** حالتِ خالصِ صفحهٔ «ورودِ چند قلم» (آزمون: `BulkEntryModelTest`) */
data class BulkEntryState(val rows: List<BulkRow>, val date: String, val seq: Int) {
  val ready: List<BulkRow> get() = rows.filter { it.ready }
  val dirty: Boolean get() = rows.any { !it.untouched }

  fun update(key: Int, change: (BulkRow) -> BulkRow) = copy(rows = rows.map { if (it.key == key) change(it) else it })

  fun add(defaultUnit: String) = copy(rows = rows + BulkRow(key = seq, unit = defaultUnit), seq = seq + 1)

  fun duplicate(key: Int): BulkEntryState {
    val i = rows.indexOfFirst { it.key == key }.takeIf { it >= 0 } ?: return this
    val copy = rows[i].copy(key = seq, newBarcode = "", scanMiss = false)
    return copy(rows = rows.toMutableList().also { it.add(i + 1, copy) }, seq = seq + 1)
  }

  fun remove(key: Int) = if (rows.size <= 1) this else copy(rows = rows.filterNot { it.key == key })

  /**
   *  یک اسکن ⇒ یک ردیف: کالای شناخته ⇐ ردیفِ همان کالا؛ ناشناس ⇐ ردیفِ «محصولِ تازه» با
   *  همین بارکد. اگر آخرین ردیف هنوز دست‌نخورده است، همان پر می‌شود.
   */
  fun scanned(d: ShopData, code: String, defaultUnit: String): BulkEntryState {
    val clean = code.trim()
    if (clean.isEmpty()) return this
    val base = if (rows.lastOrNull()?.untouched == true) this else add(defaultUnit)
    val key = base.rows.last().key
    val hit = ShopStore.barcodeIndex(d)[clean]?.let { id -> d.products.find { it.id == id } }
    return base.update(key) { row -> if (hit != null) row.pick(hit) else row.asNew(clean).copy(scanMiss = true) }
  }

  /** نخستین ردیفِ نیمه‌کاره (شمارهٔ یک‌پایه) یا null */
  fun brokenRow(): Int? = rows.indexOfFirst { !it.untouched && !it.ready }.takeIf { it >= 0 }?.plus(1)

  /** ردیف‌های آماده برای `WarehouseEngine.addEntries` — همان ساختِ صفحهٔ پیشین */
  fun entries(d: ShopData): List<WarehouseEngine.BulkEntry> = ready.map { row ->
    WarehouseEngine.BulkEntry(
      entry = WarehouseEngine.EntryDraft(
        productId = row.productId,
        units = row.amount,
        unit = row.unit,
        price = row.price.toDoubleOrNull() ?: 0.0,
        date = date,
      ),
      newProduct = if (!row.newProduct) null else WarehouseEngine.ProductDraft(
        name = row.newName,
        category = row.newCategory,
        unit = row.unit.ifBlank { d.productUnits.firstOrNull() ?: DEFAULT_UNIT },
        purchasePrice = row.price.toDoubleOrNull() ?: 0.0,
        salePrice = 0.0,
        barcode = row.newBarcode,
      ),
    )
  }

  /** پیش از ثبت: پیامِ ردیفِ نیمه‌کاره یا «هیچ ردیفِ کاملی»؛ null ⇒ آماده */
  fun problem(): String? = brokenRow()?.let { StrStock.errRow(it) } ?: if (ready.isEmpty()) StrStock.errNoRows else null

  companion object {
    /** همان پیش‌فرضِ صفحهٔ پیشین وقتی دکان هیچ واحدی ندارد — دادهٔ دفتر است، نه نوشتهٔ صفحه */
    const val DEFAULT_UNIT = "عدد"

    fun start(d: ShopData, today: String) =
      BulkEntryState(listOf(BulkRow(key = 0, unit = defaultUnit(d))), today, seq = 1)

    fun defaultUnit(d: ShopData): String = d.products.firstOrNull()?.unit.orEmpty()
  }
}
