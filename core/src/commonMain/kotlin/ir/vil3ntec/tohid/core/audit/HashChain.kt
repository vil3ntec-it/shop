package ir.vil3ntec.tohid.core.audit

/**
 * زنجیرهٔ هش — قالبِ یگانهٔ «tohid-hashchain-v1» (SEC-02، STF-09؛ بخشِ ۱۰.۳).
 *
 *     hash = hex( SHA-256( ASCII(prevHash) ‖ UTF-8(canonicalJson(doc)) ) )
 *     prevHash ِ نخستین حلقه = GENESIS (۶۴ صفر)
 *
 * ⛔ همین قرارداد در `server/src/lib/hash-chain.js` و `license/hash-chain.js` هست و هر سه با
 * `license/test-vectors/hashchain-v1.json` سنجیده می‌شوند (`HashChainVectorsTest` در jvmTest ِ
 * همین `core`). قالب عوض شد؟ بردارِ v2؛ v1 دست نمی‌خورد.
 *
 * سند «درختِ JSON» ِ ساده است — همان قراردادِ `PromoCore`: `Map<String, *>`، `List<*>`، `String`،
 * عدد، `Boolean` و `null`. canonicalJson = زیرمجموعهٔ RFC 8785 (JCS): کلیدها به ترتیبِ واحدهای
 * UTF-16 (همان `String.compareTo`)، رشته مثلِ JSON.stringify ِ JS، نیم‌جفتِ تنها رد، عدد **فقط
 * صحیح** با |n| ≤ 2^53−1 (مبلغ به کوچک‌ترین واحد؛ ‎1.0‎ همان ۱ است، ‎1.5‎ نه؛ ‎−0‎ همان ۰).
 *
 * Kotlin خالص (commonMain): بی `java.*` — درهم با [Sha256].
 * (تا ۱۴۰۵/۰۸/۰۸ همین منطق روی `JsonElement` در `android-native/.../core/audit` بود.)
 */
class HashChainException(val code: String, message: String = code) : IllegalArgumentException(message)

data class ChainEntry(val seq: Long?, val prevHash: String, val hash: String, val doc: Any?)

data class ChainCheck(
  val ok: Boolean,
  val length: Int = 0,
  val head: String = HashChain.GENESIS,
  /** شمارهٔ ۰-پایهٔ نخستین حلقهٔ شکسته؛ ‎-1‎ یعنی سالم */
  val index: Int = -1,
  /** seq_gap | prev_mismatch | bad_doc | hash_mismatch */
  val reason: String? = null,
)

object HashChain {
  const val FORMAT = "tohid-hashchain-v1"
  val GENESIS: String = "0".repeat(64)
  const val MAX_SAFE = 9_007_199_254_740_991L
  private val HEX64 = Regex("^[0-9a-f]{64}$")
  private const val HEX = "0123456789abcdef"

  fun isHash(s: String): Boolean = HEX64.matches(s)

  fun canonicalJson(value: Any?): String = StringBuilder().also { write(value, it) }.toString()

  private fun write(v: Any?, out: StringBuilder) {
    when (v) {
      null -> out.append("null")
      is Boolean -> out.append(if (v) "true" else "false")
      is String -> quote(v, out)
      is Number -> out.append(integerValue(v).toString())
      is Map<*, *> -> {
        val keys = v.keys.map { k -> k as? String ?: throw HashChainException("bad_value", "کلیدِ شیء باید رشته باشد") }
        keys.forEach { checkSurrogates(it) }
        out.append('{')
        keys.sorted().forEachIndexed { i, k ->
          if (i > 0) out.append(',')
          quote(k, out)
          out.append(':')
          write(v[k], out)
        }
        out.append('}')
      }
      is List<*> -> {
        out.append('[')
        v.forEachIndexed { i, e -> if (i > 0) out.append(','); write(e, out) }
        out.append(']')
      }
      else -> throw HashChainException("bad_value", "این نوع در سند نمی‌آید")
    }
  }

  /** عددِ صحیحِ امن ⇐ Long؛ ‎1.0‎ و ‎1e2‎ صحیح‌اند (مثلِ JSON.parse)، ‎1.5‎ و بیرون از ±(2^53−1) نه. */
  fun integerValue(v: Any?): Long {
    val n: Long = when (v) {
      is Long -> v
      is Int -> v.toLong()
      is Short -> v.toLong()
      is Byte -> v.toLong()
      is Double, is Float -> {
        val d = (v as Number).toDouble()
        if (d.isNaN() || d.isInfinite() || d != kotlin.math.floor(d) || kotlin.math.abs(d) > MAX_SAFE.toDouble()) {
          throw HashChainException("not_integer", "عدد باید صحیح باشد: $v")
        }
        d.toLong()
      }
      else -> throw HashChainException("not_integer", "عدد نیست: $v")
    }
    if (n > MAX_SAFE || n < -MAX_SAFE) throw HashChainException("not_integer", "بیرون از 2^53: $v")
    return n
  }

  private fun checkSurrogates(s: String) {
    var i = 0
    while (i < s.length) {
      val c = s[i]
      if (c.isHighSurrogate()) {
        if (i + 1 >= s.length || !s[i + 1].isLowSurrogate()) throw HashChainException("lone_surrogate")
        i++
      } else if (c.isLowSurrogate()) {
        throw HashChainException("lone_surrogate")
      }
      i++
    }
  }

  private fun quote(s: String, out: StringBuilder) {
    checkSurrogates(s)
    out.append('"')
    for (c in s) {
      when {
        c == '"' -> out.append("\\\"")
        c == '\\' -> out.append("\\\\")
        c == '\b' -> out.append("\\b")
        c == '\u000C' -> out.append("\\f")
        c == '\n' -> out.append("\\n")
        c == '\r' -> out.append("\\r")
        c == '\t' -> out.append("\\t")
        c < ' ' -> out.append("\\u00").append(HEX[c.code shr 4]).append(HEX[c.code and 0xF])
        else -> out.append(c)
      }
    }
    out.append('"')
  }

  fun sha256Hex(text: String): String = Sha256.hexOf(text)

  fun entryHash(prevHash: String, doc: Any?): String {
    if (!HEX64.matches(prevHash)) throw HashChainException("bad_prev")
    return sha256Hex(prevHash + canonicalJson(doc))
  }

  /** حلقهٔ تازه پسِ آخرین حلقهٔ [entries]. */
  fun append(entries: List<ChainEntry>, doc: Any?): ChainEntry {
    val prev = entries.lastOrNull()?.hash ?: GENESIS
    return ChainEntry(entries.size + 1L, prev, entryHash(prev, doc), doc)
  }

  fun verify(entries: List<ChainEntry>): ChainCheck {
    var prev = GENESIS
    entries.forEachIndexed { i, e ->
      fun fail(reason: String) = ChainCheck(ok = false, length = i, head = prev, index = i, reason = reason)
      if (e.seq != null && e.seq != i + 1L) return fail("seq_gap")
      if (e.prevHash != prev) return fail("prev_mismatch")
      val h = try { entryHash(prev, e.doc) } catch (ex: HashChainException) { return fail("bad_doc") }
      if (h != e.hash) return fail("hash_mismatch")
      prev = h
    }
    return ChainCheck(ok = true, length = entries.size, head = prev)
  }
}
