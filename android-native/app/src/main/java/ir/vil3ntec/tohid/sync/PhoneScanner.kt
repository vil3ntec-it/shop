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

  /* ======================= دوربینِ گوشی برای کامپیوتر ======================= */

  /** عکسی که از گوشی رسیده و در صندوقِ سرور منتظر است */
  data class InboxItem(val id: String, val productId: String, val at: Long)

  /** کامپیوتر: عکس‌های رسیده از گوشی، تازه‌ترین آخر */
  val inboxItems = androidx.compose.runtime.mutableStateListOf<InboxItem>()
  /** کامپیوتر: عکسی که همین حالا رسید و باید در استودیو باز شود (اگر کالایش معلوم است) */
  val openNow = mutableStateOf<InboxItem?>(null)
  /** گوشی: کامپیوتر عکسِ این کالا را خواسته — (شناسه، نام) */
  val photoRequest = mutableStateOf<Pair<String, String>?>(null)

  /** کامپیوتر ⇒ گوشی: «عکسِ این کالا را بگیر» */
  fun requestPhoto(productId: String, name: String): Boolean {
    val pair = listenCode.value ?: return false
    val socket = ir.vil3ntec.tohid.sync.v1.LiveSocket.current ?: return false
    if (!socket.connected) return false
    val msg = JSONObject().put("type", "relay").put("event", "photo-request").put("pair", pair)
      .put("product_id", productId).put("name", name)
    return socket.send(msg.toString())
  }

  /** از سوکت (کامپیوتر): عکسی در صندوق نشست */
  fun inboxArrived(id: String, pair: String, productId: String) {
    if (pair != listenCode.value || id.isBlank()) return
    if (inboxItems.any { it.id == id }) return
    val item = InboxItem(id, productId, System.currentTimeMillis())
    inboxItems.add(item)
    if (productId.isNotBlank()) openNow.value = item
  }

  /** از سوکت (گوشی): کامپیوتر عکس خواست */
  fun photoRequested(pair: String, productId: String, name: String) {
    if (pair != sendCode.value) return
    photoRequest.value = productId to name
  }

  /** کامپیوتر: عکس‌هایی که وقتِ خاموش بودن رسیدند */
  suspend fun refreshInbox(context: Context) {
    val pair = listenCode.value ?: return
    if (!ir.vil3ntec.tohid.data.repo.Backend.isReady(context)) return
    val r = runCatching { ir.vil3ntec.tohid.data.repo.Backend.api(context).get("/sync/v1/photo-inbox?pair=$pair") }.getOrNull() ?: return
    val items = (r["items"] as? kotlinx.serialization.json.JsonArray).orEmpty()
    for (el in items) {
      val o = el as? kotlinx.serialization.json.JsonObject ?: continue
      val id = (o["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content ?: continue
      val pid = (o["product_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content.orEmpty()
      if (inboxItems.none { it.id == id }) inboxItems.add(InboxItem(id, pid, System.currentTimeMillis()))
    }
  }

  /** کامپیوتر: عکسِ صندوق را به فایلِ موقت بیاور — برای استودیو */
  suspend fun download(context: Context, item: InboxItem): android.net.Uri? = runCatching {
    val r = ir.vil3ntec.tohid.data.repo.Backend.api(context).get("/sync/v1/photo-inbox/" + item.id)
    val data = (r["data"] as kotlinx.serialization.json.JsonPrimitive).content
    val dir = java.io.File(context.cacheDir, "phone-inbox").apply { mkdirs() }
    val f = java.io.File(dir, item.id + ".jpg")
    f.writeBytes(PhotoSync.unb64(data))
    android.net.Uri.fromFile(f)
  }.getOrNull()

  /** کامپیوتر: عکس برداشته شد (ذخیره یا دور ریخته) */
  suspend fun discard(context: Context, item: InboxItem) {
    inboxItems.removeAll { it.id == item.id }
    if (openNow.value?.id == item.id) openNow.value = null
    runCatching { ir.vil3ntec.tohid.data.repo.Backend.api(context).delete("/sync/v1/photo-inbox/" + item.id) }
    runCatching { java.io.File(java.io.File(context.cacheDir, "phone-inbox"), item.id + ".jpg").delete() }
  }

  /**
   *  گوشی ⇒ کامپیوتر: عکسِ گرفته‌شده را بفرست.
   *  تا ۱۶۰۰ پیکسل و JPEGِ ۸۵٪ — کیفیتِ کافی برای استودیو، و زیرِ ۲ مگابایتِ سرور.
   *  @return پیامِ خطا یا `null` اگر رفت
   */
  suspend fun sendPhoto(context: Context, source: android.net.Uri, productId: String): String? {
    val pair = sendCode.value ?: return "اول در «دستگاه‌های جانبی» کدِ کامپیوتر را بزنید"
    if (!ir.vil3ntec.tohid.data.repo.Backend.isReady(context)) return "برای فرستادن باید وارد حساب باشید"
    val bmp = ir.vil3ntec.tohid.data.PhotoStore.decode(context, source) ?: return "عکس خوانده نشد"
    val img = ir.vil3ntec.tohid.data.PhotoStudio.fitWithin(ir.vil3ntec.tohid.data.PhotoStore.toStudio(bmp), 1600)
    val bytes = java.io.ByteArrayOutputStream().use { out ->
      ir.vil3ntec.tohid.data.PhotoStore.fromStudio(img).compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
      out.toByteArray()
    }
    val device = runCatching { SyncStore(context).deviceUid }.getOrDefault("")
    return runCatching {
      ir.vil3ntec.tohid.data.repo.Backend.api(context).post("/sync/v1/photo-inbox", kotlinx.serialization.json.buildJsonObject {
        put("data", kotlinx.serialization.json.JsonPrimitive(PhotoSync.b64(bytes)))
        put("pair", kotlinx.serialization.json.JsonPrimitive(pair))
        put("product_id", kotlinx.serialization.json.JsonPrimitive(productId))
        put("device_id", kotlinx.serialization.json.JsonPrimitive(device))
      })
      null
    }.getOrElse { "نرفت: ${it.message ?: "اینترنت؟"}" }
  }

  /** از سوکت: فقط اگر کدِ همین گیرنده باشد */
  fun received(code: String, pair: String) {
    val mine = listenCode.value ?: return
    if (pair != mine || code.isBlank()) return
    inbox.trySend(code)
    lastReceived.value = code to System.currentTimeMillis()
  }
}
