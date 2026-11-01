package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.courier.CourierQueue
import ir.vil3ntec.tohid.core.courier.MissionCard
import ir.vil3ntec.tohid.core.courier.MissionView
import ir.vil3ntec.tohid.core.courier.QueueState
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.net.ApiResult
import ir.vil3ntec.tohid.data.repo.Backend
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 *  نگه‌داریِ حالتِ پیک روی گوشی — DLV-10 (کار کامل در حالتِ آفلاین).
 *
 *  سه چیز، جدا از دفترِ دکان (پوشه و ترجیحاتِ خودش؛ دفتر دست نمی‌خورد):
 *    • صفِ رویدادها (`QueueState`): تا سرور «رسید» نگوید پاک نمی‌شود.
 *    • کارت‌های آخرین پرسش: برای دیدنِ مأموریت‌ها بی اینترنت. کارتِ
 *      پایان‌یافته بی نشانی و مشتری نگه داشته می‌شود (`MissionView.forStorage`).
 *    • عکس‌های در انتظار: فایلِ JPEG + شناسهٔ رویداد؛ پس از رسیدن پاک.
 */
class CourierStore(context: Context) {

  private val app = context.applicationContext
  private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  fun queue(): QueueState = CourierCodec.queue(prefs.getString(K_QUEUE, null))
  fun saveQueue(q: QueueState) { prefs.edit().putString(K_QUEUE, CourierCodec.queueJson(q)).commit() }

  fun cards(scope: String): List<MissionCard> = CourierCodec.cards(prefs.getString(cardsKey(scope), null))
  fun saveCards(scope: String, cards: List<MissionCard>) {
    prefs.edit().putString(cardsKey(scope), CourierCodec.cardsJson(MissionView.forStorage(cards))).apply()
  }

  var courierName: String
    get() = prefs.getString(K_NAME, "").orEmpty()
    set(v) { prefs.edit().putString(K_NAME, v).apply() }

  /** دکان «تماس با مشتری» را روشن کرده؟ (از `/courier/me`؛ آفلاین همان آخرین پاسخ) */
  var callEnabled: Boolean
    get() = prefs.getBoolean(K_CALL, false)
    set(v) { prefs.edit().putBoolean(K_CALL, v).apply() }

  /** «مسیرِ پیشنهادی» (DLV-04) و «امتیاز» (DLV-18) روشن‌اند؟ (از `/courier/me`؛ آفلاین همان آخرین پاسخ) */
  var routeEnabled: Boolean
    get() = prefs.getBoolean(K_ROUTE, false)
    set(v) { prefs.edit().putBoolean(K_ROUTE, v).apply() }
  var ratingsEnabled: Boolean
    get() = prefs.getBoolean(K_RATINGS, false)
    set(v) { prefs.edit().putBoolean(K_RATINGS, v).apply() }

  /** مأموریت‌هایی که پیک امتیازشان را داده (دکمه دوباره دیده نشود؛ سرور هم دومی را نمی‌پذیرد) */
  fun rated(): Set<String> = prefs.getStringSet(K_RATED, emptySet()).orEmpty().toSet()
  fun markRated(assignmentId: String) {
    prefs.edit().putStringSet(K_RATED, (rated() + assignmentId).toSet()).apply()
  }

  /** آخرین بار که سرور جواب داد (ساعتِ گوشی؛ فقط برای نمایش) */
  var lastSyncAt: Long
    get() = prefs.getString(K_LAST, "0")?.toLongOrNull() ?: 0L
    set(v) { prefs.edit().putString(K_LAST, v.toString()).apply() }

  /* ------------------------------ عکس‌ها ------------------------------ */

  data class PendingPhoto(val eventId: String, val assignmentId: String, val deviceTime: Long)

  private fun photoDir(): File = File(app.filesDir, "courier-photos").apply { if (!exists()) mkdirs() }
  fun photoFile(eventId: String): File = File(photoDir(), "$eventId.jpg")

  fun photos(): List<PendingPhoto> = runCatching {
    val arr = JSONArray(prefs.getString(K_PHOTOS, "[]") ?: "[]")
    (0 until arr.length()).map { i ->
      val o = arr.getJSONObject(i)
      PendingPhoto(o.optString("eventId"), o.optString("assignmentId"), o.optLong("deviceTime"))
    }.filter { it.eventId.isNotBlank() && photoFile(it.eventId).exists() }
  }.getOrDefault(emptyList())

  private fun savePhotos(list: List<PendingPhoto>) {
    val arr = JSONArray()
    list.forEach { arr.put(JSONObject().put("eventId", it.eventId).put("assignmentId", it.assignmentId).put("deviceTime", it.deviceTime)) }
    prefs.edit().putString(K_PHOTOS, arr.toString()).commit()
  }

  fun addPhoto(p: PendingPhoto, jpeg: ByteArray) {
    photoFile(p.eventId).writeBytes(jpeg)
    savePhotos(photos().filterNot { it.eventId == p.eventId } + p)
  }

