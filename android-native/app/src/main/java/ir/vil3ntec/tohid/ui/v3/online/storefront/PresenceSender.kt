package ir.vil3ntec.tohid.ui.v3.online.storefront

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.core.storefront.PresenceDeclaration
import ir.vil3ntec.tohid.core.storefront.StorefrontOutbox
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.data.repo.PresenceResultDto
import ir.vil3ntec.tohid.data.repo.StorefrontQueue
import ir.vil3ntec.tohid.ui.screens.StorefrontGate

/**
 *  اعلامِ «آنلاین / آفلاین / بسته …» به مشتری (§۳۱، PRES-02/08/09). اعلام اول در صفِ پایدارِ ویترین
 *  (`StorefrontQueue` ⇐ core/`StorefrontOutbox`) می‌نشیند — **بسته به دکانِ همین نشست** — با همان شناسه
 *  و زمانِ دستگاه فرستاده می‌شود، و «ثبت شد» فقط با پاسخِ سرور. اعلامِ دکانِ دیگر (پس از عوض شدنِ حساب
 *  روی همین دستگاه) نه دیده می‌شود نه فرستاده؛ دکانِ نامعلوم ⇒ چیزی در صف نمی‌نشیند. فقط ردِ ماندگارِ
 *  سرور صف را پاک می‌کند (بی «ثبت شد»)؛ بی‌نت، ۵xx، نشستِ تمام‌شده و ۴۲۹ صف را نگه می‌دارند
 *  («دوباره بفرست»؛ `StorefrontOutbox.presenceKept`). آزمون: `PresenceSenderDesktopTest`.
 */
@Stable
internal class PresenceSender(context: Context, private val send: suspend (PresenceDeclaration) -> ApiResult<PresenceResultDto>) {
  private val app = context.applicationContext

  /** اعلامِ نرسیدهٔ دکانِ همین نشست؛ از `StorefrontQueue.entries` خوانده می‌شود، پس صفحه با صف تازه می‌شود */
  val pending: PresenceDeclaration? get() = StorefrontQueue.presence(app)
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
    send(p)
      .onSuccess { r ->
        StorefrontQueue.discard(app, p.eventId)
        StorefrontGate.config.value = StorefrontGate.config.value?.let { c ->
          c.copy(settings = c.settings?.copy(status = r.status, presenceMode = r.status.mode, declaredUntil = r.status.declaredUntil))
        }
        notice = if (r.applied == false) StrOnline.presenceOverridden
        else StrOnline.presenceReceipt(StorefrontOwner.clock(r.serverReceivedAt, timezone))
        confirmed++
      }
      .onFailure { e ->
        if (StorefrontOutbox.presenceKept(e.status, e.code, e.retryable)) notice = StrOnline.presenceNotConfirmed
        else {
          StorefrontQueue.discard(app, p.eventId)
          notice = e.userMessage
        }
      }
    sending = false
  }

  suspend fun declare(mode: String, until: Long?, timezone: String) {
    val p = StorefrontOwner.pending(mode, until, System.currentTimeMillis(), kotlin.random.Random.nextLong())
    if (!StorefrontQueue.putPresence(app, p)) {
      notice = StrOnline.presenceNoShop
      return
    }
    flush(timezone)
  }
}
