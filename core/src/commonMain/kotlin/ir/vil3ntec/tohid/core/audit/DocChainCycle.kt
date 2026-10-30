package ir.vil3ntec.tohid.core.audit

/**
 *  SEC-02 — یک دورِ زنجیرهٔ اسناد در هر همگام‌سازی: پرچم، ساختنِ حلقه‌های تازه، گزارش و پاسخ.
 *  همهٔ تصمیم‌ها این‌جاست؛ برنامه (`sync/platform/DocChainSync.kt`) فقط دیسک، شبکه و ساعت می‌دهد.
 *
 *  ⛔ پرچمِ خاموش (پیش‌فرض) ⇒ هیچ حلقه‌ای ساخته نمی‌شود و تنها کار، پرسیدنِ خودِ پرچم است — نه
 *  زودتر از [PROBE_MS] (همان الگوی `ReceiptFlag`). پاسخِ «خاموش» در هر جا زنجیره را می‌ایستاند.
 *  ⛔ هیچ شکستی (شبکه، سرور، دیسک) فروش یا همگام‌سازی را نمی‌گیرد؛ صدازننده خطا را می‌بلعد.
 */
object DocChainCycle {

  /** هر چند وقت یک بار پرچمِ خاموش دوباره پرسیده شود */
  const val PROBE_MS = 10 * 60_000L

  /** پرچمِ محلی: روشن؟ و آخرین پرسش */
  data class Gate(val enabled: Boolean, val probedAt: Long)

  sealed class Outcome {
    /** کاری نبود (پرچمِ خاموش و هنوز وقتِ پرسش نیست، یا سری/دکان نامعتبر) */
    object Skipped : Outcome()
    /** سرور گفت خاموش */
    object Disabled : Outcome()
    /** شبکه یا سرور جواب نداد — دورِ بعد */
    object Offline : Outcome()
    data class Reported(val state: DocChainState, val problems: List<DocChainProblem>) : Outcome()
  }

  /**
   *  یک دور. [sales] فاکتورهای همین دفتر (هر سری؛ فقط [series] برداشته می‌شود)، [by] شناسهٔ کاربرِ
   *  واردشده (در سند، STF-09). ⇐ پرچمِ تازه و نتیجه.
   */
  suspend fun tick(
    gate: Gate,
    now: Long,
    book: DocChainBook,
    server: DocChainServer,
    shopId: String,
    series: String,
    by: String,
    sales: List<SaleFacts>,
  ): Pair<Gate, Outcome> {
    if (!DocChain.validSeries(series) || DocChain.chainId(shopId, series, 1) == null) return gate to Outcome.Skipped
    var status: DocChainStatus? = null
    if (!gate.enabled) {
      //  هرگز پرسیده نشده (۰) ⇐ همین حالا؛ ساعتِ عقب‌رفته هم پرسش را تا ابد نمی‌خواباند
      if (gate.probedAt > 0 && now >= gate.probedAt && now - gate.probedAt < PROBE_MS) return gate to Outcome.Skipped
      status = server.status() ?: return gate.copy(probedAt = now) to Outcome.Offline
      if (!status.enabled) return Gate(false, now) to Outcome.Disabled
    }
    val on = Gate(true, now)
    val outcome = run(book, server, shopId, series, by, sales, status)
    return when (outcome) {
      Outcome.Disabled -> Gate(false, now) to outcome
      else -> on to outcome
    }
  }

  /**
   *  SEC-03: لغو یا «اصلاح» ِ [original] که همین حالا در دفتر نشست ⇐ سندِ معکوس در زنجیرهٔ همین
   *  دستگاه (هر سری‌ای که فاکتورِ اصلی داشته باشد؛ مرجع شناسهٔ اوست). [ownSales] فاکتورهای همین
   *  سری از دفترِ **پیش از لغو**: اگر خودِ فاکتور هنوز حلقه نشده، اول حلقهٔ «ثبت» و بعد «معکوس».
   *  پرچمِ خاموش، زنجیرهٔ نساخته یا ایستاده ⇐ هیچ (null). بی شبکه — سرور در دورِ بعد می‌گیرد.
   */
  fun recordVoid(
    gate: Gate,
    book: DocChainBook,
    shopId: String,
    series: String,
    by: String,
    ownSales: List<SaleFacts>,
    original: SaleFacts,
    at: Long,
    kind: InvoiceVoid.Kind,
    reason: String,
  ): DocChainState? {
    if (!gate.enabled) return null
    val st = book.state()?.takeIf {
      it.series == series && DocChain.chainId(it.chain)?.first == shopId && it.halted.isEmpty()
    } ?: return null
    val caught = book.catchUp(st, by, ownSales)
    return book.appendVoid(caught, by, original, at, kind, reason)
  }

  private suspend fun run(
    book: DocChainBook,
    server: DocChainServer,
    shopId: String,
    series: String,
    by: String,
    sales: List<SaleFacts>,
    known: DocChainStatus?,
  ): Outcome {
    var st = book.state()?.takeIf { it.series == series && DocChain.chainId(it.chain)?.first == shopId }
    //  حلقهٔ تازه بی شبکه هم ساخته می‌شود (فاصلهٔ ثبت تا زنجیره کوتاه بماند)
    if (st != null && st.halted.isEmpty()) st = book.catchUp(st, by, sales)
    if (st == null || st.halted in DocChain.RESTART) {
      val s = known ?: server.status() ?: return Outcome.Offline
      if (!s.enabled) return Outcome.Disabled
      //  از همین لحظه: فاکتورهای پیشین لنگر نخورده‌اند و زنجیره‌شان چیزی را ثابت نمی‌کند
      val startNo = (sales.filter { it.series == series }.maxOfOrNull { it.no } ?: 0L) + 1
      st = book.start(shopId, series, maxOf(s.lastEpoch, st?.epoch ?: 0L) + 1, startNo) ?: return Outcome.Skipped
    }
    val reply = server.report(book.report(st)) ?: return Outcome.Offline
    if (!reply.enabled) return Outcome.Disabled
    var cur: DocChainState = st
    for (a in reply.acks) if (a.series == cur.series && a.epoch == cur.epoch) cur = book.acked(cur, a.seq, a.hash)
    for (p in reply.problems) {
      if (p.series == cur.series && p.epoch == cur.epoch && (p.problem in DocChain.RESTART || p.problem == DocChain.SERIES_OWNER)) {
        cur = book.halt(cur, p.problem)
      }
    }
    return Outcome.Reported(cur, reply.problems)
  }
}

/** پاسخِ `GET /api/sync/v1/chain` برای سریِ همین دستگاه */
data class DocChainStatus(val enabled: Boolean, val lastEpoch: Long = 0)

data class DocChainAck(val series: String, val epoch: Long, val seq: Long, val hash: String)

data class DocChainProblem(val series: String, val epoch: Long, val problem: String, val seq: Long)

/** پاسخِ `POST /api/sync/v1/chain` */
data class DocChainReply(
  val enabled: Boolean,
  val acks: List<DocChainAck> = emptyList(),
  val problems: List<DocChainProblem> = emptyList(),
)

/** شبکه — هر شکست (نت، ۴۰۴ ِ سرورِ کهنه، ۴۰۳) ⇐ null */
interface DocChainServer {
  suspend fun status(): DocChainStatus?
  suspend fun report(r: DocChainReport): DocChainReply?
}
