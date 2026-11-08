package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrPurchaseUnits
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.core.product.PurchaseUnits
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.PoForms
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.formatDate
import ir.vil3ntec.tohid.latinDigits
import ir.vil3ntec.tohid.money
import ir.vil3ntec.tohid.qty
import ir.vil3ntec.tohid.todayIso

/** نوشته‌های فرمِ «ثبت خرید» — همه متنِ خامِ کادرها (عدد بی جداکننده) */
internal data class PurchaseForm(
  val supplierId: String? = null,
  val productId: String? = null,
  val quantity: String = "",
  val price: String = "",
  val paid: String = "",
  val date: String = "",
  val notes: String = "",
  /** PUR-05: ردیفِ دوم به بعدِ همین فاکتور (ردیفِ نخست همان کادرهای بالاست)؛ خالی ⇒ همان خریدِ تک‌ردیفهٔ امروز */
  val more: List<PurchaseRow> = emptyList(),
  /** PUR-05: تاریخِ انقضای ردیفِ نخست، همان‌طور که تایپ شد (شمسی؛ خالی = بی انقضا) */
  val expiry: String = "",
  /**
   *  PUR-05/PUR-06: هزینهٔ جانبیِ کلِ فاکتور (کرایه، گمرک) — روی بهای ردیف‌ها، نه بدهیِ تأمین‌کننده؛ خالی = ندارد.
   *  فقط با موتورِ تازهٔ موجودی (`StockLedger.isOn`، همان شرطِ کامپیوتر) دیده می‌شود.
   */
  val landed: String = "",
  /** PUR-05/PUR-07: سررسیدِ پرداختِ کلِ فاکتور، همان‌طور که تایپ شد (شمسی؛ خالی = ندارد)؛ همان شرطِ [landed] */
  val due: String = "",
  /**
   *  PRD-07: واحدِ خریدِ ردیفِ نخست (نامِ یکی از `Product.purchaseUnits`؛ [quantity] و [price] به همان واحد)؛ `null` = واحدِ
   *  پایه، همان پیشین. فقط با `ShopPolicy.productTools` پُر می‌شود (صفحه با پرچمِ خاموش پاکش می‌کند).
   */
  val unitName: String? = null,
)

/** یک ردیفِ افزوده‌شدهٔ فاکتورِ خرید (PUR-05) — همان متنِ خامِ کادرها؛ [expiry] مثلِ ردیفِ نخست */
internal data class PurchaseRow(
  val productId: String? = null, val quantity: String = "", val price: String = "", val expiry: String = "",
  /** PRD-07: همان [PurchaseForm.unitName] برای این ردیف */
  val unitName: String? = null,
)

/**
 *  فرمِ «ثبت خرید» (`purchasing/new?supplier=`) — منطقِ خالص، زیرِ آزمون. جمع و باقی فقط برای
 *  نمایشِ زنده‌اند (همان گِردکردنِ `LedgerEngine.addPurchase`)؛ ثبت و همهٔ اثرهایش (انبار، بدهی،
 *  تاریخچهٔ قیمت) کارِ خودِ موتور است.
 */
internal object PurchaseFormModel {

  fun initial(supplierId: String?, today: String) = PurchaseForm(supplierId = supplierId?.takeIf { it.isNotBlank() }, date = today)

