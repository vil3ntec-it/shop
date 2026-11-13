package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.pay.PayMirror
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  PAY-07 (د۱۸) — مرزِ سکوی آینهٔ وضعیتِ حساب‌پیِ خودکار (`core/pay/PayMirror`): حالتِ هر حساب@دکان روی دیسک
 *  (SharedPreferences ِ `tohid-pay-mirror`) و خواندنِ `GET /shop/payments/mirror` پس از هر دورِ `SyncV1Worker`.
 *  نتیجه فقط در [PayStatusFlow.mirror] (نمایش) — هیچ سندِ دفتر، هیچ op.
 *
 *  ⛔ پرچمِ خاموشِ `shop.pay_status_mirror` ⇒ ۴۰۴ ⇒ هیچ نوشتنی و آینهٔ خالی ⇒ همان امروز. خطای شبکه/سرور ⇒ آینهٔ
 *  پیشین می‌ماند و دورِ همگام‌سازی را نمی‌گیرد. حالتِ آموزشی ⇒ هیچ.
 */
object PayMirrorAndroid {
  private const val PREFS = "tohid-pay-mirror"
  /** سقفِ صفحه‌ها در هر دور (۲۰۰ × ۲۰): بقیه در دورِ بعد */
  private const val MAX_PAGES = 20

  private fun key(context: Context): String =
    "m." + runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).currentKey() }.getOrDefault("")

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** حالتِ ذخیرهٔ همین حساب ⇒ صفحه‌ها (بی شبکه؛ در شروعِ برنامه) */
  fun load(context: Context): PayMirror.State {
    val s = PayMirror.decode(prefs(context).getString(key(context), null).orEmpty())
    PayStatusFlow.mirror = PayMirror.view(s.sales)
    return s
  }

  /** پس از دورِ همگام‌سازی: صفحه‌ها از cursor تا ته؛ `true` یعنی چیزی تازه نشست */
  suspend fun refresh(context: Context): Boolean {
    if (TrainingMode.on || !Backend.isReady(context)) return false
    var state = load(context)
    var changed = false
    for (i in 0 until MAX_PAGES) {
      val body = try {
        Backend.api(context).get(PayMirror.path(state.cursor))
      } catch (_: ApiFailure.NotFound) {
        return changed   //  پرچمِ خاموش (یا سرورِ کهنه) ⇒ همان امروز
      }
      val page = PayMirror.parse(body) ?: return changed
      val next = PayMirror.State(page.cursor.ifEmpty { state.cursor }, PayMirror.merge(state.sales, page.entries))
      if (next != state) {
        prefs(context).edit().putString(key(context), PayMirror.encode(next)).apply()
        state = next
        changed = changed || page.entries.isNotEmpty()
      }
      if (!page.more) break
    }
    PayStatusFlow.mirror = PayMirror.view(state.sales)
    return changed
  }
}
