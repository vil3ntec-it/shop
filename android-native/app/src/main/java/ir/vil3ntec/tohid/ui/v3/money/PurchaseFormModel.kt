package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrPurchaseWorkflow
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.core.i18n.StrPurchaseFx
import ir.vil3ntec.tohid.core.pay.FxTill
import ir.vil3ntec.tohid.core.purchase.PurchaseFx
import ir.vil3ntec.tohid.data.SafeCurrencies
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
  /**
   *  e-rows-63b: «پرداخت» با سه گزینه — همه را دادم / بخشی را دادم ([paid] تایپ‌شده) / بعداً می‌دهم (با سررسید). `null` = کادرِ
   *  تایپیِ پیشین (خالی = نسیه) — فرمِ تازه ([PurchaseFormModel.initial]) همیشه یکی را دارد.
   */
  val pay: PurchasePay? = null,
  /** e-rows-63b: «الان رسیده؟ نه» ⇒ سفارشِ خرید (سرور، `shop.purchase_orders`)، نه فاکتورِ دفتر؛ کالا با «دریافتِ کالا» می‌آید */
  val later: Boolean = false,
  /**
   *  PUR-11 — ارزِ فاکتور (`null` = افغانی، همان فرمِ پیشین): قیمت‌ها و «پرداخت‌شده» به همین ارز تایپ می‌شوند و [rate] (پول برای
   *  [per] واحدِ ارز، متنِ خامِ کادر) یک بار برای کلِ فاکتور.
   */
  val currency: String? = null,
  val rate: String = "",
  val per: Long = 1,
  val packaging: PurchasePackaging = PurchasePackaging(),
)

internal data class PurchasePackaging(
  val carton: Boolean = false,
  val pieces: String = "",
  val production: String = "",
  val barcode: String = "",
) {
  fun piecesPerCarton(): Long? =
    pieces.trim().toLongOrNull()?.takeIf { it in 1..PurchaseUnits.MAX_FACTOR_MILLI / PurchaseUnits.MILLI }
  fun factor(): Long? = if (carton) piecesPerCarton()?.times(PurchaseUnits.MILLI) else null
}

/** «پرداخت» ِ فرمِ خرید (e-rows-63b) */
internal enum class PurchasePay { FULL, PART, LATER }

/** یک ردیفِ افزوده‌شدهٔ فاکتورِ خرید (PUR-05) — همان متنِ خامِ کادرها؛ [expiry] مثلِ ردیفِ نخست */
internal data class PurchaseRow(
  val productId: String? = null, val quantity: String = "", val price: String = "", val expiry: String = "",
  /** PRD-07: همان [PurchaseForm.unitName] برای این ردیف */
  val unitName: String? = null,
  val packaging: PurchasePackaging = PurchasePackaging(),
)

/**
 *  فرمِ «ثبت خرید» (`purchasing/new?supplier=`) — منطقِ خالص، زیرِ آزمون. جمع و باقی فقط برای
 *  نمایشِ زنده‌اند (همان گِردکردنِ `LedgerEngine.addPurchase`)؛ ثبت و همهٔ اثرهایش (انبار، بدهی،
 *  تاریخچهٔ قیمت) کارِ خودِ موتور است.
 */
internal object PurchaseFormModel {

  fun initial(supplierId: String?, today: String) =
    PurchaseForm(supplierId = supplierId?.takeIf { it.isNotBlank() }, date = today, pay = PurchasePay.FULL)

