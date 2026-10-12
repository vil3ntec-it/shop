package ir.vil3ntec.tohid.sync

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 *  عکسِ کالاها روی حساب — عکسی که روی گوشی گذاشته شد روی کامپیوتر و
 *  آیفون هم می‌آید، و برعکس.
 *
 *  ── چطور ───────────────────────────────────────────────────────────
 *  عکس داخلِ دفتر نمی‌رود (دفتر سنگین می‌شد)؛ سرور برای هر کالا یک
 *  ردیف دارد با `rev`ی که فقط بالا می‌رود (‎server/src/lib/product-photos.js‎).
 *    • ذخیره یا حذف ⇒ شناسه در صفِ «فرستادنی» می‌نشیند و فوراً فرستاده
 *      می‌شود؛ بی‌اینترنت می‌ماند تا بارِ بعد
 *    • سوکتِ زنده `{"event":"photos"}` می‌آورد ⇒ فهرستِ تغییرها پس از
 *      آخرین `rev` گرفته و فقط عکس‌هایی که hashشان فرق دارد دانلود می‌شوند
 *    • بارِ اول، همهٔ عکس‌های موجودِ این دستگاه یک بار فرستاده می‌شوند
 *  همه روی نخِ IO، پشتِ یک قفل — کار هیچ‌وقت دوبار هم‌زمان نمی‌رود و
 *  برنامه کند نمی‌شود.
 *
 *  `version` با هر عکسِ تازه بالا می‌رود و `ProductPhoto` با آن تازه می‌شود.
 *  ──────────────────────────────────────────────────────────────────
 */
object PhotoSync {

  /** با هر عکسِ رسیده از دستگاهِ دیگر بالا می‌رود */
  val version = mutableStateOf(0)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Mutex()

  private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("tohid-photos", 0)

  /** عکسِ این کالا روی همین دستگاه عوض شد */
  fun changed(context: Context, productId: String) {
    edit(context, "up", add = productId)
    edit(context, "del", remove = productId)
    kick(context)
  }

  /** عکسِ این کالا (یا خودِ کالا) پاک شد */
  fun removed(context: Context, productId: String) {
    edit(context, "del", add = productId)
    edit(context, "up", remove = productId)
    kick(context)
  }

  /** یک دورِ همگام‌سازی در پس‌زمینه — بی‌خطر برای چندبار صدا زدن */
  fun kick(context: Context) {
    val app = context.applicationContext
    scope.launch { runCatching { run(app) } }
  }

  /** وقتی حساب عوض شد: از اول */
  fun forget(context: Context) {
    prefs(context).edit().clear().apply()
  }

