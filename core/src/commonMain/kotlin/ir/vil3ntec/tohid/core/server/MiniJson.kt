package ir.vil3ntec.tohid.core.server

/**
 *  JSONِ کوچک و سخت‌گیرِ بخشِ «سرورها» — بی کتابخانه، تا `core` وابستگیِ تازه نگیرد.
 *
 *  فقط سه جا به کارش می‌آید: پیامِ کیو‌آرِ `tohid-server:`، پاسخِ `GET /api/v1/config/server` و
 *  فهرستِ پروفایل‌های سرور روی همین دستگاه. هر سه کوچک‌اند، پس سقفِ طول و عمق دارد:
 *  ورودیِ بزرگ یا تودرتوی بی‌پایان (از کیو‌آرِ دست‌کاری‌شده یا سرورِ ناشناس) همان‌جا رد می‌شود.
 *
 *  خروجیِ `parse`: `Map<String, Any?>`، `List<Any?>`، `String`، `Long`، `Double`، `Boolean` یا `null`.
 */
internal object MiniJson {

  const val MAX_LENGTH: Int = 65_536
  const val MAX_DEPTH: Int = 8

  /** خطا ⇒ `IllegalArgumentException` */
  fun parse(text: String): Any? {
    require(text.length <= MAX_LENGTH) { "too long" }
    val p = Parser(text)
    p.space()
    val v = p.value(0)
    p.space()
    require(p.i == text.length) { "trailing data" }
    return v
  }

  /** همان `parse`، ولی فقط شیء — و هر خطا ⇒ `null` */
  @Suppress("UNCHECKED_CAST")
  fun obj(text: String?): Map<String, Any?>? {
    if (text.isNullOrBlank()) return null
    return runCatching { parse(text) as? Map<String, Any?> }.getOrNull()
  }

  fun encode(value: Any?): String = buildString { write(this, value) }

  private fun write(out: StringBuilder, v: Any?) {
    when (v) {
      null -> out.append("null")
      is String -> quote(out, v)
      is Boolean -> out.append(if (v) "true" else "false")
      is Int, is Long -> out.append(v.toString())
      is Number -> {
        val d = v.toDouble()
        require(d.isFinite()) { "non-finite number" }
        out.append(if (d == kotlin.math.floor(d) && kotlin.math.abs(d) < 1e15) d.toLong().toString() else d.toString())
      }
      is Map<*, *> -> {
        out.append('{')
        var first = true
        for ((k, value) in v) {
          if (!first) out.append(',')
          first = false
          quote(out, k.toString())
          out.append(':')
          write(out, value)
        }
        out.append('}')
      }
      is Iterable<*> -> {
        out.append('[')
        var first = true
        for (item in v) {
          if (!first) out.append(',')
          first = false
          write(out, item)
        }
        out.append(']')
      }
      else -> quote(out, v.toString())
    }
  }

  private fun quote(out: StringBuilder, s: String) {
    out.append('"')
    for (c in s) {
      when {
        c == '"' -> out.append("\\\"")
        c == '\\' -> out.append("\\\\")
        c == '\n' -> out.append("\\n")
        c == '\r' -> out.append("\\r")
        c == '\t' -> out.append("\\t")
        c.code < 0x20 -> out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
        else -> out.append(c)
      }
    }
    out.append('"')
  }

  private class Parser(val s: String) {
    var i = 0

    fun space() {
      while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
    }

    fun value(depth: Int): Any? {
      require(depth <= MAX_DEPTH) { "too deep" }
      require(i < s.length) { "unexpected end" }
      return when (val c = s[i]) {
        '{' -> obj(depth)
        '[' -> arr(depth)
        '"' -> str()
        't' -> word("true", true)
        'f' -> word("false", false)
        'n' -> word("null", null)
        else -> if (c == '-' || c in '0'..'9') num() else throw IllegalArgumentException("unexpected '$c'")
      }
    }

    private fun word(w: String, v: Any?): Any? {
      require(s.startsWith(w, i)) { "bad literal" }
      i += w.length
      return v
    }

    private fun obj(depth: Int): Map<String, Any?> {
      i++ // {
      val out = LinkedHashMap<String, Any?>()
      space()
      if (i < s.length && s[i] == '}') { i++; return out }
      while (true) {
        space()
        require(i < s.length && s[i] == '"') { "key expected" }
        val k = str()
        space()
        require(i < s.length && s[i] == ':') { "colon expected" }
        i++
        space()
        out[k] = value(depth + 1)
        space()
        require(i < s.length) { "unexpected end" }
        when (s[i]) {
          ',' -> i++
          '}' -> { i++; return out }
          else -> throw IllegalArgumentException("comma expected")
        }
      }
    }

    private fun arr(depth: Int): List<Any?> {
      i++ // [
      val out = ArrayList<Any?>()
      space()
      if (i < s.length && s[i] == ']') { i++; return out }
      while (true) {
        space()
        out.add(value(depth + 1))
        space()
        require(i < s.length) { "unexpected end" }
        when (s[i]) {
          ',' -> i++
          ']' -> { i++; return out }
          else -> throw IllegalArgumentException("comma expected")
        }
      }
    }

    private fun str(): String {
      i++ // "
      val out = StringBuilder()
      while (true) {
        require(i < s.length) { "unterminated string" }
        val c = s[i++]
        when {
          c == '"' -> return out.toString()
          c == '\\' -> {
            require(i < s.length) { "bad escape" }
            when (val e = s[i++]) {
              '"' -> out.append('"')
              '\\' -> out.append('\\')
              '/' -> out.append('/')
              'b' -> out.append('\b')
              'f' -> out.append('\u000C')
              'n' -> out.append('\n')
              'r' -> out.append('\r')
              't' -> out.append('\t')
              'u' -> {
                require(i + 4 <= s.length) { "bad unicode escape" }
                val hex = s.substring(i, i + 4)
                require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) { "bad unicode escape" }
                out.append(hex.toInt(16).toChar())
                i += 4
              }
              else -> throw IllegalArgumentException("bad escape '$e'")
            }
          }
          c.code < 0x20 -> throw IllegalArgumentException("control character in string")
          else -> out.append(c)
        }
      }
    }

    private fun num(): Any {
      val start = i
      if (s[i] == '-') i++
      while (i < s.length && s[i] in '0'..'9') i++
      var fraction = false
      if (i < s.length && s[i] == '.') {
        fraction = true
        i++
        while (i < s.length && s[i] in '0'..'9') i++
      }
      if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
        fraction = true
        i++
        if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
        while (i < s.length && s[i] in '0'..'9') i++
      }
      val text = s.substring(start, i)
      require(text != "-" && text.isNotEmpty()) { "bad number" }
      if (!fraction) text.toLongOrNull()?.let { return it }
      return text.toDoubleOrNull()?.takeIf { it.isFinite() } ?: throw IllegalArgumentException("bad number")
    }
  }
}
