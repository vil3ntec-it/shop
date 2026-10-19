package ir.vil3ntec.tohid.print

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.invoiceLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 *  صفِ چاپِ زنده (HW-02، سناریوی S16) — منطقش `core/hw/PrintQueue`، این‌جا
 *  فقط نخ، فایل و نشانِ وضعیت.
 *
 *  ── قاعده‌ها ──────────────────────────────────────────────────────
 *  • فروش **همیشه** ثبت می‌شود؛ چاپ پس از آن و جدا از آن است.
 *  • بایت‌های هر کار همان لحظه ساخته و در `files/print-spool/` نوشته
 *    می‌شوند؛ صف پس از بسته شدنِ برنامه هم می‌ماند و به ترتیب چاپ می‌شود.
 *  • چاپِ دوباره فقط بایت‌های فاکتورِ موجود را می‌فرستد؛ به دفتر دست
 *    نمی‌زند، پس هرگز فروش نمی‌سازد.
 *  • «چاپِ خودکار پس از فروش» و «کشو پس از فروشِ نقدی» هر دو پیش‌فرض
 *    **خاموش‌اند** — رفتارِ امروز (چاپ فقط با دکمه) دست نمی‌خورد.
 *  ──────────────────────────────────────────────────────────────────
 */
object PrintSpooler {

  private const val KEY_AUTO = "print_auto_after_sale"
  private const val KEY_DEFERRED = "print_deferred"
  private const val DIR = "print-spool"

  private val queue = PrintQueue()

  /** نشانِ وضعیت — صفحهٔ دستگاه‌ها و هر جای دیگری که بخواهد */
  val status = mutableStateOf<PrintQueue.Status>(PrintQueue.Status.Idle)
  val jobs = mutableStateOf<List<PrintQueue.Job>>(emptyList())
  /** آخرین خطای چاپ (برای پیامِ «چاپگر در دسترس نیست») */
  val lastError = mutableStateOf<String?>(null)

  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val wake = Channel<Unit>(Channel.CONFLATED)
  private val firstTry = ConcurrentHashMap<String, CompletableDeferred<String?>>()

  @Volatile private var app: Context? = null

  private fun prefs(context: Context) = context.getSharedPreferences("tohid", Context.MODE_PRIVATE)

  fun autoPrint(context: Context): Boolean = prefs(context).getBoolean(KEY_AUTO, false)
  fun setAutoPrint(context: Context, on: Boolean) = prefs(context).edit().putBoolean(KEY_AUTO, on).apply()

  fun deferred(context: Context): Boolean = prefs(context).getBoolean(KEY_DEFERRED, false)
  fun setDeferred(context: Context, on: Boolean) {
    prefs(context).edit().putBoolean(KEY_DEFERRED, on).apply()
    synchronized(queue) { queue.deferred = on }
  }

  /** یک بار در هر اجرا: صفِ ذخیره‌شده خوانده و کارگر روشن می‌شود */
  fun start(context: Context) {
    synchronized(this) {
      if (app != null) return
      val ctx = context.applicationContext
      app = ctx
      synchronized(queue) {
        queue.deferred = deferred(ctx)
        queue.restore(load(ctx))
      }
      publish()
      scope.launch { loop(ctx) }
    }
  }

  /* ============================ کار گذاشتن ============================ */

  data class Submitted(val id: String?, val message: String?)

  fun submit(
    context: Context,
    kind: PrintQueue.Kind,
    link: String,
    title: String,
    bytes: ByteArray,
    saleId: String? = null,
    reason: String = "",
    waiter: CompletableDeferred<String?>? = null,
  ): Submitted {
    start(context)
    val ctx = app ?: context
    val id = "pj" + System.currentTimeMillis().toString(36) + (1000..9999).random()
    val job = PrintQueue.Job(id, kind, link, title, System.currentTimeMillis(), saleId, reason)
    runCatching { File(dir(ctx), "$id.bin").writeBytes(bytes) }
      .onFailure { return Submitted(null, "کارِ چاپ ذخیره نشد: ${it.message}") }
    if (waiter != null) firstTry[id] = waiter
    val added = synchronized(queue) { queue.enqueue(job) }
    if (!added) {
      firstTry.remove(id)
      File(dir(ctx), "$id.bin").delete()
      return Submitted(null, "این فاکتور همین حالا در صفِ چاپ است")
    }
    persist(ctx)
    publish()
    wake.trySend(Unit)
    return Submitted(id, null)
  }

