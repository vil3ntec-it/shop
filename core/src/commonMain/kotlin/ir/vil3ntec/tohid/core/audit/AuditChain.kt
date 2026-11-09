package ir.vil3ntec.tohid.core.audit

import ir.vil3ntec.tohid.core.money.mathRound
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.abs
import kotlin.math.floor

/**
 * STF-09 — زنجیرهٔ هشِ **سابقهٔ عملیاتِ دستگاه** (`auditLog`): قالبِ «tohid-audit-chain-v1».
 *
 *  ── چه چیزی در ردیف می‌آید ─────────────────────────────────────────────
 *  - مُهر (همیشه، هنگامِ نوشتن، از نشستِ همان لحظه): `userId`، `userRole`، `deviceId` (همان `deviceUid` ِ همگام‌سازی).
 *  - حلقه (فقط با پرچمِ `shop.doc_chain`): `chain` (`<deviceId>/<دوره>`)، `chainSeq` (۱، ۲، …)، `prev`، `hash` و
 *    `chainV` (سطحِ مدل؛ نبودش = ۱).
 *  هر هفت فیلد پس از ساختِ ردیف عوض نمی‌شوند (`AuditSeal` ِ برنامه). ردیفِ کهنهٔ بی مُهر «نامعلوم» دیده می‌شود.
 *
 *  ── هش ─────────────────────────────────────────────────────────────────
 *      hash = HashChain.entryHash(prev, doc(row))          (همان SHA-256 و canonicalJson ِ v1، بی رمزنگاریِ تازه)
 *      doc  = {v:1, kind:"audit", chain, seq, id, type, date, refId, createdAt, amountMinor,
 *              userId, userRole, deviceId, detailHash [, lv]}
 *      detailHash = sha256(canonicalJson(بقیهٔ فیلدهای ردیف))
 *  بدنه بی متنِ جزئیات است (یادداشت فقط از راهِ `detailHash`)، همان قاعدهٔ سابقهٔ سرور. مبلغ همان قاعدهٔ یگانهٔ
 *  `Money.fromLegacy` / `sync-v3-convert` (`amountMinor` ِ v3 اگر هست، وگرنه `round(amount × 100)`).
 *
 *  ── یکسان‌سازی (تا گوشی، کامپیوتر، سرور و نسخه‌های دیگر یک هش بسازند) ──────────────────────
 *  - «خالی» = نبودن: `null`، `""`، `0`، `false`، `[]` و `{}` در هیچ شیئی نمی‌آیند (`encodeDefaults` و فیلدِ تازه با
 *    پیش‌فرضِ تهی هش را عوض نمی‌کنند). عضوِ فهرست جایش را نگه می‌دارد.
 *  - عددِ صحیحِ امن همان عدد؛ هر عددِ دیگر (اعشاری یا بیرون از ±(2^53−1)) ⇐ `{"$f": "<۱۶ هگزِ بیت‌های IEEE-754>"}` —
 *    دقیق و بی وابستگی به قالبِ چاپِ عدد در Kotlin یا JS.
 *  ⛔ همین قرارداد در `server/src/lib/device-audit-chain.js` است و هر دو با `core/test-vectors/audit-chain-v1.json`
 *  (سازنده: `server/scripts/audit-chain-vectors.js`) سنجیده می‌شوند. شکلِ تازه ⇐ `"v": 2` و فایلِ v2.
 *
 *  ── سنجش ([verify]) ────────────────────────────────────────────────────
 *  هر زنجیره جدا و به ترتیبِ `chainSeq` (نه جای ردیف در فهرست و نه ساعتِ دستگاه): شماره‌ای که نیست ⇐ `seq_gap`
 *  (ردیفِ پاک‌شده)، دو ردیف با یک شماره ⇐ `fork`، `prev` ِ ناهمخوان ⇐ `prev_mismatch` (جابه‌جایی/درج)، هشِ
 *  ناهمخوان ⇐ `hash_mismatch` (ویرایش). لنگرِ محلی ([Anchors]، بیرون از پشتیبان) بریدنِ دُم را می‌گیرد
 *  (`truncated`، `anchor_mismatch`). پیشوندِ نبوده (بایگانیِ دفتر، `LedgerArchive`) روی دستگاه پذیرفته است و فقط
 *  سرور (`strict`) آن را هم می‌سنجد. ردیفِ سطحِ بالاتر از [verify] ِ `known` (نسخهٔ تازه‌ترِ برنامه، فیلدی که این
 *  نسخه نمی‌شناسد) فقط پیوندش سنجیده می‌شود و در `unverified` شمرده.
 *
 *  Kotlin خالص (commonMain).
 */
