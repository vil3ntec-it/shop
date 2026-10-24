package ir.vil3ntec.tohid.core.image

import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  ورودِ گروهیِ عکس: فایل ⇒ کالا (v3 ۳۴.۲، ۳۶.۸، IMG-01/IMG-02/IMG-09).
 *  همتای `matchFile`ِ `license/image-bank.js` (#289، بسته و مرج‌نشده — وب دیگر
 *  توسعه داده نمی‌شود)؛ قاعدهٔ این‌جا مرجع است.
 *
 *  ترتیب: ردیفِ CSV ← بارکد در نامِ فایل ← نامِ کاملِ کالا.
 *    یک کالا ⇒ MATCHED · چند کالا ⇒ AMBIGUOUS (هیچ‌کدام خودکار نه) · هیچ ⇒ UNMATCHED
 *  ⛔ نامِ «مشابه» هرگز تطبیق نیست؛ فقط کلیدِ مقایسهٔ یکسان (`TextNormalizer.compareKey`).
 *  ⛔ نامِ **کاملِ** فایل اول؛ بی پسوندِ شماره («(4)»، «-12»، «_2») فقط وقتی نامِ
 *     کامل هیچ نیافت. هر دو یافتند و یکی نیستند ⇒ AMBIGUOUS («باتری قلمی (4)»
 *     می‌تواند بستهٔ چهارتایی باشد، نه نسخهٔ چهارمِ عکسِ «باتری قلمی»).
 *  ⛔ CSV: بارکد و نامِ یک ردیف دو کالای متفاوت ⇒ AMBIGUOUS؛ یک نامِ فایل در چند
 *     ردیفِ متفاوت ⇒ AMBIGUOUS، نه «آخری برنده».
 */
object BulkMatch {

  data class Item(val id: String, val name: String, val barcodes: List<String>)
  data class CsvRow(val barcode: String, val name: String)
  enum class Status { MATCHED, AMBIGUOUS, UNMATCHED }
  data class Result(val status: Status, val productId: String, val candidates: List<String>, val how: String, val key: String)

  private val IMAGE_EXT = Regex("\\.(jpe?g|png|webp|gif|bmp|heic|heif)$", RegexOption.IGNORE_CASE)
  fun isImageName(name: String): Boolean = IMAGE_EXT.containsMatchIn(name)
  fun isCsvName(name: String): Boolean = name.lowercase().endsWith(".csv")

  /** ارقامِ فارسی/عربی ⇒ لاتین؛ بقیه دست‌نخورده */
  fun asciiDigits(s: String): String = buildString(s.length) {
    for (c in s) append(
      when (c) {
        in '۰'..'۹' -> '0' + (c - '۰')
        in '٠'..'٩' -> '0' + (c - '٠')
        else -> c
      }
    )
  }

  fun digitsOf(s: String): String = asciiDigits(s).filter { it in '0'..'9' }

  /** GTIN-8/12/13/14 با رقمِ کنترلِ درست ⇒ ۱۴ رقم؛ وگرنه "" (همان سرور) */
  fun gtin14(raw: String): String {
    val d = digitsOf(raw)
    if (d.length !in setOf(8, 12, 13, 14)) return ""
    var sum = 0
    var w = 3
    for (i in d.length - 2 downTo 0) { sum += (d[i] - '0') * w; w = 4 - w }
    return if ((10 - sum % 10) % 10 == d.last() - '0') d.padStart(14, '0') else ""
  }

  private val SUFFIX = Regex("(?:\\s*\\(\\d{1,2}\\)|[_-]\\d{1,2})$")

  /** نامِ فایل بی پوشه و بی پسوند — همان که اول سنجیده می‌شود */
  fun fullStemOf(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\')
    return base.substringBeforeLast('.', base).trim()
  }

  /** «۶۲۶۱۲۳۴۵۶۷۸۹۰_2»، «شیر-1»، «چای (2)» ⇒ بی پسوندِ شماره (فقط راهِ دوم) */
  fun stemOf(fileName: String): String {
    val stem = fullStemOf(fileName)
    val cut = stem.replace(SUFFIX, "").trim()
    return cut.ifEmpty { stem }
  }

