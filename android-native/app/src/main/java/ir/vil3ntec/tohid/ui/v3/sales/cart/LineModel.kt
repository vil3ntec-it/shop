package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.core.pos.LinePricing
import ir.vil3ntec.tohid.core.pos.PriceApproval
import ir.vil3ntec.tohid.core.product.UnitSale
import ir.vil3ntec.tohid.data.DiscountGate
import ir.vil3ntec.tohid.data.LinePolicy
import ir.vil3ntec.tohid.data.PlaceQty
import ir.vil3ntec.tohid.data.PlaceStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.QtyExactWiring
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.UnitLine
import ir.vil3ntec.tohid.parseNumber
import ir.vil3ntec.tohid.qty

/** آنچه برگهٔ «ردیفِ سبد» از ردیف و کالایش می‌داند */
data class LineInfo(
  val id: String,
  val name: String,
  /** واحدِ موجودی (کیلوگرم، عدد، …) — قیمت و مقدار همیشه به همین ثبت می‌شوند */
  val unit: String,
  /** واحدِ فروشِ دوم و ضریبش (شرح سرِ `Product.saleUnit`) */
  val saleUnit: String,
  val unitFactor: Double,
  val quantity: Double,
  /** قیمتِ دستیِ ردیف؛ null ⇒ قیمتِ خودِ کالا */
  val unitPrice: Double?,
  val basePrice: Double,
  val free: Boolean,
  val product: Product?,
  /** POS-16 — موجودیِ همین کالا در هر محلِ نگه‌داری (INV-20)؛ خالی ⇒ بخشش نیست (شرح سرِ `PlaceStock.of`) */
  val places: List<PlaceQty> = emptyList(),
  /**
   *  POS-09 (S05) — واحدهای کالا (پایه، واحدِ دوم و بسته‌ها با قیمتِ لیست) فقط با «واحدِ بزرگ در صندوق» ِ همین
   *  دستگاه (`TillGoodsPref.units`، پیش‌فرض خاموش) و کالای چندواحدی؛ `null` ⇒ همان برگهٔ امروز (دو واحد یا هیچ)
   */
  val units: UnitLine.Plan? = null,
) {
  val hasSaleUnit: Boolean get() = saleUnit.isNotBlank() && unitFactor > 0
  val custom: Boolean get() = free || unitPrice != null
  /** قیمتِ دستیِ ردیف که از قیمتِ لیستِ یک بسته نیامده (با عوض شدنِ واحد می‌ماند) */
  val manualPrice: Boolean get() = unitPrice != null && units?.packPriced != true
}

/** نوشته‌های فرم */
data class LineForm(
  val qtyText: String,
  val priceText: String,
  /** مقدار به واحدِ فروشِ دوم نوشته می‌شود */
  val inSaleUnit: Boolean = false,
  /** «برگشت به قیمتِ کالا» زده شد */
  val resetPrice: Boolean = false,
  /** POS-09 — واحدِ ردیف از `LineInfo.units` (۰ = پایه)؛ بی `units` بی‌اثر */
  val unitAt: Int = 0,
  /** POS-09 — کاربر خودش قیمت نوشت (با `units` قیمتِ نانوشته با واحد عوض می‌شود) */
  val priceTouched: Boolean = false,
)

/**
 *  برگهٔ «تعداد و قیمتِ این ردیف» (`sale/line/{i}`) — خالص، همان قاعدهٔ `CartLineDialog`ِ
 *  پیشین روی `SalesEngine.setCartQty`/`setLinePrice`/`toStockQty`/`lineTotal`:
 *  • مقدار به واحدِ فروشِ دوم ⇒ به واحدِ موجودی (`toStockQty`) پیش از ثبت؛
 *  • قیمتِ دست‌نخورده همان قیمتِ امروزِ ردیف می‌ماند؛ «برگشت به قیمتِ کالا» ⇒ قیمتِ کالا
 *    (null)؛ نوشته ⇒ همان عدد. قلمِ آزاد همیشه قیمتِ خودش را دارد؛
 *  • جمعِ ردیف همان `lineTotal`ِ موتور (گردِ همان‌جا که ثبت گرد می‌کند).
 */
object LineModel {