object AuditChain {
  const val FORMAT = "tohid-audit-chain-v1"
  const val V = 1L

  const val USER = "userId"
  const val ROLE = "userRole"
  const val DEVICE = "deviceId"
  const val CHAIN = "chain"
  const val SEQ = "chainSeq"
  const val PREV = "prev"
  const val HASH = "hash"
  const val LEVEL = "chainV"

  val STAMP = listOf(USER, ROLE, DEVICE)
  val LINK = listOf(CHAIN, SEQ, PREV, HASH, LEVEL)
  private val HEAD = setOf("id", "type", "date", "refId", "createdAt", "amount", "amountMinor")
  private val NOT_DETAIL = HEAD + STAMP + LINK
  private const val MAX_CHAIN = 200

  /** مُهرِ نشستِ همان لحظه */
  data class Stamp(val userId: String?, val userRole: String?, val deviceId: String)

  /** سرِ یک زنجیره: آخرین شماره و هشش (‎0‎ و GENESIS یعنی هنوز هیچ) */
  data class Head(val chain: String, val seq: Long = 0, val hash: String = HashChain.GENESIS)

  /** پیوندِ یک ردیف، بی هش‌کردنِ دوباره */
  data class Link(val chain: String, val seq: Long, val prev: String, val hash: String, val level: Long = 1)

  /**
   *  لنگرِ محلیِ زنجیره‌های همین دستگاه (پرونده‌ای کنارِ دفتر، بیرون از پشتیبان): سرِ هر زنجیره پس از هر نوشتن.
   *  `current` زنجیره‌ای است که ادامه می‌یابد؛ سرِ زنجیره‌های پیشین (دورهٔ شکسته) شاهد می‌مانند.
   */
  data class Anchors(val current: String = "", val heads: Map<String, Head> = emptyMap()) {
    fun with(head: Head): Anchors = Anchors(head.chain, heads + (head.chain to head))

    fun encode(): String = buildString {
      append("v=1\ncurrent=").append(current).append('\n')
      heads.values.sortedBy { it.chain }.forEach { append("head=").append(it.chain).append('\t').append(it.seq).append('\t').append(it.hash).append('\n') }
    }

    companion object {
      /** متنِ [encode] ⇐ لنگر؛ خراب ⇐ null (همان «لنگر نیست») */
      fun decode(text: String?): Anchors? {
        if (text.isNullOrBlank()) return null
        val lines = text.split('\n')
        if (lines.firstOrNull() != "v=1") return null
        var current = ""
        val heads = LinkedHashMap<String, Head>()
        for (l in lines.drop(1)) {
          when {
            l.startsWith("current=") -> current = l.removePrefix("current=")
            l.startsWith("head=") -> {
              val p = l.removePrefix("head=").split('\t')
              val seq = p.getOrNull(1)?.toLongOrNull()
              if (p.size != 3 || seq == null || seq < 0 || !HashChain.isHash(p[2]) || !chainOk(p[0])) return null
              heads[p[0]] = Head(p[0], seq, p[2])
            }
            l.isEmpty() -> Unit
            else -> return null
          }
        }
        return Anchors(current, heads)
      }
    }
  }

  data class Break(val chain: String, val seq: Long, val reason: String, val id: String? = null)

  data class ChainResult(
    val chain: String,
    /** نخستین شمارهٔ موجود (‎>1‎ ⇐ پیشوند بایگانی شده) */
    val from: Long,
    val seq: Long,
    val head: String,
    val rows: Int,
    val broken: Break? = null,
  )

  data class Report(
    val chains: List<ChainResult>,
    /** ردیف‌های بی حلقه (کهنه، وب، سرور، پرچمِ خاموش) */
    val legacy: Int,
    /** ردیف‌های سطحِ بالاترِ مدل که محتوایشان این‌جا سنجیده نشد */
    val unverified: Int = 0,
    /** ردیف‌هایی که پیوندشان شکلِ درست ندارد */
    val bad: List<Break> = emptyList(),
  ) {
    val ok: Boolean get() = bad.isEmpty() && chains.all { it.broken == null }
    val chained: Int get() = chains.sumOf { it.rows }
    /** نخستین شکست: پیوندِ بدشکل، وگرنه نخستین زنجیرهٔ شکسته (به ترتیبِ نام) */
    val broken: Break? get() = bad.firstOrNull() ?: chains.firstNotNullOfOrNull { it.broken }
  }

