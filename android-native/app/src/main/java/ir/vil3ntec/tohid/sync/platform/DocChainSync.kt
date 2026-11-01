package ir.vil3ntec.tohid.sync.platform

import android.content.Context
import ir.vil3ntec.tohid.core.audit.DocChainAck
import ir.vil3ntec.tohid.core.audit.DocChainBook
import ir.vil3ntec.tohid.core.audit.DocChainCycle
import ir.vil3ntec.tohid.core.audit.DocChainDisk
import ir.vil3ntec.tohid.core.audit.DocChainProblem
import ir.vil3ntec.tohid.core.audit.DocChainReply
import ir.vil3ntec.tohid.core.audit.DocChainReport
import ir.vil3ntec.tohid.core.audit.DocChainServer
import ir.vil3ntec.tohid.core.audit.DocChainStatus
import ir.vil3ntec.tohid.core.audit.HashChain
import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.data.DiskWrite
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.SaleChainFacts
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.DocSeries
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.io.RandomAccessFile

/**
 *  SEC-02 — مرزِ سکوی زنجیرهٔ اسنادِ این دستگاه: دیسک، شبکه و ساعت برای `core/audit/DocChainCycle`.
 *  همهٔ تصمیم‌ها در core است (آزمون: `DocChainTest`، `DocChainCycleTest`، `DocChainVectorsTest`).
 *
 *  ── کجا ─────────────────────────────────────────────────────────────
 *  در هر دورِ `SyncV1Worker` (پس از گرفتنِ «سریِ سند»، F0-03)، گوشی و کامپیوتر. Sync v3 هنوز نه.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  - ⛔ پرچمِ خاموشِ `shop.doc_chain` (سرور، پیش‌فرض خاموش): تا سرور «روشن» نگوید هیچ حلقه‌ای ساخته
 *    نمی‌شود و تنها کار پرسیدنِ پرچم است، حداکثر روزی یک بار (`DocChainCycle.PROBE_MS`؛
 *    بی‌پاسخ یا ۴۰۴ هم همین)، نه در هر دور.
 *  - ⛔ هیچ شکستی فروش یا همگام‌سازی را نمی‌گیرد: هر خطا همین‌جا بلعیده می‌شود (جز لغوِ کوروتین).
 *  - فایل‌ها در `noBackupFilesDir/doc-chain/<حساب>/<دکان>-<سری>` (بیرون از پشتیبانِ اندروید): گوشیِ
 *    بازیابی‌شده زنجیرهٔ کهنه را ادامه نمی‌دهد؛ دورهٔ تازه می‌سازد و سرور «آغازِ دوباره» را ثبت می‌کند
 *    (همان روحِ DATA-06 برای سریِ سند).
 *  - هر فایلِ دوره فقط افزوده می‌شود (`RandomAccessFile` + `fsync`)؛ دُمِ نیمه‌کاره پیش از افزودن
 *    بریده می‌شود. وضعیت با `DiskWrite` (اتمی).
 */
object DocChainSync {

  private const val PREFS = "tohid-doc-chain"
  private const val PATH = "/sync/v1/chain"
  private val lock = Mutex()

  /** یک دور؛ صدازننده (`SyncV1Worker`) شکست را نمی‌بیند */
  suspend fun cycle(context: Context, data: ShopData) {
    val app = context.applicationContext ?: context
    val account = SyncStateV1.currentAccount(app)
    val shop = LedgerOwner.currentShop(app)
    val series = DocSeries.current(app)
    if (account.isBlank() || shop.isBlank() || series.isBlank()) return
    lock.withLock {
      withContext(Dispatchers.IO) {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = safe(account) + "@" + safe(shop)
        val gate = DocChainCycle.Gate(prefs.getBoolean("on:$key", false), prefs.getLong("at:$key", 0L))
        val dir = File(app.noBackupFilesDir, "doc-chain/${safe(account)}/${safe(shop)}-$series")
        val (next, _) = DocChainCycle.tick(
          gate = gate,
          now = System.currentTimeMillis(),
          book = DocChainBook(DocChainFiles(dir)),
          server = ChainServer(app, SyncStore(app).deviceUid, series),
          shopId = shop,
          series = series,
          by = account,
          sales = SaleChainFacts.of(data, series),
        )
        if (next != gate) prefs.edit().putBoolean("on:$key", next.enabled).putLong("at:$key", next.probedAt).apply()
      }
    }
  }