  private fun edit(context: Context, key: String, add: String? = null, remove: String? = null) {
    val p = prefs(context)
    synchronized(this) {
      val set = (p.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
      if (add != null) set += add
      if (remove != null) set -= remove
      p.edit().putStringSet(key, set).apply()
    }
  }

  suspend fun run(context: Context) = lock.withLock {
    if (!Backend.isReady(context)) return@withLock
    val api = Backend.api(context)
    val p = prefs(context)
    val device = runCatching { SyncStore(context).deviceUid }.getOrDefault("")

    //  بارِ اول: عکس‌هایی که پیش از این قابلیت روی دستگاه بودند هم بروند
    if (!p.getBoolean("seeded", false)) {
      val local = PhotoStore.localIds(context)
      if (local.isNotEmpty()) synchronized(this) {
        val set = (p.getStringSet("up", emptySet()) ?: emptySet()).toMutableSet()
        set += local
        p.edit().putStringSet("up", set).apply()
      }
      p.edit().putBoolean("seeded", true).apply()
    }

    //  ۱) فرستادن
    for (id in (p.getStringSet("up", emptySet()) ?: emptySet()).toList()) {
      val bytes = PhotoStore.bytes(context, id)
      if (bytes == null) { edit(context, "up", remove = id); continue }
      val ok = runCatching {
        val r = api.put("/sync/v1/photos/" + enc(id), buildJsonObject {
          put("data", JsonPrimitive(b64(bytes)))
          put("device_id", JsonPrimitive(device))
        })
        r["hash"]?.jsonPrimitive?.contentOrNull?.let { p.edit().putString("h:$id", it).apply() }
      }.isSuccess
      if (ok) edit(context, "up", remove = id) else return@withLock
    }
    for (id in (p.getStringSet("del", emptySet()) ?: emptySet()).toList()) {
      val ok = runCatching { api.delete("/sync/v1/photos/" + enc(id) + "?device_id=" + enc(device)) }.isSuccess
      if (ok) { edit(context, "del", remove = id); p.edit().remove("h:$id").apply() } else return@withLock
    }

    //  ۲) گرفتن
    var since = p.getLong("rev", 0L)
    var got = 0
    repeat(40) {
      val page: JsonObject = runCatching { api.get("/sync/v1/photos?since=$since") }.getOrNull() ?: return@withLock
      val items = page["items"] as? JsonArray ?: JsonArray(emptyList())
      for (el in items) {
        val o = el.jsonObject
        val id = o["product_id"]?.jsonPrimitive?.contentOrNull ?: continue
        val hash = o["hash"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val deleted = o["deleted"]?.jsonPrimitive?.booleanOrNull == true
        //  کاری که خودِ این دستگاه هنوز نفرستاده بر نسخهٔ سرور جلو است
        if (id in (p.getStringSet("up", emptySet()) ?: emptySet())) continue
        if (deleted) {
          PhotoStore.delete(context, id); p.edit().remove("h:$id").apply(); got++
        } else if (hash.isNotEmpty() && (p.getString("h:$id", "") != hash || !PhotoStore.exists(context, id))) {
          val one = runCatching { api.get("/sync/v1/photos/" + enc(id)) }.getOrNull() ?: continue
          val data = one["data"]?.jsonPrimitive?.contentOrNull ?: continue
          if (PhotoStore.saveBytes(context, id, unb64(data)).isSuccess) {
            p.edit().putString("h:$id", hash).apply(); got++
          }
        }
      }
      since = page["head"]?.jsonPrimitive?.longOrNull ?: since
      p.edit().putLong("rev", since).apply()
      if (page["has_more"]?.jsonPrimitive?.booleanOrNull != true) {
        if (got > 0) version.value = version.value + 1
        return@withLock
      }
    }
    if (got > 0) version.value = version.value + 1
  }

  private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

  /* base64 — `java.util.Base64` روی اندروید ۷ نیست؛ بیست خط کد هر دو جا کار می‌کند */
  private const val ALPHA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

  fun b64(data: ByteArray): String {
    val sb = StringBuilder((data.size + 2) / 3 * 4)
    var i = 0
    while (i < data.size) {
      val b0 = data[i].toInt() and 255
      val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 255 else -1
      val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 255 else -1
      sb.append(ALPHA[b0 shr 2])
      sb.append(ALPHA[((b0 and 3) shl 4) or ((if (b1 < 0) 0 else b1) shr 4)])
      sb.append(if (b1 < 0) '=' else ALPHA[((b1 and 15) shl 2) or ((if (b2 < 0) 0 else b2) shr 6)])
      sb.append(if (b2 < 0) '=' else ALPHA[b2 and 63])
      i += 3
    }
    return sb.toString()
  }

  fun unb64(text: String): ByteArray {
    val out = java.io.ByteArrayOutputStream(text.length * 3 / 4 + 3)
    var buffer = 0; var bits = 0
    for (c in text) {
      if (c == '=' || c == '\n' || c == '\r' || c == ' ') continue
      val v = ALPHA.indexOf(c)
      require(v >= 0) { "base64 نادرست" }
      buffer = ((buffer shl 6) or v) and 0xFFFFFF; bits += 6
      if (bits >= 8) { bits -= 8; out.write((buffer shr bits) and 0xFF) }
    }
    return out.toByteArray()
  }
}
