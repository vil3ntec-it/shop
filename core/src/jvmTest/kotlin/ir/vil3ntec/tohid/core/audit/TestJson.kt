package ir.vil3ntec.tohid.core.audit

import java.io.File

/**
 *  خوانندهٔ کوچکِ JSON فقط برای آزمون‌های `core` (که kotlinx.serialization ندارد): درختِ ساده‌ی
 *  `Map`/`List`/`String`/`Long`/`Double`/`Boolean`/`null` — همان ورودیِ [HashChain].
 *  عددِ بی کسر و بی توان ⇐ `Long` (اگر جا شود)، وگرنه `Double` — همان معنای JSON.parse برای
 *  عددهای امن. `\uXXXX` نیم‌جفتِ تنها را هم می‌سازد (بردارِ lone_surrogate).
 */
object TestJson {

  fun parse(text: String): Any? {
    val p = Parser(text)
    val v = p.value()
    p.ws()
    check(p.i == text.length) { "JSON: نویسهٔ اضافه در ${p.i}" }
    return v
  }

  /** فایلی از ریشهٔ مخزن (از پوشهٔ کارِ آزمون رو به بالا) */
  fun repoFile(rel: String): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, rel)
      if (f.isFile) return f
      dir = dir.parentFile
    }
    error("$rel پیدا نشد (از ${File("").absolutePath})")
  }

  fun read(rel: String): Any? = parse(repoFile(rel).readText(Charsets.UTF_8))

  private class Parser(val s: String) {
    var i = 0

    fun ws() { while (i < s.length && s[i] in " \t\r\n") i++ }

    fun value(): Any? {
      ws()
      check(i < s.length) { "JSON: پایانِ ناگهانی" }
      return when (val c = s[i]) {
        '{' -> obj()
        '[' -> arr()
        '"' -> str()
        't' -> word("true", true)
        'f' -> word("false", false)
        'n' -> word("null", null)
        else -> if (c == '-' || c in '0'..'9') num() else error("JSON: نویسهٔ نابه‌جا '$c' در $i")
      }
    }

    private fun word(w: String, v: Any?): Any? {
      check(s.startsWith(w, i)) { "JSON: $w نیست در $i" }
      i += w.length
      return v
    }

    private fun obj(): Map<String, Any?> {
      val m = LinkedHashMap<String, Any?>()
      i++
      ws()
      if (s[i] == '}') { i++; return m }
      while (true) {
        ws()
        val k = str()
        ws()
        check(s[i] == ':') { "JSON: ':' نیست در $i" }
        i++
        m[k] = value()
        ws()
        when (s[i++]) {
          ',' -> continue
          '}' -> return m
          else -> error("JSON: ',' یا '}' نیست در ${i - 1}")
        }
      }
    }

    private fun arr(): List<Any?> {
      val out = ArrayList<Any?>()
      i++
      ws()
      if (s[i] == ']') { i++; return out }
      while (true) {
        out += value()
        ws()
        when (s[i++]) {
          ',' -> continue
          ']' -> return out
          else -> error("JSON: ',' یا ']' نیست در ${i - 1}")
        }
      }
    }

    private fun str(): String {
      check(s[i] == '"') { "JSON: رشته نیست در $i" }
      i++
      val sb = StringBuilder()
      while (true) {
        val c = s[i++]
        when (c) {
          '"' -> return sb.toString()
          '\\' -> when (val e = s[i++]) {
            '"' -> sb.append('"')
            '\\' -> sb.append('\\')
            '/' -> sb.append('/')
            'b' -> sb.append('\b')
            'f' -> sb.append('\u000C')
            'n' -> sb.append('\n')
            'r' -> sb.append('\r')
            't' -> sb.append('\t')
            'u' -> { sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
            else -> error("JSON: گریزِ ناشناخته \\$e")
          }
          else -> sb.append(c)
        }
      }
    }

    private fun num(): Any {
      val start = i
      if (s[i] == '-') i++
      while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
      val t = s.substring(start, i)
      if (t.none { it in ".eE" }) t.toLongOrNull()?.let { return it }
      return t.toDouble()
    }
  }
}
