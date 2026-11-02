package org.json

/*
 *  shimِ iOS ِ `org.json` (همان کتابخانهٔ درونِ اندروید) — همان رفتارِ نسخهٔ اندروید:
 *  • ترتیبِ کلیدها همان ترتیبِ افزودن (LinkedHashMap)؛ خروجیِ فشرده؛ «/» ⇐ «\/» و نویسهٔ کنترلی ⇐ \uXXXX.
 *  • عددِ خوانده‌شده: با «.» یا «e» ⇐ Double؛ وگرنه Int اگر جا شود، وگرنه Long. Double ِ برابر با یک Long
 *    بی «.0» نوشته می‌شود؛ NaN و بی‌نهایت ⇐ JSONException.
 *  • `opt*` نبود ⇐ پیش‌فرض و رشتهٔ عددی را تبدیل می‌کند؛ `get*` نبود یا نوعِ ناجور ⇐ JSONException.
 *  • `optString` روی `NULL` همان «null» ِ اندروید است.
 */
open class JSONException(message: String?) : RuntimeException(message)

private object Coerce {
  fun toBoolean(v: Any?): Boolean? = when (v) {
    is Boolean -> v
    is String -> when { v.equals("true", true) -> true; v.equals("false", true) -> false; else -> null }
    else -> null
  }

  fun toDouble(v: Any?): Double? = when (v) {
    is Double -> v
    is Number -> v.toDouble()
    is String -> v.trim().toDoubleOrNull()
    else -> null
  }

  fun toInt(v: Any?): Int? = when (v) {
    is Int -> v
    is Number -> v.toInt()
    is String -> v.trim().toDoubleOrNull()?.toInt()
    else -> null
  }

  fun toLong(v: Any?): Long? = when (v) {
    is Long -> v
    is Number -> v.toLong()
    is String -> v.trim().toDoubleOrNull()?.toLong()
    else -> null
  }

  fun toStr(v: Any?): String? = when (v) {
    is String -> v
    null -> null
    else -> v.toString()
  }

  fun typeMismatch(where: Any?, actual: Any?, want: String): JSONException =
    if (actual == null) JSONException("Value at $where is null.")
    else JSONException("Value ${actual} at $where of type ${actual::class.simpleName} cannot be converted to $want")

  fun checkDouble(d: Double): Double {
    if (d.isNaN() || d.isInfinite()) throw JSONException("Forbidden numeric value: $d")
    return d
  }

  /** `JSONObject.wrap` ِ اندروید برای مقدارِ ورودیِ `put`/سازنده */
  fun wrap(o: Any?): Any? = when (o) {
    null -> JSONObject.NULL
    is JSONObject, is JSONArray, JSONObject.NULL -> o
    is String, is Boolean, is Int, is Long, is Short, is Byte -> o
    is Double -> checkDouble(o)
    is Float -> checkDouble(o.toDouble()).let { o }
    is Number -> o
    is Char -> o.toString()
    is Map<*, *> -> JSONObject(o)
    is Collection<*> -> JSONArray(o)
    is Array<*> -> JSONArray(o.toList())
    else -> o.toString()
  }
}

internal object Writer {
  fun quote(s: String, out: StringBuilder) {
    out.append('"')
    for (c in s) {
      when (c) {
        '"', '\\', '/' -> out.append('\\').append(c)
        '\t' -> out.append("\\t")
        '\b' -> out.append("\\b")
        '\n' -> out.append("\\n")
        '\r' -> out.append("\\r")
        '\u000C' -> out.append("\\f")
        else -> if (c.code <= 0x1F) out.append("\\u").append(c.code.toString(16).padStart(4, '0')) else out.append(c)
      }
    }
    out.append('"')
  }

  fun number(n: Number): String {
    val d = n.toDouble()
    Coerce.checkDouble(d)
    if (n is Double && d == 0.0 && 1.0 / d < 0) return "-0"
    val l = n.toLong()
    return if (d == l.toDouble()) l.toString() else n.toString()
  }

  fun value(v: Any?, out: StringBuilder) {
    when (v) {
      null, JSONObject.NULL -> out.append("null")
      is JSONObject -> v.write(out)
      is JSONArray -> v.write(out)
      is String -> quote(v, out)
      is Boolean -> out.append(v)
      is Number -> out.append(number(v))
      else -> quote(v.toString(), out)
    }
  }
}

class JSONTokener(private val s: String) {
  private var i = 0

