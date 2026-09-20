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
    val tree = LedgerDiff.toJson(data)
    val shadow = readShadow()
    if (shadow == null) {
      /*
       *  نخستین بار روی این دستگاه: دفترِ امروز «تغییر» نیست، حالتِ
       *  پایه است. اگر همین‌جا تفاضل می‌گرفتیم، کلِ دفتر یک‌جا به سرور
       *  می‌رفت — دقیقاً همان کاری که قانونِ طلایی منع کرده. فرستادنِ
       *  دفترِ موجود کارِ `baseline()` است و فقط یک بار.
       */
      writeShadow(tree)
      publish()
      return 0
    }
    val ops = LedgerDiff.diff(shadow, tree)
    if (ops.isEmpty()) return 0
    queue.push(ops)
    writeShadow(tree)
    publish()
    return ops.size
  }

  /**
   *  دفترِ امروز را یک‌جا به سرور می‌دهد — فقط برای دستگاهِ نخست.
   *
   *  ⚠️ تنها جایی است که «کلِ دفتر» می‌رود و عمدی است: حسابی که تازه
   *  ساخته شده روی سرور هیچ ردیفی ندارد. دستگاهِ دوم همین را از
   *  `snapshot` می‌گیرد، نه از این راه.
   */
  fun baseline(data: ShopData): Int {
    val tree = LedgerDiff.toJson(data)
    val ops = LedgerDiff.diff(null, tree)
    queue.push(ops)
    writeShadow(tree)
    publish()
    return ops.size
  }

  /* ==========================================================
     ۲) یک دورِ کامل: Push تا ته، بعد Pull
     ========================================================== */

  /**
   *  @param read  دفترِ همین حالا
   *  @param write دفترِ تازه پس از اعمالِ opهای رسیده
   */
  suspend fun runOnce(read: () -> ShopData, write: suspend (ShopData) -> Unit): Result<Unit> =
    gate.withLock {
      if (!Backend.isReady(app)) { publish(); return@withLock Result.success(Unit) }
      if (!Backend.isOnline(app)) { publish(); return@withLock Result.success(Unit) }
      busy = true
      publish()
      try {
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
          if (got.ops.isNotEmpty()) {
            val next = LedgerDiff.applyRemote(LedgerDiff.toJson(read()), got.ops)
            write(LedgerDiff.fromJson(next))
            //  سایه همین‌جا جلو می‌رود، وگرنه همین‌ها دوباره به سرور
            //  پس فرستاده می‌شوند
            writeShadow(next)
          }
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
  suspend fun firstSync(read: () -> ShopData, write: suspend (ShopData) -> Unit) {
    if (state.snapshotAt > 0 || state.cursor > 0) return
    val data = read()
    val tree = LedgerDiff.toJson(data)
    val rows: Int = LedgerDiff.COLLECTIONS.sumOf { key ->
      (tree[key] as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }
    if (rows == 0) { restoreFromServer(read, write); return }
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
      dropped = runCatching { queue.dropped().size }.getOrDefault(0),
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
