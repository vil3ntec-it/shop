package ir.vil3ntec.tohid.desktop.offline

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.core.net.HttpEngine
import ir.vil3ntec.tohid.core.net.TokenStorage
import ir.vil3ntec.tohid.core.sync.PushSince
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.desktop.DesktopPrefs
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.ShadowStore
import ir.vil3ntec.tohid.sync.v1.SyncCommit
import ir.vil3ntec.tohid.sync.v1.SyncOp
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Client
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/*
 *  ابزارِ `Offline30DaysDesktopTest` — دکانِ واقعی با دو دستگاه، بی اینترنت، روی سرورِ واقعی.
 *
 *  هیچ منطقی از برنامه این‌جا بازنویسی نمی‌شود: فروش، برگشت، انبار، قرض و صندوق همان موتورهای پوشهٔ `data`؛ ثبتِ op همان
 *  `SyncCommit` (ژورنال ⇐ صف ⇐ سایه + cursor با هم)، صف همان `OpQueue` روی دیسک، سایه و cursor همان `SyncStateV1`
 *  (`ShadowStore`)، شبکه همان `SyncV1Client` ⇐ `ApiClient` (۴۰۱ ⇐ تازه‌سازیِ یک‌باره) ⇐ `HttpEngine`. فقط حلقهٔ
 *  `SyncV1Engine.runOnce`/`pushQueue` این‌جا سطر به سطر تکرار شده، چون موتور یگانهٔ برنامه است (یک نمونه در هر
 *  فرایند، نشانیِ سرور از `BuildConfig`) و دو دستگاه در یک آزمون جا نمی‌گیرند.
 */

/** ساعتِ دکان — همان `now`/`today` که هر موتور می‌گیرد (ساعتِ تزریق‌پذیرِ خودِ کد) */
internal class ShopClock(val start: Long) {
  var now: Long = start
  fun at(day: Int, minuteOfDay: Int) { now = start + day * DAY + minuteOfDay * 60_000L }
  fun tick(ms: Long) { now += ms }
  val today: String get() = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString()
  companion object { const val DAY = 24L * 3600 * 1000 }
}

/** نشستِ برنامه در حافظه (همان `TokenStorage` ِ `ConflictInboxDesktopTest`) — شمارِ ذخیره و پاک‌شدن برای سنجش */
internal class MemTokens(access: String, refresh: String) : TokenStorage {
  override var accessToken: String? = access
  override var refreshToken: String? = refresh
  override var accessExpiresAt: Long = 0
  var saves = 0
  var clears = 0
  override fun save(access: String?, refresh: String?, expiresAt: Long) {
    saves++
    accessToken = access
    if (refresh != null) refreshToken = refresh
    if (expiresAt > 0) accessExpiresAt = expiresAt
  }
  override fun clear() { clears++; accessToken = null; refreshToken = null; accessExpiresAt = 0 }
}

/** پوشهٔ دادهٔ یک دستگاه — هر دستگاه دیسک و تنظیماتِ خودش را دارد (همان `DesktopPrefs` ِ برنامه) */
internal class DeviceContext(private val root: File) : Context() {
  private val prefs = HashMap<String, DesktopPrefs>()
  override val filesDir: File get() = File(root, "files").apply { mkdirs() }
  override val cacheDir: File get() = File(root, "cache").apply { mkdirs() }
  override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
    synchronized(prefs) { prefs.getOrPut(name) { DesktopPrefs(File(File(root, "shared_prefs").apply { mkdirs() }, "$name.json")) } }
  override val contentResolver = ContentResolver()
  override val packageManager = PackageManager()
  override fun getSystemService(name: String): Any? = null
  override fun <T> getSystemService(serviceClass: Class<T>): T? = null
  override fun startActivity(intent: Intent) {}
}

