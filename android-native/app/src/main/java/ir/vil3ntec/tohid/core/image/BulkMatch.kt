package ir.vil3ntec.tohid.core.image

import ir.vil3ntec.tohid.core.text.TextNormalizer

/**
 *  ورودِ گروهیِ عکس: فایل ⇒ کالا (v3 ۳۴.۲، ۳۶.۸، IMG-01/IMG-02/IMG-09).
 *  همتای `matchFile`ِ `license/image-bank.js` — همان ترتیب و همان نتیجه.
 *
 *  ترتیب: ردیفِ CSV ← بارکد در نامِ فایل ← نامِ کاملِ کالا.
 *    یک کالا ⇒ MATCHED · چند کالا ⇒ AMBIGUOUS (هیچ‌کدام خودکار نه) · هیچ ⇒ UNMATCHED
 *  ⛔ نامِ «مشابه» هرگز تطبیق نیست؛ فقط کلیدِ مقایسهٔ یکسان (`TextNormalizer.compareKey`).
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

  /** «۶۲۶۱۲۳۴۵۶۷۸۹۰_2»، «شیر-1»، «چای (2)» ⇒ همان کالا */
  fun stemOf(fileName: String): String {
    val base = fileName.substringAfterLast('/').substringAfterLast('\\')
    val stem = base.substringBeforeLast('.', base)
    val cut = stem.replace(SUFFIX, "").trim()
    return cut.ifEmpty { stem.trim() }
  }

  /** CSV با سرستون: barcode | name | image_filename (یا image_path، filename، file، image) */
  fun parseCsv(text: String): Map<String, CsvRow> {
    val lines = text.removePrefix("﻿").split(Regex("\\r?\\n")).filter { it.isNotBlank() }
    if (lines.isEmpty()) return emptyMap()
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
    if (fi < 0 || (bi < 0 && ni < 0)) return emptyMap()
    val map = LinkedHashMap<String, CsvRow>()
    for (l in lines.drop(1)) {
      val c = split(l)
      val f = c.getOrElse(fi) { "" }.substringAfterLast('/').substringAfterLast('\\').lowercase()
      if (f.isNotEmpty()) map[f] = CsvRow(if (bi >= 0) c.getOrElse(bi) { "" } else "", if (ni >= 0) c.getOrElse(ni) { "" } else "")
    }
    return map
  }

  fun match(fileName: String, products: List<Item>, csv: Map<String, CsvRow> = emptyMap()): Result {
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
    val base = fileName.substringAfterLast('/').substringAfterLast('\\').lowercase()
    csv[base]?.let { row ->
      if (digitsOf(row.barcode).isNotEmpty()) {
        val r = out(byCode(row.barcode), "csv_barcode", row.barcode)
        if (r.status != Status.UNMATCHED || row.name.isBlank()) return r
      }
      return out(byName(row.name), "csv_name", row.name)
    }
    val stem = stemOf(fileName)
    val compact = asciiDigits(stem).replace(Regex("[\\s-]"), "")
    if (Regex("^\\d{8,14}$").matches(compact)) return out(byCode(compact), "barcode", compact)
    return out(byName(stem), "name", stem)
  }
}
