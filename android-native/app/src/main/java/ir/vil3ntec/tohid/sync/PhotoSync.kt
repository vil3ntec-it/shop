package ir.vil3ntec.tohid.sync

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.PhotoSyncPlan
import ir.vil3ntec.tohid.core.sync.PhotoThumbBackfill
import ir.vil3ntec.tohid.data.PhotoStore
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
 *    • WEB-15: پس از هر عکس نسخهٔ کارتِ ویترینش (۳۲۰px) با درِ جدای کارت می‌رود؛ عکسِ کهنهٔ بی کارت سرِ فرصت
 *      کارت می‌گیرد (`PhotoThumbBackfill`) — اصل دوباره بالا نمی‌رود
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

    //  SYN-14: اولویتِ پایین‌تر از دفتر — تا همگام‌سازیِ دفتر مشغول است صبر (رویهم حداکثر یک دقیقه در هر دور)
    val waitedUp = yieldToLedger(context, 0L)

    //  ۱) فرستادن — WEB-15: درست پس از رسیدنِ اصل، نسخهٔ کارتِ ویترینش (`PhotoStore.thumb`) با درِ جدای کارت؛
    //  کارت نشد ⇒ اصل همان رسیده است و `backfill` سرِ فرصت دوباره می‌فرستد
    for (id in (p.getStringSet("up", emptySet()) ?: emptySet()).toList()) {
      val bytes = PhotoStore.bytes(context, id)
      if (bytes == null) { edit(context, "up", remove = id); continue }
      val ok = runCatching {
        val r = api.put("/sync/v1/photos/" + enc(id), buildJsonObject {
          put("data", JsonPrimitive(b64(bytes)))
          put("device_id", JsonPrimitive(device))
        })
        r["hash"]?.jsonPrimitive?.contentOrNull?.let { h ->
          p.edit().putString("h:$id", h).apply()
          PhotoStore.thumb(bytes)?.let { sendThumb(api, id, h, it) }
        }
      }.isSuccess
      if (ok) edit(context, "up", remove = id) else return@withLock
    }
    for (id in (p.getStringSet("del", emptySet()) ?: emptySet()).toList()) {
      val ok = runCatching { api.delete("/sync/v1/photos/" + enc(id) + "?device_id=" + enc(device)) }.isSuccess
      if (ok) { edit(context, "del", remove = id); p.edit().remove("h:$id").apply() } else return@withLock
    }

    //  ۲) گرفتن — SYN-14: نشانه فقط تا پیش از نخستین عکسِ ناتمام جلو می‌رود (`PhotoSyncPlan.cursor`) تا دانلودِ
    //  قطع‌شده دورِ بعد از همان‌جا ادامه یابد؛ عکسی که `MAX_TRIES` بار نشد رد می‌شود تا صف بسته نماند
    var waited = waitedUp
    val got = kotlin.run pull@{
      var since = p.getLong("rev", 0L)
      var n = 0
      repeat(40) {
        waited = yieldToLedger(context, waited)
        val page: JsonObject = runCatching { api.get("/sync/v1/photos?since=$since") }.getOrNull() ?: return@pull n
        val items = page["items"] as? JsonArray ?: JsonArray(emptyList())
        val done = ArrayList<PhotoSyncPlan.Item>(items.size)
        for (el in items) {
          val o = el.jsonObject
          val rev = o["rev"]?.jsonPrimitive?.longOrNull ?: since
          val id = o["product_id"]?.jsonPrimitive?.contentOrNull
          if (id == null) { done += PhotoSyncPlan.Item(rev, true); continue }
          val hash = o["hash"]?.jsonPrimitive?.contentOrNull.orEmpty()
          val deleted = o["deleted"]?.jsonPrimitive?.booleanOrNull == true
          //  کاری که خودِ این دستگاه هنوز نفرستاده بر نسخهٔ سرور جلو است
          if (id in (p.getStringSet("up", emptySet()) ?: emptySet())) { done += PhotoSyncPlan.Item(rev, true); continue }
          if (deleted) {
            PhotoStore.delete(context, id); p.edit().remove("h:$id").apply(); n++
            done += PhotoSyncPlan.Item(rev, true)
          } else if (hash.isNotEmpty() && (p.getString("h:$id", "") != hash || !PhotoStore.exists(context, id))) {
            val data = runCatching { api.get("/sync/v1/photos/" + enc(id)) }.getOrNull()?.get("data")?.jsonPrimitive?.contentOrNull
            val ok = data != null && runCatching { PhotoStore.saveBytes(context, id, unb64(data)).isSuccess }.getOrDefault(false)
            if (ok) {
              p.edit().putString("h:$id", hash).apply(); n++
              done += PhotoSyncPlan.Item(rev, true)
            } else {
              val (stuckRev, tries) = PhotoSyncPlan.failed(p.getLong("stuck_rev", -1L), p.getInt("stuck_tries", 0), rev)
              p.edit().putLong("stuck_rev", stuckRev).putInt("stuck_tries", tries).apply()
              done += PhotoSyncPlan.Item(rev, PhotoSyncPlan.giveUp(stuckRev, tries, rev))
              if (!done.last().done) break
            }
          } else {
            done += PhotoSyncPlan.Item(rev, true)
          }
        }
        since = PhotoSyncPlan.cursor(since, done, page["head"]?.jsonPrimitive?.longOrNull ?: since)
        p.edit().putLong("rev", since).apply()
        if (PhotoSyncPlan.stopped(done) || page["has_more"]?.jsonPrimitive?.booleanOrNull != true) return@pull n
      }
      n
    }
    arrived(context, got)

    //  ۳) WEB-15: کارتِ ویترین برای عکس‌های پیش از این قابلیت — سرِ فرصت و با سقف (`PhotoThumbBackfill`)
    backfill(context)
  }

  /**
   *  WEB-15: `PUT /sync/v1/photos/:id/thumb` (فقط دکان، جدا از درِ عکسِ مشترک با پمپ) با `of` = hash ِ همان اصل ⇒ کدِ HTTP؛
   *  `0` ⇒ شبکه نشد. هرگز خطا نمی‌اندازد (جز لغوِ کوروتین): کارت هیچ‌وقت فرستادنِ عکس را نمی‌خواباند.
   */
  private suspend fun sendThumb(api: ApiClient, id: String, of: String, thumb: ByteArray): Int = try {
    api.put("/sync/v1/photos/" + enc(id) + "/thumb", buildJsonObject {
      put("data", JsonPrimitive(b64(thumb)))
      put("of", JsonPrimitive(of))
    })
    200
  } catch (e: CancellationException) {
    throw e
  } catch (e: ApiFailure) {
    e.status
  } catch (_: Exception) {
    0
  }

  /**
   *  WEB-15: نسخهٔ کارت برای عکسی که این دستگاه **همان** اصلش را دارد (`h:<id>` = hash ِ سرور) و سرور کارتش را ندارد.
   *  نشانهٔ جدا (`trev`)، حداکثر `BATCH` در هر دور با فاصله، و اگر کار ماند دورِ بعد `NEXT_RUN_MS` بعد. سرورِ کهنه که
   *  `thumb` را در فهرست نمی‌دهد ⇒ هیچ کاری (نشانه هم جلو نمی‌رود تا پس از به‌روزرسانیِ سرور از همان‌جا).
   */
  private suspend fun backfill(context: Context) {
    val api = Backend.api(context)
    val p = prefs(context)
    var waited = 0L
    val io = object : PhotoThumbBackfill.Io {
      override suspend fun page(since: Long): PhotoThumbBackfill.Page? {
        waited = yieldToLedger(context, waited)
        val o = runCatching { api.get("/sync/v1/photos?since=$since") }.getOrNull() ?: return null
        val items = o["items"] as? JsonArray ?: return null
        val rows = ArrayList<PhotoThumbBackfill.Row>(items.size)
        for (el in items) {
          val x = el as? JsonObject ?: continue
          val thumb = x["thumb"]?.jsonPrimitive?.booleanOrNull ?: return null
          rows += PhotoThumbBackfill.Row(
            productId = x["product_id"]?.jsonPrimitive?.contentOrNull ?: continue,
            hash = x["hash"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            rev = x["rev"]?.jsonPrimitive?.longOrNull ?: continue,
            deleted = x["deleted"]?.jsonPrimitive?.booleanOrNull == true,
            thumb = thumb,
          )
        }
        return PhotoThumbBackfill.Page(rows, o["head"]?.jsonPrimitive?.longOrNull ?: since, o["has_more"]?.jsonPrimitive?.booleanOrNull == true)
      }

      override fun localHash(productId: String): String? =
        p.getString("h:$productId", null)?.takeIf {
          PhotoStore.exists(context, productId) && productId !in (p.getStringSet("up", emptySet()) ?: emptySet())
        }

      override fun build(productId: String): ByteArray? = PhotoStore.bytes(context, productId)?.let { PhotoStore.thumb(it) }

      override suspend fun send(productId: String, of: String, thumb: ByteArray): Int = sendThumb(api, productId, of, thumb)

      override suspend fun pause(ms: Long) = delay(ms)

      override fun save(cursor: Long) { p.edit().putLong("trev", cursor).apply() }
    }
    val out = PhotoThumbBackfill.run(p.getLong("trev", 0L), io)
    if (out.more) scope.launch { delay(PhotoThumbBackfill.NEXT_RUN_MS); runCatching { run(context) } }
  }

  /** عکسی رسید ⇒ صفحه‌ها تازه و ربات (اگر روشن است) فقط تازه‌ها را */
  private fun arrived(context: Context, got: Int) {
    if (got > 0) { version.value = version.value + 1; if (ir.vil3ntec.tohid.data.PhotoBot.autoEnabled(context)) ir.vil3ntec.tohid.data.PhotoBot.start(context, onlyNew = true) }
  }

  /** SYN-14 — تا دفتر مشغول است صبر (`PhotoSyncPlan.waitMs`)؛ @return صبرِ رویهمِ همین دور */
  private suspend fun yieldToLedger(context: Context, waitedMs: Long): Long {
    var waited = waitedMs
    while (true) {
      val busy = runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).ledgerBusy }.getOrDefault(false)
      val w = PhotoSyncPlan.waitMs(busy, waited)
      if (w <= 0L) return waited
      delay(w)
      waited += w
    }
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
      require(v >= 0) { "base64 نادرست" }  // داده: خطای برنامه‌نویس (ورودیِ نادرستِ کد، نه کاربر)
      buffer = ((buffer shl 6) or v) and 0xFFFFFF; bits += 6
      if (bits >= 8) { bits -= 8; out.write((buffer shr bits) and 0xFF) }
    }
    return out.toByteArray()
  }
}
