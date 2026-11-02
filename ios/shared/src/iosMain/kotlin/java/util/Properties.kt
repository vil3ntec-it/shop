package java.util

import java.io.InputStream
import java.io.OutputStream

/** shimِ iOS ِ `Properties` — همان قالبِ «کلید=مقدار» ِ JDK (گریزِ `\` و `\uXXXX`، توضیحِ `#`/`!`) */
class Properties {
  private val map = LinkedHashMap<String, String>()
  fun getProperty(key: String): String? = map[key]
  fun getProperty(key: String, defaultValue: String): String = map[key] ?: defaultValue
  fun setProperty(key: String, value: String): Any? = map.put(key, value)
  fun stringPropertyNames(): Set<String> = map.keys.toSet()
  val size: Int get() = map.size
  private fun put(key: String, value: String) { map[key] = value }

  fun load(input: InputStream) {
    val text = input.readBytes().decodeToString()
    val joined = text.replace(Regex("\\\\\\r?\\n[ \\t\\f]*"), "")
    for (raw in joined.lines()) {
      val line = raw.trimStart(' ', '\t', '\u000C')
      if (line.isEmpty() || line[0] == '#' || line[0] == '!') continue
      var i = 0
      val key = StringBuilder()
      while (i < line.length) {
        val c = line[i]
        if (c == '\\' && i + 1 < line.length) { i = unescape(line, i, key); continue }
        if (c == '=' || c == ':' || c == ' ' || c == '\t') break
        key.append(c); i++
      }
      while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
      if (i < line.length && (line[i] == '=' || line[i] == ':')) i++
      while (i < line.length && (line[i] == ' ' || line[i] == '\t')) i++
      val value = StringBuilder()
      while (i < line.length) {
        if (line[i] == '\\' && i + 1 < line.length) i = unescape(line, i, value) else value.append(line[i++])
      }
      put(key.toString(), value.toString())
    }
  }

  private fun unescape(s: String, at: Int, out: StringBuilder): Int {
    val c = s[at + 1]
    return when (c) {
      'u' -> { out.append(s.substring(at + 2, at + 6).toInt(16).toChar()); at + 6 }
      't' -> { out.append('\t'); at + 2 }
      'n' -> { out.append('\n'); at + 2 }
      'r' -> { out.append('\r'); at + 2 }
      'f' -> { out.append('\u000C'); at + 2 }
      else -> { out.append(c); at + 2 }
    }
  }

  fun store(out: OutputStream, comments: String?) {
    val sb = StringBuilder()
    if (comments != null) sb.append('#').append(comments).append('\n')
    sb.append('#').append(Date().getTime()).append('\n')
    for ((k, v) in map) sb.append(escape(k, true)).append('=').append(escape(v, false)).append('\n')
    out.write(sb.toString().encodeToByteArray())
    out.flush()
  }

  private fun escape(s: String, key: Boolean): String = buildString {
    s.forEachIndexed { i, c ->
      when {
        c == '\\' -> append("\\\\")
        c == '\t' -> append("\\t")
        c == '\n' -> append("\\n")
        c == '\r' -> append("\\r")
        c == '\u000C' -> append("\\f")
        c == '=' || c == ':' || c == '#' || c == '!' -> append('\\').append(c)
        c == ' ' && (key || i == 0) -> append("\\ ")
        c.code < 0x20 || c.code > 0x7e -> append("\\u").append(c.code.toString(16).uppercase().padStart(4, '0'))
        else -> append(c)
      }
    }
  }
}
