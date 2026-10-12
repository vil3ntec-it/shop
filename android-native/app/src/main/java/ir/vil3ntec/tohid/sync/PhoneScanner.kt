package ir.vil3ntec.tohid.sync

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.channels.Channel
import org.json.JSONObject

/**
 *  گوشی به‌عنوانِ بارکدخوانِ کامپیوتر — با یک کدِ شش‌رقمی.
 *
 *  ── چطور ───────────────────────────────────────────────────────────
 *  کامپیوتر (یا هر دستگاهی که «گیرنده» شود) یک کدِ شش‌رقمی نشان می‌دهد.
 *  گوشیِ همان حساب آن کد را می‌زند؛ از آن به بعد هر بارکدی که دوربینِ
 *  گوشی در صفحهٔ فروش بخواند، به‌جای سبدِ گوشی همان لحظه در سبدِ
 *  کامپیوتر می‌نشیند.
 *
 *  راه از سوکتِ زندهٔ همگام‌سازی است (`{"type":"relay","event":"scan"}`)،
 *  نه بلوتوث: روی ویندوز، مک و لینوکس بی نصبِ درایور کار می‌کند، فاصله
 *  مهم نیست و هر چند گوشی که بخواهید هم‌زمان وصل می‌شوند. سرور فقط
 *  پیام را به دستگاه‌های **همان حساب** می‌رساند و چیزی ذخیره نمی‌کند؛
 *  کد هم نمی‌گذارد اسکنِ یک گوشی به کامپیوترِ دیگرِ همان دکان برود.
 *  ⚠️ هر دو دستگاه باید وارد حساب باشند و اینترنت داشته باشند.
 *  ──────────────────────────────────────────────────────────────────
 */
object PhoneScanner {

  /** این دستگاه گیرنده است با این کد (کامپیوتر) */
  val listenCode = mutableStateOf<String?>(null)
  /** این دستگاه فرستنده است به این کد (گوشی) */
  val sendCode = mutableStateOf<String?>(null)
  /** آخرین بارکدی که از گوشی رسید — صفحهٔ فروش با دیدنش باز می‌شود */
  val lastReceived = mutableStateOf<Pair<String, Long>?>(null)
  /** صندوقِ ورودی: صفحهٔ فروش هر چه این‌جاست را به سبد می‌برد */
  val inbox = Channel<String>(Channel.UNLIMITED)

  private var loaded = false

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-devices", 0)

  fun load(context: Context) {
    if (loaded) return
    loaded = true
    val p = prefs(context)
    listenCode.value = p.getString("listen_code", null)
    sendCode.value = p.getString("send_code", null)
  }

  /** کامپیوتر: گیرنده شدن با کدِ تازه */
  fun startListening(context: Context): String {
    val code = (100000 + java.security.SecureRandom().nextInt(900000)).toString()
    listenCode.value = code
    prefs(context).edit().putString("listen_code", code).apply()
    return code
  }

  fun stopListening(context: Context) {
    listenCode.value = null
    prefs(context).edit().remove("listen_code").apply()
  }

  /** گوشی: فرستادن به کامپیوتری که این کد را نشان می‌دهد */
  fun pairTo(context: Context, code: String): Boolean {
    val c = ir.vil3ntec.tohid.latinDigits(code).filter { it.isDigit() }
    if (c.length != 6) return false
    sendCode.value = c
    prefs(context).edit().putString("send_code", c).apply()
    return true
  }

  fun unpair(context: Context) {
    sendCode.value = null
    prefs(context).edit().remove("send_code").apply()
  }

  /** گوشی: بارکد به کامپیوتر برود؟ `false` یعنی وصل نیست — بارکد در سبدِ خودِ گوشی بنشیند */
  fun send(code: String): Boolean {
    val pair = sendCode.value ?: return false
    val socket = ir.vil3ntec.tohid.sync.v1.LiveSocket.current ?: return false
    if (!socket.connected) return false
    val msg = JSONObject().put("type", "relay").put("event", "scan").put("code", code).put("pair", pair)
    return socket.send(msg.toString())
  }

  /** از سوکت: فقط اگر کدِ همین گیرنده باشد */
  fun received(code: String, pair: String) {
    val mine = listenCode.value ?: return
    if (pair != mine || code.isBlank()) return
    inbox.trySend(code)
    lastReceived.value = code to System.currentTimeMillis()
  }
}
