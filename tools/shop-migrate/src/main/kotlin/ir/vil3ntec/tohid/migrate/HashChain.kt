package ir.vil3ntec.tohid.migrate

import java.security.MessageDigest

/**
 *  زنجیرهٔ هشِ اسنادِ مالی (بخشِ ۱۰.۳):
 *
 *      hash = SHA-256(prevHash ‖ canonicalJson(doc))
 *
 *  ‌• `prevHash` و `hash` رشتهٔ hexِ کوچک‌حرفِ ۶۴ نویسه‌ای‌اند؛ نخستین سند
 *    `GENESIS` (شصت‌وچهار صفر) را می‌گیرد.
 *  ‌• `‖` یعنی چسباندنِ دو رشته؛ بایت‌ها UTF-8.
 *  ‌• `canonicalJson` = RFC 8785 (JCS) برای زیرمجموعه‌ای که اسناد لازم دارند:
 *    شیء، آرایه، رشته، عددِ صحیح (|n| ≤ 2^53)، true/false، null. مبلغ همیشه
 *    عددِ صحیحِ واحدِ کوچک است، پس Kotlin و JS یک هش می‌سازند.
 *  ‌• دادهٔ شخصی (نام، تلفن، یادداشت) در سند نیست — فقط شناسه.
 */
object HashChain {

  const val GENESIS = "0000000000000000000000000000000000000000000000000000000000000000"
  private const val MAX_SAFE = 9_007_199_254_740_991L

  fun hash(prevHash: String, doc: Map<String, Any?>): String {
    require(prevHash.length == 64) { "prevHash must be 64 hex chars" }
    val bytes = (prevHash + canonicalJson(doc)).toByteArray(Charsets.UTF_8)
    return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
  }

  fun canonicalJson(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

  private fun write(out: StringBuilder, v: Any?) {
    when (v) {
      null -> out.append("null")
      is Boolean -> out.append(if (v) "true" else "false")
      is Int -> write(out, v.toLong())
      is Long -> {
        require(v in -MAX_SAFE..MAX_SAFE) { "integer outside the JS-safe range: $v" }
        out.append(v.toString())
      }
      is String -> quote(out, v)
      is Map<*, *> -> {
        out.append('{')
        //  JCS: کلیدها به ترتیبِ واحدهای UTF-16 — همان `String.compareTo`
        v.entries.map { (it.key as String) to it.value }.sortedBy { it.first }
          .forEachIndexed { i, (k, value) ->
            if (i > 0) out.append(',')
            quote(out, k)
            out.append(':')
            write(out, value)
          }
        out.append('}')
      }
      is List<*> -> {
        out.append('[')
        v.forEachIndexed { i, item -> if (i > 0) out.append(','); write(out, item) }
        out.append(']')
      }
      else -> throw IllegalArgumentException("not allowed in a hashed document: ${v::class.simpleName}")
    }
  }

  /** همان گریزِ `JSON.stringify` (ES2019، well-formed) که JCS می‌خواهد */
  private fun quote(out: StringBuilder, s: String) {
    out.append('"')
    var i = 0
    while (i < s.length) {
      val c = s[i]
      when {
        c == '"' -> out.append("\\\"")
        c == '\\' -> out.append("\\\\")
        c == '\b' -> out.append("\\b")
        c == '\u000C' -> out.append("\\f")
        c == '\n' -> out.append("\\n")
        c == '\r' -> out.append("\\r")
        c == '\t' -> out.append("\\t")
        c < ' ' -> out.append("\\u%04x".format(c.code))
        c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
          out.append(c).append(s[i + 1]); i++
        }
        c.isSurrogate() -> out.append("\\u%04x".format(c.code))
        else -> out.append(c)
      }
      i++
    }
    out.append('"')
  }

  data class Link(val id: String, val seq: Long, val prevHash: String, val hash: String, val doc: Map<String, Any?>)

  /** زنجیره را از نو حساب می‌کند؛ نخستین حلقهٔ خراب را برمی‌گرداند یا null */
  fun firstBroken(chain: List<Link>): Link? {
    var prev = GENESIS
    chain.sortedBy { it.seq }.forEachIndexed { i, link ->
      if (link.seq != i + 1L || link.prevHash != prev || hash(prev, link.doc) != link.hash) return link
      prev = link.hash
    }
    return null
  }
}