  fun nextValue(): Any? {
    skip()
    if (i >= s.length) throw syntax("End of input")
    return when (val c = s[i]) {
      '{' -> obj()
      '[' -> arr()
      '"', '\'' -> { i++; str(c) }
      else -> literal()
    }
  }

  internal fun end() { skip(); if (i < s.length) throw syntax("Unexpected trailing content") }

  private fun syntax(msg: String) = JSONException("$msg at character $i of $s".take(300))

  private fun skip() {
    while (i < s.length) {
      when {
        s[i].isWhitespace() -> i++
        s.startsWith("//", i) || s[i] == '#' -> { while (i < s.length && s[i] != '\n' && s[i] != '\r') i++ }
        s.startsWith("/*", i) -> { val e = s.indexOf("*/", i + 2); if (e < 0) throw syntax("Unterminated comment"); i = e + 2 }
        else -> return
      }
    }
  }

  private fun obj(): JSONObject {
    i++
    val o = JSONObject()
    skip()
    if (i < s.length && s[i] == '}') { i++; return o }
    while (true) {
      skip()
      val key = nextValue()
      if (key !is String) throw syntax("Names must be strings, but $key is of type ${key?.let { it::class.simpleName }}")
      skip()
      if (i < s.length && (s[i] == ':' || s[i] == '=')) {
        i++
        if (i < s.length && s[i] == '>') i++
      } else throw syntax("Expected ':' after $key")
      o.put(key, nextValue())
      skip()
      if (i >= s.length) throw syntax("Unterminated object")
      when (s[i++]) {
        '}' -> return o
        ';', ',' -> { skip(); if (i < s.length && s[i] == '}') { i++; return o } }
        else -> throw syntax("Unterminated object")
      }
    }
  }

  private fun arr(): JSONArray {
    i++
    val a = JSONArray()
    skip()
    if (i < s.length && s[i] == ']') { i++; return a }
    while (true) {
      a.put(nextValue())
      skip()
      if (i >= s.length) throw syntax("Unterminated array")
      when (s[i++]) {
        ']' -> return a
        ',', ';' -> { skip(); if (i < s.length && s[i] == ']') { i++; return a } }
        else -> throw syntax("Unterminated array")
      }
    }
  }

  private fun str(q: Char): String {
    val b = StringBuilder()
    while (true) {
      if (i >= s.length) throw syntax("Unterminated string")
      val c = s[i++]
      when {
        c == q -> return b.toString()
        c == '\\' -> {
          if (i >= s.length) throw syntax("Unterminated escape sequence")
          when (val e = s[i++]) {
            'u' -> {
              if (i + 4 > s.length) throw syntax("Unterminated escape sequence")
              b.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: throw syntax("Invalid escape sequence"))
              i += 4
            }
            't' -> b.append('\t'); 'b' -> b.append('\b'); 'n' -> b.append('\n'); 'r' -> b.append('\r'); 'f' -> b.append('\u000C')
            else -> b.append(e)
          }
        }
        else -> b.append(c)
      }
    }
  }

  private fun literal(): Any? {
    val start = i
    while (i < s.length && s[i] !in " \t\r\n\u000C,:]}/\\\"[{;=#") i++
    val t = s.substring(start, i)
    if (t.isEmpty()) throw syntax("Expected literal value")
    when {
      t.equals("null", true) -> return JSONObject.NULL
      t.equals("true", true) -> return true
      t.equals("false", true) -> return false
    }
    if (t.indexOf('.') < 0 && t.indexOf('e') < 0 && t.indexOf('E') < 0) {
      val l = when {
        t.startsWith("0x") || t.startsWith("0X") -> t.substring(2).toLongOrNull(16)
        t.length > 1 && t.startsWith("0") -> t.toLongOrNull(8)
        else -> t.toLongOrNull()
      }
      if (l != null) return if (l in Int.MIN_VALUE..Int.MAX_VALUE) l.toInt() else l
    }
    t.toDoubleOrNull()?.let { return it }
    return t
  }
}

class JSONObject() {
  private val map = LinkedHashMap<String, Any?>()

  constructor(json: String) : this() {
    val t = JSONTokener(json)
    val v = t.nextValue() as? JSONObject ?: throw JSONException("Value $json cannot be converted to JSONObject".take(300))
    t.end()
    map.putAll(v.map)
  }

  constructor(copyFrom: Map<*, *>) : this() {
    for ((k, v) in copyFrom) map[k as? String ?: throw NullPointerException("key == null")] = Coerce.wrap(v)
  }