/** سرورِ واقعی (`server/test/e2e/lib/offline30-backend.mjs`) با ساعت، شبکه و درِ کنترل */
internal class OfflineBackend private constructor(private val proc: Process, val api: String, private val ctl: String, val db: String) : AutoCloseable {
  private val http = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).connectTimeout(Duration.ofSeconds(10)).build()

  fun call(method: String, path: String, body: JsonObject? = null): JsonObject {
    val b = HttpRequest.newBuilder(URI.create(ctl + path)).timeout(Duration.ofMinutes(2)).header("Content-Type", "application/json")
    val req = if (method == "GET") b.GET().build() else b.method(method, HttpRequest.BodyPublishers.ofString((body ?: JsonObject(emptyMap())).toString())).build()
    val res = http.send(req, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
    check(res.statusCode() == 200) { "ctl $method $path: ${res.statusCode()} ${res.body().take(400)}" }
    return Json.parseToJsonElement(res.body()).jsonObject
  }

  /** ساعتِ سرور = روزِ [day] پس از آغاز (همان جابه‌جایی برای همهٔ قاعده‌های سرور) */
  fun serverDay(day: Double) = call("POST", "/clock", obj("days" to day))

  /**
   *  قطعی / وصل؛ [cutPushAfter] ⇒ چند push ِ بعدی رد می‌شود و بعدی بریده (پیش از سرور یا پس از اعمال، بی پاسخ)؛
   *  [thenDown] ⇒ پس از بریدن شبکه هم می‌رود (تلاشِ دوبارهٔ خودکارِ `HttpURLConnection` هم نمی‌رسد)
   */
  fun net(up: Boolean, cutPushAfter: Int? = null, cutKind: String = "response", thenDown: Boolean = false) =
    call("POST", "/net", obj("mode" to if (up) "up" else "down", "cutPushAfter" to cutPushAfter, "cutKind" to cutKind, "thenDown" to thenDown))

  override fun close() {
    runCatching { call("POST", "/stop") }
    runCatching { proc.outputStream.close() }
    if (!proc.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) proc.destroyForcibly()
  }

  companion object {
    /** ریشهٔ مخزن (پوشه‌ای که `server/` و `desktop/` دارد) */
    fun repoRoot(): File? = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "server/test/e2e/lib/offline30-backend.mjs").isFile }

    fun available(root: File?): String? = when {
      root == null -> "مخزن پیدا نشد"
      !File(root, "server/node_modules").exists() -> "server/node_modules نیست (cd server && npm ci)"
      runCatching { ProcessBuilder("node", "--version").start().waitFor() != 0 }.getOrDefault(true) -> "node نیست"
      else -> null
    }

    fun start(root: File, log: File): OfflineBackend {
      val out = File(log.parentFile, "offline30-backend-${System.nanoTime()}.json")
      val pb = ProcessBuilder("node", File(root, "server/test/e2e/lib/offline30-backend.mjs").path, out.path)
        .directory(File(root, "server")).redirectErrorStream(true).redirectOutput(log)
      //  PostgreSQL ِ محلی (پایگاهِ جدا؛ schema ِ public از نو) — بی آن PGlite ِ در حافظه
      env("OFFLINE30_DATABASE_URL")?.let { pb.environment()["E2E_DATABASE_URL"] = it }
      val p = pb.start()
      val until = System.currentTimeMillis() + 180_000
      while (!out.isFile || out.length() == 0L) {
        check(p.isAlive) { "سرورِ آزمون بالا نیامد:\n" + log.readText().takeLast(3000) }
        check(System.currentTimeMillis() < until) { "سرورِ آزمون در ۳ دقیقه بالا نیامد" }
        Thread.sleep(100)
      }
      val o = Json.parseToJsonElement(out.readText()).jsonObject
      out.delete()
      return OfflineBackend(p, o.str("api"), o.str("ctl"), o.str("db"))
    }
  }
}

internal fun env(name: String): String? =
  (System.getenv(name) ?: System.getProperty(name.lowercase().replace('_', '.')))?.takeIf { it.isNotBlank() }

internal fun JsonObject.str(k: String): String = (this[k] as? JsonPrimitive)?.content.orEmpty()
internal fun JsonObject.obj(k: String): JsonObject = this[k] as JsonObject

internal fun obj(vararg kv: Pair<String, Any?>): JsonObject = JsonObject(kv.associate { (k, v) -> k to toJson(v) })
private fun toJson(v: Any?): JsonElement = when (v) {
  null -> kotlinx.serialization.json.JsonNull
  is JsonElement -> v
  is Number -> JsonPrimitive(v)
  is Boolean -> JsonPrimitive(v)
  else -> JsonPrimitive(v.toString())
}

/**
 *  یک دستگاهِ دکان: دفتر در حافظه (همان `ShopData` که `ShopStore` نگه می‌دارد)، صف/سایه/ژورنال روی دیسکِ خودش.
 *  [boot] همان باز شدنِ دوبارهٔ برنامه است — صف و سایه از دیسک، نه از حافظه.
 */