  /**
   *  @param places «محل‌های نگه‌داری» ِ همین دستگاه روشن است (`StockPlacesPref.places`) — صدازننده می‌خواند؛
   *  پیش‌فرض false ⇒ همان برگهٔ امروز
   *  @param units «واحدِ بزرگ در صندوق» ِ همین دستگاه (`TillGoodsPref.units`) و [wholeOnly] «فقط بستهٔ کامل»
   *  (`TillGoodsPref.wholePacks`) — صدازننده می‌خواند؛ پیش‌فرض false ⇒ همان برگهٔ امروز
   */
  fun info(d: ShopData, line: SalesEngine.CartLine, places: Boolean = false, units: Boolean = false, wholeOnly: Boolean = false): LineInfo {
    val p = d.products.find { it.id == line.productId }
    val base = if (line.free) line.unitPrice ?: 0.0 else p?.salePrice ?: 0.0
    return withUnits(LineInfo(
      id = line.productId,
      name = if (line.free) line.label else p?.name.orEmpty(),
      unit = p?.unit.orEmpty(),
      saleUnit = p?.saleUnit.orEmpty(),
      unitFactor = p?.unitFactor ?: 1.0,
      quantity = line.quantity,
      unitPrice = line.unitPrice,
      basePrice = base,
      free = line.free,
      product = p,
      places = if (places && !line.free) PlaceStock.of(d, p) else emptyList(),
    ), units, wholeOnly)
  }

  /** POS-09 — برنامهٔ واحدهای ردیف روی [info] (`UnitLine.plan`)؛ خاموش، قلمِ آزاد یا کالای تک‌واحد ⇒ همان [info] */
  fun withUnits(info: LineInfo, on: Boolean, wholeOnly: Boolean): LineInfo {
    val p = info.product
    if (!on || info.free || p == null) return info
    return info.copy(units = UnitLine.plan(p, wholeOnly, info.quantity, info.unitPrice, next = false))
  }

  /** واحدِ [i] ِ برنامهٔ ردیف؛ بیرون از فهرست ⇒ پایه */
  private fun unitOf(plan: UnitLine.Plan, i: Int): UnitSale.Unit = plan.units.getOrNull(i) ?: plan.units[0]

  /** برچسبِ هر واحد برای انتخاب: پایه با نامِ خودش، بسته «کارتن (۱۲ دانه)» */
  fun unitLabels(info: LineInfo): List<String> = info.units?.units.orEmpty().mapIndexed { i, u ->
    if (i == 0) info.unit.ifBlank { StrSale.unitFallback } else UnitLine.label(u, info.unit)
  }

  /** قیمتِ هر واحدِ پایه از قیمتِ لیستِ واحدِ انتخاب‌شده؛ `null` ⇒ قیمتِ خودِ کالا (`UnitSale.lineUnitPrice`) */
  fun listPrice(form: LineForm, info: LineInfo): Double? = info.units?.let { UnitSale.lineUnitPrice(unitOf(it, form.unitAt)) }

  /** قیمتِ واحدِ [u] در لیستِ قیمت (خودِ بسته، نه هر واحدِ پایه)؛ `null` ⇒ بسته قیمتِ جدا ندارد */
  fun packPriceText(form: LineForm, info: LineInfo): String? {
    val plan = info.units ?: return null
    val u = unitOf(plan, form.unitAt)
    val minor = u.priceMinor ?: return null
    return if (form.unitAt > 0) StrSale.packListPrice(u.name, ir.vil3ntec.tohid.ui.components.pos.PhoneSaleText.amount(minor / 100.0)) else null
  }

  /**
   *  POS-09 — عوض کردنِ واحدِ ردیف به [to]: همان مقدار به واحدِ تازه (نه پاک شدنِ کادر)؛ قیمتِ نانوشته (و نه دستیِ
   *  پیشینِ ردیف) قیمتِ لیستِ واحدِ تازه می‌شود — همان `CartLineDialog` ِ پیشین.
   */
  fun switchTo(form: LineForm, info: LineInfo, to: Int): LineForm {
    val plan = info.units ?: return form
    if (to == form.unitAt || to !in plan.units.indices) return form
    val typed = parseNumber(form.qtyText)
    val qtyText = if (typed == null) form.qtyText
      else qty(UnitSale.fromBase(typed * unitOf(plan, form.unitAt).sizeMilli / 1000.0, plan.units[to]))
    val next = form.copy(qtyText = qtyText, unitAt = to)
    val list = listPrice(next, info)
    return if (!form.priceTouched && (form.resetPrice || !info.manualPrice)) next.copy(priceText = qty(list ?: info.basePrice)) else next
  }

  /** «۳ کیلوگرم» ِ یک محل */
  fun placeQtyText(q: PlaceQty, unit: String): String = (qty(q.milli / 1000.0) + " " + unit).trim()

  fun start(info: LineInfo): LineForm = info.units?.let { plan ->
    //  POS-09: برگه با واحدی باز می‌شود که مقدارِ ردیف را بی اعشار می‌گیرد (`UnitLine.plan`)
    LineForm(qty(plan.startCount), qty(info.unitPrice ?: info.basePrice), unitAt = plan.startAt)
  } ?: LineForm(qty(info.quantity), qty(info.unitPrice ?: info.basePrice))