  /**
   *  گذاشتن در صف و ماندن تا **نخستین** تلاش — تا دکمه‌ای که کاربر زد
   *  همان لحظه بگوید چه شد. اگر نشد، کار در صف می‌ماند و دوباره تلاش می‌شود.
   */
  suspend fun submitAndWait(
    context: Context,
    kind: PrintQueue.Kind,
    link: String,
    title: String,
    bytes: ByteArray,
    saleId: String? = null,
    reason: String = "",
    done: String = "چاپ شد",
  ): String {
    val waiter = CompletableDeferred<String?>()
    val s = submit(context, kind, link, title, bytes, saleId, reason, waiter)
    val id = s.id ?: return s.message ?: "چاپ نشد"
    //  `null`ِ انتظار یعنی «چاپ شد»؛ پس در یک فهرست پیچیده می‌شود تا با «مهلت تمام شد» قاطی نشود
    val outcome = withTimeoutOrNull(30_000) { listOf(waiter.await()) }
      ?: return if (synchronized(queue) { queue.all().any { it.id == id } }) "در صفِ چاپ است — پشتِ کارِ قبلی" else done
    val err = outcome[0] ?: return done
    return "چاپگر در دسترس نیست: $err — در صف ماند و دوباره تلاش می‌شود"
  }

  /**
   *  چاپِ فاکتور از دکمهٔ «چاپ». `reason` یعنی چاپِ دوباره: روی کاغذ
   *  «چاپِ دوباره» می‌آید و دلیل در سابقه می‌ماند.
   */
  suspend fun printSale(context: Context, d: ShopData, sale: Sale, link: String, width: Int, reason: String?): String {
    val printer = PrinterRegistry.savedFor(context, link, width)
    val bytes = withContext(Dispatchers.IO) {
      runCatching { HwPages.saleBytes(context, d, sale, printer, openDrawer = false, copyMark = if (reason != null) "— چاپِ دوباره —" else null) }
    }.getOrElse { return "فاکتور کشیده نشد: ${it.message ?: "دلیل نامعلوم"}" }
    val kind = if (reason != null) PrintQueue.Kind.REPRINT else PrintQueue.Kind.RECEIPT
    return submitAndWait(context, kind, link, "فاکتور #${sale.invoiceLabel() ?: "—"}", bytes, sale.id, reason.orEmpty(),
      done = if (reason != null) "چاپِ دوباره انجام شد" else "فاکتور چاپ شد")
  }

  /**
   *  پس از هر فروشِ ثبت‌شده (`CartStore.post`). هر دو تنظیم خاموش ⇒ هیچ.
   *    • «چاپِ خودکار» روشن ⇒ فاکتور به صف (و کشو هم اگر نقدی است و روشن)
   *    • فقط کشو روشن و فروش نقدی ⇒ فقط ضربهٔ کشو
   */
  fun afterSale(context: Context, data: ShopData, saleId: String) {
    val auto = autoPrint(context)
    val printer = PrinterRegistry.defaultSaved(context) ?: return
    val sale = data.sales.firstOrNull { it.id == saleId } ?: return
    val cash = sale.paymentMethod != "credit" && sale.status != "cancelled"
    val kick = printer.profile().drawer && cash
    if (!auto && !kick) return
    scope.launch {
      if (auto) {
        val bytes = runCatching { HwPages.saleBytes(context, data, sale, printer, openDrawer = kick) }
          .getOrElse { lastError.value = "فاکتور کشیده نشد: ${it.message}"; return@launch }
        submit(context, PrintQueue.Kind.RECEIPT, printer.link, "فاکتور #${sale.invoiceLabel() ?: "—"}", bytes, sale.id)
      } else {
        submit(context, PrintQueue.Kind.DRAWER, printer.link, "کشوی پول", EscPos.drawerOnly(printer.profile()))
      }
    }
  }

  fun retryNow() {
    synchronized(queue) { queue.retryNow(System.currentTimeMillis()) }
    app?.let { persist(it) }
    publish()
    wake.trySend(Unit)
  }

