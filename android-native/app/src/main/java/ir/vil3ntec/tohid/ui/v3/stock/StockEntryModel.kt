package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEngine

/** کادرهای برگهٔ ورود — برای خطای درجا زیرِ همان کادر */
enum class EntryField { PRODUCT, QUANTITY, PRICE, EXPIRY, MADE }

/**
 *  فرمِ «ورود کالا به انبار» — حالتِ خالص (آزمون: `StockEntryModelTest`).
 *
 *  «۴ کارتن × ۲۵» یعنی ۱۰۰ واحد و حسابش دستِ برنامه است؛ کارتنِ ناقص ⇐ حالتِ «واحد».
 *  تاریخ‌ها خورشیدی یا میلادی تایپ می‌شوند (`Jalali.parseTyped`)؛ تایپِ نادرست به
 *  موتور «?» می‌رسد تا همان پیامِ روشنِ امروز را بدهد. سنجشِ نهایی کارِ موتور است
 *  (`WarehouseEngine.addEntry`)؛ `errors` فقط خطای هر کادر را زودتر زیرِ خودش می‌گوید.
 */
data class EntryForm(
  val productId: String = "",
  val cartonMode: Boolean = false,
  val cartons: String = "",
  val perCarton: String = "",
  val units: String = "",
  val price: String = "",
  val unit: String = "",
  val expiry: String = "",
  val batch: String = "",
  val made: String = "",
  val notes: String = "",
) {
  /** مقدارِ واحد: کارتن × در هر کارتن، یا همان «تعداد واحد» */
  val amount: Double
    get() = if (cartonMode) (cartons.toDoubleOrNull() ?: 0.0) * (perCarton.toDoubleOrNull() ?: 0.0) else units.toDoubleOrNull() ?: 0.0

  val total: Double? get() = price.toDoubleOrNull()?.takeIf { amount > 0 }?.let { it * amount }

  /** چیزی نوشته یا عوض شده؟ (برای «دور ریخته شود؟») */
  fun dirty(initial: EntryForm): Boolean = this != initial

  fun pick(p: Product): EntryForm = copy(
    productId = p.id,
    unit = p.unit.ifBlank { unit },
    price = if (price.isBlank() && p.purchasePrice > 0) plainNumber(p.purchasePrice) else price,
  )

  fun errors(d: ShopData): Map<EntryField, String> = buildMap {
    if (d.products.none { it.id == productId }) put(EntryField.PRODUCT, StrStock.errPickProduct)
    if (amount <= 0) put(EntryField.QUANTITY, StrStock.errUnits)
    if ((price.toDoubleOrNull() ?: -1.0) < 0) put(EntryField.PRICE, StrStock.errPrice)
    if (expiry.isNotBlank() && Jalali.parseTyped(expiry) == null) put(EntryField.EXPIRY, StrStock.errExpiry)
    if (made.isNotBlank() && Jalali.parseTyped(made) == null) put(EntryField.MADE, StrStock.errMade)
  }

  fun draft(): WarehouseEngine.EntryDraft = WarehouseEngine.EntryDraft(
    productId = productId,
    cartons = if (cartonMode) cartons.toDoubleOrNull() ?: 0.0 else 0.0,
    perCarton = if (cartonMode) perCarton.toDoubleOrNull() ?: 0.0 else 0.0,
    units = amount,
    unit = unit,
    price = price.toDoubleOrNull() ?: -1.0,
    notes = notes.trim(),
    //  خالی ⇒ بی انقضا؛ تایپِ نادرست ⇒ موتور با پیامِ روشن ردش می‌کند
    expiryDate = if (expiry.isBlank()) "" else Jalali.parseTyped(expiry) ?: "?",
    batch = batch.trim(),
    productionDate = if (made.isBlank()) "" else Jalali.parseTyped(made) ?: "?",
  )

  companion object {
    /**
     *  آغازِ فرم از مسیر: `product` ⇐ همان کالا؛ `barcode` ِ شناخته ⇐ کالای همان بارکد؛
     *  بارکدِ ناشناس ⇐ بی کالا (برگه «محصولِ تازه» با همین بارکد را پیشنهاد می‌کند).
     */
    fun start(d: ShopData, product: String?, barcode: String?): EntryForm {
      val id = product?.takeIf { p -> d.products.any { it.id == p } }
        ?: barcode?.let { ShopStore.barcodeIndex(d)[it.trim()] }
      val p = d.products.find { it.id == id }
      val unit = d.products.firstOrNull()?.unit.orEmpty()
      return if (p == null) EntryForm(unit = unit) else EntryForm(unit = unit).pick(p)
    }

    /** عدد برای کادر: بی اعشارِ بی‌مورد، رقمِ لاتین */
    fun plainNumber(v: Double): String =
      if (v == Math.floor(v) && !v.isInfinite()) v.toLong().toString() else v.toString()
  }
}
