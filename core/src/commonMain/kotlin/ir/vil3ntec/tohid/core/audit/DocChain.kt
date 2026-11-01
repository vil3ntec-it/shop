package ir.vil3ntec.tohid.core.audit

/**
 * SEC-02 — زنجیرهٔ هشِ اسنادِ مالیِ **هر سریِ دستگاه**، لنگرشده در سرور (بخشِ ۱۰.۳).
 *
 *  ── چه می‌کند ─────────────────────────────────────────────────────────
 *  هر فاکتورِ ثبت‌شدهٔ سریِ همین دستگاه (`A1`، `B1`، …؛ F0-03) یک حلقه می‌شود:
 *      hash = SHA-256( prevHash ‖ canonicalJson(سند) )        ([HashChain]، قالبِ v1)
 *  و «سر» ِ زنجیره و حلقه‌های تازه در هر دورِ همگام‌سازی به سرور می‌رود
 *  (`POST /api/sync/v1/chain`). سرور هر حلقه را دوباره هش می‌کند، به `hash_chain` ِ خودش
 *  (`doc/<دکان>/<سری>/<دوره>`) می‌افزاید و هر ناهمخوانی (انشعاب، عقب‌گرد، پیوندِ شکسته) را
 *  «رویدادِ امنیتی» ثبت می‌کند — **فروش را هرگز نمی‌بندد**. پس دفترِ دستگاه که پس از گزارش
 *  دست‌کاری شود، در دورِ بعد دیده می‌شود: سرور حلقه‌های پیشین را دارد.
 *
 *  ── قاعده‌ها ─────────────────────────────────────────────────────────
 *  - سند **بی دادهٔ شخصی** است: فقط شناسهٔ مشتری (`customerId`)، شناسهٔ کالا، مقدار (هزارم)
 *    و مبلغ (پول، کوچک‌ترین واحد) — نه نام، نه تلفن، نه یادداشت (SEC-10 زنجیره را نمی‌شکند).
 *  - فقط بخش‌های **تغییرناپذیرِ** فاکتور در سند است (شماره، زمان، قلم‌ها، جمع، پرداختِ همان
 *    لحظه). وضعیتِ «لغو» حلقهٔ تازه است (سندِ معکوس، SEC-03)، نه ویرایشِ حلقهٔ پیشین.
 *  - زنجیره از لحظهٔ روشن شدن شروع می‌شود (`startNo` = بزرگ‌ترین شمارهٔ سری + ۱)؛ گذشته را
 *    دوباره نمی‌سازد، چون گذشتهٔ لنگرنخورده چیزی را ثابت نمی‌کند.
 *  - «دوره» (`epoch`): زنجیره‌ای که شکست (دفترِ محلی پاک یا دست‌کاری شد، یا سرور انشعاب دید)
 *    ادامه داده نمی‌شود؛ دورهٔ تازه از همان لحظه شروع می‌شود و سرور آغازِ دورهٔ تازه را هم
 *    رویدادِ امنیتی ثبت می‌کند. دورهٔ کهنه روی سرور و دستگاه می‌ماند (شاهد).
 *  - هر حلقه یک خط است: `L1⇥seq⇥prevHash⇥hash⇥type⇥ref⇥no⇥body` — canonicalJson هیچ TAB یا
 *    پایانِ خطِ خامی ندارد (رشته‌ها گریز می‌خورند). فایل فقط افزوده می‌شود؛ خطِ نیمه‌کارهٔ پایانی
 *    (قطعِ برق وسطِ نوشتن) دور ریخته و همان حلقه دوباره ساخته می‌شود.
 *
 *  Kotlin خالص (commonMain)؛ دیسک پشتِ [DocChainDisk] است (اندروید/کامپیوتر: پوشهٔ `platform/`).
 */

/** فاکتوری که در زنجیره می‌نشیند — فقط بخش‌های تغییرناپذیر و بی دادهٔ شخصی. مبلغ به پول، مقدار به هزارم. */
data class SaleFacts(
  val id: String,
  val series: String,
  val no: Long,
  val postedAt: Long,
  val customerId: String?,
  val currency: String,
  val lines: List<Line>,
  val subtotal: Long,
  val discount: Long,
  val total: Long,
  val paid: Long,
  val method: String,
) {
  data class Line(val productId: String, val qty: Long, val unitPrice: Long, val amount: Long)
}

