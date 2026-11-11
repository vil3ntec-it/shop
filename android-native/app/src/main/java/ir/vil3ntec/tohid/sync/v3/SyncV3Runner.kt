package ir.vil3ntec.tohid.sync.v3

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.ApiFailure
import ir.vil3ntec.tohid.core.sync.ReplayBook
import ir.vil3ntec.tohid.core.sync.RestoredDevice
import ir.vil3ntec.tohid.core.sync.platform.FileReplayStore
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

/**
 *  صف و حالِ v3 روی دیسک — هر فایل با `DiskWrite` (کناری + fsync + جابه‌جاییِ اتمی). DATA-06 (`V3StateDir`): حال، ژورنال
 *  و دفترچه در [dir] (بیرون از پشتیبان؛ فایلِ جای ساختِ پیشین در نخستین خواندن به آن می‌رود)، صف در [queueDir] (با پشتیبان).
 */
internal class FileV3Store(private val dir: File, private val queueDir: File = dir) : V3Store {
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
  /** DATA-06: فایلی در همین اجرا خوانده نشد و کنار رفت ⇒ حصار تصمیم نمی‌گیرد (`V3Fence`) */
  private var bad = false
  override val unreadable: Boolean get() = bad
  private val metaFile = V3StateDir.file(dir, queueDir, V3StateDir.META)
  private val queueFile = File(queueDir, V3StateDir.QUEUE)
  override val journal = SyncCommit.Journal(V3StateDir.file(dir, queueDir, V3StateDir.JOURNAL))
  /** §۹.۵: پس از بازیابیِ سرور — `replay-sent.jsonl` کنارِ حال */
  override val replay = ReplayBook(FileReplayStore(V3StateDir.file(dir, queueDir, V3StateDir.REPLAY)))

  override fun loadMeta(): V3Meta? = read(metaFile) { json.decodeFromString(V3Meta.serializer(), it) }
  override fun saveMeta(meta: V3Meta) = write(metaFile, json.encodeToString(V3Meta.serializer(), meta))
  override fun loadQueue(): V3Queue? = read(queueFile) { json.decodeFromString(V3Queue.serializer(), it) }
  override fun saveQueue(queue: V3Queue) = write(queueFile, json.encodeToString(V3Queue.serializer(), queue))

  private fun <T> read(f: File, parse: (String) -> T): T? {
    if (!f.isFile) return null
    return runCatching { parse(f.readText(Charsets.UTF_8)) }.getOrElse {
      bad = true
      //  فایلِ خراب پاک نمی‌شود؛ کنار می‌رود تا دیده شود (همان قاعدهٔ `OpQueue`)
      runCatching { f.renameTo(File(f.parentFile, "${f.name}.corrupt-${System.currentTimeMillis()}")) }
      null
    }
  }

  private fun write(f: File, text: String) {
    f.parentFile?.mkdirs()
    DiskWrite.text(f, text)
  }
}

/** `ApiClient`ِ برنامه (توکن، نوسازی و سرآیندهای قرارداد از همان‌جا) */
internal class HttpV3Transport(private val api: ApiClient) : V3Transport {
  /** SYN-04: آخرین pull `gzip_push` داشت (پرچمِ دکانِ `sync.gzip_push`) ⇒ push ِ بزرگ فشرده (`V3Gzip`) */
  @Volatile private var gzip = false
  /** فشرده پذیرفته نشد (۴۱۵، یا ۴۰۰ ِ `bad_json` ِ پراکسی) ⇒ تا پایانِ همین اجرا ساده */
  @Volatile private var refused = false

