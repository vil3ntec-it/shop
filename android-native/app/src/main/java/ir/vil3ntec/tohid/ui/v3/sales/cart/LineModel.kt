package ir.vil3ntec.tohid.ui.v3.sales.cart

import ir.vil3ntec.tohid.core.i18n.StrSale
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
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
) {
  val hasSaleUnit: Boolean get() = saleUnit.isNotBlank() && unitFactor > 0
  val custom: Boolean get() = free || unitPrice != null
}

/** نوشته‌های فرم */
data class LineForm(
  val qtyText: String,
  val priceText: String,
  /** مقدار به واحدِ فروشِ دوم نوشته می‌شود */
  val inSaleUnit: Boolean = false,
  /** «برگشت به قیمتِ کالا» زده شد */
  val resetPrice: Boolean = false,
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

  fun info(d: ShopData, line: SalesEngine.CartLine): LineInfo {
    val p = d.products.find { it.id == line.productId }
    val base = if (line.free) line.unitPrice ?: 0.0 else p?.salePrice ?: 0.0
    return LineInfo(
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
    )
  }

  fun start(info: LineInfo): LineForm = LineForm(qty(info.quantity), qty(info.unitPrice ?: info.basePrice))

  /** عوض کردنِ واحد: همان مقدار به واحدِ تازه، نه پاک شدنِ کادر */
  fun switchUnit(form: LineForm, info: LineInfo, toSale: Boolean): LineForm {
    if (toSale == form.inSaleUnit || !info.hasSaleUnit) return form
    val typed = parseNumber(form.qtyText) ?: return form.copy(inSaleUnit = toSale)
    val next = if (toSale) typed / info.unitFactor else typed * info.unitFactor
    return form.copy(qtyText = qty(next), inSaleUnit = toSale)
  }

  /** مقدارِ ثبت به واحدِ موجودی؛ null ⇒ عدد نیست */
  fun quantity(form: LineForm, info: LineInfo): Double? {
    val typed = parseNumber(form.qtyText) ?: return null
    val p = info.product
    return if (form.inSaleUnit && p != null) SalesEngine.toStockQty(p, typed) else typed
  }

  /** واحدی که کادرِ مقدار با آن نوشته می‌شود */
  fun shownUnit(form: LineForm, info: LineInfo): String = if (form.inSaleUnit) info.saleUnit else info.unit

  fun qtyError(form: LineForm, info: LineInfo): String? {
    val q = quantity(form, info) ?: return StrSale.badNumberShort
    if (q <= 0) return StrSale.positiveNeeded
    return info.product?.let { SalesEngine.quantityProblem(it, q) }
  }

  fun priceError(form: LineForm): String? {
    if (form.priceText.isBlank()) return null
    return if (parseNumber(form.priceText) == null) StrSale.badNumberShort else null
  }

  /** قیمتی که ردیف پس از ثبت دارد (null ⇒ قیمتِ کالا) */
  fun wantedPrice(form: LineForm, info: LineInfo, start: LineForm): Double? {
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

  /** سبدِ تازه پس از «ثبت» */
  fun apply(lines: List<SalesEngine.CartLine>, info: LineInfo, form: LineForm, start: LineForm): List<SalesEngine.CartLine> {
    val q = quantity(form, info) ?: return lines
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
