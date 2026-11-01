package ir.vil3ntec.tohid.ui.v3.stock

import ir.vil3ntec.tohid.core.i18n.StrStock
import ir.vil3ntec.tohid.data.WarehouseEngine

/** سه گونهٔ اصلاح: زیاد، کم، برگشت به تأمین‌کننده (کاهش با گونهٔ `SUPPLIER_RETURN`) */
enum class AdjustChoice(val increase: Boolean, val kind: WarehouseEngine.AdjustKind) {
  UP(true, WarehouseEngine.AdjustKind.ADJUSTMENT),
  DOWN(false, WarehouseEngine.AdjustKind.ADJUSTMENT),
  RETURN(false, WarehouseEngine.AdjustKind.SUPPLIER_RETURN),
}

/** فرمِ «اصلاح موجودی» — حالتِ خالص (آزمون: `AdjustModelTest`) */
data class AdjustForm(val choice: AdjustChoice = AdjustChoice.UP, val amount: String = "", val reason: String = "") {
  val quantity: Double get() = amount.toDoubleOrNull() ?: 0.0

  /** پیش‌نمایشِ «۱۴۴ ← ۱۳۰»: موجودیِ پس از این اصلاح؛ null تا مقدار درست نیست */
  fun after(stock: Double): Double? = quantity.takeIf { it > 0 }?.let { if (choice.increase) stock + it else stock - it }

  val dirty: Boolean get() = amount.isNotBlank() || reason.isNotBlank()

  /** خطای کادرها با همان متنِ موتور (`adjustStock`)؛ کمبودِ موجودی را خودِ موتور می‌گوید */
  fun errors(): Map<String, String> = buildMap {
    if (quantity <= 0) put(AMOUNT, StrStock.errAmount)
    if (reason.isBlank()) put(REASON, StrStock.errReason)
  }

  companion object {
    const val AMOUNT = "amount"
    const val REASON = "reason"
  }
}