/** یک حلقهٔ ذخیره‌شده روی دستگاه. `body` همان canonicalJson ِ هش‌شده است. */
data class DocLink(
  val seq: Long,
  val prevHash: String,
  val hash: String,
  /** `sale` | `void` */
  val type: String,
  /** شناسهٔ فاکتور (برای `void` همان فاکتورِ اصلی) */
  val ref: String,
  /** شمارهٔ فاکتور در سری؛ ‎0‎ اگر نیست */
  val no: Long,
  val body: String,
) {
  fun line(): String = "$TAG\t$seq\t$prevHash\t$hash\t$type\t$ref\t$no\t$body"

  companion object {
    const val TAG = "L1"
    private val TYPE = Regex("^[a-z_]{1,16}$")

    /** خطِ فایل ⇐ حلقه؛ خطِ نادرست ⇐ null */
    fun parse(line: String): DocLink? {
      val p = line.split('\t', limit = 8)
      if (p.size != 8 || p[0] != TAG) return null
      val seq = p[1].toLongOrNull() ?: return null
      val no = p[6].toLongOrNull() ?: return null
      if (seq < 1 || !HashChain.isHash(p[2]) || !HashChain.isHash(p[3]) || !TYPE.matches(p[4])) return null
      if (p[5].isEmpty() || !p[7].startsWith("{")) return null
      return DocLink(seq, p[2], p[3], p[4], p[5], no, p[7])
    }
  }
}

/** وضعیتِ کوچکِ کنارِ فایل؛ فایل منبعِ حقیقت است و این فقط جای خواندنِ دوباره را می‌گیرد. */
data class DocChainState(
  /** `doc/<دکان>/<سری>/<دوره>` — همان نامِ زنجیره در `hash_chain` ِ سرور */
  val chain: String,
  val series: String,
  val epoch: Long,
  /** فاکتورهای این سری با شمارهٔ ≥ این در زنجیره می‌آیند */
  val startNo: Long,
  val seq: Long = 0,
  val head: String = HashChain.GENESIS,
  /** طولِ فایلِ دوره (بایت) تا پایانِ آخرین حلقهٔ پذیرفته */
  val bytes: Long = 0,
  /** بزرگ‌ترین شمارهٔ فاکتورِ زنجیره‌شده */
  val lastNo: Long = startNo - 1,
  /** آخرین حلقه‌ای که سرور نوشت (و همان هش را دارد) */
  val ackSeq: Long = 0,
  val ackHash: String = HashChain.GENESIS,
  /** بایتِ پایانِ حلقهٔ [ackSeq] — ‎0‎ یعنی نامعلوم (از ابتدا خوانده می‌شود) */
  val ackBytes: Long = 0,
  /** خالی ⇐ زنده؛ وگرنه کدِ ایستادن (`DocChain.RESTART` یا `series_owner`) */
  val halted: String = "",
) {
  fun encode(): String = listOf(
    "v" to "1", "chain" to chain, "series" to series, "epoch" to "$epoch", "startNo" to "$startNo",
    "seq" to "$seq", "head" to head, "bytes" to "$bytes", "lastNo" to "$lastNo",
    "ackSeq" to "$ackSeq", "ackHash" to ackHash, "ackBytes" to "$ackBytes", "halted" to halted,
  ).joinToString("\n") { (k, v) -> "$k=$v" }

  companion object {
    /** متنِ [encode] ⇐ وضعیت؛ خراب یا ناقص ⇐ null */
    fun decode(text: String?): DocChainState? {
      if (text.isNullOrBlank()) return null
      val m = HashMap<String, String>()
      for (row in text.split('\n')) {
        val i = row.indexOf('=')
        if (i > 0) m[row.substring(0, i)] = row.substring(i + 1)
      }
      if (m["v"] != "1") return null
      fun long(k: String) = m[k]?.toLongOrNull()
      val chain = m["chain"] ?: return null
      val series = m["series"] ?: return null
      val epoch = long("epoch") ?: return null
      val startNo = long("startNo") ?: return null
      val seq = long("seq") ?: return null
      val head = m["head"]?.takeIf { HashChain.isHash(it) } ?: return null
      val ackHash = m["ackHash"]?.takeIf { HashChain.isHash(it) } ?: return null
      if (DocChain.chainId(chain)?.second != series || epoch < 1 || seq < 0) return null
      return DocChainState(
        chain = chain, series = series, epoch = epoch, startNo = startNo, seq = seq, head = head,
        bytes = long("bytes") ?: return null, lastNo = long("lastNo") ?: return null,
        ackSeq = long("ackSeq") ?: return null, ackHash = ackHash, ackBytes = long("ackBytes") ?: return null,
        halted = m["halted"].orEmpty(),
      )
    }
  }
}