  /** «برگشت به قیمتِ کالا»: قیمتِ خودِ کالا — با `units` قیمتِ لیستِ واحدِ انتخاب‌شده */
  fun resetPrice(form: LineForm, info: LineInfo): LineForm =
    form.copy(priceText = qty(listPrice(form, info) ?: info.basePrice), resetPrice = true, priceTouched = false)

  /** عوض کردنِ واحد: همان مقدار به واحدِ تازه، نه پاک شدنِ کادر */
  fun switchUnit(form: LineForm, info: LineInfo, toSale: Boolean): LineForm {
    if (toSale == form.inSaleUnit || !info.hasSaleUnit) return form
    val typed = parseNumber(form.qtyText) ?: return form.copy(inSaleUnit = toSale)
    val next = if (toSale) typed / info.unitFactor else typed * info.unitFactor
    return form.copy(qtyText = qty(next), inSaleUnit = toSale)
  }

  /** مقدارِ ثبت به واحدِ موجودی؛ null ⇒ عدد نیست (با `units`: صفر/منفی یا بخشی از بستهٔ نامجاز هم) */
  fun quantity(form: LineForm, info: LineInfo): Double? {
    val typed = parseNumber(form.qtyText) ?: return null
    info.units?.let { plan -> return UnitSale.toBase(typed, unitOf(plan, form.unitAt), plan.wholeOnly) }
    val p = info.product
    return if (form.inSaleUnit && p != null) SalesEngine.toStockQty(p, typed) else typed
  }

  /** واحدی که کادرِ مقدار با آن نوشته می‌شود */
  fun shownUnit(form: LineForm, info: LineInfo): String = info.units?.let { plan ->
    if (form.unitAt == 0) info.unit else unitOf(plan, form.unitAt).name
  } ?: if (form.inSaleUnit) info.saleUnit else info.unit

  /** زیرِ کادرِ مقدار: «= ۱۲ دانه» وقتی به واحدی جز پایه نوشته می‌شود */
  fun qtyHelper(form: LineForm, info: LineInfo): String? =
    if ((info.units != null && form.unitAt > 0) || (info.units == null && form.inSaleUnit)) quantity(form, info)?.let { "= ${qty(it)} ${info.unit}" } else null

  fun qtyError(form: LineForm, info: LineInfo): String? {
    info.units?.let { plan ->
      val typed = parseNumber(form.qtyText) ?: return StrSale.badNumberShort
      if (typed <= 0) return StrSale.positiveNeeded
      val u = unitOf(plan, form.unitAt)
      if (UnitSale.partRejected(typed, u, plan.wholeOnly)) return StrSale.partPackRejected(u.name)
    }
    val q = quantity(form, info) ?: return StrSale.badNumberShort
    if (q <= 0) return StrSale.positiveNeeded
    //  PRD-08 (`shop.qty_exact`): «1.2345» بی‌صدا ۱٫۲۳۵ نمی‌شود؛ خاموش ⇒ همان پیشین
    if (QtyExactWiring.tooPrecise(form.qtyText)) return StrSale.qtyThreeDecimals
    return info.product?.let { SalesEngine.quantityProblem(it, q) }
  }

  fun priceError(form: LineForm): String? {
    if (form.priceText.isBlank()) return null
    return if (parseNumber(form.priceText) == null) StrSale.badNumberShort else null
  }

  /** قیمتی که ردیف پس از ثبت دارد (null ⇒ قیمتِ کالا) */
  fun wantedPrice(form: LineForm, info: LineInfo, start: LineForm): Double? {
    if (info.units != null && !info.free) {
      //  POS-09: نانوشته ⇒ قیمتِ دستیِ پیشینِ ردیف، وگرنه قیمتِ لیستِ واحدِ انتخاب‌شده (null ⇒ قیمتِ کالا)
      val list = listPrice(form, info)
      val untouched = if (info.manualPrice) info.unitPrice else list
      return when {
        form.resetPrice -> list
        form.priceTouched -> parseNumber(form.priceText) ?: untouched
        else -> untouched
      }
    }
    val wanted = when {
      form.resetPrice && form.priceText == qty(info.basePrice) -> null
      form.priceText == start.priceText -> info.unitPrice
      else -> parseNumber(form.priceText) ?: info.unitPrice
    }
    //  قلمِ آزاد بی قیمت معنی ندارد؛ پاک شد ⇒ همان قبلی
    return if (info.free) wanted ?: info.unitPrice else wanted
  }