  private fun text(row: JsonObject, k: String): String? = (row[k] as? JsonPrimitive)?.takeIf { it.isString }?.content

  private fun chainOk(c: String): Boolean = c.isNotEmpty() && c.length <= MAX_CHAIN && c.none { it == '\t' || it == '\n' || it == '\r' }

  /* ───────────────────────── یکسان‌سازی و هش ───────────────────────── */

  /** درختِ JSON ⇐ درختِ [HashChain] (عددِ ناصحیح ⇐ `$f`) */
  fun norm(e: JsonElement?): Any? = when (e) {
    null, JsonNull -> null
    is JsonObject -> LinkedHashMap<String, Any?>().also { m -> e.forEach { (k, v) -> m[k] = norm(v) } }
    is JsonArray -> e.map { norm(it) }
    is JsonPrimitive -> when {
      e.isString -> e.content
      e.content == "true" -> true
      e.content == "false" -> false
      else -> number(e.content)
    }
  }

  private fun number(text: String): Any {
    text.toLongOrNull()?.let { if (it in -HashChain.MAX_SAFE..HashChain.MAX_SAFE) return it }
    val d = text.toDoubleOrNull() ?: throw HashChainException("bad_value", "عدد نیست: $text")
    if (!d.isNaN() && !d.isInfinite() && d == floor(d) && abs(d) <= HashChain.MAX_SAFE.toDouble()) return d.toLong()
    return mapOf("\$f" to d.toRawBits().toULong().toString(16).padStart(16, '0'))
  }

  /** «خالی» در هیچ شیئی نمی‌ماند */
  fun prune(v: Any?): Any? = when (v) {
    is Map<*, *> -> LinkedHashMap<String, Any?>().also { out ->
      v.forEach { (k, x) -> val p = prune(x); if (!empty(p)) out[k as String] = p }
    }
    is List<*> -> v.map { prune(it) }
    else -> v
  }

  private fun empty(v: Any?): Boolean = when (v) {
    null -> true
    is String -> v.isEmpty()
    is Long -> v == 0L
    is Boolean -> !v
    is Map<*, *> -> v.isEmpty()
    is List<*> -> v.isEmpty()
    else -> false
  }

  /** فیلدهای جزئیات (هر چیزی جز سر، مُهر و حلقه) */
  fun detail(row: JsonObject): Map<String, Any?> {
    val m = LinkedHashMap<String, Any?>()
    row.forEach { (k, v) -> if (k !in NOT_DETAIL) m[k] = norm(v) }
    @Suppress("UNCHECKED_CAST")
    return prune(m) as Map<String, Any?>
  }

  fun detailHash(row: JsonObject): String = HashChain.sha256Hex(HashChain.canonicalJson(detail(row)))

