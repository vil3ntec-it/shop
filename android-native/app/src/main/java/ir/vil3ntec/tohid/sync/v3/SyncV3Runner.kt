package ir.vil3ntec.tohid.sync.v3

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.data.DiskWrite
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncDot
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncStatus
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/**
 *  تنظیمِ هر دستگاه: کدام موتورِ همگام‌سازی — **پیش‌فرض v1** (قاعدهٔ «پیش‌فرض خاموش»).
 *
 *  ⛔ گذر فقط وقتی صفِ موتورِ فعلی خالی است؛ وگرنه همان تغییرها از دو راه با دو
 *     شناسه می‌رفتند و دلتاها (پرداخت، باقی، مرجوعی) دو بار جمع می‌شدند.
 *  ⛔ هنوز هیچ دکمه‌ای در برنامه این را روشن نمی‌کند (فازِ ۱، آزمایشی)؛ جای دکمه کنارِ
 *     نشانِ همگام‌سازی است وقتی v3 برای آزمایشِ میدانی آماده شد.
 */
object SyncChoice {
  const val V1 = "v1"
  const val V3 = "v3"
  private const val PREFS = "tohid-sync-choice"
  private const val KEY = "engine"

  @Volatile private var cached: String? = null

  fun current(context: Context): String = cached ?: (
    context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, V1)
      .let { if (it == V3) V3 else V1 }
    ).also { cached = it }

  fun isV3(context: Context): Boolean = current(context) == V3

  /**
   *  @param pending تعدادِ منتظرِ موتورِ فعلی
   *  @return `""` یا دلیلِ نپذیرفتن
   */
  fun set(context: Context, next: String, pending: Int): String {
    if (next != V1 && next != V3) return "bad_choice"
    if (pending > 0) return "pending"
    val ok = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, next).commit()
    if (!ok) return "storage"
    cached = next
    return ""
  }
}

/** صف و حالِ v3 روی دیسک — هر فایل با `DiskWrite` (کناری + fsync + جابه‌جاییِ اتمی) */
internal class FileV3Store(private val dir: File) : V3Store {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  private val metaFile = File(dir, "state.json")
  private val queueFile = File(dir, "queue.json")
  override val journal = SyncCommit.Journal(File(dir, "journal.json"))
  /** §۹.۵: پس از بازیابیِ سرور — `replay-sent.jsonl` و `replay-seen.jsonl` در همین پوشه */
  override val replay = ReplayBook.inDir(dir, "replay")

  override fun loadMeta(): V3Meta? = read(metaFile) { json.decodeFromString(V3Meta.serializer(), it) }
  override fun saveMeta(meta: V3Meta) = write(metaFile, json.encodeToString(V3Meta.serializer(), meta))
  override fun loadQueue(): V3Queue? = read(queueFile) { json.decodeFromString(V3Queue.serializer(), it) }
  override fun saveQueue(queue: V3Queue) = write(queueFile, json.encodeToString(V3Queue.serializer(), queue))

  private fun <T> read(f: File, parse: (String) -> T): T? {
    if (!f.isFile) return null
    return runCatching { parse(f.readText(Charsets.UTF_8)) }.getOrElse {
      //  فایلِ خراب پاک نمی‌شود؛ کنار می‌رود تا دیده شود (همان قاعدهٔ `OpQueue`)
      runCatching { f.renameTo(File(f.parentFile, "${f.name}.corrupt-${System.currentTimeMillis()}")) }
      null
    }
  }

  private fun write(f: File, text: String) {
    dir.mkdirs()
    DiskWrite.text(f, text)
  }
}

/** `ApiClient`ِ برنامه (توکن، نوسازی و سرآیندهای قرارداد از همان‌جا) */
internal class HttpV3Transport(private val api: ApiClient) : V3Transport {
  override suspend fun request(method: String, path: String, body: JsonObject?): V3Response = try {
    val full = "/sync/v3$path"
    val res = if (method == "GET") api.get(full) else api.post(full, body ?: JsonObject(emptyMap()))
    V3Response(200, res)
  } catch (f: ApiFailure) {
    //  status صفر یعنی اصلاً به سرور نرسید ⇒ همان «قطعِ شبکه»
    if (f.status <= 0) throw f
    V3Response(f.status, buildJsonObject {
      put("error", JsonPrimitive(f.code))
      put("message", JsonPrimitive(f.userMessage))
    })
  }
}

/**
 *  اتصالِ موتورِ v3 به برنامه — فقط وقتی `SyncChoice` روی v3 است صدا زده می‌شود
 *  (از `SyncV1Engine.record/recover/forgetArchived` و `SyncV1Worker`).
 *
 *  ⚠️ نوشتن در دفتر زیرِ همان قفلی است که `ShopStore` دارد (`SyncV1Engine.of(app)`).
 */