  fun length(): Int = map.size
  fun has(name: String): Boolean = map.containsKey(name)
  fun isNull(name: String): Boolean = map[name].let { it == null || it === NULL }
  fun keys(): Iterator<String> = map.keys.toList().iterator()
  fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray(map.keys.toList())
  fun remove(name: String): Any? = map.remove(name)

  fun put(name: String, value: Boolean): JSONObject { map[name] = value; return this }
  fun put(name: String, value: Int): JSONObject { map[name] = value; return this }
  fun put(name: String, value: Long): JSONObject { map[name] = value; return this }
  fun put(name: String, value: Double): JSONObject { map[name] = Coerce.checkDouble(value); return this }

  /** null ⇐ کلید پاک می‌شود (همان اندروید) */
  fun put(name: String, value: Any?): JSONObject {
    if (value == null) { map.remove(name); return this }
    map[name] = Coerce.wrap(value)
    return this
  }

  fun putOpt(name: String?, value: Any?): JSONObject = if (name == null || value == null) this else put(name, value)

  fun opt(name: String?): Any? = name?.let { map[it] }

  fun get(name: String): Any = map[name] ?: throw JSONException("No value for $name")

  fun optBoolean(name: String, fallback: Boolean = false): Boolean = Coerce.toBoolean(opt(name)) ?: fallback
  fun getBoolean(name: String): Boolean = get(name).let { Coerce.toBoolean(it) ?: throw Coerce.typeMismatch(name, it, "boolean") }

  fun optDouble(name: String, fallback: Double = Double.NaN): Double = Coerce.toDouble(opt(name)) ?: fallback
  fun getDouble(name: String): Double = get(name).let { Coerce.toDouble(it) ?: throw Coerce.typeMismatch(name, it, "double") }

  fun optInt(name: String, fallback: Int = 0): Int = Coerce.toInt(opt(name)) ?: fallback
  fun getInt(name: String): Int = get(name).let { Coerce.toInt(it) ?: throw Coerce.typeMismatch(name, it, "int") }

  fun optLong(name: String, fallback: Long = 0L): Long = Coerce.toLong(opt(name)) ?: fallback
  fun getLong(name: String): Long = get(name).let { Coerce.toLong(it) ?: throw Coerce.typeMismatch(name, it, "long") }

  fun optString(name: String, fallback: String = ""): String = Coerce.toStr(opt(name)) ?: fallback
  fun getString(name: String): String = get(name).let { Coerce.toStr(it) ?: throw Coerce.typeMismatch(name, it, "String") }

  fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject
  fun getJSONObject(name: String): JSONObject = get(name).let { it as? JSONObject ?: throw Coerce.typeMismatch(name, it, "JSONObject") }

  fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray
  fun getJSONArray(name: String): JSONArray = get(name).let { it as? JSONArray ?: throw Coerce.typeMismatch(name, it, "JSONArray") }

  internal fun write(out: StringBuilder) {
    out.append('{')
    var first = true
    for ((k, v) in map) {
      if (!first) out.append(',')
      first = false
      Writer.quote(k, out)
      out.append(':')
      Writer.value(v, out)
    }
    out.append('}')
  }

  override fun toString(): String = StringBuilder().also { write(it) }.toString()

  /** با تورفتگی، همان شکلِ اندروید */
  fun toString(indentSpaces: Int): String = Pretty.format(toString(), indentSpaces)

  companion object {
    /** همان نشانهٔ «null» ِ JSON (نه null ِ Kotlin) */
    val NULL: Any = object {
      override fun equals(other: Any?): Boolean = other === this || other == null
      override fun hashCode(): Int = 0
      override fun toString(): String = "null"
    }

    fun quote(data: String?): String = if (data == null) "\"\"" else StringBuilder().also { Writer.quote(data, it) }.toString()
    fun numberToString(number: Number): String = Writer.number(number)
  }
}

class JSONArray() {
  private val list = ArrayList<Any?>()

  constructor(json: String) : this() {
    val t = JSONTokener(json)
    val v = t.nextValue() as? JSONArray ?: throw JSONException("Value $json cannot be converted to JSONArray".take(300))
    t.end()
    list.addAll(v.list)
  }

  constructor(copyFrom: Collection<*>) : this() {
    for (v in copyFrom) list.add(Coerce.wrap(v))
  }

  fun length(): Int = list.size

