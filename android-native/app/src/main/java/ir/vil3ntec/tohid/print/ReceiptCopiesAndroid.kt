package ir.vil3ntec.tohid.print

import android.content.Context
import ir.vil3ntec.tohid.core.hw.ReceiptCopies

/**
 *  POS-35 — سمتِ پلتفرمِ `core/hw/ReceiptCopies`: شمارِ نسخهٔ چاپِ خودکار روی **همین دستگاه**
 *  (`SharedPreferences`ِ «tohid»، همگام نمی‌شود). نبوده یا خراب ⇒ ۱ = همان یک رسیدِ امروز.
 */
object ReceiptCopiesPref {

  private const val KEY = "print_receipt_copies"

  fun get(context: Context): Int = runCatching {
    ReceiptCopies.clamp(context.getSharedPreferences("tohid", Context.MODE_PRIVATE).getInt(KEY, ReceiptCopies.DEFAULT))
  }.getOrDefault(ReceiptCopies.DEFAULT)

  fun set(context: Context, n: Int) {
    context.getSharedPreferences("tohid", Context.MODE_PRIVATE).edit().putInt(KEY, ReceiptCopies.clamp(n)).apply()
  }
}
