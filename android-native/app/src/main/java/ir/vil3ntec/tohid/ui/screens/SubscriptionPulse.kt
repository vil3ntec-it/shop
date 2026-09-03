package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  وضعیتِ اشتراک، از زبانِ سرور — یک سرچشمه برای همهٔ صفحه‌ها.
 *
 *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
 *  گزارش شد: «چرا سربرگ قبلاً قرمز می‌شد و الان نمی‌شود؟» و در همان
 *  عکس، پروفایل می‌گفت «۷ روز مانده». دو جا، دو حرفِ مختلف.
 *
 *  علتش این بود که هر کدام از جای دیگری می‌خواندند:
 *   • سربرگ و نشانِ اشتراک از **مجوزِ امضاشدهٔ روی گوشی**
 *   • پروفایل از **سرور** (`/me/subscription`)
 *
 *  و آن مجوز با همگام‌سازیِ موفق می‌آید؛ تا نیامده، سربرگ فکر می‌کند
 *  اشتراکی نیست و آبی می‌ماند، در حالی که سرور دورهٔ آزمایشی را باز
 *  کرده و پروفایل همان را نشان می‌دهد.
 *
 *  حالا حرفِ سرور یک جا کَش می‌شود و هر سه از همین یکی می‌خوانند.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  چرا کَش: سربرگ در **هر** صفحه ساخته می‌شود. اگر هر بار از سرور
 *  بپرسد، رفتن بین دو تب یعنی دو درخواست. پس پاسخ دو دقیقه تازه شمرده
 *  می‌شود و در این مدت دوباره پرسیده نمی‌شود.
 */
object SubscriptionPulse {

  /** روزهای مانده — صفر یعنی نمی‌دانیم یا تمام شده */
  var days by mutableIntStateOf(0)
    private set

  /** آیا این همان دورهٔ آزمایشی است، نه اشتراکِ خریداری‌شده */
  var trial by mutableStateOf(false)
    private set

  /** آیا سرور می‌گوید اشتراک (یا آزمایشی) فعال است */
  var active by mutableStateOf(false)
    private set

  /** زمانِ آخرین پرسش */
  var at by mutableLongStateOf(0L)
    private set

  private const val FRESH_MS = 120_000L

  @Volatile private var asking = false

  suspend fun refresh(context: Context, force: Boolean = false) {
    if (asking) return
    if (!Backend.isReady(context)) return
    val now = System.currentTimeMillis()
    if (!force && at > 0 && now - at < FRESH_MS) return
    asking = true
    try {
      Backend.account(context).subscription().onSuccess { dto ->
        at = System.currentTimeMillis()
        days = dto.daysLeft
        trial = dto.trial
        //  «فعال» را از خودِ سرور می‌گیریم، نه از حسابِ روز: سرور
        //  می‌داند دورهٔ آزمایشی هم فعال است، ما نه.
        active = dto.status == "active" || (dto.trial && dto.daysLeft > 0)
      }
    } finally {
      asking = false
    }
  }
}
