package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.LedgerEngine
import ir.vil3ntec.tohid.data.Product

/** نوشته‌های فرمِ «ثبت خرید» — همه متنِ خامِ کادرها (عدد بی جداکننده) */
internal data class PurchaseForm(
  val supplierId: String? = null,
  val productId: String? = null,
  val quantity: String = "",
  val price: String = "",
  val paid: String = "",
  val date: String = "",
  val notes: String = "",
)

/**
 *  فرمِ «ثبت خرید» (`purchasing/new?supplier=`) — منطقِ خالص، زیرِ آزمون. جمع و باقی فقط برای
 *  نمایشِ زنده‌اند (همان گِردکردنِ `LedgerEngine.addPurchase`)؛ ثبت و همهٔ اثرهایش (انبار، بدهی،
 *  تاریخچهٔ قیمت) کارِ خودِ موتور است.
 */
internal object PurchaseFormModel {

  fun initial(supplierId: String?, today: String) = PurchaseForm(supplierId = supplierId?.takeIf { it.isNotBlank() }, date = today)

  private fun num(text: String): Double? = text.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

  /** جمعِ خرید = مقدار × قیمت، گردشده مثلِ موتور */
  fun total(f: PurchaseForm): Double = Math.round((num(f.quantity) ?: 0.0) * (num(f.price) ?: 0.0)).toDouble()

  /** باقی (نسیه) = جمع − پرداخت‌شده، نه کمتر از صفر */
  fun remaining(f: PurchaseForm): Double = (total(f) - (num(f.paid) ?: 0.0)).coerceAtLeast(0.0)

  /** کالا که انتخاب شد و قیمت خالی بود ⇒ قیمتِ خریدِ فعلیِ همان کالا (قابلِ عوض کردن) */
  fun pickProduct(f: PurchaseForm, product: Product): PurchaseForm =
    f.copy(
      productId = product.id,
      price = if (f.price.isBlank() && product.purchasePrice > 0) priceText(product.purchasePrice) else f.price,
    )

  private fun priceText(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

  data class Errors(
    val supplier: String? = null,
    val product: String? = null,
    val quantity: String? = null,
    val price: String? = null,
    val paid: String? = null,
  ) {
    val any: Boolean get() = listOf(supplier, product, quantity, price, paid).any { it != null }
  }

  /** خطای درجای هر کادر — همان شرط‌های موتور، پیش از زدن */
  fun errors(f: PurchaseForm): Errors = Errors(
    supplier = if (f.supplierId == null) StrMoney.pickSupplier else null,
    product = if (f.productId == null) StrMoney.pickItem else null,
    quantity = num(f.quantity).let { if (it == null || it <= 0) StrMoney.invalidNumber else null },
    price = num(f.price).let { if (it == null || it < 0) StrMoney.invalidNumber else null },
    paid = if (f.paid.isNotBlank() && (num(f.paid) ?: -1.0) < 0) StrMoney.invalidNumber else null,
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
  )
}