  /**
   *  SEC-03: لغو یا «اصلاح» ِ فاکتورِ [saleId] که همین حالا در دفتر نشست ⇐ سندِ معکوس در زنجیرهٔ
   *  همین دستگاه (`DocChainCycle.recordVoid`). [before] دفترِ **پیش از لغو** است (فاکتورِ اصلی و
   *  فاکتورهای حلقه‌نشدهٔ همین سری). پرچمِ خاموش یا زنجیرهٔ نساخته ⇐ هیچ؛ هر شکست بی‌صدا — لغو
   *  پیش‌تر نشسته و به این بسته نیست. صفحهٔ نسخهٔ ۳ (`CancelSheet`، #391) هم پس از `store.save` همین را
   *  صدا بزند.
   */
  suspend fun recordVoid(context: Context, before: ShopData, saleId: String, kind: InvoiceVoid.Kind, reason: String) {
    val app = context.applicationContext ?: context
    val account = SyncStateV1.currentAccount(app)
    val shop = LedgerOwner.currentShop(app)
    val series = DocSeries.current(app)
    if (account.isBlank() || shop.isBlank() || series.isBlank()) return
    val original = before.sales.find { it.id == saleId } ?: return
    try {
      lock.withLock {
        withContext(Dispatchers.IO) {
          val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
          val key = safe(account) + "@" + safe(shop)
          val gate = DocChainCycle.Gate(prefs.getBoolean("on:$key", false), prefs.getLong("at:$key", 0L))
          val facts = SaleChainFacts.facts(original, before.saleItems.filter { it.saleId == saleId })
          DocChainCycle.recordVoid(
            gate = gate,
            book = DocChainBook(DocChainFiles(File(app.noBackupFilesDir, "doc-chain/${safe(account)}/${safe(shop)}-$series"))),
            shopId = shop,
            series = series,
            by = account,
            ownSales = SaleChainFacts.of(before, series),
            original = facts,
            at = System.currentTimeMillis(),
            kind = kind,
            reason = reason,
          )
        }
      }
    } catch (c: CancellationException) {
      throw c
    } catch (e: Exception) {
      //  زنجیره نباید لغوِ نشسته را خراب کند؛ دورِ بعدِ همگام‌سازی هر ناهمخوانی را گزارش می‌کند
    }
  }

  private fun safe(s: String): String = s.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)

  /** فایل‌های یک سری: `state` و `epoch-<n>.chain` */
  private class DocChainFiles(private val dir: File) : DocChainDisk {
    private val stateFile = File(dir, "state")
    private fun epochFile(epoch: Long) = File(dir, "epoch-$epoch.chain")

    override fun readState(): String? = if (stateFile.isFile) stateFile.readText(Charsets.UTF_8) else null

    override fun writeState(text: String) {
      dir.mkdirs()
      DiskWrite.text(stateFile, text)
    }

    override fun size(epoch: Long): Long = epochFile(epoch).let { if (it.isFile) it.length() else 0L }

    override fun readFrom(epoch: Long, from: Long): List<String> {
      val f = epochFile(epoch)
      if (!f.isFile) return emptyList()
      RandomAccessFile(f, "r").use { raf ->
        val len = raf.length()
        if (from < 0 || from >= len) return emptyList()
        val bytes = ByteArray((len - from).toInt())
        raf.seek(from)
        raf.readFully(bytes)
        //  خطِ آخرِ بی پایانِ خط (نوشتنِ نیمه‌کاره) کنار می‌ماند
        return String(bytes, Charsets.UTF_8).split('\n').dropLast(1)
      }
    }

    override fun append(epoch: Long, lines: List<String>) {
      if (lines.isEmpty()) return
      dir.mkdirs()
      RandomAccessFile(epochFile(epoch), "rw").use { raf ->
        var end = raf.length()
        while (end > 0) {
          raf.seek(end - 1)
          if (raf.read() == '\n'.code) break
          end--
        }
        raf.setLength(end)
        raf.seek(end)
        raf.write(lines.joinToString("") { it + "\n" }.toByteArray(Charsets.UTF_8))
        raf.fd.sync()
      }
    }
  }

  /** `GET`/`POST /api/sync/v1/chain` (`server/src/lib/doc-chain.js`)؛ هر شکست ⇐ null */
  private class ChainServer(private val app: Context, private val device: String, private val series: String) : DocChainServer {

    private suspend fun <T> quiet(block: suspend () -> T): T? = try {
      block()
    } catch (c: CancellationException) {
      throw c
    } catch (e: Exception) {
      null
    }

    private fun str(o: JsonObject, k: String): String = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    private fun num(o: JsonObject, k: String): Long = (o[k] as? JsonPrimitive)?.longOrNull ?: 0L
    private fun on(o: JsonObject): Boolean = (o["enabled"] as? JsonPrimitive)?.booleanOrNull == true

    override suspend fun status(): DocChainStatus? = quiet {
      val res = Backend.api(app).get("$PATH?device_id=$device")
      val mine = (res["series"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.firstOrNull { str(it, "series") == series }
      DocChainStatus(enabled = on(res), lastEpoch = mine?.let { num(it, "lastEpoch") } ?: 0L)
    }

    override suspend fun report(r: DocChainReport): DocChainReply? = quiet {
      val body = buildJsonObject {
        put("device_id", JsonPrimitive(device))
        put("format", JsonPrimitive(HashChain.FORMAT))
        put("heads", JsonArray(listOf(buildJsonObject {
          put("series", JsonPrimitive(r.series))
          put("epoch", JsonPrimitive(r.epoch))
          put("seq", JsonPrimitive(r.seq))
          put("hash", JsonPrimitive(r.head))
          r.broken?.let { put("broken", JsonPrimitive(it)) }
        })))
        put("links", JsonArray(r.links.map { l ->
          buildJsonObject {
            put("series", JsonPrimitive(r.series))
            put("epoch", JsonPrimitive(r.epoch))
            put("seq", JsonPrimitive(l.seq))
            put("prevHash", JsonPrimitive(l.prevHash))
            put("hash", JsonPrimitive(l.hash))
            put("body", JsonPrimitive(l.body))
          }
        }))
      }
      val res = Backend.api(app).post(PATH, body)
      DocChainReply(
        enabled = on(res),
        acks = (res["acks"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
          DocChainAck(str(it, "series"), num(it, "epoch"), num(it, "seq"), str(it, "hash"))
        },
        problems = (res["problems"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.map {
          DocChainProblem(str(it, "series"), num(it, "epoch"), str(it, "problem"), num(it, "seq"))
        },
      )
    }

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()
  }
}