  private fun num(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

  /** سقفِ ردیف‌های یک فاکتورِ گوشی (ردیفِ نخست هم شمرده می‌شود) */
  const val MAX_ROWS = 50

  private fun rowTotal(quantity: String, price: String): Double = Math.round((num(quantity) ?: 0.0) * (num(price) ?: 0.0)).toDouble()

  /** جمعِ خرید = جمعِ مقدار × قیمتِ هر ردیف، هر ردیف گردشده مثلِ موتور (`PurchaseBook.lineTotal`)؛ تک‌ردیفه همان امروز */
  fun total(f: PurchaseForm): Double = rowTotal(f.quantity, f.price) + f.more.sumOf { rowTotal(it.quantity, it.price) }

  /**
   *  PUR-05: فاکتورِ دفترِ خرید (`PurchaseBook.record`)؟ چندردیفه، یا تک‌ردیفه با هزینهٔ جانبی (که فقط `PurchaseBook` روی بها
   *  پخش می‌کند). وگرنه ⇒ همان `LedgerEngine.addPurchase` ِ امروز (سررسید را خودش می‌گیرد).
   */
  fun multi(f: PurchaseForm): Boolean = f.more.isNotEmpty() || (landedTotal(f) ?: 0.0) > 0 || unitOrNull(f.unitName) != null

  private fun unitOrNull(name: String?): String? = name?.trim()?.ifEmpty { null }

  /** PUR-06: هزینهٔ جانبی ⇒ عدد؛ خالی ⇒ ۰؛ نادرست یا منفی ⇒ null (خطای درجا، ثبت نمی‌شود) */
  fun landedTotal(f: PurchaseForm): Double? =
    if (f.landed.isBlank()) 0.0 else num(f.landed)?.takeIf { !it.isNaN() && it >= 0 }

  /** PUR-07: سررسیدِ تایپ‌شده ⇒ `yyyy-MM-dd`؛ خالی ⇒ "" (ندارد)؛ نادرست ⇒ null — همان قاعدهٔ کامپیوتر (`Jalali.parseTyped`) */
  fun dueIso(f: PurchaseForm): String? = expiryIso(f.due)

  /** سررسیدِ پیش‌نویس برای موتور: ندارد یا نادرست ⇒ null (نادرست پیش‌تر خطای درجا دارد) */
  private fun dueOrNull(f: PurchaseForm): String? = dueIso(f)?.ifEmpty { null }

  /** ردیفِ تازهٔ خالی؛ پس از سقف همان فرم */
  fun addRow(f: PurchaseForm): PurchaseForm = if (f.more.size + 1 >= MAX_ROWS) f else f.copy(more = f.more + PurchaseRow())

  fun removeRow(f: PurchaseForm, index: Int): PurchaseForm =
    if (index !in f.more.indices) f else f.copy(more = f.more.filterIndexed { i, _ -> i != index })

  fun setRow(f: PurchaseForm, index: Int, row: PurchaseRow): PurchaseForm =
    if (index !in f.more.indices) f else f.copy(more = f.more.mapIndexed { i, r -> if (i == index) row else r })

  /** کالای ردیفِ افزوده؛ قیمتِ خالی ⇒ قیمتِ خریدِ فعلیِ همان کالا (مثلِ [pickProduct]) */
  fun pickRowProduct(f: PurchaseForm, index: Int, product: Product, unitsOn: Boolean = false): PurchaseForm {
    val r = f.more.getOrNull(index) ?: return f
    return setRow(f, index, picked(r, product, defaultUnit(product, f.supplierId, unitsOn)))
  }

  /** باقی (نسیه) = جمع − پرداخت‌شده، نه کمتر از صفر */
  fun remaining(f: PurchaseForm): Double = (total(f) - (num(f.paid) ?: 0.0)).coerceAtLeast(0.0)

  /**
   *  کالا که انتخاب شد و قیمت خالی بود ⇒ قیمتِ خریدِ فعلیِ همان کالا (قابلِ عوض کردن).
   *  PRD-07 ([unitsOn] = `ShopPolicy.productTools`): واحدی که تأمین‌کنندهٔ پیش‌فرضش همین تأمین‌کننده است پیش‌گزیده می‌شود
   *  (`PurchaseUnits.defaultFor`، همان فاکتورِ کامپیوتر) و قیمتِ پیشنهادی به همان واحد؛ نبود ⇒ واحدِ پایه.
   */
  fun pickProduct(f: PurchaseForm, product: Product, unitsOn: Boolean = false): PurchaseForm =
    withFirst(f, picked(first(f), product, defaultUnit(product, f.supplierId, unitsOn)))

  private fun priceText(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

  /* ── PRD-07: واحدِ خریدِ ردیف ──────────────────────────────────────── */

  /** ردیفِ نخست به شکلِ یک ردیف (و برگشت) — تا هر کارِ ردیف یک بار نوشته شود */
  private fun first(f: PurchaseForm) = PurchaseRow(f.productId, f.quantity, f.price, f.expiry, f.unitName)

  private fun withFirst(f: PurchaseForm, r: PurchaseRow) =
    f.copy(productId = r.productId, quantity = r.quantity, price = r.price, expiry = r.expiry, unitName = r.unitName)

  private fun row(f: PurchaseForm, index: Int): PurchaseRow? = if (index < 0) first(f) else f.more.getOrNull(index)

  private fun put(f: PurchaseForm, index: Int, r: PurchaseRow): PurchaseForm = if (index < 0) withFirst(f, r) else setRow(f, index, r)

  /** کالا روی ردیف با واحدِ [unit]؛ قیمتِ خالی ⇒ قیمتِ خریدِ کالا به همان واحد (`PoForms.repriced`، همان حسابِ سفارشِ خرید) */
  private fun picked(r: PurchaseRow, p: Product, unit: String?): PurchaseRow {
    val price = if (r.price.isBlank() && p.purchasePrice > 0) priceText(p.purchasePrice).let { base ->
      if (unit == null) base else PoForms.repriced(p, base, null, unit) ?: base
    } else r.price
    return r.copy(productId = p.id, price = price, unitName = unit)
  }

  /** واحدِ پیش‌فرضِ همین تأمین‌کننده (`PurchaseUnits.defaultFor`)؛ پرچمِ خاموش ⇒ همیشه واحدِ پایه */
  private fun defaultUnit(p: Product, supplierId: String?, unitsOn: Boolean): String? =
    if (!unitsOn) null else PurchaseUnits.defaultFor(p.purchaseUnits?.map(ShopStore::coreOf), supplierId)?.name

  /** واحدهای خریدِ کالای ردیف (همان `PoForms.purchaseUnits`)؛ خالی ⇒ صفحه انتخاب را نمی‌کشد */
  fun units(d: ShopData, productId: String?): List<PurchaseUnits.Item> = PoForms.purchaseUnits(d, productId.orEmpty())

  /**
   *  ردیفِ [index] (−۱ = ردیفِ نخست) به واحدِ [unitName] (`null` = پایه)؛ مقدار همان عدد و فی به نسبتِ ضریب
   *  (`PoForms.repriced`) تا فیِ هر واحدِ پایه همان بماند. واحدی که روی کالا نیست ⇒ همان فرم.
   */
  fun pickUnit(f: PurchaseForm, d: ShopData, index: Int, unitName: String?): PurchaseForm {
    val r = row(f, index) ?: return f
    val p = d.products.find { it.id == r.productId } ?: return f
    val to = PurchaseUnits.find(p.purchaseUnits?.map(ShopStore::coreOf), unitName)
    if (unitName != null && to == null) return f
    return put(f, index, r.copy(unitName = to?.name, price = PoForms.repriced(p, r.price, r.unitName, to?.name) ?: r.price))
  }

  /** همهٔ ردیف‌ها به واحدِ پایه (پرچمِ خاموش): فی به پایه برمی‌گردد و هیچ نامِ واحدی نمی‌ماند */
  fun withoutUnits(f: PurchaseForm, d: ShopData): PurchaseForm {
    var out = pickUnit(f, d, -1, null)
    f.more.indices.forEach { out = pickUnit(out, d, it, null) }
    return out.copy(unitName = null, more = out.more.map { it.copy(unitName = null) })
  }

  fun hasUnits(f: PurchaseForm): Boolean = f.unitName != null || f.more.any { it.unitName != null }

  /**
   *  زیرِ ردیفِ به واحدِ خرید: «= ۲۴ دانه · بهای هر دانه ۴۱٫۶۷» — همان حسابی که ثبت می‌کند: جمعِ ردیف (`PurchaseBook.lineTotal`)
   *  ⇐ `PurchaseBook.inUnitOf` ⇐ `PurchaseUnits.baseCost`. ردیفِ پایه یا عددِ هنوز نادرست ⇒ null.
   */
  fun unitLine(d: ShopData, productId: String?, unitName: String?, quantity: String, price: String): String? {
    val name = unitOrNull(unitName) ?: return null
    val p = d.products.find { it.id == productId } ?: return null
    val u = inUnit(p, name, quantity, price) ?: return null
    val bc = runCatching { PurchaseUnits.baseCost(u.qtyMilli, u.factorMilli, u.totalMinor) }.getOrNull() ?: return null
    return StrPurchaseUnits.lineBase(qty(bc.baseQtyMilli / 1000.0), p.unit, money(bc.unitCostMinor / 100.0))
  }

  private fun inUnit(p: Product, name: String, quantity: String, price: String): LedgerEngine.InUnit? {
    val q = num(quantity)?.takeIf { it > 0 } ?: return null
    val pr = num(price)?.takeIf { it >= 0 } ?: return null
    return PurchaseBook.inUnitOf(p, name, q, PurchaseBook.lineTotal(PurchaseBook.Line(p.id, q, pr)))
  }

  /** خطای واحدِ ردیف — همان `PurchaseBook.unitWhy` ِ ثبت؛ مقدار یا قیمتِ نادرست خطای خودشان را دارند */
  private fun unitError(d: ShopData?, productId: String?, unitName: String?, quantity: String, price: String): String? {
    val name = unitOrNull(unitName) ?: return null
    val p = d?.products?.find { it.id == productId } ?: return null
    val q = num(quantity)?.takeIf { it > 0 } ?: return null
    if ((num(price) ?: -1.0) < 0) return null
    return if (inUnit(p, name, quantity, price) == null) PurchaseBook.unitWhy(p, name, q) else null
  }

  sealed interface Scan {
    /** [unit] نامِ واحدِ خریدی که بارکدش خوانده شد؛ بارکدِ خودِ کالا ⇒ null */
    data class Added(val form: PurchaseForm, val name: String, val unit: String?) : Scan
    data class Unknown(val code: String) : Scan
    /** کالای تازه و فاکتور به [MAX_ROWS] رسیده */
    data object Full : Scan
  }

  private fun sameUnit(a: String?, b: String?): Boolean =
    if (a == null || b == null) a == b else PurchaseUnits.key(a) == PurchaseUnits.key(b)

  private fun plusOne(text: String): String = PoForms.num((num(text)?.takeIf { it > 0 } ?: 0.0) + 1.0)

  /**
   *  بارکدخوان روی فرم: کالای همان بارکد به ردیفِ خودش (همان کالا و همان واحد ⇒ یکی بیشتر)، وگرنه ردیفِ خالی یا ردیفِ تازه.
   *  PRD-07 ([units] = `ShopStore.purchaseUnitIndex`، فقط با پرچم؛ null ⇒ همان کالا به واحدِ پایه): بارکدِ یک واحدِ خرید
   *  همان واحد را برمی‌گزیند؛ بارکدِ خودِ کالا ⇒ واحدِ پیش‌فرضِ همین تأمین‌کننده یا پایه (همان فاکتورِ کامپیوتر).
   *  [index] = `ShopStore.barcodeIndex`؛ رقمِ فارسیِ بارکدخوان هم پذیرفته.
   */
  fun scan(f: PurchaseForm, d: ShopData, raw: String, index: Map<String, String>, units: PurchaseUnits.Index? = null): Scan {
    val code = raw.trim()
    val hit = if (code.isEmpty()) null else units?.find(code)
    val pid = hit?.productId ?: index[code] ?: index[latinDigits(code)]
    val p = d.products.find { it.id == pid } ?: return Scan.Unknown(code)
    val unit = hit?.unit?.name ?: defaultUnit(p, f.supplierId, units != null)
    val rows = listOf(first(f)) + f.more
    val same = rows.indexOfFirst { it.productId == p.id && sameUnit(it.unitName, unit) }
    val at = when {
      same >= 0 -> same - 1
      f.productId == null -> -1
      else -> f.more.indexOfFirst { it.productId == null }.takeIf { it >= 0 }
        ?: (if (f.more.size + 1 >= MAX_ROWS) return Scan.Full else f.more.size)
    }
    val base = if (at >= f.more.size) addRow(f) else f
    val r = row(base, at) ?: return Scan.Full
    val next = if (same >= 0) r.copy(quantity = plusOne(r.quantity)) else picked(r, p, unit).copy(quantity = r.quantity.ifBlank { "1" })
    return Scan.Added(put(base, at, next), p.name, hit?.unit?.name)
  }

  /** خطای یک ردیفِ افزوده */
  data class RowErrors(
    val product: String? = null, val quantity: String? = null, val price: String? = null, val expiry: String? = null,
    /** PRD-07 */
    val unit: String? = null,
  ) {
    val any: Boolean get() = listOf(product, quantity, price, expiry, unit).any { it != null }
  }

  data class Errors(
    val supplier: String? = null,
    val product: String? = null,
    val quantity: String? = null,
    val price: String? = null,
    val paid: String? = null,
    /** هم‌اندازهٔ `PurchaseForm.more` */
    val rows: List<RowErrors> = emptyList(),
    val expiry: String? = null,
    /** PUR-06/PUR-07 */
    val landed: String? = null,
    val due: String? = null,
    /** PRD-07: واحدِ خریدِ ردیفِ نخست (همان سنجشِ ثبت) */
    val unit: String? = null,
  ) {
    val any: Boolean get() = listOf(supplier, product, quantity, price, paid, expiry, landed, due, unit).any { it != null } || rows.any { it.any }
  }

  private fun quantityError(text: String): String? = num(text).let { if (it == null || it <= 0) StrMoney.invalidNumber else null }
  private fun priceError(text: String): String? = num(text).let { if (it == null || it < 0) StrMoney.invalidNumber else null }

  /** PUR-05: انقضای تایپ‌شده ⇒ `yyyy-MM-dd`؛ خالی ⇒ "" (بی انقضا)؛ نادرست ⇒ null (همان قاعدهٔ «ورودِ کالا» ِ انبار) */
  fun expiryIso(text: String): String? = if (text.isBlank()) "" else Jalali.parseTyped(text)

  private fun expiryError(text: String): String? = if (expiryIso(text) == null) StrStock.errExpiry else null

  /** خطای درجای هر کادر — همان شرط‌های موتور، پیش از زدن؛ [d] (PRD-07) برای سنجشِ واحدِ خریدِ ردیف‌ها */
  fun errors(f: PurchaseForm, d: ShopData? = null): Errors = Errors(
    supplier = if (f.supplierId == null) StrMoney.pickSupplier else null,
    product = if (f.productId == null) StrMoney.pickItem else null,
    quantity = quantityError(f.quantity),
    price = priceError(f.price),
    paid = if (f.paid.isNotBlank() && (num(f.paid) ?: -1.0) < 0) StrMoney.invalidNumber else null,
    rows = f.more.map { r ->
      RowErrors(
        product = if (r.productId == null) StrMoney.pickItem else null,
        quantity = quantityError(r.quantity),
        price = priceError(r.price),
        expiry = expiryError(r.expiry),
        unit = unitError(d, r.productId, r.unitName, r.quantity, r.price),
      )
    },
    expiry = expiryError(f.expiry),
    landed = if (landedTotal(f) == null) StrPurchasePages.landedBad else null,
    //  همان دو شرطِ موتور (`PurchaseBook.record`/`LedgerEngine.addPurchase`): تاریخِ درست و نه پیش از تاریخِ خرید
    due = when (val iso = dueIso(f)) {
      null -> StrPurchasePages.dueBad(formatDate(f.date.ifBlank { todayIso() }))
      "" -> null
      else -> if (f.date.isNotBlank() && iso < f.date) PurchaseBook.DUE_BEFORE_DATE else null
    },
    unit = unitError(d, f.productId, f.unitName, f.quantity, f.price),
  )

  /** چیزی نوشته یا عوض شده ⇒ «برگشت» اول «دور ریخته شود؟» */
  fun dirty(f: PurchaseForm, initial: PurchaseForm): Boolean = f != initial

  /** پیش‌نویسِ موتور — واحد همان واحدِ کالا (مثلِ امروز) */
  fun draft(f: PurchaseForm, unit: String): LedgerEngine.PurchaseDraft = LedgerEngine.PurchaseDraft(
    supplierId = f.supplierId.orEmpty(),
    productId = f.productId.orEmpty(),
    quantity = num(f.quantity) ?: 0.0,
    unit = unit,
    purchasePrice = num(f.price) ?: -1.0,
    paidAmount = num(f.paid) ?: 0.0,
    date = f.date,
    notes = f.notes.trim(),
    //  PUR-05: انقضای محموله روی ورودِ انبار (FEFO و هشدارِ انقضا)؛ خالی ⇒ همان خریدِ امروز
    expiryDate = expiryIso(f.expiry).orEmpty(),
    //  PUR-07: سررسیدِ پرداخت (یادآورِ بدهی)؛ خالی ⇒ همان خریدِ امروز
    dueDate = dueOrNull(f),
  )

  /**
   *  PUR-05: فاکتور برای `PurchaseBook.record` — هزینهٔ جانبی (PUR-06، روی بهای ردیف‌ها پخش می‌شود) و سررسید (PUR-07)؛
   *  [on] = موتورِ تازهٔ موجودی (همان شرطِ کامپیوتر): خاموش ⇒ ۰ و بی سررسید، همان فاکتورِ پیشین.
   */
  data class Extras(val landedTotal: Double = 0.0, val dueDate: String? = null)

  fun extras(f: PurchaseForm, on: Boolean): Extras =
    if (!on) Extras() else Extras(landedTotal(f) ?: 0.0, dueOrNull(f))

  /**
   *  PUR-05: ردیف‌های فاکتورِ چندردیفه برای `PurchaseBook.record` (همان فاکتورِ چندردیفهٔ کامپیوتر): ردیفِ نخست، سپس
   *  ردیف‌های افزوده به همان ترتیب؛ واحد همان واحدِ کالا. پرداخت میانِ ردیف‌ها به ترتیب پخش می‌شود (کارِ `PurchaseBook`).
   *  PRD-07: ردیفِ به واحدِ خرید ⇒ `Line.unitName` (موجودی = مقدار × ضریب، ارزش = جمعِ دقیقِ ردیف)؛ بی واحد همان ردیفِ پیشین.
   */
  fun lines(f: PurchaseForm, unitOf: (String) -> String): List<PurchaseBook.Line> =
    (listOf(first(f)) + f.more).map { r ->
      val id = r.productId.orEmpty()
      PurchaseBook.Line(
        productId = id, quantity = num(r.quantity) ?: 0.0, price = num(r.price) ?: -1.0, unit = unitOf(id),
        expiryDate = expiryIso(r.expiry).orEmpty(), unitName = unitOrNull(r.unitName),
      )
    }

  /** پرداختِ کلِ فاکتور (خالی = نسیه) */
  fun paid(f: PurchaseForm): Double = num(f.paid) ?: 0.0
}