  fun put(value: Boolean): JSONArray { list.add(value); return this }
  fun put(value: Int): JSONArray { list.add(value); return this }
  fun put(value: Long): JSONArray { list.add(value); return this }
  fun put(value: Double): JSONArray { list.add(Coerce.checkDouble(value)); return this }
  fun put(value: Any?): JSONArray { list.add(if (value == null) null else Coerce.wrap(value)); return this }

  fun put(index: Int, value: Any?): JSONArray {
    if (index < 0) throw JSONException("Index $index out of range")
    while (list.size <= index) list.add(null)
    list[index] = if (value == null) null else Coerce.wrap(value)
    return this
  }

  fun remove(index: Int): Any? = if (index in list.indices) list.removeAt(index) else null
  fun isNull(index: Int): Boolean = opt(index).let { it == null || it === JSONObject.NULL }
  fun opt(index: Int): Any? = list.getOrNull(index)

  fun get(index: Int): Any {
    if (index !in list.indices) throw JSONException("Index $index out of range [0..${list.size})")
    return list[index] ?: throw JSONException("Value at $index is null.")
  }

  fun optBoolean(index: Int, fallback: Boolean = false): Boolean = Coerce.toBoolean(opt(index)) ?: fallback
  fun getBoolean(index: Int): Boolean = get(index).let { Coerce.toBoolean(it) ?: throw Coerce.typeMismatch(index, it, "boolean") }
  fun optDouble(index: Int, fallback: Double = Double.NaN): Double = Coerce.toDouble(opt(index)) ?: fallback
  fun getDouble(index: Int): Double = get(index).let { Coerce.toDouble(it) ?: throw Coerce.typeMismatch(index, it, "double") }
  fun optInt(index: Int, fallback: Int = 0): Int = Coerce.toInt(opt(index)) ?: fallback
  fun getInt(index: Int): Int = get(index).let { Coerce.toInt(it) ?: throw Coerce.typeMismatch(index, it, "int") }
  fun optLong(index: Int, fallback: Long = 0L): Long = Coerce.toLong(opt(index)) ?: fallback
  fun getLong(index: Int): Long = get(index).let { Coerce.toLong(it) ?: throw Coerce.typeMismatch(index, it, "long") }
  fun optString(index: Int, fallback: String = ""): String = Coerce.toStr(opt(index)) ?: fallback
  fun getString(index: Int): String = get(index).let { Coerce.toStr(it) ?: throw Coerce.typeMismatch(index, it, "String") }
  fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
  fun getJSONObject(index: Int): JSONObject = get(index).let { it as? JSONObject ?: throw Coerce.typeMismatch(index, it, "JSONObject") }
  fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray
  fun getJSONArray(index: Int): JSONArray = get(index).let { it as? JSONArray ?: throw Coerce.typeMismatch(index, it, "JSONArray") }

  fun join(separator: String): String = buildString {
    list.forEachIndexed { i, v -> if (i > 0) append(separator); Writer.value(v, this) }
  }

  internal fun write(out: StringBuilder) {
    out.append('[')
    list.forEachIndexed { i, v -> if (i > 0) out.append(','); Writer.value(v, out) }
    out.append(']')
  }

  override fun toString(): String = StringBuilder().also { write(it) }.toString()
  fun toString(indentSpaces: Int): String = Pretty.format(toString(), indentSpaces)
}

/** تورفتگی روی متنِ فشرده (رشته‌ها دست‌نخورده) — فقط برای نمایش/گزارش */
private object Pretty {
  fun format(compact: String, indent: Int): String {
    val out = StringBuilder()
    var depth = 0
    var inStr = false
    var i = 0
    fun nl() { out.append('\n'); repeat(depth * indent) { out.append(' ') } }
    while (i < compact.length) {
      val c = compact[i]
      if (inStr) {
        out.append(c)
        if (c == '\\') { out.append(compact[i + 1]); i++ } else if (c == '"') inStr = false
      } else when (c) {
        '"' -> { inStr = true; out.append(c) }
        '{', '[' -> {
          out.append(c)
          if (i + 1 < compact.length && (compact[i + 1] == '}' || compact[i + 1] == ']')) { out.append(compact[i + 1]); i++ } else { depth++; nl() }
        }
        '}', ']' -> { depth--; nl(); out.append(c) }
        ',' -> { out.append(c); nl() }
        ':' -> out.append(": ")
        else -> out.append(c)
      }
      i++
    }
    return out.toString()
  }
}