/** آنچه به سرور می‌رود: سرِ همین دوره و حلقه‌های تأییدنشده (یا شکستِ دفترِ محلی). */
data class DocChainReport(
  val series: String,
  val epoch: Long,
  val seq: Long,
  val head: String,
  val links: List<DocLink>,
  /** null ⇐ دفترِ محلی سالم؛ وگرنه دلیل (`local_broken`، `truncated`، …) */
  val broken: String? = null,
)

object DocChain {
  /** همان `DocSeries.FORMAT` و `SERIES_RE` ِ سرور */
  private val SERIES = Regex("^[A-Z][1-9][0-9]{0,3}$")
  private val SHOP = Regex("^[A-Za-z0-9_.:-]{1,80}$")
  private val CHAIN = Regex("^doc/([A-Za-z0-9_.:-]{1,80})/([A-Z][1-9][0-9]{0,3})/([1-9][0-9]{0,8})$")

  /** حداکثرِ حلقه و بایتِ بدنه در هر گزارش (سقفِ سرور ۲۰۰ حلقه و بدنهٔ ۲ مگابایت) */
  const val MAX_LINKS = 200
  const val MAX_BYTES = 512 * 1024

  /** مقدار در سند به هزارم */
  const val QTY_SCALE = 1000L

  /** مشکل‌هایی از سرور که پس از آن همین دوره ادامه نمی‌یابد و دورهٔ تازه شروع می‌شود */
  val RESTART: Set<String> = setOf(
    "fork", "rollback", "prev_mismatch", "bad_link", "not_canonical", "body_mismatch", "hash_mismatch",
    "local_broken", "truncated", "bad_epoch",
  )

  /** سری مالِ دستگاهِ دیگری است — دوره‌ی تازه هم کمکی نمی‌کند؛ فقط سر گزارش می‌شود */
  const val SERIES_OWNER = "series_owner"

  fun validSeries(series: String): Boolean = SERIES.matches(series)

  fun chainId(shopId: String, series: String, epoch: Long): String? =
    if (SHOP.matches(shopId) && SERIES.matches(series) && epoch in 1..999_999_999L) "doc/$shopId/$series/$epoch" else null

