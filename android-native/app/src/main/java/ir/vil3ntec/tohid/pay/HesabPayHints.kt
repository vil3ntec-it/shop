package ir.vil3ntec.tohid.pay

import android.content.Context
import ir.vil3ntec.tohid.core.gateway.HesabPayHint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 *  پیشنهادِ «پول رسید» از اعلانِ برنامهٔ حساب‌پی (حالتِ سادهٔ «حساب‌پیِ دکان»، فقط اندروید، با روشن کردنِ صاحب).
 *
 *  - تنظیمِ همین دستگاه، پیش‌فرض **خاموش** (`tohid-hesabpay` ⇐ `notice_hint`، دستهٔ B).
 *  - سرویسِ اعلانِ اندروید (`pay/platform/HesabPayNoticeAndroid.kt`) فقط آخرین مبلغِ دریافتی را این‌جا می‌گذارد —
 *    در حافظه، نه دیسک، نه سرور، نه لاگ؛ برگهٔ صندوق فقط **پیشنهاد** نشان می‌دهد و صندوقدار «پول رسید» را می‌زند.
 *  - ⛔ پیامک خوانده نمی‌شود (READ_SMS نه) و «مبلغِ برابر» هرگز خودکار فاکتور را نمی‌بندد ([HesabPayHint]).
 */
object HesabPayHints {
  private const val PREFS = "tohid-hesabpay"
  const val KEY = "notice_hint"

  private val last = MutableStateFlow<HesabPayHint.Hint?>(null)
  val hint: StateFlow<HesabPayHint.Hint?> get() = last

  fun enabled(context: Context): Boolean =
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false) }.getOrDefault(false)

  fun setEnabled(context: Context, on: Boolean) {
    runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply() }
    if (!on) last.value = null
  }

  /** متنِ اعلانِ حساب‌پی ⇐ آخرین پیشنهاد (اگر مبلغِ دریافتی داشت) */
  fun offer(text: String, at: Long) {
    HesabPayHint.hintOf(text.take(MAX_TEXT), at)?.let { last.value = it }
  }

  /** فاکتور بسته شد یا برگه رفت ⇐ همان پیشنهاد برای فاکتورِ بعدی نماند */
  fun consume() { last.value = null }

  private const val MAX_TEXT = 500
}