  fun dropPhoto(eventId: String) {
    runCatching { photoFile(eventId).delete() }
    savePhotos(photos().filterNot { it.eventId == eventId })
  }

  /** خروج از حسابِ پیک: صف، کارت‌ها و عکس‌ها از گوشی پاک — نشانیِ مشتری نمی‌ماند */
  fun wipe() {
    prefs.edit().clear().commit()
    runCatching { photoDir().listFiles()?.forEach { it.delete() } }
  }

  private fun cardsKey(scope: String) = if (scope == "done") K_DONE else K_ACTIVE

  companion object {
    private const val PREFS = "tohid-courier"
    private const val K_QUEUE = "queue"
    private const val K_ACTIVE = "cards_active"
    private const val K_DONE = "cards_done"
    private const val K_NAME = "name"
    private const val K_CALL = "call_enabled"
    private const val K_ROUTE = "route_enabled"
    private const val K_RATINGS = "ratings_enabled"
    private const val K_RATED = "rated"
    private const val K_LAST = "last_sync"
    private const val K_PHOTOS = "photos"
  }
}

/**
 *  فرستادنِ صف و عکس‌ها، و تازه کردنِ کارت‌ها. یک کار در یک زمان (`Mutex`).
 *
 *  قاعدهٔ خطا: شبکه/سرور/نشست ⇒ صف می‌ماند و بعداً دوباره؛ `courier_scope`،
 *  `delivery_disabled` و `not_a_courier` ⇒ می‌ماند و پیامِ روشن نشان داده
 *  می‌شود (هیچ رویدادی بی‌صدا دور ریخته نمی‌شود).
 */
object CourierSync {

  data class Outcome(
    val sent: Int = 0,
    val failure: ApiFailure? = null,
    val photoErrors: List<String> = emptyList(),
  ) {
    val ok: Boolean get() = failure == null
  }

  private val lock = Mutex()

  /** کدهای عکس که با دوباره فرستادن درست نمی‌شوند ⇒ عکس کنار می‌رود و پیامش می‌ماند */
  private val PHOTO_FINAL = setOf(
    "photo_not_allowed_now", "photo_consent_missing", "too_many_photos", "event_key_reused",
    "assignment_not_found", "parcel_only_required", "photo_too_large", "bad_photo",
  )

  suspend fun flush(context: Context, store: CourierStore = CourierStore(context)): Outcome = lock.withLock {
    val repo = Backend.courier(context)
    val device = runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).deviceUid }.getOrDefault("")
    var sent = 0
    var failure: ApiFailure? = null

    //  ۱) صفِ رویدادها، بسته به بسته
    while (failure == null) {
      val q = store.queue()
      val batch = CourierQueue.batch(q)
      if (batch.isEmpty()) break
      val r = repo.send(device, batch)
      val results = r.valueOrNull()
      if (results == null) { failure = errorOf(r); break }
      val next = CourierQueue.applyResults(store.queue(), batch, results)
      store.saveQueue(next)
      val left = batch.count { b -> next.pending.any { it.eventId == b.eventId } }
      sent += batch.size - left
      if (left > 0) break   // پاسخِ ناقص ⇒ بقیه بعداً، بی حلقهٔ بی‌پایان
    }

    //  ۲) عکس‌ها — پس از رویدادها، چون عکس فقط در «رسیدم/تحویل» پذیرفته است
    val photoErrors = ArrayList<String>()
    if (failure == null) {
      for (p in store.photos()) {
        val bytes = runCatching { store.photoFile(p.eventId).readBytes() }.getOrNull()
        if (bytes == null) { store.dropPhoto(p.eventId); continue }
        val r = repo.photo(p.assignmentId, p.eventId, PhotoSyncB64.encode(bytes), p.deviceTime)
        if (r.isSuccess) { store.dropPhoto(p.eventId); continue }
        val err = errorOf(r)
        if (err.code in PHOTO_FINAL) { store.dropPhoto(p.eventId); photoErrors += err.userMessage }
        else { failure = err; break }
      }
    }

    //  ۳) کارت‌ها
    if (failure == null) {
      repo.me().onSuccess {
        store.courierName = it.name; store.callEnabled = it.callEnabled
        store.routeEnabled = it.routeEnabled; store.ratingsEnabled = it.ratingsEnabled
      }
      val a = repo.missions("active")
      val cards = a.valueOrNull()
      if (cards != null) store.saveCards("active", cards) else failure = errorOf(a)
      if (failure == null) repo.missions("done").onSuccess { store.saveCards("done", it) }
      if (failure == null) store.lastSyncAt = System.currentTimeMillis()
    }
    Outcome(sent, failure, photoErrors)
  }

  private fun errorOf(r: ApiResult<*>): ApiFailure =
    (r as? ApiResult.Failure)?.error ?: ApiFailure.InvalidResponse()
}

/** base64ِ همان `PhotoSync` — `java.util.Base64` روی اندروید ۷ نیست */
private object PhotoSyncB64 {
  fun encode(bytes: ByteArray): String = ir.vil3ntec.tohid.sync.PhotoSync.b64(bytes)
}