  /**
   *  CSV با سرستون: barcode | name | image_filename (یا image_path، filename، file، image).
   *  نامِ فایل ⇒ ردیف‌هایش؛ ردیفِ عیناً تکراری یکی می‌شود، ردیفِ متفاوت کنارش
   *  می‌ماند تا `match` «مبهم» بدهد. چند CSV ⇒ همه در همان `into`.
   */
  fun parseCsv(text: String, into: MutableMap<String, List<CsvRow>> = LinkedHashMap()): Map<String, List<CsvRow>> {
    val lines = text.removePrefix("﻿").split(Regex("\\r?\\n")).filter { it.isNotBlank() }
    if (lines.isEmpty()) return into
    val sep = listOf(',', ';', '\t').maxBy { c -> lines[0].count { it == c } }
    fun split(l: String): List<String> {
      val out = ArrayList<String>()
      val cur = StringBuilder()
      var q = false
      var i = 0
      while (i < l.length) {
        val ch = l[i]
        when {
          ch == '"' && q && i + 1 < l.length && l[i + 1] == '"' -> { cur.append('"'); i++ }
          ch == '"' -> q = !q
          ch == sep && !q -> { out += cur.toString().trim(); cur.clear() }
          else -> cur.append(ch)
        }
        i++
      }
      out += cur.toString().trim()
      return out
    }
    val head = split(lines[0]).map { it.lowercase() }
    fun col(names: List<String>) = head.indexOfFirst { it in names }
    val fi = col(listOf("image_filename", "image_path", "filename", "file", "image"))
    val bi = col(listOf("barcode", "gtin", "بارکد"))
    val ni = col(listOf("name", "product", "نام"))
    if (fi < 0 || (bi < 0 && ni < 0)) return into
    for (l in lines.drop(1)) {
      val c = split(l)
      val f = c.getOrElse(fi) { "" }.substringAfterLast('/').substringAfterLast('\\').lowercase()
      if (f.isEmpty()) continue
      val row = CsvRow(if (bi >= 0) c.getOrElse(bi) { "" } else "", if (ni >= 0) c.getOrElse(ni) { "" } else "")
      val prev = into[f].orEmpty()
      if (row !in prev) into[f] = prev + row
    }
    return into
  }

  fun match(fileName: String, products: List<Item>, csv: Map<String, List<CsvRow>> = emptyMap()): Result {
    fun byCode(code: String): List<Item> {
      val d = digitsOf(code)
      val g = gtin14(code)
      if (d.isEmpty()) return emptyList()
      return products.filter { p -> p.barcodes.any { b -> digitsOf(b) == d || (g.isNotEmpty() && gtin14(b) == g) } }
    }
    fun byName(n: String): List<Item> {
      val k = TextNormalizer.compareKey(n.replace(Regex("_+"), " "))
      return if (k.isEmpty()) emptyList() else products.filter { TextNormalizer.compareKey(it.name) == k }
    }
    fun out(hits: List<Item>, how: String, key: String) = when (hits.size) {
      1 -> Result(Status.MATCHED, hits[0].id, listOf(hits[0].id), how, key)
      0 -> Result(Status.UNMATCHED, "", emptyList(), how, key)
      else -> Result(Status.AMBIGUOUS, "", hits.map { it.id }, how, key)
    }
    /** دو راه، دو جواب ⇒ مبهم با همهٔ نامزدها (حتی اگر یکی باشد)؛ ترتیب همان فهرستِ کالا */
    fun conflict(ids: Set<String>, how: String, key: String): Result {
      val c = products.filter { it.id in ids }.map { it.id }
      return Result(if (c.isEmpty()) Status.UNMATCHED else Status.AMBIGUOUS, "", c, how, key)
    }
    fun ids(hits: List<Item>) = hits.mapTo(LinkedHashSet()) { it.id }

    val base = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
    csv[base]?.let { rows ->
      fun resolve(row: CsvRow): Result {
        val code = if (digitsOf(row.barcode).isNotEmpty()) byCode(row.barcode) else null
        val name = if (row.name.isNotBlank()) byName(row.name) else null
        return when {
          code == null && name == null -> out(emptyList(), "csv_name", row.name)
          code == null -> out(name!!, "csv_name", row.name)
          name.isNullOrEmpty() -> out(code, "csv_barcode", row.barcode)
          code.isEmpty() -> out(name, "csv_name", row.name)
          ids(code) == ids(name) -> out(code, "csv_barcode", row.barcode)
          else -> conflict(ids(code) + ids(name), "csv_conflict", row.barcode)
        }
      }
      if (rows.size == 1) return resolve(rows[0])
      //  یک فایل در چند ردیف: فقط وقتی همه دقیقاً همان یک کالا را بگویند
      val all = rows.map(::resolve)
      val one = all.first().productId
      if (one.isNotEmpty() && all.all { it.status == Status.MATCHED && it.productId == one }) return all.first()
      return conflict(all.flatMapTo(LinkedHashSet()) { it.candidates }, "csv_duplicate", base)
    }

    fun byStem(stem: String): Result {
      val compact = asciiDigits(stem).replace(Regex("[\\s-]"), "")
      return if (Regex("^\\d{8,14}$").matches(compact)) out(byCode(compact), "barcode", compact)
      else out(byName(stem), "name", stem)
    }
    val full = byStem(fullStemOf(fileName))
    val cutStem = stemOf(fileName)
    if (cutStem == fullStemOf(fileName)) return full
    val cut = byStem(cutStem)
    return when {
      full.candidates.isEmpty() -> cut
      cut.candidates.isEmpty() || cut.candidates.toSet() == full.candidates.toSet() -> full
      else -> conflict(full.candidates.toSet() + cut.candidates, "suffix_conflict", full.key)
    }
  }
}