  /** نامِ زنجیره ⇐ (دکان، سری، دوره)؛ نادرست ⇐ null */
  fun chainId(chain: String): Triple<String, String, Long>? {
    val m = CHAIN.matchEntire(chain) ?: return null
    return Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3].toLong())
  }

  /**
   *  سندِ فاکتورِ ثبت‌شده (v1). `chain` و `seq` درونِ سند می‌آیند تا جابه‌جاییِ حلقه هم در سنجش
   *  دیده شود (همان قاعدهٔ `hash-chain-store.append` و `verifyChain` ِ سرور). [by] شناسهٔ کاربرِ
   *  واردشده روی دستگاه است (سابقهٔ هر کاربر، STF-09)؛ خالی ⇐ نوشته نمی‌شود.
   */
  fun saleDoc(chain: String, seq: Long, by: String, f: SaleFacts): Map<String, Any?> {
    val doc = LinkedHashMap<String, Any?>()
    doc["chain"] = chain
    doc["seq"] = seq
    doc["kind"] = "doc"
    doc["type"] = "sale"
    doc["v"] = 1L
    doc["id"] = f.id
    doc["series"] = f.series
    doc["no"] = f.no
    doc["status"] = "posted"
    doc["postedAt"] = f.postedAt
    if (by.isNotBlank()) doc["by"] = by
    if (!f.customerId.isNullOrBlank()) doc["customerId"] = f.customerId
    doc["currency"] = f.currency
    doc["qtyScale"] = QTY_SCALE
    doc["lines"] = f.lines.map {
      mapOf("productId" to it.productId, "qty" to it.qty, "unitPrice" to it.unitPrice, "amount" to it.amount)
    }
    doc["subtotal"] = f.subtotal
    doc["discount"] = f.discount
    doc["total"] = f.total
    doc["paid"] = f.paid
    doc["method"] = f.method
    return doc
  }

  /** حلقهٔ تازه پسِ سرِ [state] برای سندِ [doc] (که `chain` و `seq` ِ همین حلقه را دارد) */
  fun link(state: DocChainState, type: String, ref: String, no: Long, doc: Map<String, Any?>): DocLink {
    require(ref.isNotEmpty() && ref.none { it == '\t' || it == '\n' || it == '\r' }) { "ref" }
    val body = HashChain.canonicalJson(doc)
    return DocLink(state.seq + 1, state.head, HashChain.sha256Hex(state.head + body), type, ref, no, body)
  }

  /** وضعیت پس از نشستنِ [l] (که [lineBytes] بایت با پایانِ خط گرفت) */
  fun adopt(state: DocChainState, l: DocLink, lineBytes: Long): DocChainState = state.copy(
    seq = l.seq,
    head = l.hash,
    bytes = state.bytes + lineBytes,
    lastNo = if (l.type == "sale" && l.no > state.lastNo) l.no else state.lastNo,
  )

  /** آیا [l] درست پسِ (seq، head) می‌نشیند و هشش با بدنه می‌خواند؟ ⇐ null یا دلیل */
  fun linkProblem(seq: Long, head: String, l: DocLink): String? = when {
    l.seq != seq + 1 -> "seq_gap"
    l.prevHash != head -> "prev_mismatch"
    HashChain.sha256Hex(l.prevHash + l.body) != l.hash -> "hash_mismatch"
    else -> null
  }

  /** فاکتورهای همین سری که هنوز در زنجیره نیستند، به ترتیبِ شماره */
  fun pending(state: DocChainState, sales: List<SaleFacts>): List<SaleFacts> =
    sales.filter { it.series == state.series && it.no > state.lastNo && it.no >= state.startNo }
      .sortedBy { it.no }
      .distinctBy { it.no }

  /** بایتِ UTF-8 ِ یک خط با پایانِ خط */
  fun lineBytes(line: String): Long = line.encodeToByteArray().size + 1L

  /** سنجشِ کاملِ حلقه‌های یک دوره از ابتدا ⇐ همان [ChainCheck] ِ [HashChain.verify] */
  fun verify(links: List<DocLink>): ChainCheck {
    var seq = 0L
    var head = HashChain.GENESIS
    links.forEachIndexed { i, l ->
      linkProblem(seq, head, l)?.let { return ChainCheck(ok = false, length = i, head = head, index = i, reason = it) }
      seq = l.seq
      head = l.hash
    }
    return ChainCheck(ok = true, length = links.size, head = head)
  }
}

/**
 *  دیسکِ یک سری (یک حساب، یک دکان): وضعیت + یک فایلِ فقط‌افزودنی برای هر دوره.
 *  پیاده‌سازیِ اندروید/کامپیوتر در `sync/platform/DocChainFiles.kt`.
 */
interface DocChainDisk {
  fun readState(): String?
  /** نوشتنِ اتمی (فایلِ کناری + جابه‌جایی) */
  fun writeState(text: String)
  /** طولِ فایلِ دوره به بایت (نبود ⇐ ۰) */
  fun size(epoch: Long): Long
  /** خط‌های **کاملِ** فایلِ دوره از بایتِ [from] (خطِ بی پایانِ خطِ آخر نه) */
  fun readFrom(epoch: Long, from: Long): List<String>
  /** افزودنِ پایدار (fsync)؛ پیش از آن هر دُمِ نیمه‌کاره (بی ‎\n‎) بریده می‌شود */
  fun append(epoch: Long, lines: List<String>)
}

/**
 *  دفترِ زنجیرهٔ یک سری روی [disk]. همهٔ کارها پشتِ سرِ هم (صدازننده یک قفل دارد: کارگرِ همگام‌سازی).
 *  هیچ کاری فروش را نمی‌بندد: هر شکستی فقط زنجیره را می‌ایستاند (`halted`) و گزارش می‌شود.
 */
class DocChainBook(private val disk: DocChainDisk) {

