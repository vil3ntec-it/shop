package ir.vil3ntec.tohid.ui.v3.shell

import androidx.compose.runtime.Composable
import ir.vil3ntec.tohid.core.i18n.StrHome

/**
 *  رویِ دومِ کارتِ کششی: «گاوصندوق» (owner-feedback-3 B) — کاربر پولِ گاوصندوق را می‌شمارد
 *  و موجودیِ دقیق (با سابقهٔ شمارش/جابه‌جایی) را می‌بیند. هستهٔ داده‌اش (`SafeEngine`) جدا
 *  ساخته می‌شود؛ تا API ِ آن تأیید شود این‌جا فقط جای همان رو است — عددی ساخته نمی‌شود.
 */
@Composable
internal fun HomeSafeFace() {
  PullFace(label = StrHome.safe, amount = null, line = StrHome.safeSoon, live = false)
}