class SyncV3Runner private constructor(context: Context) {
  private val app = context.applicationContext
  private var key = ""
  private var engine: SyncV3Engine? = null
  @Volatile private var shop: ShopStore? = null

  private val _status = MutableStateFlow<V3Status?>(null)
  /** برای نشانِ وضعیت (SYN-08) و نگهبانِ خروج (SYN-10) */
  val status: StateFlow<V3Status?> = _status.asStateFlow()

  private fun lock(): Any = SyncV1Engine.of(app)

  private fun accountKey(): String {
    val store = SyncStore(app)
    return SyncStateV1.currentAccount(app).ifBlank { store.accountName }.ifBlank { store.deviceUid }
  }

  @Synchronized
  private fun engine(): SyncV3Engine {
    val k = accountKey()
    engine?.let { if (k == key) return it }
    key = k
    val dir = File(app.filesDir, "sync-v3-${OpQueue.fileKey(k)}")
    val ledger = object : V3Ledger {
      override fun read(): ShopData = shop?.data?.value ?: ShopData()
      override fun write(next: ShopData) {
        val s = shop ?: throw java.io.IOException("دفتر باز نیست")
        s.applyFromServerNow(next)
      }
    }
    return SyncV3Engine(
      transport = HttpV3Transport(Backend.api(app)),
      store = FileV3Store(dir),
      deviceId = SyncStore(app).deviceUid,
      ledger = ledger,
      withLock = { block -> synchronized(lock()) { block() } },
      archived = { LedgerArchive.archivedIds(app) },
    ).load().also { engine = it }
  }

  private fun publish() {
    _status.value = engine?.status()
    //  نوار، نشان و قفلِ خروج (#230) از `SyncV1Engine.status` می‌خوانند — همان را تازه کن
    runCatching { SyncV1Engine.of(app).publish() }
  }

  /**
   *  حالِ v3 به شکلِ همان `SyncStatus`ی که نوارِ پایین، نشانِ همگام‌سازی و `LogoutGuard`
   *  می‌خوانند (SYN-08، SYN-10): صفِ منتظر، آخرین موفق، خطای آخر؛ ۴۲۶ ⇒ `holding`.
   */
  internal fun asV1Status(): SyncStatus? = runCatching {
    val e = engine()
    val s = e.status()
    SyncStatus(
      dot = SyncDot.of(
        error = s.lastError.isNotBlank(), online = Backend.isOnline(app), signedIn = Backend.tokens(app).signedIn,
        configured = AppConfig.isConfigured(app), queued = s.queued, busy = s.busy,
      ),
      queued = s.queued,
      dropped = s.rejected,
      cursor = e.meta.cursor,
      lastOkAt = s.lastOkAt,
      //  §۹.۵: هشدارِ پوششِ دفترچه — نوشته، ولی چراغ را قرمز نمی‌کند
      lastError = s.lastError.ifBlank { s.notice },
      holding = s.upgradeRequired,
      upgradeAvailable = false,
      schemaVersion = LedgerDiff.SCHEMA_VERSION,
      serverSchema = 0,
      live = false,
    )
  }.getOrNull()

  /** زیرِ قفلِ `ShopStore.writeInternal` */
  fun record(data: ShopData): Int = runCatching { engine().record(data) }.getOrDefault(0).also { publish() }

  /** همان، با opهای هدفمند (`SyncV1Engine.recordOps`) */
  fun recordOps(ops: List<ir.vil3ntec.tohid.sync.v1.SyncOp>): Int =
    runCatching { engine().recordOps(ops) }.getOrDefault(0).also { publish() }

  fun recover(onDisk: ShopData) {
    runCatching { engine().recover(onDisk) }
    publish()
  }

  fun forgetArchived(gone: Map<String, Collection<String>>): Int = engine().forget(gone).also { publish() }

  /** @return آیا دورِ همگام‌سازی تمام شد (یا آگاهانه ایستاد) — `false` یعنی بعداً دوباره */
  suspend fun work(store: ShopStore): Boolean {
    shop = store
    val e = engine()
    synchronized(lock()) { e.recover(store.data.value) }
    //  دستگاهی که هرگز با v1 همگام نشده، دفترش را یک بار «درج» می‌فرستد (نخستین بارگذاری)
    val firstUpload = SyncV1Engine.of(app).snapshotStatus().lastOkAt == 0L
    val r = e.sync(keepLocalOnly = firstUpload) { synchronized(lock()) { e.record(store.data.value) } }
    publish()
    return r.ok || r.stopped
  }

  fun queueSize(): Int = runCatching { engine().queued() }.getOrDefault(0)

  companion object {
    @Volatile private var instance: SyncV3Runner? = null

    fun of(context: Context): SyncV3Runner =
      instance ?: synchronized(this) { instance ?: SyncV3Runner(context).also { instance = it } }
  }
}
