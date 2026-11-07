package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchasePages
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.PurchaseBook
import ir.vil3ntec.tohid.formatDate
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
)

/** یک ردیفِ افزوده‌شدهٔ فاکتورِ خرید (PUR-05) — همان متنِ خامِ کادرها؛ [expiry] مثلِ ردیفِ نخست */
internal data class PurchaseRow(val productId: String? = null, val quantity: String = "", val price: String = "", val expiry: String = "")

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
  fun multi(f: PurchaseForm): Boolean = f.more.isNotEmpty() || (landedTotal(f) ?: 0.0) > 0

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
  fun pickRowProduct(f: PurchaseForm, index: Int, product: Product): PurchaseForm {
    val r = f.more.getOrNull(index) ?: return f
    return setRow(f, index, r.copy(
      productId = product.id,
      price = if (r.price.isBlank() && product.purchasePrice > 0) priceText(product.purchasePrice) else r.price,
    ))
  }

  /** باقی (نسیه) = جمع − پرداخت‌شده، نه کمتر از صفر */
  fun remaining(f: PurchaseForm): Double = (total(f) - (num(f.paid) ?: 0.0)).coerceAtLeast(0.0)

  /** کالا که انتخاب شد و قیمت خالی بود ⇒ قیمتِ خریدِ فعلیِ همان کالا (قابلِ عوض کردن) */
  fun pickProduct(f: PurchaseForm, product: Product): PurchaseForm =
    f.copy(
      productId = product.id,
      price = if (f.price.isBlank() && product.purchasePrice > 0) priceText(product.purchasePrice) else f.price,
    )

  private fun priceText(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

  /** خطای یک ردیفِ افزوده */
  data class RowErrors(val product: String? = null, val quantity: String? = null, val price: String? = null, val expiry: String? = null) {
    val any: Boolean get() = listOf(product, quantity, price, expiry).any { it != null }
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
  ) {
    val any: Boolean get() = listOf(supplier, product, quantity, price, paid, expiry, landed, due).any { it != null } || rows.any { it.any }
  }

  private fun quantityError(text: String): String? = num(text).let { if (it == null || it <= 0) StrMoney.invalidNumber else null }
  private fun priceError(text: String): String? = num(text).let { if (it == null || it < 0) StrMoney.invalidNumber else null }

  /** PUR-05: انقضای تایپ‌شده ⇒ `yyyy-MM-dd`؛ خالی ⇒ "" (بی انقضا)؛ نادرست ⇒ null (همان قاعدهٔ «ورودِ کالا» ِ انبار) */
  fun expiryIso(text: String): String? = if (text.isBlank()) "" else Jalali.parseTyped(text)

  private fun expiryError(text: String): String? = if (expiryIso(text) == null) StrStock.errExpiry else null

  /** خطای درجای هر کادر — همان شرط‌های موتور، پیش از زدن */
  fun errors(f: PurchaseForm): Errors = Errors(
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
   */
  fun lines(f: PurchaseForm, unitOf: (String) -> String): List<PurchaseBook.Line> =
    (listOf(PurchaseRow(f.productId, f.quantity, f.price, f.expiry)) + f.more).map { r ->
      val id = r.productId.orEmpty()
      PurchaseBook.Line(
        productId = id, quantity = num(r.quantity) ?: 0.0, price = num(r.price) ?: -1.0, unit = unitOf(id),
        expiryDate = expiryIso(r.expiry).orEmpty(),
      )
    }

  /** پرداختِ کلِ فاکتور (خالی = نسیه) */
  fun paid(f: PurchaseForm): Double = num(f.paid) ?: 0.0
}
