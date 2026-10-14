package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.config.AppConfig
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.math.min
import kotlin.math.pow

/**
 *  موتورِ Sync v1 — همان کاری که `license/sync-engine.js` در وب می‌کند.
 *
 *      ShopStore.save()  ⇒  record()  ⇒  تفاضل ⇒ صف ⇒ Push
 *      سوکتِ «changed» یا کارِ دوره‌ای  ⇒  Pull ⇒ اعمال روی دفتر
 *
 *  ── قاعده‌هایی که نباید بشکنند ──────────────────────────────────
 *  ⛔ **opی که سرور نپذیرفته از صف بیرون نمی‌رود.** فقط `applied` و
 *     `duplicate`. `rejected` بیرون می‌رود ولی با دلیل در دفترِ کنار.
 *  ⛔ **۴۲۶ یعنی نگه‌دار، نه دور بریز** (بندِ ۲۰.۶).
 *  ⛔ **حالِ هر حساب جداست.** بی این، سایهٔ حسابِ قبلی روی یک گوشیِ
 *     مشترک همهٔ ردیف‌هایش را «تغییرِ تازه» می‌دید.
 *  ⚠️ **اعمالِ opهای رسیده باید سایه را هم جلو ببرد**، وگرنه تفاضلِ
 *     بعدی همان‌ها را به سرور پس می‌فرستد — حلقهٔ بی‌پایان.
 */
class SyncV1Engine private constructor(context: Context) {

  private val app = context.applicationContext
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val store = SyncStore(app)

  /** کلیدِ حساب — نشستِ نبوده هم کلیدِ خودش را دارد. */
  private var accountKey: String = keyOf()
  private var state = SyncStateV1(app, accountKey)
  private var queue = OpQueue(app, accountKey)

