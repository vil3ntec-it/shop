package ir.vil3ntec.tohid.pay

import androidx.compose.runtime.mutableStateOf

/**
 *  آنچه نمایشگرِ رو به مشتری در پرداختِ درگاهی نشان می‌دهد (HW-10، POS-30): مبلغ، QR و وضعیت.
 *
 *  برگهٔ حساب‌پی (`HesabPaySheet`) پرش می‌کند و با بسته شدنش خالی؛ نمایشگرِ دوم — Presentation ِ اندروید
 *  روی صفحهٔ دومِ کارت‌خوان‌های دوصفحه‌ای، یا پنجرهٔ دومِ کامپیوتر — همین را می‌خواند. بی صفحهٔ دوم هیچ‌جا
 *  دیده نمی‌شود. فقط آنچه مشتری باید ببیند: نه سود، نه موجودی، نه نامِ مشتری.
 */
object CustomerPay {
  enum class Stage { WAITING, PAID }

  /** `picture` = عکسِ QR ِ خودِ دکان (۱۴۰۵/۰۸/۲۶)، خوانده و سنجیده؛ `null` ⇒ همان QR ِ کشیده از [qrPayload] */
  data class View(val amountMinor: Long, val qrPayload: String, val stage: Stage, val picture: androidx.compose.ui.graphics.ImageBitmap? = null)

  val view = mutableStateOf<View?>(null)
}
