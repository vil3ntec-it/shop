package ir.vil3ntec.tohid.print

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.EscPos
import ir.vil3ntec.tohid.core.hw.DrawerRule
import ir.vil3ntec.tohid.core.hw.KickOnce
import ir.vil3ntec.tohid.core.hw.PrintQueue
import ir.vil3ntec.tohid.core.hw.PrinterProfile
import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.auditId
import ir.vil3ntec.tohid.data.invoiceLabel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
 *  • **کشو «همین حالا یا هرگز»** (HW-03): ضربهٔ کشو هرگز در صف، فایل یا
 *    تلاشِ دوباره نمی‌رود؛ یک بار و جدا فرستاده می‌شود (`kick`). بایتِ هر
 *    کارِ صف هم پیش از فرستادن از ضربه پاک می‌شود (`PrintQueue.payload`).
 *  • رسیدی که دوباره تلاش می‌شود یا پس از بازشدنِ برنامه از صف برمی‌گردد
 *    نسخهٔ «چاپِ دوباره» را می‌برد (`<id>.copy.bin` که همان لحظه ساخته شد).
 *  • هر بار باز کردنِ دستیِ کشو ردیفِ `no_sale` و هر چاپِ دوباره ردیفِ
 *    `reprint` در سابقهٔ دکان می‌نویسد (`ShopStore.appendAudit` که `AppRoot`
 *    وصل می‌کند: روی دفترِ همان لحظه، زیرِ قفلِ نوشتن) — همگام می‌شود.
 *  ──────────────────────────────────────────────────────────────────
 */
object PrintSpooler {

  private const val KEY_AUTO = "print_auto_after_sale"
  private const val KEY_DEFERRED = "print_deferred"
  private const val DIR = "print-spool"
  private const val COPY_MARK = "— چاپِ دوباره —"

  private val queue = PrintQueue()

  /** نشانِ وضعیت — صفحهٔ دستگاه‌ها و هر جای دیگری که بخواهد */
  val status = mutableStateOf<PrintQueue.Status>(PrintQueue.Status.Idle)
  val jobs = mutableStateOf<List<PrintQueue.Job>>(emptyList())
  /** آخرین خطای چاپ (برای پیامِ «چاپگر در دسترس نیست») */
  val lastError = mutableStateOf<String?>(null)

  /**
   *  پیامِ صندوق — چاپِ خودکار یا کشوی پس از فروش که نشد. صفحهٔ فروش
   *  (`Sale.kt`، `DeskSale.kt`) نشانش می‌دهد و با `consumed` برش می‌دارد.
   */
  data class Notice(val id: Long, val text: String)
  val tillNotice = mutableStateOf<Notice?>(null)
  private fun notifyTill(text: String) { tillNotice.value = Notice(System.nanoTime(), text) }
  fun consumed(n: Notice) { if (tillNotice.value?.id == n.id) tillNotice.value = null }

  /**
   *  ردیف‌های سابقهٔ دکان (`no_sale`، `reprint`) ⇐ `ShopStore.appendAudit`
   *  (روی دفترِ همان لحظه، زیرِ قفلِ نوشتن). `AppRoot` پس از بارِ دفتر وصلش
   *  می‌کند؛ تا آن وقت ردیف‌ها در حافظه می‌مانند.
   */
  private var auditSink: (suspend ((ShopData) -> AuditEntry) -> Boolean)? = null
  private val pendingAudits = ArrayList<(ShopData) -> AuditEntry>()
  private val auditLock = kotlinx.coroutines.sync.Mutex()

  fun attachAudit(sink: suspend ((ShopData) -> AuditEntry) -> Boolean) {
    scope.launch {
      auditLock.lock()
      try {
        auditSink = sink
        flushAudits(sink)
      } finally {
        auditLock.unlock()
      }
    }
  }

  /** ردیف‌هایی که پیش‌تر نرسیدند — به همان ترتیب؛ نرسیده‌ها می‌مانند (زیرِ `auditLock`) */
  private suspend fun flushAudits(sink: suspend ((ShopData) -> AuditEntry) -> Boolean) {
    val waiting = synchronized(pendingAudits) { pendingAudits.toList().also { pendingAudits.clear() } }
    waiting.forEach { row -> if (!runCatching { sink(row) }.getOrDefault(false)) synchronized(pendingAudits) { pendingAudits += row } }
  }