  /** قیمتِ واحدی که جمعِ پیش‌نمایش با آن حساب می‌شود */
  fun shownPrice(form: LineForm, info: LineInfo, start: LineForm): Double = wantedPrice(form, info, start) ?: info.basePrice

  /** جمعِ این ردیف — همان `lineTotal`ِ ثبت */
  fun total(form: LineForm, info: LineInfo, start: LineForm): Double =
    SalesEngine.lineTotal(shownPrice(form, info, start), quantity(form, info)?.takeIf { it > 0 } ?: 0.0)

  fun valid(form: LineForm, info: LineInfo): Boolean = qtyError(form, info) == null && priceError(form) == null

  /**
   *  S45 — قیمتِ **دستی**ای که قاعدهٔ «قیمت و تخفیفِ ردیف» می‌سنجد؛ `null` ⇒ قیمتِ کالا یا قیمتِ لیستِ بسته (تغییرِ دستی نیست،
   *  همان `if (manual) parsedPrice else null` ِ `CartLineDialog`). قیمتِ دستیِ دست‌نخوردهٔ ردیف «تغییر» نیست (`LinePricing.edit`).
   */
  fun typedPrice(form: LineForm, info: LineInfo, start: LineForm): Double? =
    if (info.units != null && !info.free) parseNumber(form.priceText)?.takeIf { form.priceTouched && !form.resetPrice }
    else wantedPrice(form, info, start)

  /**
   *  S45 (POS-08/10/21) — همان قاعدهٔ کادرِ ردیفِ کامپیوتر (`LinePolicy.checked`): قاعدهٔ جهت، سقفِ قلمِ آزاد و «کاهش برای کسی که
   *  تخفیف برایش ممنوع است»، با همان پیام‌ها؛ ردِ برداشتنی ⇒ «درخواستِ تأیید» ([canAsk])، و تأییدِ [grant] فقط همان قاعده را برای
   *  همان قیمت برمی‌دارد. سیاستِ خاموش (پیش‌فرض) ⇒ بی رد، همان برگهٔ پیشین. تخفیفِ ردیف در برگهٔ گوشی نیست.
   */
  fun check(policy: LinePricing.Policy, form: LineForm, info: LineInfo, start: LineForm, grant: PriceApproval.Grant? = null, canAsk: Boolean = false): LinePolicy.Checked =
    LinePolicy.checked(policy, info.free, info.basePrice, info.unitPrice ?: info.basePrice, typedPrice(form, info, start), quantity(form, info), true, "", grant, canAsk)

  /** S45 — تأییدِ مدیر برای قیمتِ نوشته‌شده (همان `Grant` ِ `CartLineDialog`) */
  fun grantOf(code: String, by: String, role: String, reason: String, requester: String, info: LineInfo, price: Double): PriceApproval.Grant =
    PriceApproval.Grant(code, by, role, reason, requester, DiscountGate.minor(info.basePrice), DiscountGate.minor(price))

  /**
   *  سبدِ تازه پس از «ثبت». S45: ردِ قاعدهٔ «قیمت و تخفیفِ ردیف» ([policy]) بی تأییدِ همان قیمت ([grant]) ⇒ سبد دست نمی‌خورد —
   *  سدِ دوم پشتِ دکمهٔ برگه؛ [policy] ِ پیش‌فرض خاموش است.
   */
  fun apply(
    lines: List<SalesEngine.CartLine>, info: LineInfo, form: LineForm, start: LineForm,
    policy: LinePricing.Policy = LinePricing.Policy(), grant: PriceApproval.Grant? = null,
  ): List<SalesEngine.CartLine> {
    val q = quantity(form, info) ?: return lines
    if (!check(policy, form, info, start, grant).edit.ok) return lines
    val next = SalesEngine.setCartQty(lines, info.id, q)
    return SalesEngine.setLinePrice(next, info.id, wantedPrice(form, info, start))
  }

  /** سبدِ تازه پس از «برداشتن از سبد» */
  fun remove(lines: List<SalesEngine.CartLine>, info: LineInfo): List<SalesEngine.CartLine> =
    SalesEngine.setCartQty(lines, info.id, 0.0)

  /**
   *  «برگرداندن» ِ برداشتن: همان ردیف سرِ همان جا (اگر میانِ این دو کسی همان کالا را دوباره
   *  افزوده باشد، دست نمی‌خورد). سبد سند نیست و همگام نمی‌شود؛ برگرداندنش امن است.
   */
  fun restore(lines: List<SalesEngine.CartLine>, line: SalesEngine.CartLine, at: Int): List<SalesEngine.CartLine> =
    if (lines.any { it.productId == line.productId }) lines
    else lines.toMutableList().apply { add(at.coerceIn(0, size), line) }
}