  /**
   *  وضعیت از دیسک، آشتی‌داده با فایل: حلقه‌های سالمِ پس از [DocChainState.bytes] (نوشته‌شده ولی
   *  وضعیتشان نرسیده، مثلاً قطعِ برق) پذیرفته می‌شوند؛ فایلِ کوتاه‌تر یا حلقهٔ ناجور ⇐ `halted`.
   *  نبودِ وضعیت ⇐ null (هنوز زنجیره‌ای نیست).
   */
  fun state(): DocChainState? {
    var st = DocChainState.decode(disk.readState()) ?: return null
    if (st.halted.isNotEmpty()) return st
    val size = disk.size(st.epoch)
    if (size < st.bytes) return save(st.copy(halted = "truncated"))
    if (size > st.bytes) {
      var adopted = 0
      for (line in disk.readFrom(st.epoch, st.bytes)) {
        val l = DocLink.parse(line)
        if (l == null || DocChain.linkProblem(st.seq, st.head, l) != null) return save(st.copy(halted = "local_broken"))
        st = DocChain.adopt(st, l, DocChain.lineBytes(line))
        adopted++
      }
      //  فقط دُمِ نیمه‌کاره بود ⇐ چیزی عوض نشد (نوشتنِ بعدی آن را می‌بُرد)
      if (adopted > 0) save(st)
    }
    return st
  }

  /**
   *  دورهٔ تازه. [epoch] کمینه است: اگر فایلِ آن دوره روی دیسک چیزی دارد (دوره‌ای که هرگز به سرور
   *  نرسید)، دورهٔ بعدی برداشته می‌شود — حلقه‌ای روی حلقهٔ دیگر نمی‌نشیند.
   */
  fun start(shopId: String, series: String, epoch: Long, startNo: Long): DocChainState? {
    var e = maxOf(1L, epoch)
    while (disk.size(e) > 0) e++
    val chain = DocChain.chainId(shopId, series, e) ?: return null
    return save(DocChainState(chain = chain, series = series, epoch = e, startNo = maxOf(1L, startNo)))
  }

  /** فاکتورهای تازهٔ سری ⇐ حلقه (یک بار نوشتن + fsync، بعد وضعیت). ایستاده ⇐ هیچ. */
  fun catchUp(state: DocChainState, by: String, sales: List<SaleFacts>): DocChainState {
    if (state.halted.isNotEmpty()) return state
    val todo = DocChain.pending(state, sales)
    if (todo.isEmpty()) return state
    var st = state
    val lines = ArrayList<String>(todo.size)
    for (f in todo) {
      val doc = DocChain.saleDoc(st.chain, st.seq + 1, by, f)
      val l = try {
        DocChain.link(st, "sale", f.id, f.no, doc)
      } catch (e: IllegalArgumentException) {
        //  سندِ ناساز (عددِ بیرون از 2^53، شناسهٔ دارای TAB) — بی حلقه؛ زنجیره نمی‌ایستد
        continue
      }
      val line = l.line()
      lines += line
      st = DocChain.adopt(st, l, DocChain.lineBytes(line))
    }
    if (lines.isEmpty()) return state
    disk.append(st.epoch, lines)
    return save(st)
  }

  /**
   *  گزارشِ این دور: سر + حلقه‌های پس از تأییدِ سرور، سنجیده از همان تأیید. ایستاده ⇐ فقط سر.
   *  حلقه‌ای که با تأیید نخواند ⇐ `broken` و هیچ حلقه‌ای (زنجیره در پاسخ می‌ایستد).
   */
  fun report(state: DocChainState, maxLinks: Int = DocChain.MAX_LINKS, maxBytes: Int = DocChain.MAX_BYTES): DocChainReport {
    val base = DocChainReport(state.series, state.epoch, state.seq, state.head, emptyList())
    if (state.halted.isNotEmpty()) return base.copy(broken = state.halted.takeIf { it in DocChain.RESTART })
    if (state.seq <= state.ackSeq) return base
    val out = ArrayList<DocLink>()
    var seq = state.ackSeq
    var head = state.ackHash
    var bytes = 0 // نویسه‌های بدنه — برای سقف، تقریبِ بایت بس است (بدنه تقریباً همه ASCII است)
    for (line in disk.readFrom(state.epoch, state.ackBytes)) {
      val l = DocLink.parse(line) ?: return base.copy(broken = "local_broken")
      if (l.seq <= state.ackSeq) {
        if (l.seq == state.ackSeq && l.hash != state.ackHash) return base.copy(broken = "local_broken")
        continue
      }
      if (DocChain.linkProblem(seq, head, l) != null) return base.copy(broken = "local_broken")
      if (out.isNotEmpty() && (out.size >= maxLinks || bytes + l.body.length > maxBytes)) break
      out += l
      bytes += l.body.length
      seq = l.seq
      head = l.hash
      if (seq >= state.seq) break
    }
    return base.copy(links = out)
  }