  private fun num(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()?.takeIf { it.isFinite() }

  private fun rowTotal(row: PurchaseRow): Double = PurchaseBook.lineTotal(PurchaseBook.Line(
    productId = row.productId.orEmpty(), quantity = num(row.quantity) ?: 0.0, price = num(row.price) ?: 0.0,
    factorMilli = row.packaging.factor(),
  ))

  /**
   *  جمعِ خرید = جمعِ مقدار × قیمتِ هر ردیف، هر ردیف گردشده مثلِ موتور (`PurchaseBook.lineTotal`)؛ تک‌ردیفه همان امروز.
   *  PUR-11: فاکتورِ ارزی ⇒ معادلِ افغانیِ سند با نرخِ همین فاکتور (همان `PurchaseFx.doc` ِ ثبت؛ نرخِ نادرست ⇒ ۰).
   */
  fun total(f: PurchaseForm): Double =
    if (f.currency != null) (fxDoc(f)?.afnTotal ?: 0L) / 100.0
    else rowTotal(first(f)) + f.more.sumOf(::rowTotal)

  /* ── PUR-11: ارز و نرخِ فاکتور ──────────────────────────────────── */

  /** نرخِ فاکتورِ ارزی (`PurchaseFx.rate`) از کادرِ نرخ (پول؛ رقمِ فارسی و «٫» هم)؛ افغانی یا نادرست ⇒ null */
  fun fxRate(f: PurchaseForm): PurchaseFx.Rate? {
    val code = f.currency ?: return null
    val pul = FxTill.parseAmount(f.rate, PurchaseFx.AFN) ?: return null
    return PurchaseFx.rate(code, pul, f.per)
  }

  /** قیمت یا مبلغِ تایپی به واحدِ کوچکِ ارزِ [code] (همان خواندنِ کادرِ گاوصندوق)؛ خالی یا نادرست ⇒ null */
  fun fxMinor(text: String, code: String): Long? = if (text.isBlank()) null else SafeCurrencies.parse(text, code)

  /** جمعِ ارزیِ یک ردیف (واحدِ کوچکِ ارز) یا null */
  private fun rowFx(quantity: String, price: String, code: String): Long? {
    val q = num(quantity)?.takeIf { it > 0 } ?: return null
    val p = fxMinor(price, code) ?: return null
    return runCatching { PurchaseFx.lineFx(ir.vil3ntec.tohid.data.StockLedger.milli(q), p) }.getOrNull()
  }

  /** جمعِ ارزیِ فاکتور (ردیف‌های نادرست ⇒ ۰) — برای خطِ زنده */
  fun fxTotal(f: PurchaseForm): Long {
    val code = f.currency ?: return 0L
    return (listOf(first(f)) + f.more).sumOf { rowFx(it.quantity, it.price, code) ?: 0L }
  }

  /** پرداختِ هنگامِ خرید به همان ارز: «همه» ⇒ جمعِ ارزی؛ «بعداً» ⇒ ۰؛ بخشی ⇒ مبلغِ تایپی */
  fun fxPaid(f: PurchaseForm): Long {
    val code = f.currency ?: return 0L
    return when (f.pay) {
      PurchasePay.FULL -> fxTotal(f)
      PurchasePay.LATER -> 0L
      PurchasePay.PART, null -> fxMinor(f.paid, code) ?: 0L
    }
  }

  /** سندِ زنده (همان حسابِ ثبت): نرخِ درست و جمعِ ارزی ⇒ معادلِ افغانی و سهمِ ردیف‌ها؛ وگرنه null */
  fun fxDoc(f: PurchaseForm): PurchaseFx.Doc? {
    val r = fxRate(f) ?: return null
    val code = f.currency ?: return null
    val lines = (listOf(first(f)) + f.more).map { rowFx(it.quantity, it.price, code) ?: 0L }
    return runCatching { PurchaseFx.doc(r, lines, fxPaid(f)) }.getOrNull()
  }

  /**
   *  ارزِ فاکتور ⇒ فرم: ارزِ تازه قیمت‌ها و «پرداخت‌شده» را پاک می‌کند (عددِ افغانیِ تایپی دالر خوانده نشود) و نرخ را از آخرین
   *  نرخِ همین ارز ([lastRate]: پول برای per) پیش‌پُر؛ افغانی ⇒ همان فرمِ پیشین (بی نرخ). «نرسیده» با ارز ممکن نیست.
   */
  fun setCurrency(f: PurchaseForm, code: String?, lastRate: PurchaseFx.Rate? = null): PurchaseForm {
    val c = code?.takeIf { it != PurchaseFx.AFN }
    if (c == f.currency) return f
    val cleared = f.copy(currency = c, price = "", paid = "", more = f.more.map { it.copy(price = "") })
    if (c == null) return cleared.copy(rate = "", per = 1)
    val r = lastRate?.takeIf { it.currency == c }
    return cleared.copy(
      rate = r?.let { FxTill.afnText(it.rate) }.orEmpty(),
      per = r?.per ?: PurchaseFx.defaultPer(c),
      later = false,
    )
  }

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

  /** افزودن یک ردیف خالی به خرید */
  fun addRow(f: PurchaseForm): PurchaseForm = f.copy(more = f.more + PurchaseRow())

  fun removeRow(f: PurchaseForm, index: Int): PurchaseForm =
    if (index !in f.more.indices) f else f.copy(more = f.more.filterIndexed { i, _ -> i != index })

  fun setRow(f: PurchaseForm, index: Int, row: PurchaseRow): PurchaseForm =
    if (index !in f.more.indices) f else f.copy(more = f.more.mapIndexed { i, r -> if (i == index) row else r })

  /** کالای ردیفِ افزوده؛ قیمتِ خالی ⇒ قیمتِ خریدِ فعلیِ همان کالا (مثلِ [pickProduct]) */
  fun pickRowProduct(f: PurchaseForm, index: Int, product: Product, unitsOn: Boolean = false): PurchaseForm {
    val r = f.more.getOrNull(index) ?: return f
    return setRow(f, index, picked(r, product, defaultUnit(product, f.supplierId, unitsOn), f.currency != null))
  }

  /**
   *  PUR-12/AI-10: ردیف‌های **تأییدشدهٔ** «از عکسِ فاکتور» ⇒ ردیف‌های همین فرم: ردیفِ نخستِ خالی، سپس ردیف‌های خالیِ افزوده،
   *  سپس ردیفِ تازه؛ مقدار به واحدِ پایه (`unitName = null`) و فی همان که کاربر تأیید کرد. ثبت هنوز همان «ثبت» ِ فرم
   *  است (`PurchaseBook.record`). ⇒ (فرم، شمارِ افزوده)؛ کالای نبودِ دفتر کنار می‌رود.
   */
  fun withAccepted(f: PurchaseForm, rows: List<ir.vil3ntec.tohid.core.purchase.InvoiceOcr.Accepted>, d: ShopData): Pair<PurchaseForm, Int> {
    // خروجی OCR ارزی ندارد؛ قیمت خوانده‌شده را به ارز دیگری نسبت ندهید.
    if (f.currency != null) return f to 0
    var out = f
    var added = 0
    fun blank(r: PurchaseRow) = r.productId == null && r.quantity.isBlank() && r.price.isBlank()
    for (a in rows) {
      if (d.products.none { it.id == a.productId }) continue
      val r = PurchaseRow(a.productId, ir.vil3ntec.tohid.core.purchase.InvoiceOcr.text(a.qty), ir.vil3ntec.tohid.core.purchase.InvoiceOcr.text(a.price))
      val empty = out.more.indexOfFirst(::blank)
      out = when {
        blank(first(out)) -> withFirst(out, r.copy(expiry = out.expiry))
        empty >= 0 -> setRow(out, empty, r)
        else -> out.copy(more = out.more + r)
      }
      added++
    }
    return out to added
  }

  /** باقی (نسیه) = جمع − پرداخت‌شده، نه کمتر از صفر */
  fun remaining(f: PurchaseForm): Double = (total(f) - paid(f)).coerceAtLeast(0.0)

  /** کادرِ مبلغِ پرداخت پیداست؟ (بخشی، یا کادرِ تایپیِ پیشین) */
  fun typesPaid(f: PurchaseForm): Boolean = f.pay == null || f.pay == PurchasePay.PART

  /**
   *  کادرِ «سررسیدِ پرداخت» پیداست؟ نسیه یا بخشی (هر جا، e-rows-63b) یا موتورِ تازهٔ موجودی (PUR-07، همان شرطِ کامپیوتر)؛ نه برای
   *  «همه را دادم» و نه کالای نرسیده (سفارش سررسیدِ پرداخت ندارد؛ بدهی با رسیدنِ کالا می‌آید).
   */
  fun dueShown(f: PurchaseForm, extrasOn: Boolean): Boolean =
    !f.later && f.pay != PurchasePay.FULL && (extrasOn || f.pay == PurchasePay.PART || f.pay == PurchasePay.LATER)

  /** گزینهٔ پرداخت ⇒ فرم: «همه» و «بعداً» مبلغِ تایپی را پاک می‌کنند؛ «همه» سررسید را هم */
  fun setPay(f: PurchaseForm, pay: PurchasePay): PurchaseForm = when (pay) {
    PurchasePay.FULL -> f.copy(pay = pay, paid = "", due = "")
    PurchasePay.PART -> f.copy(pay = pay)
    PurchasePay.LATER -> f.copy(pay = pay, paid = "")
  }

  /** «الان رسیده؟» ⇒ فرم: نرسیده سررسیدِ پرداخت و هزینهٔ جانبی ندارد (بها و بدهی با رسید می‌آیند) */
  fun setLater(f: PurchaseForm, later: Boolean): PurchaseForm = if (later) f.copy(later = true, due = "", landed = "") else f.copy(later = false)

  /**
   *  کالا که انتخاب شد و قیمت خالی بود ⇒ قیمتِ خریدِ فعلیِ همان کالا (قابلِ عوض کردن).
   *  PRD-07 ([unitsOn] = `ShopPolicy.productTools`): واحدی که تأمین‌کنندهٔ پیش‌فرضش همین تأمین‌کننده است پیش‌گزیده می‌شود
   *  (`PurchaseUnits.defaultFor`، همان فاکتورِ کامپیوتر) و قیمتِ پیشنهادی به همان واحد؛ نبود ⇒ واحدِ پایه.
   */
  fun pickProduct(f: PurchaseForm, product: Product, unitsOn: Boolean = false): PurchaseForm =
    withFirst(f, picked(first(f), product, defaultUnit(product, f.supplierId, unitsOn), f.currency != null))

  private fun priceText(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

  /* ── PRD-07: واحدِ خریدِ ردیف ──────────────────────────────────────── */

  /** ردیفِ نخست به شکلِ یک ردیف (و برگشت) — تا هر کارِ ردیف یک بار نوشته شود */
  fun first(f: PurchaseForm) = PurchaseRow(f.productId, f.quantity, f.price, f.expiry, f.unitName, f.packaging)

  fun withFirst(f: PurchaseForm, r: PurchaseRow) =
    f.copy(productId = r.productId, quantity = r.quantity, price = r.price, expiry = r.expiry, unitName = r.unitName, packaging = r.packaging)

  private fun row(f: PurchaseForm, index: Int): PurchaseRow? = if (index < 0) first(f) else f.more.getOrNull(index)

  private fun put(f: PurchaseForm, index: Int, r: PurchaseRow): PurchaseForm = if (index < 0) withFirst(f, r) else setRow(f, index, r)

  /**
   *  کالا روی ردیف با واحدِ [unit]؛ قیمتِ خالی ⇒ قیمتِ خریدِ کالا به همان واحد (`PoForms.repriced`، همان حسابِ سفارشِ خرید).
   *  PUR-11: فاکتورِ ارزی ([fx]) قیمتِ افغانیِ کالا را پیش‌پُر نمی‌کند (افغانی دالر خوانده نشود).
   */
  private fun picked(r: PurchaseRow, p: Product, unit: String?, fx: Boolean = false): PurchaseRow {
    val price = if (!fx && !r.packaging.carton && r.price.isBlank() && p.purchasePrice > 0) priceText(p.purchasePrice).let { base ->
      if (unit == null) base else PoForms.repriced(p, base, null, unit) ?: base
    } else r.price
    return r.copy(productId = p.id, price = price, unitName = unit.takeUnless { r.packaging.carton })
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
  fun unitLine(d: ShopData, productId: String?, unitName: String?, quantity: String, price: String, f: PurchaseForm? = null): String? {
    val name = unitOrNull(unitName) ?: return null
    val p = d.products.find { it.id == productId } ?: return null
    val u = inUnit(p, name, quantity, price, f) ?: return null
    val bc = runCatching { PurchaseUnits.baseCost(u.qtyMilli, u.factorMilli, u.totalMinor) }.getOrNull() ?: return null
    return StrPurchaseUnits.lineBase(qty(bc.baseQtyMilli / 1000.0), p.unit, money(bc.unitCostMinor / 100.0))
  }

  /** PUR-11: فاکتورِ ارزی ([f] با ارز) ⇒ جمعِ افغانیِ ردیف = معادلِ همان ردیف با نرخِ فاکتور (تنها، برای نمایش و سنجش) */
  private fun inUnit(p: Product, name: String, quantity: String, price: String, f: PurchaseForm? = null): LedgerEngine.InUnit? {
    val q = num(quantity)?.takeIf { it > 0 } ?: return null
    val code = f?.currency
    if (code != null) {
      val fx = rowFx(quantity, price, code) ?: return null
      val r = fxRate(f) ?: return null
      val afn = runCatching { PurchaseFx.toAfn(fx, r) }.getOrNull() ?: return null
      return PurchaseBook.inUnitOf(p, name, q, afn / 100.0)
    }
    val pr = num(price)?.takeIf { it >= 0 } ?: return null
    return PurchaseBook.inUnitOf(p, name, q, PurchaseBook.lineTotal(PurchaseBook.Line(p.id, q, pr)))
  }

  /** خطای واحدِ ردیف — همان `PurchaseBook.unitWhy` ِ ثبت؛ مقدار یا قیمتِ نادرست خطای خودشان را دارند */
  private fun unitError(d: ShopData?, productId: String?, unitName: String?, quantity: String, price: String, f: PurchaseForm? = null): String? {
    val name = unitOrNull(unitName) ?: return null
    val p = d?.products?.find { it.id == productId } ?: return null
    val q = num(quantity)?.takeIf { it > 0 } ?: return null
    val code = f?.currency
    if (code == null && (num(price) ?: -1.0) < 0) return null
    if (code != null && (fxMinor(price, code) == null || fxRate(f) == null)) return null
    return if (inUnit(p, name, quantity, price, f) == null) PurchaseBook.unitWhy(p, name, q) else null
  }

  sealed interface Scan {
    /** [unit] نامِ واحدِ خریدی که بارکدش خوانده شد؛ بارکدِ خودِ کالا ⇒ null */
    data class Added(val form: PurchaseForm, val name: String, val unit: String?) : Scan
    data class Unknown(val code: String) : Scan
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
    val same = rows.indexOfFirst { it.productId == p.id && sameUnit(it.unitName, unit) && !it.packaging.carton }
    val at = when {
      same >= 0 -> same - 1
      f.productId == null -> -1
      else -> f.more.indexOfFirst { it.productId == null }.takeIf { it >= 0 }
        ?: f.more.size
    }
    val base = if (at >= f.more.size) addRow(f) else f
    val r = row(base, at) ?: return Scan.Unknown(code)
    val next = if (same >= 0) r.copy(quantity = plusOne(r.quantity)) else picked(r, p, unit, f.currency != null).copy(quantity = r.quantity.ifBlank { "1" })
    return Scan.Added(put(base, at, next), p.name, hit?.unit?.name)
  }

  /** خطای یک ردیفِ افزوده */
  data class RowErrors(
    val product: String? = null, val quantity: String? = null, val price: String? = null, val expiry: String? = null,
    /** PRD-07 */
    val unit: String? = null,
    val packaging: String? = null,
    val production: String? = null,
  ) {
    val any: Boolean get() = listOf(product, quantity, price, expiry, unit, packaging, production).any { it != null }
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
    /** PUR-11: نرخِ فاکتورِ ارزی */
    val rate: String? = null,
    val packaging: String? = null,
    val production: String? = null,
  ) {
    val any: Boolean get() = listOf(supplier, product, quantity, price, paid, expiry, landed, due, unit, rate, packaging, production).any { it != null } || rows.any { it.any }
  }

  private fun quantityError(text: String): String? = num(text).let { if (it == null || it <= 0) StrMoney.invalidNumber else null }
  private fun priceError(text: String, code: String? = null): String? =
    if (code != null) (if (fxMinor(text, code) == null) StrMoney.invalidNumber else null)
    else num(text).let { if (it == null || it < 0) StrMoney.invalidNumber else null }

  private fun paidError(f: PurchaseForm): String? {
    if (f.pay == PurchasePay.FULL || f.pay == PurchasePay.LATER) return null
    if (f.paid.isBlank()) return StrPurchaseWorkflow.partialAmountRequired.takeIf { f.pay == PurchasePay.PART }
    val currency = f.currency
    val paid = if (currency == null) num(f.paid) else fxMinor(f.paid, currency)?.toDouble()
    if (paid == null || paid < 0) return StrMoney.invalidNumber
    if (f.pay != PurchasePay.PART) return null
    val payable = if (currency == null) total(f) else fxTotal(f).toDouble()
    return when {
      paid <= 0 -> StrPurchaseWorkflow.partialAmountRequired
      paid > payable -> StrPurchaseWorkflow.paymentTooHigh
      else -> null
    }
  }

  /** PUR-05: انقضای تایپ‌شده ⇒ `yyyy-MM-dd`؛ خالی ⇒ "" (بی انقضا)؛ نادرست ⇒ null (همان قاعدهٔ «ورودِ کالا» ِ انبار) */
  fun expiryIso(text: String): String? = if (text.isBlank()) "" else Jalali.parseTyped(text)

  private fun expiryError(text: String): String? = if (expiryIso(text) == null) StrStock.errExpiry else null

  private fun packagingError(row: PurchaseRow): String? {
    if (!row.packaging.carton) return null
    val pieces = row.packaging.piecesPerCarton()
    val cartons = row.quantity.trim().toLongOrNull()
    return if (pieces == null || cartons == null || cartons <= 0 || cartons > Long.MAX_VALUE / 1000 / pieces)
      StrPurchaseWorkflow.cartonInvalid else null
  }

  private fun productionError(row: PurchaseRow, later: Boolean): String? = when {
    !later && expiryIso(row.packaging.production) == null -> StrStock.errExpiry
    !later && expiryIso(row.packaging.production).orEmpty().isNotEmpty() &&
      expiryIso(row.expiry).orEmpty().isNotEmpty() && expiryIso(row.packaging.production).orEmpty() > expiryIso(row.expiry).orEmpty() -> StrPurchaseWorkflow.dateOrder
    else -> null
  }

  /** خطای درجای هر کادر — همان شرط‌های موتور، پیش از زدن؛ [d] (PRD-07) برای سنجشِ واحدِ خریدِ ردیف‌ها */
  fun errors(f: PurchaseForm, d: ShopData? = null): Errors = Errors(
    supplier = if (f.supplierId == null) StrMoney.pickSupplier else null,
    product = if (f.productId == null) StrMoney.pickItem else null,
    quantity = quantityError(f.quantity),
    price = priceError(f.price, f.currency),
    paid = paidError(f),
    rows = f.more.map { r ->
      RowErrors(
        product = if (r.productId == null) StrMoney.pickItem else null,
        quantity = quantityError(r.quantity),
        price = priceError(r.price, f.currency),
        expiry = if (f.later) null else expiryError(r.expiry),
        packaging = packagingError(r),
        production = productionError(r, f.later),
        unit = unitError(d, r.productId, r.unitName, r.quantity, r.price, f),
      )
    },
    expiry = if (f.later) null else expiryError(f.expiry),
    packaging = packagingError(first(f)),
    production = productionError(first(f), f.later),
    landed = if (landedTotal(f) == null) StrPurchasePages.landedBad else null,
    //  همان دو شرطِ موتور (`PurchaseBook.record`/`LedgerEngine.addPurchase`): تاریخِ درست و نه پیش از تاریخِ خرید
    due = when (val iso = dueIso(f)) {
      null -> StrPurchasePages.dueBad(formatDate(f.date.ifBlank { todayIso() }))
      "" -> null
      else -> if (f.date.isNotBlank() && iso < f.date) PurchaseBook.DUE_BEFORE_DATE else null
    },
    unit = unitError(d, f.productId, f.unitName, f.quantity, f.price, f),
    rate = if (f.currency != null && fxRate(f) == null) StrPurchaseFx.rateBad else null,
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
    paidAmount = paid(f),
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
  data class Extras(
    val landedTotal: Double = 0.0,
    val dueDate: String? = null,
    /** PUR-11: نرخِ فاکتورِ ارزی و پرداختِ هنگامِ خرید به همان ارز (واحدِ کوچک)؛ افغانی ⇒ null و ۰ */
    val fx: PurchaseFx.Rate? = null,
    val fxPaid: Long = 0,
  )

  fun extras(f: PurchaseForm, on: Boolean): Extras =
    Extras(if (on) landedTotal(f) ?: 0.0 else 0.0, if (dueShown(f, on)) dueOrNull(f) else null, fxRate(f), fxPaid(f))

  /**
   *  PUR-05: ردیف‌های فاکتورِ چندردیفه برای `PurchaseBook.record` (همان فاکتورِ چندردیفهٔ کامپیوتر): ردیفِ نخست، سپس
   *  ردیف‌های افزوده به همان ترتیب؛ واحد همان واحدِ کالا. پرداخت میانِ ردیف‌ها به ترتیب پخش می‌شود (کارِ `PurchaseBook`).
   *  PRD-07: ردیفِ به واحدِ خرید ⇒ `Line.unitName` (موجودی = مقدار × ضریب، ارزش = جمعِ دقیقِ ردیف)؛ بی واحد همان ردیفِ پیشین.
   */
  fun lines(f: PurchaseForm, unitOf: (String) -> String): List<PurchaseBook.Line> =
    (listOf(first(f)) + f.more).map { r ->
      val id = r.productId.orEmpty()
      PurchaseBook.Line(
        productId = id, quantity = num(r.quantity) ?: 0.0, price = if (f.currency != null) 0.0 else num(r.price) ?: -1.0, unit = unitOf(id),
        expiryDate = expiryIso(r.expiry).orEmpty(),
        unitName = if (r.packaging.carton) StrPurchaseWorkflow.carton else unitOrNull(r.unitName),
        factorMilli = r.packaging.factor(),
        productionDate = expiryIso(r.packaging.production).orEmpty(),
        barcode = r.packaging.barcode.trim().take(128).ifEmpty { null },
        //  PUR-11: فیِ ردیف به واحدِ کوچکِ ارزِ فاکتور
        fxPrice = f.currency?.let { code -> fxMinor(r.price, code) ?: -1L },
      )
    }

  /** پرداختِ کلِ فاکتور: «همه» ⇒ جمع؛ «بعداً» ⇒ ۰؛ بخشی یا کادرِ پیشین ⇒ مبلغِ تایپی (خالی = نسیه) */
  fun paid(f: PurchaseForm): Double = if (f.currency != null) (fxDoc(f)?.afnPaid ?: 0L) / 100.0 else when (f.pay) {
    PurchasePay.FULL -> total(f)
    PurchasePay.LATER -> 0.0
    PurchasePay.PART, null -> num(f.paid) ?: 0.0
  }

  /**
   *  e-rows-63b: «الان رسیده؟ نه» ⇒ ردیف‌های سفارشِ خرید (`PoForms.draft` ⇐ همان ساختنِ سفارشِ امروز). واحدِ خریدِ ردیف همان
   *  واحد می‌ماند (`EditRow.unitName`)؛ انقضا در سفارش نیست (هنگامِ دریافت).
   */
  fun orderRows(f: PurchaseForm, d: ShopData): List<PoForms.EditRow> =
    (listOf(first(f)) + f.more).mapIndexed { i, r ->
      val p = d.products.find { it.id == r.productId }
      PoForms.EditRow(i, r.productId.orEmpty(), p?.name.orEmpty(), p?.unit.orEmpty(), r.quantity.trim(), r.price.trim(), if (r.packaging.carton) StrPurchaseWorkflow.carton else unitOrNull(r.unitName), r.packaging.factor())
    }
}