  /** کنار گذاشتن («رسیدِ دیجیتال فرستادم») — فروش دست نمی‌خورد */
  fun discard(id: String) {
    synchronized(queue) { queue.discard(id) }
    app?.let { ctx -> File(dir(ctx), "$id.bin").delete(); persist(ctx) }
    firstTry.remove(id)?.complete("کنار گذاشته شد")
    publish()
    wake.trySend(Unit)
  }

  /* ============================ کارگر ============================ */

  private suspend fun loop(ctx: Context) {
    while (true) {
      val now = System.currentTimeMillis()
      val job = synchronized(queue) { queue.next(now) }
      if (job == null) {
        val head = synchronized(queue) { queue.all().firstOrNull() }
        val wait = when {
          head == null || head.held -> 60_000L
          else -> (head.nextAt - now).coerceIn(50L, 60_000L)
        }
        withTimeoutOrNull(wait) { wake.receive() }
        continue
      }
      val file = File(dir(ctx), "${job.id}.bin")
      val bytes = runCatching { file.readBytes() }.getOrNull()
      val link = ThermalPrinter.Link.parse(job.link)
      val err = when {
        bytes == null -> "فایلِ این کار پیدا نشد"
        link == null -> "نشانیِ چاپگر خوانده نشد"
        else -> runCatching { ThermalPrinter.send(ctx, link, bytes) }.getOrElse { it.message ?: "چاپ نشد" }
      }
      synchronized(queue) {
        when {
          err == null -> queue.succeeded(job.id)
          //  کارِ خراب هرگز درست نمی‌شود — صف را پشتش نگه نداریم
          bytes == null || link == null -> queue.discard(job.id)
          else -> queue.failed(job.id, err, System.currentTimeMillis())
        }
      }
      if (err == null || bytes == null || link == null) file.delete()
      if (err == null && job.saleId != null && (job.kind == PrintQueue.Kind.RECEIPT || job.kind == PrintQueue.Kind.REPRINT)) {
        PrintLog.add(ctx, PrintLog.Entry(job.saleId, job.kind.key, job.reason, System.currentTimeMillis(), true))
      }
      lastError.value = err
      firstTry.remove(job.id)?.complete(err)
      persist(ctx)
      publish()
    }
  }

  private fun publish() {
    val (s, all) = synchronized(queue) { queue.status() to queue.all() }
    status.value = s
    jobs.value = all
  }

  /* ============================ فایل ============================ */

  private fun dir(ctx: Context) = File(ctx.filesDir, DIR).apply { mkdirs() }

  private fun persist(ctx: Context) {
    val arr = JSONArray()
    synchronized(queue) { queue.all() }.forEach { j ->
      arr.put(
        JSONObject().put("id", j.id).put("kind", j.kind.key).put("link", j.link).put("title", j.title)
          .put("createdAt", j.createdAt).put("saleId", j.saleId ?: "").put("reason", j.reason)
          .put("attempts", j.attempts).put("held", j.held).put("lastError", j.lastError ?: "")
      )
    }
    runCatching { File(dir(ctx), "queue.json").writeText(arr.toString()) }
  }

  private fun load(ctx: Context): List<PrintQueue.Job> = runCatching {
    val f = File(dir(ctx), "queue.json")
    if (!f.isFile) return emptyList()
    val arr = JSONArray(f.readText())
    (0 until arr.length()).map { arr.getJSONObject(it) }
      .filter { File(dir(ctx), it.getString("id") + ".bin").isFile }
      .map { o ->
        PrintQueue.Job(
          id = o.getString("id"),
          kind = PrintQueue.Kind.of(o.optString("kind")),
          link = o.getString("link"),
          title = o.optString("title"),
          createdAt = o.optLong("createdAt"),
          saleId = o.optString("saleId").ifBlank { null },
          reason = o.optString("reason"),
          attempts = o.optInt("attempts"),
          //  پس از بازشدنِ برنامه زود دوباره تلاش شود
          nextAt = 0,
          lastError = o.optString("lastError").ifBlank { null },
          held = o.optBoolean("held"),
        )
      }
  }.getOrDefault(emptyList())
}