  /**
   *  @return آیا ردیف به دیسکِ دفتر رسید. `false` ⇒ در حافظه می‌ماند و با ردیفِ
   *  بعدی یا وصل شدنِ دوبارهٔ `AppRoot` دوباره نوشته می‌شود.
   */
  private suspend fun audit(row: (ShopData) -> AuditEntry): Boolean {
    auditLock.lock()
    try {
      val sink = auditSink
      if (sink == null) { synchronized(pendingAudits) { pendingAudits += row }; return false }
      flushAudits(sink)
      val ok = runCatching { sink(row) }.getOrDefault(false)
      if (!ok) synchronized(pendingAudits) { pendingAudits += row }
      return ok
    } finally {
      auditLock.unlock()
    }
  }

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
    /** نسخهٔ «چاپِ دوباره»ِ همین رسید — برای تلاشِ دوباره و پس از بازگشتِ صف؛ رسید بی آن پذیرفته نمی‌شود */
    copyBytes: ByteArray? = null,
  ): Submitted {
    //  کشو «همین حالا یا هرگز» است — هرگز در صف (`kick`)
    if (kind == PrintQueue.Kind.DRAWER) return Submitted(null, "کشو در صف نمی‌رود")
    if (kind == PrintQueue.Kind.RECEIPT && copyBytes == null) return Submitted(null, "نسخهٔ «چاپِ دوباره» ساخته نشد")
    start(context)
    val ctx = app ?: context
    val id = "pj" + System.currentTimeMillis().toString(36) + (1000..9999).random()
    val job = PrintQueue.Job(id, kind, link, title, System.currentTimeMillis(), saleId, reason)
    runCatching {
      binFile(ctx, id).writeBytes(EscPos.withoutDrawerKick(bytes))
      copyBytes?.let { copyFile(ctx, id).writeBytes(EscPos.withoutDrawerKick(it)) }
    }.onFailure { dropFiles(ctx, id); return Submitted(null, "کارِ چاپ ذخیره نشد: ${it.message}") }
    if (waiter != null) firstTry[id] = waiter
    val added = synchronized(queue) { queue.enqueue(job) }
    if (!added) {
      firstTry.remove(id)
      dropFiles(ctx, id)
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
    copyBytes: ByteArray? = null,
  ): String {
    val waiter = CompletableDeferred<String?>()
    val s = submit(context, kind, link, title, bytes, saleId, reason, waiter, copyBytes)
    val id = s.id ?: return s.message ?: "چاپ نشد"
    return firstOutcome(id, waiter) ?: done
  }

  /** نتیجهٔ نخستین تلاش: `null` = چاپ شد؛ وگرنه پیامِ فارسی */
  private suspend fun firstOutcome(id: String, waiter: CompletableDeferred<String?>): String? {
    //  `null`ِ انتظار یعنی «چاپ شد»؛ پس در یک فهرست پیچیده می‌شود تا با «مهلت تمام شد» قاطی نشود
    val outcome = withTimeoutOrNull(30_000) { listOf(waiter.await()) }
      ?: return if (synchronized(queue) { queue.all().any { it.id == id } }) "در صفِ چاپ است — پشتِ کارِ قبلی" else null
    val err = outcome[0] ?: return null
    return "چاپگر در دسترس نیست: $err — در صف ماند و دوباره تلاش می‌شود"
  }

  /**
   *  چاپِ فاکتور از دکمهٔ «چاپ». `reason` یعنی چاپِ دوباره: روی کاغذ
   *  «چاپِ دوباره» می‌آید و دلیل در سابقه می‌ماند.
   */
  suspend fun printSale(context: Context, d: ShopData, sale: Sale, link: String, width: Int, reason: String?): String {
    val printer = PrinterRegistry.savedFor(context, link, width)
    val (bytes, copy) = withContext(Dispatchers.IO) {
      runCatching {
        //  چاپِ دوباره خودش نشان دارد؛ رسیدِ تازه نسخهٔ نشان‌دارش را هم همین حالا می‌سازد
        if (reason != null) HwPages.saleBytes(context, d, sale, printer, copyMark = COPY_MARK) to null
        else HwPages.saleBytes(context, d, sale, printer) to HwPages.saleBytes(context, d, sale, printer, copyMark = COPY_MARK)
      }
    }.getOrElse { return "فاکتور کشیده نشد: ${it.message ?: "دلیل نامعلوم"}" }
    val kind = if (reason != null) PrintQueue.Kind.REPRINT else PrintQueue.Kind.RECEIPT
    return submitAndWait(context, kind, link, "فاکتور #${sale.invoiceLabel() ?: "—"}", bytes, sale.id, reason.orEmpty(),
      done = if (reason != null) "چاپِ دوباره انجام شد" else "فاکتور چاپ شد", copyBytes = copy)
  }

  /**
   *  پس از هر فروشِ ثبت‌شده (`CartStore.post`). هر دو تنظیم خاموش ⇒ هیچ.
   *    • کشو روشن و فروش نقدی ⇒ یک ضربه، همین حالا، بیرون از صف (`kick`)
   *    • «چاپِ خودکار» روشن ⇒ فاکتور (بی کشو) به صف
   *  هر چه نشد روی صفحهٔ فروش گفته می‌شود (`tillNotice`)، نه فقط در صفحهٔ دستگاه‌ها.
   */
  fun afterSale(context: Context, data: ShopData, saleId: String) {
    val auto = autoPrint(context)
    val printer = PrinterRegistry.defaultSaved(context) ?: return
    val sale = data.sales.firstOrNull { it.id == saleId } ?: return
    //  HW-03: فقط وقتی نقد به دخل آمد — ردیفِ نقدِ بی‌امانت در تسویهٔ نسخهٔ ۲ (نقد + کارت، بخشی نقد)
    //  یا `cash`ِ فاکتورِ نسخهٔ ۱ (همان رفتارِ همیشگی). قاعده و آزمونش: `core/hw/DrawerRule`
    val cash = DrawerRule.opensAfter(
      DrawerRule.Sale(
        paymentMethod = sale.paymentMethod,
        cancelled = sale.status == "cancelled",
        online = ir.vil3ntec.tohid.data.CashShift.isOnline(sale),
        payments = sale.payments?.map { DrawerRule.Payment(it.kind, it.amountMinor, it.status, it.custody) },
      ),
    )
    val drawerNow = printer.profile().drawer && cash
    if (!auto && !drawerNow) return
    val no = sale.invoiceLabel() ?: "—"
    val label = "فاکتورِ #$no"
    scope.launch {
      if (drawerNow) kick(context, printer.link, printer.profile()).error?.let { notifyTill("کشو باز نشد: $it — با کلید باز کنید") }
      if (!auto) return@launch
      val (bytes, copy) = runCatching {
        HwPages.saleBytes(context, data, sale, printer) to HwPages.saleBytes(context, data, sale, printer, copyMark = COPY_MARK)
      }.getOrElse {
        val m = "$label کشیده نشد: ${it.message ?: "دلیل نامعلوم"}"
        lastError.value = m; notifyTill(m); return@launch
      }
      val waiter = CompletableDeferred<String?>()
      val s = submit(context, PrintQueue.Kind.RECEIPT, printer.link, "فاکتور #$no", bytes, sale.id, waiter = waiter, copyBytes = copy)
      val id = s.id ?: run { s.message?.let { notifyTill("$label: $it") }; return@launch }
      firstOutcome(id, waiter)?.let { notifyTill("$label چاپ نشد — $it") }
    }
  }

  /* ============================ کشو ============================ */

  private class Kick(val reached: Boolean, val error: String?)

  /**
   *  یک ضربهٔ کشو — «همین حالا یا هرگز». نه صف، نه فایل، نه تلاشِ دوباره:
   *  اگر چاپگر تا `KickOnce.WINDOW_MS` آزاد نشد یا خطا داد، کشو باز نمی‌شود
   *  و همین گفته می‌شود. `reached` یعنی بایت‌ها واقعاً به چاپگر سپرده شد
   *  (بلوتوثِ خاموش یا اتصالِ ناشده ⇒ نه).
   */
  private suspend fun kick(context: Context, link: String, profile: PrinterProfile): Kick = withContext(Dispatchers.IO) {
    val ticket = KickOnce(System.currentTimeMillis())
    val l = ThermalPrinter.Link.parse(link) ?: return@withContext Kick(false, "نشانیِ چاپگر خوانده نشد")
    val sent = ThermalPrinter.sendWithin(context, l, EscPos.drawerOnly(profile), KickOnce.WINDOW_MS) { ticket.take(System.currentTimeMillis()) }
      ?: return@withContext Kick(false, "چاپگر مشغول بود")
    Kick(sent.wrote, sent.error)
  }

  /**
   *  «باز کردنِ کشو» با دست (HW-03) — مجوزِ `drawer.open` را صفحه می‌سنجد.
   *  هر تلاشی که به چاپگر رسید (حتی با خطا؛ شاید کشو باز شده باشد) یک ردیفِ
   *  `no_sale` با همان شکلِ #286 می‌نویسد: چه کسی (`by`)، کی، چرا (`reason`)،
   *  تأییدکننده و در `notes` کدام دستگاه و کدام چاپگر.
   */
  suspend fun openDrawer(context: Context, printer: PrinterRegistry.Saved, reason: String): String {
    val why = reason.trim()
    if (why.isEmpty()) return "دلیل را بنویسید"
    //  همان #286: نقشی که سیاستِ دکان اجازه داده خودش تأییدکننده است؛ شاگردِ «نیازمندِ تأیید» نه
    if (!ir.vil3ntec.tohid.data.ShopRole.canOpenDrawer(context)) return "باز کردنِ کشو بی فروش تأییدِ صاحب یا مدیر می‌خواهد (APPROVAL_REQUIRED)"
    //  در دامنهٔ خودِ صف: صفحه‌ای که وسطِ کار بسته شد ضربه را بی ردیفِ سابقه نمی‌گذارد
    return scope.async { openDrawerNow(context.applicationContext ?: context, printer, why) }.await()
  }

  private suspend fun openDrawerNow(context: Context, printer: PrinterRegistry.Saved, why: String): String {
    val k = kick(context, printer.link, printer.profile())
    //  ضربه‌ای که به چاپگر نرسید (بلوتوثِ خاموش، اتصالِ ناشده) کشو را باز نکرد ⇒ ردیفی هم نیست
    if (!k.reached) return "کشو باز نشد: ${k.error ?: "به چاپگر نرسید"}"
    val now = System.currentTimeMillis()
    val by = who(context)
    val approver = by ?: POLICY_APPROVER
    val notes = "کشو بی فروش — $why — دستگاه: ${device()} · چاپگر: ${printer.name}" +
      (if (k.error != null) " — خطای چاپگر: ${k.error}" else "") + " — تأیید: $approver"
    val saved = audit { d ->
      AuditEntry(
        id = auditId(), type = "no_sale", date = ir.vil3ntec.tohid.todayIso(),
        refId = ir.vil3ntec.tohid.data.CashShift.current(d)?.open?.id, notes = notes, createdAt = now,
        by = by, reason = why, approvedBy = approver,
      )
    }
    val head = if (k.error == null) "کشو باز شد" else "شاید کشو باز شد (خطای چاپگر: ${k.error})"
    return head + if (saved) " — در سابقه ثبت شد" else " — ولی در سابقه ثبت نشد؛ دوباره ثبت می‌شود"
  }

  private fun who(context: Context): String? =
    runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrNull()?.trim()?.ifEmpty { null }

  /** تأییدکننده وقتی نامِ کاربر نیست ولی سیاستِ دکان اجازه داده (هرگز «صاحب/مدیر»ِ ساختگی) */
  private const val POLICY_APPROVER = "policy"

  private fun device(): String = runCatching { android.os.Build.MODEL }.getOrNull()?.ifBlank { null } ?: "این دستگاه"

  /** چاپِ دوباره (دستی، یا تلاشِ دوبارهٔ رسید) ⇐ ردیفِ `reprint` در سابقهٔ دکان */
  private suspend fun auditReprint(ctx: Context, job: PrintQueue.Job, reason: String) {
    val saleId = job.saleId ?: return
    val now = System.currentTimeMillis()
    val by = who(ctx)
    val notes = "چاپِ دوبارهٔ ${job.title} — $reason — دستگاه: ${device()}" + (by?.let { " — $it" } ?: "")
    audit { _ ->
      AuditEntry(id = auditId(), type = "reprint", date = ir.vil3ntec.tohid.todayIso(), refId = saleId, notes = notes,
        createdAt = now, by = by, reason = reason)
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
    app?.let { ctx -> dropFiles(ctx, id); persist(ctx) }
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
      //  تلاشِ پس از خطا یا رسیدی که پیش از بسته شدنِ برنامه نیمه‌راه ماند ⇒ نسخهٔ «چاپِ دوباره»؛ ضربهٔ کشو هرگز
      val copy = job.needsCopyMark
      val bytes = PrintQueue.payload(job) { c -> runCatching { (if (c) copyFile(ctx, job.id) else binFile(ctx, job.id)).readBytes() }.getOrNull() }
      val link = ThermalPrinter.Link.parse(job.link)
      if (bytes != null && link != null) {
        //  «آغاز شد» پیش از فرستادن روی دیسک می‌رود: برنامه‌ای که وسطِ چاپ بسته شد نسخهٔ نشان‌دار را می‌فرستد
        synchronized(queue) { queue.begin(job.id) }
        persist(ctx)
      }
      val err = when {
        bytes == null && copy && !copyFile(ctx, job.id).isFile ->
          "${job.title} نسخهٔ «چاپِ دوباره» ندارد (صفِ نسخهٔ پیشین) و کنار گذاشته شد — از فهرستِ فاکتورها با دلیل دوباره چاپ کنید".also(::notifyTill)
        bytes == null -> "فایلِ این کار پیدا نشد"
        link == null -> "نشانیِ چاپگر خوانده نشد"
        //  `send` خودش یک فرستنده در هر لحظه است (`ThermalPrinter.wire`)
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
      if (err == null || bytes == null || link == null) dropFiles(ctx, job.id)
      val printedSale = job.saleId
      if (err == null && printedSale != null && (job.kind == PrintQueue.Kind.RECEIPT || job.kind == PrintQueue.Kind.REPRINT)) {
        val reprint = job.kind == PrintQueue.Kind.REPRINT || copy
        val why = when {
          job.kind == PrintQueue.Kind.REPRINT -> job.reason
          job.attempts == 0 -> "چاپِ نیمه‌راه پیش از بسته شدنِ برنامه"
          else -> "تلاشِ دوباره پس از خطای چاپگر"
        }
        PrintLog.add(ctx, PrintLog.Entry(printedSale, if (reprint) PrintQueue.Kind.REPRINT.key else job.kind.key,
          if (reprint) why else job.reason, System.currentTimeMillis(), true))
        if (reprint) auditReprint(ctx, job, why)
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
  private fun binFile(ctx: Context, id: String) = File(dir(ctx), "$id.bin")
  private fun copyFile(ctx: Context, id: String) = File(dir(ctx), "$id.copy.bin")
  private fun dropFiles(ctx: Context, id: String) { binFile(ctx, id).delete(); copyFile(ctx, id).delete() }

  /**
   *  صف روی دیسک — یک نویسنده و اتمی: تصویرِ صف و نوشتن زیرِ همان قفلِ صف
   *  است (نوشتنِ کهنه‌تر روی تازه‌تر نمی‌نشیند)، در پروندهٔ موقت نوشته و
   *  با `rename` جای پرونده می‌نشیند (برنامه‌ای که وسطِ نوشتن بسته شد صفِ
   *  نیمه‌نوشته نمی‌گذارد).
   */
  private fun persist(ctx: Context) {
    synchronized(queue) {
      val arr = JSONArray()
      queue.all().forEach { j ->
        arr.put(
          JSONObject().put("id", j.id).put("kind", j.kind.key).put("link", j.link).put("title", j.title)
            .put("createdAt", j.createdAt).put("saleId", j.saleId ?: "").put("reason", j.reason)
            .put("attempts", j.attempts).put("held", j.held).put("lastError", j.lastError ?: "")
            .put("started", j.started)
        )
      }
      runCatching {
        val target = File(dir(ctx), "queue.json")
        val tmp = File(dir(ctx), "queue.json.tmp")
        tmp.writeText(arr.toString())
        check(tmp.renameTo(target)) { "rename" }
      }
    }
  }

  private fun load(ctx: Context): List<PrintQueue.Job> = runCatching {
    val f = File(dir(ctx), "queue.json")
    if (!f.isFile) return emptyList()
    val arr = JSONArray(f.readText())
    (0 until arr.length()).map { arr.getJSONObject(it) }
      .filter { o ->
        val id = o.getString("id")
        val kind = PrintQueue.Kind.of(o.optString("kind"))
        //  کشو هرگز برنمی‌گردد. رسیدِ بی نسخهٔ «چاپِ دوباره» (صفِ نسخهٔ پیشین) می‌ماند:
        //  اگر هرگز فرستاده نشده بی نشان چاپ می‌شود، وگرنه کارگر با پیام کنارش می‌گذارد
        val keep = kind != PrintQueue.Kind.DRAWER && binFile(ctx, id).isFile
        if (!keep) dropFiles(ctx, id)
        keep
      }
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
          started = o.optBoolean("started"),
        )
      }
  }.getOrDefault(emptyList())
}
