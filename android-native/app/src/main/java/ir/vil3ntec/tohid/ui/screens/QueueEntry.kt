package ir.vil3ntec.tohid.ui.screens

import androidx.compose.runtime.Composable

/**
 *  یک درِ صفِ سفارش‌ها در «…» ِ سربرگ (۱۴۰۵/۰۸/۲۴: دکمه‌های سرِ صف به منو رفتند): [label] تهی ⇒ ردیفی نیست (پرچم یا
 *  اجازه)؛ [open] برگه را باز می‌کند و [host] همان برگه را می‌کشد (صدازننده یک بار جایی در صفحه صدایش می‌زند).
 */
class QueueEntry(val label: String?, val open: () -> Unit, val host: @Composable () -> Unit) {
  companion object {
    val NONE = QueueEntry(null, {}, {})
  }
}