  /**
   *  تأییدِ سرور برای همین دوره: سرور تا [seq] با هشِ [hash] دارد. اگر همان حلقهٔ محلی است ⇐
   *  `ackSeq` جلو (یا عقب، اگر سرور از پشتیبان برگشت و حلقه‌هایی را ندارد) می‌رود؛ اگر سرور
   *  چیزی دارد که این دستگاه ندارد ⇐ `local_broken` (دفترِ محلی عقب رفته یا عوض شده).
   */
  fun acked(state: DocChainState, seq: Long, hash: String): DocChainState {
    if (state.halted.isNotEmpty() || seq < 0 || !HashChain.isHash(hash)) return state
    if (seq == state.ackSeq && hash == state.ackHash) return state
    if (seq > state.seq) return save(state.copy(halted = "local_broken"))
    if (seq == 0L) {
      return if (hash == HashChain.GENESIS) save(state.copy(ackSeq = 0, ackHash = HashChain.GENESIS, ackBytes = 0)) else state
    }
    //  جای حلقهٔ [seq] در فایل: از تأییدِ پیشین اگر جلوتر است، وگرنه از ابتدا
    var from = if (seq >= state.ackSeq) state.ackBytes else 0L
    var at = if (seq >= state.ackSeq) state.ackSeq else 0L
    for (line in disk.readFrom(state.epoch, from)) {
      from += DocChain.lineBytes(line)
      val l = DocLink.parse(line) ?: return save(state.copy(halted = "local_broken"))
      if (l.seq <= at) continue
      at = l.seq
      if (l.seq == seq) {
        return if (l.hash == hash) save(state.copy(ackSeq = seq, ackHash = hash, ackBytes = from))
        else save(state.copy(halted = "local_broken"))
      }
      if (l.seq > seq) break
    }
    return save(state.copy(halted = "local_broken"))
  }

  /**
   *  SEC-03: سندِ معکوسِ لغو یا اصلاحِ [original] (`InvoiceVoid.reversalDoc`) — حلقهٔ `void` با
   *  `ref` = شناسهٔ فاکتورِ اصلی. صدازننده **پس از** نشستنِ لغو در دفتر صدا می‌زند. ایستاده یا سندِ
   *  ناساز (فاکتورِ بی شماره) ⇐ هیچ حلقه‌ای؛ فروش و لغو هرگز به این بسته نیستند.
   */
  fun appendVoid(
    state: DocChainState,
    by: String,
    original: SaleFacts,
    at: Long,
    kind: InvoiceVoid.Kind,
    reason: String,
  ): DocChainState {
    if (state.halted.isNotEmpty()) return state
    val l = try {
      DocChain.link(state, "void", original.id, original.no,
        InvoiceVoid.reversalDoc(state.chain, state.seq + 1, by, original, at, kind, reason))
    } catch (e: IllegalArgumentException) {
      return state
    }
    val line = l.line()
    disk.append(state.epoch, listOf(line))
    return save(DocChain.adopt(state, l, DocChain.lineBytes(line)))
  }

  /** پاسخِ سرور: این دوره ایستاد (کدِ مشکل) */
  fun halt(state: DocChainState, problem: String): DocChainState =
    if (state.halted.isNotEmpty() || problem.isBlank()) state else save(state.copy(halted = problem))

  /** سنجشِ کاملِ یک دوره روی دیسک — ابزارِ «بررسیِ یکپارچگی» (سنگین؛ فقط با درخواست) */
  fun verify(epoch: Long): ChainCheck {
    val links = ArrayList<DocLink>()
    for ((i, line) in disk.readFrom(epoch, 0).withIndex()) {
      links += DocLink.parse(line) ?: return ChainCheck(ok = false, length = i, index = i, reason = "bad_line")
    }
    return DocChain.verify(links)
  }

  private fun save(st: DocChainState): DocChainState {
    disk.writeState(st.encode())
    return st
  }
}
