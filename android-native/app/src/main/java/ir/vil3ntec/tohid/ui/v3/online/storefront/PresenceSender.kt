package ir.vil3ntec.tohid.ui.v3.online.storefront

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.StorefrontRepository
import ir.vil3ntec.tohid.ui.screens.StorefrontGate

/**
 *  اعلامِ «آنلاین / آفلاین / بسته …» به مشتری (§۳۱، PRES-02/08/09) — همان صفِ یک‌خانه‌ایِ صفحهٔ
 *  پیشین روی همین دستگاه (`tohid-storefront` › `pending-presence`): اعلام اول در صف می‌نشیند،
 *  با همان شناسه و زمانِ دستگاه فرستاده می‌شود، و «ثبت شد» فقط با پاسخِ سرور. خطای ماندگارِ
 *  سرور صف را پاک می‌کند (بی «ثبت شد»)؛ قطعِ اینترنت صف را نگه می‌دارد («دوباره بفرست»).
 */
@Stable
internal class PresenceSender(context: Context, private val repo: StorefrontRepository) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  var pending by mutableStateOf(StorefrontOwner.decode(prefs.getString(PENDING_KEY, null)))
    private set
  var sending by mutableStateOf(false)
    private set
  /** خطِ رسید زیرِ کارت («ثبت شد — زمانِ سرور: …»، یا چرا ثبت نشد) */
  var notice by mutableStateOf<String?>(null)
    private set
  /** شمارندهٔ «ثبت شد» برای تیکِ موفقیت */
  var confirmed by mutableStateOf(0)
    private set

  suspend fun flush(timezone: String) {
    val p = pending ?: return
    sending = true
    repo.declare(p, "")
      .onSuccess { r ->
        prefs.edit().remove(PENDING_KEY).apply()
        pending = null
        StorefrontGate.config.value = StorefrontGate.config.value?.let { c ->
          c.copy(settings = c.settings?.copy(status = r.status, presenceMode = r.status.mode, declaredUntil = r.status.declaredUntil))
        }
        notice = if (r.applied == false) StrOnline.presenceOverridden
        else StrOnline.presenceReceipt(StorefrontOwner.clock(r.serverReceivedAt, timezone))
        confirmed++
      }
      .onFailure { e ->
        if (!e.retryable) {
          prefs.edit().remove(PENDING_KEY).apply()
          pending = null
          notice = e.userMessage
        } else notice = StrOnline.presenceNotConfirmed
      }
    sending = false
  }

  suspend fun declare(mode: String, until: Long?, timezone: String) {
    val p = StorefrontOwner.pending(mode, until, System.currentTimeMillis(), kotlin.random.Random.nextLong())
    prefs.edit().putString(PENDING_KEY, StorefrontOwner.encode(p)).apply()
    pending = p
    flush(timezone)
  }

  private companion object {
    const val PREFS = "tohid-storefront"
    const val PENDING_KEY = "pending-presence"
  }
}