  /** مبلغ به پول: `amountMinor` ِ v3، وگرنه `Math.round(amount × 100)` (همان `Money.fromLegacy`) */
  fun amountMinor(row: JsonObject): Long? {
    val minor = norm(row["amountMinor"])
    if (minor is Long) return minor
    val a = (row["amount"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toDoubleOrNull() ?: return null
    if (a.isNaN() || a.isInfinite()) return null
    val r = mathRound(a * 100)
    return if (r in -HashChain.MAX_SAFE..HashChain.MAX_SAFE) r else null
  }

  fun level(row: JsonObject): Long = (norm(row[LEVEL]) as? Long)?.takeIf { it >= 1 } ?: 1L

  /** بدنهٔ هش‌شدهٔ ردیف — بی متنِ جزئیات */
  fun doc(row: JsonObject): Map<String, Any?> {
    val m = LinkedHashMap<String, Any?>()
    m["v"] = V
    m["kind"] = "audit"
    m["chain"] = norm(row[CHAIN])
    m["seq"] = norm(row[SEQ])
    level(row).let { if (it > 1) m["lv"] = it }
    for (k in listOf("id", "type", "date", "refId", "createdAt")) m[k] = norm(row[k])
    m["amountMinor"] = amountMinor(row)
    for (k in STAMP) m[k] = norm(row[k])
    m["detailHash"] = detailHash(row)
    @Suppress("UNCHECKED_CAST")
    return prune(m) as Map<String, Any?>
  }

  /** هشِ ردیف روی `prev` ِ خودش */
  fun hashOf(row: JsonObject): String {
    val prev = text(row, PREV) ?: throw HashChainException("bad_prev")
    return HashChain.entryHash(prev, doc(row))
  }

  /** مُهرِ نشست روی ردیفِ تازه (فیلدِ خالیِ مُهر نوشته نمی‌شود) */
  fun stamped(row: JsonObject, stamp: Stamp): JsonObject = put(row, buildMap {
    stamp.userId?.takeIf { it.isNotBlank() }?.let { put(USER, JsonPrimitive(it)) }
    stamp.userRole?.takeIf { it.isNotBlank() }?.let { put(ROLE, JsonPrimitive(it)) }
    put(DEVICE, JsonPrimitive(stamp.deviceId))
  })

  /** حلقهٔ تازه پسِ [head]: ردیف با `chain`/`chainSeq`/`prev`/`hash` (و `chainV` اگر ‎>1‎) و سرِ تازه */
  fun linked(row: JsonObject, head: Head, level: Long = 1): Pair<JsonObject, Head> {
    if (!chainOk(head.chain)) throw HashChainException("bad_chain")
    val seq = head.seq + 1
    val base = put(row, buildMap {
      put(CHAIN, JsonPrimitive(head.chain))
      put(SEQ, JsonPrimitive(seq))
      put(PREV, JsonPrimitive(head.hash))
      if (level > 1) put(LEVEL, JsonPrimitive(level))
    }, drop = setOf(HASH, LEVEL))
    val h = hashOf(base)
    return put(base, mapOf(HASH to JsonPrimitive(h))) to Head(head.chain, seq, h)
  }

  private fun put(row: JsonObject, add: Map<String, JsonElement>, drop: Set<String> = emptySet()): JsonObject = buildJsonObject {
    row.forEach { (k, v) -> if (k !in add && k !in drop) put(k, v) }
    add.forEach { (k, v) -> put(k, v) }
  }

  /** پیوندِ ردیف؛ بی هیچ فیلدِ حلقه ⇐ null؛ بدشکل ⇐ [HashChainException] (`bad_link`) */
  fun linkOf(row: JsonObject): Link? {
    if (LINK.none { k -> norm(row[k]) != null }) return null
    fun s(k: String) = text(row, k)
    val chain = s(CHAIN)?.takeIf(::chainOk) ?: throw HashChainException("bad_link")
    val seq = (norm(row[SEQ]) as? Long)?.takeIf { it >= 1 } ?: throw HashChainException("bad_link")
    val prev = s(PREV)?.takeIf(HashChain::isHash) ?: throw HashChainException("bad_link")
    val hash = s(HASH)?.takeIf(HashChain::isHash) ?: throw HashChainException("bad_link")
    val lv = norm(row[LEVEL])
    if (lv != null && (lv !is Long || lv < 1)) throw HashChainException("bad_link")
    return Link(chain, seq, prev, hash, (lv as Long?) ?: 1)
  }

  /* ───────────────────────── ادامه از کجا ───────────────────────── */

  private fun deviceOf(chain: String) = chain.substringBeforeLast('/', "")
  private fun epochOf(chain: String) = chain.substringAfterLast('/', "").toLongOrNull() ?: 0L

  /**
   *  زنجیره و سری که حلقهٔ تازهٔ [deviceId] پسِ آن می‌نشیند. زنجیرهٔ `current` ِ لنگر فقط وقتی ادامه می‌یابد که
   *  دفتر همان سرِ لنگر را دارد (یا جلوتر است: نوشتنِ لنگر پس از دفتر نرسید). دفترِ عقب‌رفته (بازیابی، بریدنِ دم،
   *  دست‌کاری) یا لنگرِ نبوده ⇐ دورهٔ تازه از ۱ (`<deviceId>/<بزرگ‌ترین دوره + ۱>`)؛ دورهٔ کهنه شاهد می‌ماند.
   */
  fun resume(deviceId: String, links: List<Link>, anchors: Anchors?): Head {
    val mine = links.filter { deviceOf(it.chain) == deviceId }
    val cur = anchors?.current?.takeIf { it.isNotEmpty() && deviceOf(it) == deviceId }
    if (cur != null) {
      val a = anchors?.heads?.get(cur) ?: Head(cur)
      val inChain = mine.filter { it.chain == cur }
      val top = inChain.maxByOrNull { it.seq }
      val atAnchor = a.seq == 0L || inChain.any { it.seq == a.seq && it.hash == a.hash }
      if (atAnchor && (top?.seq ?: 0L) >= a.seq) return top?.let { Head(cur, it.seq, it.hash) } ?: a
    }
    val epochs = mine.map { epochOf(it.chain) } + (anchors?.heads?.keys.orEmpty().filter { deviceOf(it) == deviceId }.map(::epochOf))
    return Head("$deviceId/${(epochs.maxOrNull() ?: 0L) + 1}")
  }

  /* ───────────────────────── سنجش ───────────────────────── */

  /**
   *  @param anchors لنگرِ همین دستگاه (null ⇐ بی سنجشِ دم)
   *  @param strict  سرور: زنجیره باید از ۱ باشد (پیشوندِ نبوده ⇐ `seq_gap` در ۱)
   *  @param known   بالاترین سطحِ مدلی که این خواننده همهٔ فیلدهایش را دارد (سرور: [Long.MAX_VALUE])
   */
  fun verify(rows: List<JsonObject>, anchors: Anchors? = null, strict: Boolean = false, known: Long = 1): Report {
    var legacy = 0
    var unverified = 0
    val bad = ArrayList<Break>()
    val groups = LinkedHashMap<String, MutableList<Pair<Link, JsonObject>>>()
    for (r in rows) {
      val l = try { linkOf(r) } catch (e: HashChainException) {
        bad += Break(text(r, CHAIN).orEmpty(), (runCatching { norm(r[SEQ]) }.getOrNull() as? Long) ?: 0L, "bad_link", text(r, "id"))
        continue
      }
      if (l == null) { legacy++; continue }
      groups.getOrPut(l.chain) { ArrayList() } += l to r
    }
    val results = groups.keys.sorted().map { chain ->
      val list = groups.getValue(chain).sortedBy { it.first.seq }   //  پایدار: هم‌شماره‌ها به ترتیبِ فهرست
      var broken: Break? = null
      var last: Link? = null
      for ((l, r) in list) {
        val id = text(r, "id")
        fun fail(seq: Long, reason: String) { broken = Break(chain, seq, reason, id) }
        val p = last
        if (p == null) {
          if (l.seq == 1L && l.prev != HashChain.GENESIS) { fail(1, "prev_mismatch"); break }
          if (l.seq > 1L && strict) { fail(1, "seq_gap"); break }
        } else {
          if (l.seq == p.seq) { fail(l.seq, "fork"); break }
          if (l.seq != p.seq + 1) { fail(p.seq + 1, "seq_gap"); break }
          if (l.prev != p.hash) { fail(l.seq, "prev_mismatch"); break }
        }
        if (l.level > known) {
          unverified++
        } else {
          val h = try { hashOf(r) } catch (e: HashChainException) { fail(l.seq, "bad_doc"); break }
          if (h != l.hash) { fail(l.seq, "hash_mismatch"); break }
        }
        last = l
      }
      val first = list.first().first
      val top = last ?: first
      val anchorBreak = if (broken != null) null else anchors?.heads?.get(chain)?.let { a ->
        when {
          a.seq > top.seq -> Break(chain, top.seq + 1, "truncated")
          a.seq >= first.seq && list.none { it.first.seq == a.seq && it.first.hash == a.hash } -> Break(chain, a.seq, "anchor_mismatch")
          else -> null
        }
      }
      ChainResult(chain, first.seq, top.seq, top.hash, list.size, broken ?: anchorBreak)
    }.toMutableList()
    //  زنجیرهٔ جاریِ لنگر که هیچ ردیفی از آن در دفتر نیست ⇐ دمِ بریده (دورهٔ کهنهٔ نبوده = بایگانی، سنجیده نمی‌شود)
    anchors?.let { a ->
      val h = a.heads[a.current]
      if (h != null && h.seq > 0 && a.current !in groups) results += ChainResult(a.current, 0, 0, HashChain.GENESIS, 0, Break(a.current, 1, "truncated"))
    }
    return Report(results.sortedBy { it.chain }, legacy, unverified, bad)
  }
}