  /**
   *  سه تکهٔ حال (صف، سایه، cursor) برای commitِ ژورنالی — F0-05، شرح سرِ
   *  `SyncCommit`. همیشه حال و صفِ **همین حالا**ی حساب را می‌دهد.
   */
  private val parts = object : SyncCommit.Parts {
    override fun shadow(): String = state.shadow
    override fun commit(shadow: String, cursor: Long?) {
      if (!state.commitShadow(shadow, cursor)) throw java.io.IOException("سایهٔ همگام‌سازی روی دیسک ننشست")
    }
    override fun queued(): Set<String> = queue.all().mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) = queue.push(ops)
  }
  private var commit = commitFor(accountKey)

  private fun commitFor(key: String) =
    SyncCommit(SyncCommit.Journal(java.io.File(app.filesDir, "sync-journal-${OpQueue.fileKey(key)}.json")), parts)

  private val gate = Mutex()
  private val _status = MutableStateFlow(snapshotStatus())
  val status: StateFlow<SyncStatus> = _status.asStateFlow()

  @Volatile private var busy = false
  @Volatile var liveConnected: Boolean = false
    internal set
  @Volatile private var attempt = 0

  /**
   *  کلیدِ حساب — شناسهٔ کاربر اگر معلوم باشد، وگرنه نامِ حساب، وگرنه
   *  خودِ دستگاه. هر سه پایدارند و هیچ‌کدام بینِ دو حساب مشترک نیست.
   */
  private fun keyOf(): String =
    SyncStateV1.currentAccount(app)
      .ifBlank { store.accountName }
      .ifBlank { store.deviceUid }

  /** پس از ورود یا خروج صدا زده می‌شود — کلیدها عوض می‌شوند. */
  fun rebind() {
    val fresh = keyOf()
    if (fresh == accountKey) return
    accountKey = fresh
    state = SyncStateV1(app, accountKey)
    queue = OpQueue(app, accountKey)
    commit = commitFor(accountKey)
    publish()
  }

  /**
   *  بازیابیِ commitِ نیمه‌مانده — هنگامِ شروع، با دفتری که همین حالا از
   *  دیسک خوانده شد (`ShopStore.load`). شرح سرِ `SyncCommit`.
   */
  @Synchronized
  fun recover(ledger: ShopData) {
    if (!commit.pending()) return
    if (commit.recover(ledger) == SyncCommit.Outcome.MISMATCH) {
      //  نباید پیش بیاید؛ اگر آمد، دیده شود
      state.lastError = "ژورنالِ همگام‌سازی با سایه نخواند"
    }
    publish()
  }

  /* ==========================================================
     ۱) هر تغییرِ دفتر ⇒ op
     ========================================================== */

  /**
   *  تنها درِ ساختنِ op. `ShopStore.save()` این را صدا می‌زند و هیچ
   *  نوشتنِ دیگری در دفتر نیست.
   *
   *  @return چند op ساخته شد
   */
  @Synchronized
  fun record(data: ShopData): Int {
    /*
     *  نخستین بار روی این دستگاه (سایه‌ای نیست): دفترِ امروز «تغییر» نیست،
     *  حالتِ پایه است. اگر همین‌جا تفاضل می‌گرفتیم، کلِ دفتر یک‌جا به سرور
     *  می‌رفت — دقیقاً همان کاری که قانونِ طلایی منع کرده. فرستادنِ دفترِ
     *  موجود کارِ `baseline()` است و فقط یک بار.
     *
     *  ⛔ صف و سایه با هم (F0-05): اگر میانِ این دو بسته شود، ذخیرهٔ بعدی
     *  همان تغییرها را با op_idِ تازه می‌فرستاد و `$inc` دو بار جمع می‌شد.
     *  شرح سرِ `SyncCommit`.
     */
    val made = commit.record(data)
    publish()
    return made
  }

  /**
   *  بایگانیِ دفتر: ردیف‌هایی که به بایگانی می‌روند از سایهٔ همین حساب هم
   *  بیرون می‌آیند، زیرِ همان قفلِ `record()` — شرح سرِ `SyncCommit.forget`.
   *  شکست پرتاب می‌شود تا `LedgerArchive.compact` دفتر را کوتاه نکند.
   */
  @Synchronized
  fun forgetArchived(gone: Map<String, Collection<String>>): Int = commit.forget(gone)

  /**
   *  دفترِ امروز را یک‌جا به سرور می‌دهد — فقط برای دستگاهِ نخست.
   *
   *  ⚠️ تنها جایی است که «کلِ دفتر» می‌رود و عمدی است: حسابی که تازه
   *  ساخته شده روی سرور هیچ ردیفی ندارد. دستگاهِ دوم همین را از
   *  `snapshot` می‌گیرد، نه از این راه.
   */
  fun baseline(data: ShopData): Int {
    val made = commit.baseline(data)
    publish()
    return made
  }

  /* ==========================================================
     ۲) یک دورِ کامل: Push تا ته، بعد Pull
     ========================================================== */

  /**
   *  @param read  دفترِ همین حالا
   *  @param write دفترِ تازه پس از اعمالِ opهای رسیده
   */
  suspend fun runOnce(read: () -> ShopData, write: (ShopData) -> Unit): Result<Unit> =
    gate.withLock {
      if (!Backend.isReady(app)) { publish(); return@withLock Result.success(Unit) }
      if (!Backend.isOnline(app)) { publish(); return@withLock Result.success(Unit) }
      busy = true
      publish()
      try {
        //  ژورنالِ نیمه‌مانده پیش از هر Push/Pull (F0-05)
        if (commit.pending()) synchronized(this) { commit.recover(read()) }
        val client = SyncV1Client(Backend.api(app))
        val device = store.deviceUid

        //  صف را تا ته خالی می‌کنیم، دسته‌دسته
        var guard = 0
        while (queue.size() > 0 && guard < 500) {
          guard++
          val batch = queue.batch()
          if (batch.isEmpty()) break
          val out = try {
            client.push(device, batch, queue.size())
          } catch (upgrade: SyncV1Client.UpgradeRequired) {
            //  سرور عقب‌تر است: هیچ opی پاک نمی‌شود
            state.holding = true
            state.lastError = upgrade.reason
            publish()
            return@withLock Result.failure(upgrade)
          }
          state.holding = false
          state.serverSchema = out.serverSchema
          state.upgradeAvailable = out.upgradeAvailable

          val accepted = out.results.filter { it.accepted }.map { it.opId }
          val rejected = out.results.filter { !it.accepted }
          if (accepted.isNotEmpty()) queue.ack(accepted)
          if (rejected.isNotEmpty()) {
            //  ردشده‌ها از صف بیرون می‌روند — وگرنه صف تا ابد قفل
            //  می‌کند — ولی بی‌صدا نه.
            val ids = rejected.map { it.opId }.toHashSet()
            queue.drop(batch.filter { it.opId in ids }, "rejected")
            queue.ack(ids)
          }
          state.lastPushAt = System.currentTimeMillis()
        }

        //  Pull — تا وقتی سرور بگوید چیزِ دیگری هست
        var rounds = 0
        while (rounds < 50) {
          rounds++
          val got = client.pull(device, state.cursor)
          if (got.ops.isNotEmpty()) absorbRemote(got.ops, read, write, got.cursor)
          state.cursor = got.cursor
          state.lastPullAt = System.currentTimeMillis()
          if (got.serverSchema > 0) state.serverSchema = got.serverSchema
          if (!got.hasMore) break
        }

        attempt = 0
        state.lastError = ""
        state.lastOkAt = System.currentTimeMillis()
        Result.success(Unit)
      } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
      } catch (error: Throwable) {
        attempt++
        state.lastError = error.message ?: "خطای نامشخص"
        Result.failure(error)
      } finally {
        busy = false
        publish()
      }
    }

  /**
   *  هم‌ترازیِ یک‌باره با سرور — پیش از نخستین دورِ این حساب روی این دستگاه.
   *
   *  ── چرا ───────────────────────────────────────────────────────────
   *  `firstSync` در اندروید هرگز صدا زده نمی‌شد. یعنی ردیف‌هایی که پیش از
   *  آمدنِ v1 روی گوشی بودند هرگز op نشدند و فقط موتورِ قدیم آن‌ها را
   *  می‌برد. حالا که موتورِ قدیم دیگر به دفتر دست نمی‌زند (دو موتور روی
   *  یک دفتر، `$inc`ها را دو بار می‌شمرد)، این‌جا یک بار جبران می‌شود:
   *
   *   • دفترِ خالی ⇒ همان Snapshot (گوشیِ نو).
   *   • دفترِ پر ⇒ فقط ردیف‌هایی که **سرور ندارد** به‌صورتِ INSERT می‌روند.
   *     ردیفی که سرور دارد دست نمی‌خورد؛ فرستادنِ دوباره‌اش مقدارِ تازهٔ
   *     دستگاهِ دیگر را با مقدارِ کهنهٔ این‌جا می‌پوشاند.
   *   • اگر این دستگاه هنوز هیچ opی نگرفته (`cursor == 0`)، cursor سرِ
   *     Snapshot می‌نشیند و ردیف‌هایی که فقط سرور دارد به دفتر اضافه
   *     می‌شوند — وگرنه pull از صفر همهٔ تاریخچه را روی دفتری می‌ریخت که
   *     همان‌ها را از راهِ قدیم دارد و `$inc`ها دو بار جمع می‌شدند.
   *  ──────────────────────────────────────────────────────────────────
   */
  suspend fun prepare(read: () -> ShopData, write: (ShopData) -> Unit) {
    if (state.reconciledAt > 0) return
    gate.withLock {
      if (state.reconciledAt > 0) return@withLock
      val body = SyncV1Client(Backend.api(app)).snapshot()
      val server = LedgerDiff.fromSnapshot(body, JsonObject(emptyMap()))
      val serverCursor = (body["cursor"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.content?.toLongOrNull() ?: 0L
      synchronized(this) {
        val local = LedgerDiff.toJson(read())
        val localRows = LedgerDiff.COLLECTIONS.sumOf { (local[it] as? JsonArray)?.size ?: 0 }
        if (localRows == 0) {
          val next = LedgerDiff.fromSnapshot(body, local)
          write(LedgerDiff.fromJson(next))
          writeShadow(next)
          state.cursor = serverCursor
          state.snapshotAt = System.currentTimeMillis()
        } else {
          val missing = LedgerDiff.diff(server, local).filter { it.type == SyncOp.INSERT }
          if (missing.isNotEmpty()) queue.push(missing)
          if (state.cursor == 0L) {
            val next = LedgerDiff.unionRows(local, server)
            write(LedgerDiff.fromJson(next))
            val shadow = readShadow()
            writeShadow(if (shadow != null) LedgerDiff.unionRows(shadow, server) else next)
            state.cursor = serverCursor
          }
        }
        state.reconciledAt = System.currentTimeMillis()
      }
    }
    publish()
  }

  /**
   *  نشاندنِ opهای رسیده — زیرِ **همان قفلِ** `record()`.
   *
   *  پیش‌تر «بخوان ← اعمال کن ← بنویس ← سایه» فقط زیرِ `gate` بود و
   *  `record()` قفلِ دیگری داشت. فروشی که وسطِ این چهار گام ثبت می‌شد،
   *  با نوشتنِ دفترِ کهنه از روی گوشی پاک می‌شد و سایه هم «فرستاده شد»
   *  ثبتش می‌کرد. حالا `ShopStore` هم نوشتنِ خودش را زیرِ همین قفل
   *  (`synchronized(engine)`) انجام می‌دهد.
   *
   *  سایه هم به‌جای «کلِ دفترِ امروز»، خودِ سایه به‌اضافهٔ همین opهاست؛
   *  تغییرِ محلی‌ای که هنوز op نشده، با این کار گم نمی‌شود.
   */
  @Synchronized
  internal fun absorbRemote(
    ops: List<SyncOp>,
    read: () -> ShopData,
    write: (ShopData) -> Unit,
    cursor: Long? = null,
  ) {
    /*
     *  ⛔ دفتر، سایه و cursor با هم (F0-05): اگر دفتر نوشته شود و سایه و
     *  cursor نه، همان opها دوباره گرفته و روی دفتر جمع می‌شدند و تفاضلِ
     *  بعدی آن‌ها را «تغییرِ من» به سرور پس می‌فرستاد.
     */
    try {
      commit.absorb(ops, read(), cursor, write)
    } catch (e: Throwable) {
      //  نیمه‌کاره در همین اجرا (نه بسته شدن): همان‌جا با دفترِ همین حالا
      //  تمام یا برگردانده می‌شود، پیش از آنکه ذخیرهٔ محلی‌ای برسد
      runCatching { commit.recover(read()) }
      throw e
    }
  }

  /* ==========================================================
     ۳) Snapshot — گوشیِ نو، یک بار
     ========================================================== */

  suspend fun restoreFromServer(read: () -> ShopData, write: suspend (ShopData) -> Unit): Int {
    val client = SyncV1Client(Backend.api(app))
    val body = client.snapshot()
    val next = LedgerDiff.fromSnapshot(body, LedgerDiff.toJson(read()))
    write(LedgerDiff.fromJson(next))
    writeShadow(next)
    state.cursor = (body["cursor"] as? kotlinx.serialization.json.JsonPrimitive)
      ?.content?.toLongOrNull() ?: 0L
    state.snapshotAt = System.currentTimeMillis()
    state.lastOkAt = System.currentTimeMillis()
    state.lastError = ""
    publish()
    return (body["rows"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() ?: 0
  }

  /**
   *  نخستین همگام‌سازیِ این دستگاه با این حساب.
   *
   *  دفترِ خالی ⇒ Snapshot (گوشیِ نو). دفترِ پر ⇒ یک بار فرستادنِ همان
   *  دفتر، چون این گوشی تا امروز آفلاین کار می‌کرده.
   */
  suspend fun firstSync(read: () -> ShopData, write: (ShopData) -> Unit) {
    if (state.snapshotAt > 0 || state.cursor > 0) return
    val data = read()
    val tree = LedgerDiff.toJson(data)
    val rows: Int = LedgerDiff.COLLECTIONS.sumOf { key ->
      (tree[key] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }
    if (rows == 0) { restoreFromServer(read) { write(it) }; return }
    /*
     *  ⚠️ **اول فرستادن، بعد گرفتن** — و این ترتیب عمدی است. اگر اول
     *  pull می‌کردیم، سایه روی «دفترِ ادغام‌شده» می‌نشست و ردیف‌های
     *  محلی — که سرور آن‌ها را ندارد — دیگر «تغییر» شمرده نمی‌شدند و
     *  هیچ‌وقت نمی‌رفتند.
     */
    baseline(data)
    runOnce(read, write)
  }

  /* ==========================================================
     ۴) وضعیت و عقب‌نشینی
     ========================================================== */

  /** عقب‌نشینیِ نمایی: ۲، ۴، ۸ … تا پنج دقیقه، بی‌نهایت تلاش. */
  fun backoffMs(): Long {
    val ms = min(MAX_BACKOFF_MS.toDouble(), MIN_BACKOFF_MS * 2.0.pow(attempt)).toLong()
    return ms
  }

  fun snapshotStatus(): SyncStatus {
    val queued = runCatching { queue.size() }.getOrDefault(0)
    return SyncStatus(
      dot = SyncDot.of(
        error = state.lastError.isNotBlank(),
        online = Backend.isOnline(app),
        signedIn = Backend.tokens(app).signedIn,
        configured = AppConfig.isConfigured(app),
        queued = queued,
        busy = busy,
      ),
      queued = queued,
      dropped = runCatching { queue.droppedCount() }.getOrDefault(0),
      cursor = state.cursor,
      lastOkAt = state.lastOkAt,
      lastError = state.lastError,
      holding = state.holding,
      upgradeAvailable = state.upgradeAvailable,
      schemaVersion = LedgerDiff.SCHEMA_VERSION,
      serverSchema = state.serverSchema,
      live = liveConnected,
    )
  }

  internal fun publish() { _status.value = snapshotStatus() }

  fun queueSize(): Int = queue.size()
  fun droppedOps(): List<JsonObject> = queue.dropped()
  fun errorReports(): Boolean = state.errorReports
  fun setErrorReports(on: Boolean) { state.errorReports = on }
  fun deviceId(): String = store.deviceUid

  private fun readShadow(): JsonObject? {
    val raw = state.shadow
    if (raw.isBlank()) return null
    return runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
  }

  private fun writeShadow(tree: JsonObject) {
    state.shadow = LedgerDiff.shadowOf(tree).toString()
  }

  companion object {
    const val MIN_BACKOFF_MS = 2_000L
    const val MAX_BACKOFF_MS = 5 * 60_000L

    @Volatile private var instance: SyncV1Engine? = null

    fun of(context: Context): SyncV1Engine =
      instance ?: synchronized(this) {
        instance ?: SyncV1Engine(context).also { instance = it }
      }
  }
}