internal class ShopDevice(
  val label: String,
  val deviceId: String,
  val userId: String,
  val seller: String,
  apiUrl: String,
  val tokens: MemTokens,
  root: File,
  appVersion: String,
) {
  val ctx = DeviceContext(root)
  val key = "off30-$label"
  @Volatile var online = true
  var ledger = ShopData()
  var series = ""
  lateinit var state: SyncStateV1
  lateinit var queue: OpQueue
  private lateinit var commit: SyncCommit

  /** هر op ای که این دستگاه به صف داد (همان شناسه‌ای که سرور باید یک بار بگیرد) */
  val made = LinkedHashSet<String>()
  var boots = 0
  var pushes = 0
  var maxBatch = 0
  var maxBatchBytes = 0
  var duplicates = 0
  var refused = 0
  var saveMs = 0L
  var saves = 0
  var slowestSaveMs = 0L

  val api = ApiClient(
    HttpEngine(
      baseUrl = { apiUrl }, allowInsecure = true, online = { online },
      identity = { mapOf("X-App" to "shop", "X-App-Id" to "shop", "X-Device" to deviceId, "X-App-Version" to appVersion) },
    ),
    tokens,
  )
  private val client = SyncV1Client(api) { PushSince.since(state.pushSince, state.cursor) }

  /** همان سه تکهٔ `SyncV1Engine.parts` */
  private val parts = object : SyncCommit.Parts {
    override fun shadow(): String = state.shadow
    override fun commit(shadow: String, cursor: Long?) {
      if (!state.commitShadow(shadow, cursor)) throw java.io.IOException("سایهٔ همگام‌سازی روی دیسک ننشست")
    }
    override fun queued(): Set<String> = queue.all().mapTo(HashSet()) { it.opId }
    override fun push(ops: List<SyncOp>) {
      if (ops.isNotEmpty()) state.pushSince = PushSince.note(state.pushSince, queue.size() == 0, state.cursor)
      ops.mapTo(made) { it.opId }
      queue.push(ops)
    }
  }

  init { boot() }

  /** باز شدنِ برنامه: حال و صف از دیسک؛ ژورنالِ نیمه‌مانده با دفترِ همین حالا تمام می‌شود (`SyncCommit.start`) */
  fun boot() {
    ShadowStore.restartForTest()
    state = SyncStateV1(ctx, key)
    queue = OpQueue(ctx, key)
    commit = SyncCommit(SyncCommit.Journal(File(ctx.filesDir, "sync-journal-${OpQueue.fileKey(key)}.json")), parts)
    if (commit.pending()) commit.recover(ledger)
    boots++
  }

  /** دستگاهِ هرگز همگام‌نشده بی سرور (آزمونِ بی‌سرور): سایهٔ پایه = همین دفتر، تا هر ذخیرهٔ بعدی op بسازد */
  fun seatEmpty() {
    check(state.commitShadow(LedgerDiff.shadowOf(LedgerDiff.modelTree(LedgerDiff.toJson(ledger))).toString(), 0L)) { "سایه ننشست" }
  }

  /** `ShopStore.save` ⇒ `SyncV1Engine.record`: دفترِ تازه ⇐ تفاضل با سایه ⇐ صف (ژورنالی) */
  fun save(next: ShopData): Int {
    val t0 = System.nanoTime()
    ledger = next
    val n = commit.record(next)
    val ms = (System.nanoTime() - t0) / 1_000_000
    saveMs += ms; saves++
    if (ms > slowestSaveMs) slowestSaveMs = ms
    return n
  }

  /** `SyncV1Engine.restoreFromServer`: دفترِ خالی ⇐ Snapshot؛ سایه از درِ مدل (A00)، cursor ِ همان Snapshot */
  suspend fun firstSync() {
    val body = client.snapshot()
    val next = LedgerDiff.fromSnapshot(body, LedgerDiff.toJson(ledger))
    ledger = LedgerDiff.fromJson(next)
    val cursor = (body["cursor"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    check(state.commitShadow(LedgerDiff.shadowOf(LedgerDiff.modelTree(next)).toString(), cursor)) { "سایه ننشست" }
  }

  /** `SyncV1Engine.runOnce`: ژورنال ⇐ صف تا ته (دسته‌دسته) ⇐ Pull تا وقتی سرور بگوید هست */
  suspend fun sync(): Result<Unit> = runCatching {
    if (commit.pending()) commit.recover(ledger)
    pushQueue()
    var rounds = 0
    while (rounds < 50) {
      rounds++
      val got = client.pull(deviceId, state.cursor)
      if (got.ops.isNotEmpty()) commit.absorb(got.ops, ledger, got.cursor) { ledger = it }
      state.cursor = got.cursor
      if (got.serverSchema > 0) state.serverSchema = got.serverSchema
      if (!got.hasMore) break
    }
  }

  /** `SyncV1Engine.pushQueue`: فقط applied/duplicate از صف بیرون؛ ردشده با دلیل در دفترِ کنار */
  private suspend fun pushQueue() {
    var guard = 0
    while (queue.size() > 0 && guard < 500) {
      guard++
      val batch = queue.batch()
      if (batch.isEmpty()) break
      maxBatch = maxOf(maxBatch, batch.size)
      maxBatchBytes = maxOf(maxBatchBytes, batch.sumOf { it.toJson().toString().toByteArray().size + 1 })
      pushes++
      val out = client.push(deviceId, batch, queue.size(), caps = true, schema = LedgerDiff.wireSchema(state.serverSchema))
      state.serverSchema = out.serverSchema
      val accepted = out.results.filter { it.accepted }.map { it.opId }
      duplicates += out.results.count { it.status == "duplicate" }
      val rejected = out.results.filter { !it.accepted }
      if (accepted.isNotEmpty()) queue.ack(accepted)
      if (rejected.isNotEmpty()) {
        refused += rejected.size
        rejected.groupBy { it.reason.ifBlank { "rejected" } }.forEach { (why, rs) ->
          val m = rs.map { it.opId }.toSet()
          queue.drop(batch.filter { it.opId in m }, why)
        }
        queue.ack(rejected.map { it.opId })
      }
    }
  }
}
