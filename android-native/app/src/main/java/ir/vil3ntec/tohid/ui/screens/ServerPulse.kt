package ir.vil3ntec.tohid.ui.screens

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.data.repo.Backend

/**
 *  آیا سرور بالاست — یک پرسش، یک جواب.
 *
 *  ── چه چیزی را می‌بندد ────────────────────────────────────────────
 *  تا امروز راهی نبود که صاحب دکان بفهمد سرورش کار می‌کند یا نه. نقطهٔ
 *  سربرگ وضعیتِ **همگام‌سازی** را می‌گفت و آن هم فقط بعد از ورود به حساب
 *  دیده می‌شد — یعنی درست همان لحظه‌ای که آدم می‌خواهد بداند «سرورم بالا
 *  آمد یا نه؟» هیچ نشانه‌ای روی صفحه نبود. تنها راه این بود که ثبت‌نام
 *  کند و ببیند خطا می‌دهد یا نه.
 *
 *  حالا همان نقطه، پیش از ورود هم هست و اول از همه یک چیز را می‌گوید:
 *  به سرور می‌رسیم یا نمی‌رسیم.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── چطور می‌سنجد ──────────────────────────────────────────────────
 *  با `/health` خودِ سرور — همان مسیری که پاسخش می‌گوید سرور و دیتابیس
 *  هر دو سرِ پا هستند. مسیرِ عمومی است و توکن نمی‌خواهد، پس پیش از ورود
 *  هم کار می‌کند.
 *
 *  سنجش **گران** نیست ولی مجانی هم نیست: هر بار یک درخواست روی نتِ
 *  گوشی است. پس تا یک دقیقه پاسخِ قبلی تازه شمرده می‌شود و دوباره
 *  پرسیده نمی‌شود، مگر خودِ کاربر بخواهد. نه حلقه‌ای در کار است و نه
 *  سنجشِ دوره‌ای در پس‌زمینه.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  چرا «نتِ گوشی قطع است» حالتِ جداست: اگر گوشی آنتن ندارد، سرور
 *  بی‌گناه است. قرمز نشان دادنش یعنی فرستادنِ آدم به دنبالِ اشکالی که
 *  در سرور نیست.
 */
object ServerPulse {

  enum class State {
    /** هنوز پرسیده نشده */
    UNKNOWN,
    /** در حال پرسیدن */
    CHECKING,
    /** سرور جواب داد */
    UP,
    /** سرور جواب نداد */
    DOWN,
    /** گوشی نت ندارد — تقصیرِ سرور نیست */
    NO_NET,
    /** این نسخه به هیچ سروری بسته نشده */
    NO_SERVER,
  }

  var state by mutableStateOf(State.UNKNOWN)
    private set

  /** زمانِ آخرین سنجش */
  var at by mutableLongStateOf(0L)
    private set

  /** اگر نشد، چرا نشد — پیامِ خودِ لایهٔ شبکه */
  var note by mutableStateOf<String?>(null)
    private set

  /**
   *  نسخهٔ سروری که جواب داد.
   *
   *  چرا مهم است: سه گزارشِ جدا — کدِ شاگرد ساخته نمی‌شود، ورود با گوگل
   *  نیست، کدِ پیامکی نمی‌آید — هر سه یک ریشه داشتند: ظرفِ سرور با
   *  ایمیجِ کهنه بالا آمده بود و مسیرهای تازه رویش نبود. هیچ‌جا هم معلوم
   *  نمی‌شد. حالا نسخه روی همان برگه نوشته می‌شود.
   */
  var version by mutableStateOf("")
    private set

  /** تا این مدت، جوابِ قبلی تازه است */
  private const val FRESH_MS = 60_000L

  /**
   *  @param force درست باشد، حتی جوابِ تازه هم از نو پرسیده می‌شود —
   *    برای وقتی که کاربر خودش دکمهٔ «سنجش دوباره» را می‌زند.
   */
  suspend fun probe(context: Context, force: Boolean = false) {
    if (!AppConfig.isConfigured(context)) {
      state = State.NO_SERVER
      return
    }
    if (state == State.CHECKING) return
    val now = System.currentTimeMillis()
    if (!force && at > 0 && now - at < FRESH_MS) return

    if (!Backend.isOnline(context)) {
      state = State.NO_NET
      at = now
      note = null
      return
    }

    state = State.CHECKING
    val answer = Backend.auth(context).healthDetail()
    at = System.currentTimeMillis()
    val info = answer.valueOrNull()
    if (info != null) {
      state = State.UP
      note = null
      version = info.version
      //  سرور بالاست ولی دیتابیسش نه — این هم «وصل است» نیست
      if (info.database.isNotBlank() && info.database != "connected") {
        note = "سرور بالاست ولی دیتابیسش وصل نیست"
      }
    } else {
      state = State.DOWN
      note = answer.errorMessage()
    }
  }

  /** نشانیِ سرور، برای نشان دادن در همان برگه */
  fun address(context: Context): String = AppConfig.baseUrl(context)
}