  override suspend fun request(method: String, path: String, body: JsonObject?): V3Response = try {
    val full = "/sync/v3$path"
    val text = body?.toString()
    val packed = method == "POST" && path == "/push" && gzip && !refused && text != null && text.length >= V3Gzip.MIN_CHARS
    val res = when {
      method == "GET" -> api.get(full)
      packed -> try {
        api.postBytes(full, V3Gzip.pack(text!!), "application/json", mapOf("Content-Encoding" to "gzip"))
      } catch (f: ApiFailure) {
        //  پیش از هر اثری رد شد (بدنه باز نشد) ⇒ همان دم ساده، و از این پس ساده
        if (f.status != 415 && !(f.status == 400 && f.code == "bad_json")) throw f
        refused = true
        api.post(full, body!!)
      }
      else -> api.post(full, body ?: JsonObject(emptyMap()))
    }
    if (method == "GET" && path.startsWith("/pull")) gzip = (res["gzip_push"] as? JsonPrimitive)?.content == "true"
    V3Response(200, res)
  } catch (f: ApiFailure) {
    //  ۳-۱۳: `Retry-After` ِ سرور ⇒ دورهای خودکارِ بعدی زودتر از آن نه (همان درِ `SyncV1Worker`)
    ir.vil3ntec.tohid.sync.v1.SyncV1Worker.reconnect.serverAsked(f.retryAfterMs)
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
    //  DATA-06: حال در `filesDir/sync-v3/<کلید>/` (بیرون از پشتیبان)، صف در `sync-v3-<کلید>/` (با پشتیبان) — `V3StateDir`
    val v3Store = V3StateDir.store(app.filesDir, OpQueue.fileKey(k))
    val ledger = object : V3Ledger {
      //  دفترِ همین لحظهٔ دیسک (`ShopStore.ledgerNow`) — شرح سرِ همان
      override fun read(): ShopData = shop?.ledgerNow() ?: ShopData()
      override fun write(next: ShopData) {
        val s = shop ?: throw java.io.IOException("دفتر باز نیست")  // داده: متنِ خامِ `lastError` (ذخیره می‌شود؛ `SyncModel.classify` با نشانه‌هایش دسته می‌کند) — جزئیاتِ پشتیبانی
        s.applyFromServerNow(next)
      }
    }
    return SyncV3Engine(
      transport = HttpV3Transport(Backend.api(app)),
      store = v3Store,
      deviceId = SyncStore(app).deviceUid,
      ledger = ledger,
      withLock = { block -> synchronized(lock()) { block() } },
      archived = { LedgerArchive.archivedIds(LedgerArchive.readArchive(app)) },
      //  SYN-09: پرچمِ `app.multi_server` خاموش ⇒ null ⇒ همان امروز
      hub = ir.vil3ntec.tohid.platform.HubRelayAndroid.of(app),
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
    val online = Backend.isOnline(app)
    //  SYN-12: قطعیِ کوتاه یا شبکهٔ بی‌نت سرخ نمی‌کند؛ قطعیِ ماندگار اعلانِ آرام است
    val outage = e.outage(online)
    SyncStatus(
      dot = SyncDot.of(
        error = s.lastError.isNotBlank(), online = online, signedIn = Backend.tokens(app).signedIn,
        configured = AppConfig.isConfigured(app), queued = s.queued, busy = s.busy, alarm = outage.alarm,
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
      outage = outage.kind,
    )
  }.getOrNull()

  /** زیرِ قفلِ `ShopStore.writeInternal` */
  fun record(data: ShopData): Int = runCatching { engine().record(data) }.getOrDefault(0).also { publish() }

  /** همان، با opهای هدفمند (`SyncV1Engine.recordOps`) */
  fun recordOps(ops: List<ir.vil3ntec.tohid.sync.v1.SyncOp>): Int =
    runCatching { engine().recordOps(ops) }.getOrDefault(0).also { publish() }

  /** v3 روی این حساب راه افتاده (از این به بعد ذخیره op می‌سازد) — `ShopStore.restoreBackup`؛ نخوانده ⇒ `true` (امن) */
  fun initialized(): Boolean = runCatching { engine().meta.initialized }.getOrDefault(true)

  fun recover(onDisk: ShopData) {
    runCatching { engine().recover(onDisk) }
    //  DATA-06: پس از ژورنال، پیش از نخستین ذخیره (پایهٔ سایه = همین دفتر)؛ شکست ⇒ [work] دوباره، پیش از هر Pull
    runCatching { fence(onDisk) }
    publish()
  }

  /**
   *  DATA-06: حالِ v3 ِ این حساب از پشتیبان/رونوشتِ دستگاهِ دیگر آمد؟ (`V3Fence`، `RestoredDevice`) — هنگامِ باز شدنِ دفتر،
   *  پس از هر ورود (`AccountBind`) و پیش از هر دور ([work]). شناسهٔ ناخوانا ⇒ هیچ تصمیمی. ننشست ⇒ پرتاب.
   */
  fun fence(ledger: ShopData): RestoredDevice.Verdict = fenceOn(engine(), ledger)

  private fun fenceOn(e: SyncV3Engine, ledger: ShopData): RestoredDevice.Verdict {
    val sync = SyncStore(app)
    val device = runCatching { sync.deviceUid }.getOrDefault("")
    val restoredAt = runCatching { sync.restoredAt }.getOrDefault(0L)
    return e.fence(device, restoredAt, ledger)
  }

  /** DATA-06: این حساب هنوز «بازسازی از سرور» ِ پس از بازیابی را می‌خواهد (Pull بسته) */
  fun restoredPending(): Boolean = runCatching { engine().meta.restoredRebuild }.getOrDefault(false)

  fun forgetArchived(gone: Map<String, Collection<String>>): Int = engine().forget(gone).also { publish() }

  /** @return آیا دورِ همگام‌سازی تمام شد (یا آگاهانه ایستاد) — `false` یعنی بعداً دوباره */
  suspend fun work(store: ShopStore): Boolean {
    //  OPS-07: دفترِ آموزشی، یا هر دفتری در حالتِ آموزشی، هرگز به موتور نمی‌رسد
    if (store.training || !ir.vil3ntec.tohid.data.TrainingMode.networkAllowed()) return false
    shop = store
    val e = engine()
    //  دیسک جلوتر بود و خوانده نشد (`ledgerNow`، فقط با پرچمِ `shop.ledger_merge`) ⇒ هیچ چیزی نشانده
    //  نمی‌شود؛ دورِ بعد دوباره. هر خطای دیگر همان رفتارِ `main` (بالا می‌رود، بی `publish`).
    try {
      synchronized(lock()) {
        val now = store.ledgerNow()
        e.recover(now)
        //  DATA-06: حالِ دستگاهِ دیگر ⇒ حصار پیش از هر Pull؛ ننشست (دیسک) ⇒ این دور هیچ، cursor ِ کهنه هرگز
        if (runCatching { fenceOn(e, now) }.isFailure) { publish(); return false }
      }
      //  دستگاهی که هرگز با v1 همگام نشده، دفترش را یک بار «درج» می‌فرستد (نخستین بارگذاری)
      val firstUpload = SyncV1Engine.of(app).snapshotStatus().lastOkAt == 0L
      val r = e.sync(keepLocalOnly = firstUpload) { synchronized(lock()) { e.record(store.ledgerNow()) } }
      publish()
      return r.ok || r.stopped
    } catch (stale: ShopStore.StaleLedger) {
      publish()
      return false
    }
  }

  fun queueSize(): Int = runCatching { engine().queued() }.getOrDefault(0)

  /** SYN-12: `SyncV1Engine.rebind` (ورود، خروج، دکانِ دیگر) — موتورِ ساخته‌شده رشتهٔ شکستش را فراموش می‌کند؛ بی ساختنِ موتور */
  fun forgetOutage() { engine?.forgetOutage() }

  /** opهای ردشدهٔ همین حساب روی همین دستگاه (نشانِ «سرور نپذیرفت» ِ فاکتور، `data/RejectedDocs`) */
  fun rejected(): List<V3Entry> = runCatching { engine().rejected() }.getOrDefault(emptyList())

  /** «کنار بگذار»/«اصلاح و ثبت» ِ صاحب روی همین دفتر (`data/SetAside`)؛ همان دفترِ [work] و همان قفل */
  fun setAside(store: ShopStore, decisions: List<ir.vil3ntec.tohid.core.sync.ConflictInbox.Mine>, book: List<JsonObject>): Int {
    if (store.training) return 0
    shop = store
    return engine().setAside(decisions, book, marks()).also { publish() }
  }

  /** `SetAsideMarks` ِ همین حساب در پوشهٔ v3 ِ همان (DATA-SAFETY §۷) */
  private fun marks() = File(File(app.filesDir, "sync-v3-${OpQueue.fileKey(accountKey())}"), ir.vil3ntec.tohid.sync.v1.SetAsideMarks.V3_NAME)
    .let { f -> ir.vil3ntec.tohid.sync.v1.SetAsideMarks({ f.takeIf { it.isFile }?.readText() }, { f.parentFile?.mkdirs(); DiskWrite.text(f, it) }) }

  /** opهای تصمیم‌هایی که بیرون از دفتر «نشست» نشان خورده‌اند — `SetAside.openRejected` */
  fun settledOps(): Set<String> = runCatching { marks().read().ops }.getOrDefault(emptySet())

  companion object {
    @Volatile private var instance: SyncV3Runner? = null

    fun of(context: Context): SyncV3Runner =
      instance ?: synchronized(this) { instance ?: SyncV3Runner(context).also { instance = it } }
  }
}
