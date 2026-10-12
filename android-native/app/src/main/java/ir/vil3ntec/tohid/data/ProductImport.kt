package ir.vil3ntec.tohid.data

/**
 *  ورودِ گروهیِ کالا از فایلِ CSV (اکسل ⇐ «ذخیره به CSV»).
 *
 *  ── قاعده ─────────────────────────────────────────────────────────
 *  • سرستون‌ها همان خروجیِ «گزارشِ محصولات» است («نام محصول»، «دسته»،
 *    «واحد»، «قیمت خرید»، «قیمت فروش»، «حداقل موجودی») به‌اضافهٔ «بارکد»؛
 *    پس فایلی که از همین برنامه بیرون آمده بی‌دست‌کاری برمی‌گردد. نام‌های
 *    انگلیسی هم پذیرفته می‌شوند.
 *  • هیچ ردیفی بی‌صدا گم نمی‌شود: ردیفِ بی‌نام، نامِ تکراری (در دکان یا
 *    در خودِ فایل)، بارکدِ تکراری یا عددِ نادرست با شمارهٔ ردیف و دلیل
 *    در پیش‌نمایش می‌آید و ثبت نمی‌شود. بقیه با همان `addProducts`ِ
 *    ثبتِ گروهی ثبت می‌شوند — یعنی همان قاعده‌های فرمِ تک‌کالا.
 *  • جداکنندهٔ «;» (اکسلِ بعضی زبان‌ها) هم شناخته می‌شود.
 *  • رقمِ فارسی، «٬» هزارگان و «٫» اعشار خوانده می‌شوند.
 *
 *  همان `importProductsCsv`ِ نسخهٔ وب.
 *  ──────────────────────────────────────────────────────────────────
 */
object ProductImport {

  data class Skip(val row: Int, val name: String, val reason: String)

  data class Plan(val drafts: List<WarehouseEngine.ProductDraft>, val skipped: List<Skip>, val missingName: Boolean)

  private val ALIASES: Map<String, String> = mapOf(
    "نام محصول" to "name", "نام کالا" to "name", "نام" to "name", "کالا" to "name", "name" to "name", "product" to "name",
    "دسته" to "category", "دسته‌بندی" to "category", "دسته بندی" to "category", "category" to "category",
    "واحد" to "unit", "unit" to "unit",
    "قیمت خرید" to "purchase", "purchase price" to "purchase", "purchaseprice" to "purchase", "cost" to "purchase",
    "قیمت فروش" to "sale", "sale price" to "sale", "saleprice" to "sale", "price" to "sale",
    "حداقل موجودی" to "min", "min stock" to "min", "minstock" to "min",
    "بارکد" to "barcode", "barcode" to "barcode",
  )

  /** CSVِ استاندارد: گیومه، گیومهٔ دوتایی، خطِ تازه درونِ گیومه، BOM */
  fun parse(text: String): List<List<String>> {
    val src = text.removePrefix("﻿")
    val firstLine = src.lineSequence().firstOrNull().orEmpty()
    val sep = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
    val rows = ArrayList<List<String>>()
    var row = ArrayList<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    while (i < src.length) {
      val c = src[i]
      if (quoted) {
        if (c == '"') {
          if (i + 1 < src.length && src[i + 1] == '"') { cell.append('"'); i++ } else quoted = false
        } else cell.append(c)
      } else when (c) {
        '"' -> quoted = true
        sep -> { row += cell.toString(); cell.clear() }
        '\r' -> {}
        '\n' -> { row += cell.toString(); cell.clear(); rows += row; row = ArrayList() }
        else -> cell.append(c)
      }
      i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) { row += cell.toString(); rows += row }
    //  ردیفِ خالی نگه داشته می‌شود تا شمارهٔ ردیف با اکسل یکی بماند
    return rows
  }

  /** «۱٬۲۵۰٫۵» ⇐ 1250.5؛ خالی ⇐ 0؛ نادرست ⇐ NaN */
  internal fun number(text: String): Double {
    val t = ir.vil3ntec.tohid.latinDigits(text.trim())
      .replace("٬", "").replace(",", "").replace("٫", ".").replace(" ", "")
    if (t.isEmpty()) return 0.0
    return t.toDoubleOrNull() ?: Double.NaN
  }

  fun plan(d: ShopData, text: String): Plan {
    val rows = parse(text)
    if (rows.none { r -> r.any { it.isNotBlank() } }) return Plan(emptyList(), emptyList(), missingName = true)
    val header = rows.first().map { ALIASES[it.trim().lowercase()] }
    val col = header.withIndex().filter { it.value != null }.associate { it.value!! to it.index }
    if ("name" !in col) return Plan(emptyList(), emptyList(), missingName = true)
    fun at(r: List<String>, key: String): String = col[key]?.let { r.getOrNull(it) }?.trim().orEmpty()

    val drafts = ArrayList<WarehouseEngine.ProductDraft>()
    val skipped = ArrayList<Skip>()
    val seenNames = HashSet<String>()
    val seenCodes = HashSet<String>()
    val codes = ShopStore.barcodeIndex(d)
    rows.drop(1).forEachIndexed { i, r ->
      val n = i + 2   // شمارهٔ ردیف در اکسل (سرستون ردیفِ ۱ است)
      if (r.all { it.isBlank() }) return@forEachIndexed
      val name = at(r, "name")
      val key = name.replace(Regex("\\s+"), " ").replace('ي', 'ی').replace('ك', 'ک').lowercase()
      val purchase = number(at(r, "purchase"))
      val sale = number(at(r, "sale"))
      val min = number(at(r, "min"))
      val barcode = at(r, "barcode")
      val reason = when {
        name.isEmpty() -> "نام ندارد"
        !seenNames.add(key) -> "نامِ تکراری در همین فایل"
        WarehouseEngine.duplicateName(d, name) != null -> "این کالا از قبل در دکان هست"
        purchase.isNaN() || purchase < 0 -> "قیمتِ خرید درست نیست"
        sale.isNaN() || sale < 0 -> "قیمتِ فروش درست نیست"
        min.isNaN() || min < 0 -> "حداقلِ موجودی درست نیست"
        barcode.isNotEmpty() && (codes.containsKey(barcode) || !seenCodes.add(barcode)) -> "بارکدِ تکراری"
        else -> null
      }
      if (reason != null) skipped += Skip(n, name, reason)
      else drafts += WarehouseEngine.ProductDraft(
        name = name,
        category = at(r, "category"),
        unit = at(r, "unit").ifEmpty { "عدد" },
        purchasePrice = purchase,
        salePrice = sale,
        minStock = min,
        barcode = barcode,
      )
    }
    return Plan(drafts, skipped, missingName = false)
  }
}
