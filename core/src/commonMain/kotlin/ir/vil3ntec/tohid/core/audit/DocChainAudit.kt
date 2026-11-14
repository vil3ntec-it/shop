package ir.vil3ntec.tohid.core.audit

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 *  SEC-02 — «بررسیِ یکپارچگی» ِ زنجیرهٔ اسنادِ همین دستگاه (صفحهٔ `DocChainCheckScreen`؛ فقط با درخواستِ کاربر).
 *
 *  سه پرسش، هر سه روی دیسکِ همین دستگاه و دفترِ همین لحظه:
 *   ۱) **زنجیره سالم است؟** هر حلقه درست پسِ حلقهٔ پیشین و هشش با بدنه می‌خواند ([DocChain.verify]).
 *   ۲) **سند پس از لنگر عوض شد یا رفت؟** هر حلقه از دفترِ امروز **از نو ساخته** می‌شود (همان [DocChain.saleDoc] و
 *      [DocChainDocs.doc] با همان `chain`، `seq` و `by` ِ خودِ حلقه) و بایت‌به‌بایت با بدنهٔ لنگرخورده سنجیده می‌شود:
 *      فرق ⇐ «عوض شد»؛ نبود ⇐ «در دفتر نیست». حلقهٔ لغو (`void`) فقط می‌خواهد فاکتورش هنوز «لغوشده» باشد.
 *   ۳) **چه هنوز لنگر نخورده؟** فاکتورِ سریِ خود از `startNo` و سندِ بی‌شمارهٔ پس از آغاز که حلقه ندارد: «در انتظار»
 *      (دورِ بعدِ همگام‌سازی می‌آید) یا «بی لنگر» (پیش از پنجرهٔ دیررسیده‌ها — ساعتِ عقب‌رفته یا همگام‌سازیِ بسیار دیر).
 *  تأییدِ سرور ([DocChainState.ackSeq]) هم گزارش می‌شود: حلقهٔ تأییدشده روی سرور هم هست و دست‌کاریِ دیسک را آن‌جا هم
 *  می‌بینند. Kotlin خالص؛ هیچ چیز نمی‌نویسد.
 */
object DocChainAudit {

  /** یک سندِ ناجور: نوع (`sale`، `void`، `return`…)، شناسه، شمارهٔ حلقه */
  data class Finding(val type: String, val ref: String, val seq: Long)

  data class Report(
    val chain: String,
    val epoch: Long,
    /** سنجشِ پیوندِ حلقه‌ها؛ `ok = false` ⇐ [ChainCheck.index] نخستین حلقهٔ ناجور */
    val links: ChainCheck,
    val total: Int,
    /** شمارِ حلقه‌ها به نوع */
    val byType: Map<String, Int>,
    val changed: List<Finding>,
    val missing: List<Finding>,
    val waiting: Int,
    val unanchored: Int,
    /** حلقه‌های تأییدشدهٔ سرور از [total] */
    val acked: Long,
    /** خالی ⇐ زنده؛ وگرنه کدِ ایستادن */
    val halted: String,
    /** «تا کجا» ِ اسنادِ بی‌شماره (‎-1‎ ⇐ هنوز نه) و آخرین شمارهٔ فاکتورِ زنجیره‌شده */
    val docFrom: Long,
    val docAt: Long,
    val lastNo: Long,
  ) {
    /** زنجیره سالم، هیچ سندی عوض نشده یا نرفته و زنجیره نایستاده */
    val ok: Boolean get() = links.ok && changed.isEmpty() && missing.isEmpty() && halted.isEmpty()
  }

  private val json = Json { ignoreUnknownKeys = true }

  private fun byOf(body: String): String = runCatching {
    ((json.parseToJsonElement(body) as? JsonObject)?.get("by") as? JsonPrimitive)?.contentOrNull.orEmpty()
  }.getOrDefault("")

  /**
   *  [lines] همهٔ خط‌های دورهٔ [state] (null ⇐ خطِ نادرست)؛ [sales] فاکتورهای شماره‌دارِ دفتر (هر سری) و [docs] اسنادِ بی‌شمارهٔ دفترِ
   *  امروز؛ [cancelled] شناسهٔ فاکتورهای لغوشده.
   */
  fun check(
    state: DocChainState,
    lines: List<DocLink?>,
    sales: List<SaleFacts>,
    docs: List<PlainFacts>,
    cancelled: Set<String>,
  ): Report {
    val bad = lines.indexOfFirst { it == null }
    val links = lines.filterNotNull()
    val check = if (bad >= 0) ChainCheck(ok = false, length = bad, index = bad, reason = "bad_line") else DocChain.verify(links)
    //  حلقهٔ لغو فاکتورِ هر سری‌ای را می‌تواند داشته باشد (`DocChainCycle.recordVoid`)
    val saleOf = sales.associateBy { it.id }
    val docOf = docs.associateBy(DocChainDocs::key)
    val changed = ArrayList<Finding>()
    val missing = ArrayList<Finding>()
    val chainedSales = HashSet<String>()
    val chainedDocs = HashSet<String>()
    val byType = LinkedHashMap<String, Int>()
    for (l in links) {
      byType[l.type] = (byType[l.type] ?: 0) + 1
      val finding = Finding(l.type, l.ref, l.seq)
      when (l.type) {
        "sale" -> {
          chainedSales += l.ref
          val f = saleOf[l.ref]
          if (f == null) missing += finding
          else if (!same(l, DocChain.saleDoc(state.chain, l.seq, byOf(l.body), f))) changed += finding
        }
        "void" -> if (l.ref !in cancelled) {
          if (saleOf[l.ref] == null) missing.add(finding) else changed.add(finding)
        }
        in DocChainDocs.TYPES -> {
          val key = "${l.type}:${l.ref}"
          chainedDocs += key
          val f = docOf[key]
          if (f == null) missing += finding
          else if (!same(l, DocChainDocs.doc(state.chain, l.seq, byOf(l.body), f))) changed += finding
        }
      }
    }
    var waiting = 0
    var unanchored = 0
    for (s in sales) {
      if (s.series != state.series || s.no < state.startNo || s.id in chainedSales) continue
      if (s.no > state.lastNo) waiting++ else unanchored++
    }
    if (state.docFrom >= 0) {
      val pending = DocChainDocs.pending(state, docs).mapTo(HashSet(), DocChainDocs::key)
      for ((key, d) in docOf) {
        if (d.type !in DocChainDocs.TYPES || d.postedAt < state.docFrom || key in chainedDocs) continue
        if (key in pending) waiting++ else unanchored++
      }
    }
    return Report(
      chain = state.chain, epoch = state.epoch, links = check, total = links.size, byType = byType,
      changed = changed, missing = missing, waiting = waiting, unanchored = unanchored,
      acked = minOf(state.ackSeq, links.size.toLong()), halted = state.halted,
      docFrom = state.docFrom, docAt = state.docAt, lastNo = state.lastNo,
    )
  }

  /** بدنهٔ ساخته از دفترِ امروز همان بدنهٔ لنگرخورده است؟ (سندِ ناساز ⇐ نه) */
  private fun same(l: DocLink, doc: Map<String, Any?>): Boolean =
    runCatching { HashChain.canonicalJson(doc) == l.body }.getOrDefault(false)
}
