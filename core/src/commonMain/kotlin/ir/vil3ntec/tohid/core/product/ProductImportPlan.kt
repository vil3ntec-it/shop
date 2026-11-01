package ir.vil3ntec.tohid.core.product

/*
 *  ورودِ کالا از Excel یا CSV — PRD-20 (§۶.۳)؛ پیش‌نمایشِ هر ردیف پیش از هر نوشتن.
 *
 *  • سرستون: همان نام‌های «گزارشِ محصولات» (و انگلیسی)؛ بی ستونِ نام یا ستونِ دوباره ⇒ کلِ فایل رد
 *    (`problem`). ستونِ ناشناس (مثلاً «موجودی») نادیده و نامش در پیش‌نمایش (`ignored`) — موجودی با ورودِ
 *    انبار عوض می‌شود، نه با این فایل. ستون‌های خودِ «فایلِ ردیف‌های رد» بی‌صدا نادیده‌اند.
 *  • هر ردیف یکی از پنج وضع (`Status`):
 *      NEW    کالای تازه
 *      SAME   همین کالا با همین داده‌ها در دکان هست ⇒ کاری نمی‌شود (ورودِ دوبارهٔ همان فایل بی اثر است)
 *      KEPT   در دکان هست و فایل چیزِ دیگری می‌گوید، ولی «به‌روزرسانیِ کالاهای موجود» خاموش است ⇒ دست نمی‌خورد
 *      UPDATE همان، با انتخابِ به‌روزرسانی ⇒ فقط `changes`
 *      ERROR  ثبت نمی‌شود؛ `reason` و در «فایلِ ردیف‌های رد»
 *  • ⛔ هیچ چیز پاک نمی‌شود: خانهٔ خالی یعنی «همان که هست»، بارکد فقط افزوده می‌شود، واحد هرگز عوض
 *    نمی‌شود (معنای موجودیِ گذشته عوض می‌شد؛ فقط یادداشت) و نام کلیدِ تطبیق است.
 *  • پول عددِ صحیح به یک‌صدمِ افغانی و مقدار به هزارم، از **رشته** (نه Double)، نیمه به +∞.
 *    خروجیِ «گزارشِ محصولات» قیمت را تا افغانی گرد می‌نویسد؛ پس عددِ درستِ فایل که همان گردشدهٔ قیمتِ
 *    دکان است تغییر نیست (۴۱٫۶۷ ⇐ «42»)، ولی «42.00» ِ صریح هست.
 *  • تکراری: نامِ یکسان (کلیدِ `SimilarNames.exactKey` ِ موتور) در همین فایل خطاست و با دکان یعنی همان
 *    کالا؛ بارکدِ کالای دیگر یا بارکدِ ردیفِ دیگرِ فایل خطاست؛ «نامِ مشابه» فقط هشدار (PRD-19).
 *  منطقِ خالص؛ آزمون: `ProductImportPlanTest` (commonTest).
 */
object ProductImportPlan {

  const val MAX_NAME = 200
  const val MAX_TEXT = 100
  const val MAX_BARCODE = 64
  /** یک میلیارد افغانی */
  const val MAX_MONEY_MINOR = 100_000_000_000L
  const val MAX_QTY_MILLI = 1_000_000_000_000L
  const val DEFAULT_UNIT = "عدد"
  const val LINE_HEADER = "ردیفِ فایلِ اصلی"
  const val REASON_HEADER = "دلیلِ رد"

  enum class Col(val label: String) {
    NAME("نام محصول"), CATEGORY("دسته"), UNIT("واحد"), PURCHASE("قیمت خرید"), SALE("قیمت فروش"),
    MIN("حداقل موجودی"), BARCODE("بارکد"),
  }

  enum class Status { NEW, SAME, KEPT, UPDATE, ERROR }

  /** کالای دکان به زبانِ همین ورود: پول به یک‌صدم، حداقلِ موجودی به هزارم */
  data class Existing(
    val id: String,
    val name: String,
    val category: String,
    val unit: String,
    val purchaseMinor: Long,
    val saleMinor: Long,
    val minMilli: Long,
    val barcodes: List<String>,
    /** PRD-04: بارکدهای بسته‌های همین کالا (کارتن…) — مالِ کالا هستند ولی بارکدِ «یک واحد» نمی‌شوند */
    val reserved: List<String> = emptyList(),
  )

  sealed interface Change {
    data class Text(val col: Col, val old: String, val new: String) : Change
    data class Price(val col: Col, val oldMinor: Long, val newMinor: Long) : Change
    data class MinStock(val oldMilli: Long, val newMilli: Long) : Change
    data class AddBarcodes(val codes: List<String>) : Change
  }

  /**
   *  یک ردیفِ فایل. `line` شمارهٔ ردیف در اکسل (سرستون ۱). برای کالای موجود، فیلدِ `null` یعنی خانهٔ خالی
   *  (دست نمی‌خورد)؛ برای کالای تازه پیش‌فرض‌ها گذاشته شده‌اند (واحد «عدد»، قیمت و حداقل صفر).
   */
  data class Row(
    val line: Int,
    val cells: List<String>,
    val status: Status,
    val reason: String = "",
    val name: String = "",
    val category: String? = null,
    val unit: String? = null,
    val purchaseMinor: Long? = null,
    val saleMinor: Long? = null,
    val minMilli: Long? = null,
    val barcodes: List<String> = emptyList(),
    val existingId: String? = null,
    val changes: List<Change> = emptyList(),
    val notes: List<String> = emptyList(),
  )

  data class Similar(val line: Int, val name: String, val other: String, val inFile: Boolean)

  data class Plan(
    val header: List<String>,
    val rows: List<Row>,
    val ignored: List<String>,
    val similar: List<Similar>,
    val fingerprint: String,
    val update: Boolean,
    /** ایرادِ کلِ فایل (بی ستونِ نام، ستونِ دوباره) — هیچ ردیفی سنجیده نشده */
    val problem: String? = null,
  ) {
    fun count(s: Status): Int = rows.count { it.status == s }
    /** چیزی برای نوشتن هست؟ */
    val writes: Int get() = count(Status.NEW) + count(Status.UPDATE)
  }

  private fun key(text: String) = SimilarNames.exactKey(text)

  private val ALIASES: Map<String, Col> = listOf(
    Col.NAME to listOf("نام محصول", "نام کالا", "نام", "کالا", "name", "product"),
    Col.CATEGORY to listOf("دسته", "دسته‌بندی", "دسته بندی", "category"),
    Col.UNIT to listOf("واحد", "unit"),
    Col.PURCHASE to listOf("قیمت خرید", "purchase price", "purchaseprice", "cost"),
    Col.SALE to listOf("قیمت فروش", "sale price", "saleprice", "price"),
    Col.MIN to listOf("حداقل موجودی", "min stock", "minstock"),
    Col.BARCODE to listOf("بارکد", "بارکدها", "barcode", "barcodes"),
  ).flatMap { (col, names) -> names.map { key(it) to col } }.toMap()

  private val REPORT_HEADERS = setOf(key(LINE_HEADER), key(REASON_HEADER))

  private const val NO_NAME_COLUMN = "ستونِ «نام محصول» در سطرِ اولِ فایل پیدا نشد"

  fun plan(table: List<List<String>>, existing: List<Existing>, update: Boolean): Plan {
    val fp = ImportTable.fingerprint(table)
    val header = table.firstOrNull().orEmpty()
    fun fail(why: String) = Plan(header, emptyList(), emptyList(), emptyList(), fp, update, problem = why)
    if (header.none { it.isNotBlank() }) return fail(NO_NAME_COLUMN)

    val cols = HashMap<Col, Int>()
    val ignored = ArrayList<String>()
    header.forEachIndexed { i, raw ->
      val k = key(raw)
      val col = ALIASES[k]
      if (col != null) {
        val first = cols[col]
        if (first != null) return fail("ستونِ «${col.label}» دو بار آمده (ستون‌های ${first + 1} و ${i + 1}) — یکی را بردارید")
        cols[col] = i
      } else if (k.isNotEmpty() && k !in REPORT_HEADERS) {
        ignored += raw.trim()
      }
    }
    if (Col.NAME !in cols) return fail(NO_NAME_COLUMN)

    val byName = HashMap<String, Existing>()
    existing.forEach { e -> byName.getOrPut(key(e.name)) { e } }
    val owner = HashMap<String, Existing>()
    existing.forEach { e -> e.reserved.forEach { owner[it] = e } }
    existing.forEach { e -> e.barcodes.forEach { owner[it] = e } }

    val seenName = HashMap<String, Int>()
    val seenCode = HashMap<String, Int>()
    val rows = ArrayList<Row>()

    table.forEachIndexed { i, r ->
      if (i == 0 || r.all { it.isBlank() }) return@forEachIndexed
      val line = i + 1
      fun at(c: Col): String? = cols[c]?.let { r.getOrNull(it) }?.trim()
      val name = at(Col.NAME).orEmpty()
      val codes = ProductBarcodes.parse(at(Col.BARCODE).orEmpty())
      //  بارکدهای هر ردیف (حتی ردیفِ خطادار) ثبت می‌شوند تا تکرارِ بعدی همیشه گرفته شود
      val repeated = codes.firstNotNullOfOrNull { c -> seenCode[c]?.let { c to it } }
      codes.forEach { c -> seenCode.getOrPut(c) { line } }
      val k = key(name)
      val firstSame = if (name.isEmpty()) null else seenName[k]
      if (name.isNotEmpty() && firstSame == null) seenName[k] = line
      val match = byName[k]

      val purchase = number(at(Col.PURCHASE), 2, MAX_MONEY_MINOR)
      val sale = number(at(Col.SALE), 2, MAX_MONEY_MINOR)
      val min = number(at(Col.MIN), 3, MAX_QTY_MILLI)
      val category = at(Col.CATEGORY).orEmpty()
      val unit = at(Col.UNIT).orEmpty()

      val reason: String? = when {
        name.isEmpty() -> "نام ندارد"
        name.length > MAX_NAME -> "نام بیش از $MAX_NAME نویسه است"
        firstSame != null -> "نامِ تکراری در همین فایل (ردیفِ $firstSame)"
        purchase is Num.Bad -> numberReason("قیمتِ خرید", purchase)
        sale is Num.Bad -> numberReason("قیمتِ فروش", sale)
        min is Num.Bad -> numberReason("حداقلِ موجودی", min)
        category.length > MAX_TEXT -> "دسته بیش از $MAX_TEXT نویسه است"
        unit.length > MAX_TEXT -> "واحد بیش از $MAX_TEXT نویسه است"
        else -> barcodeReason(codes, repeated, match, owner)
      }
      val base = Row(
        line = line, cells = r, status = Status.ERROR, reason = reason.orEmpty(), name = name,
        barcodes = codes, existingId = match?.id,
      )
      rows += when {
        reason != null -> base
        match == null -> base.copy(
          status = Status.NEW,
          category = category,
          unit = unit.ifEmpty { DEFAULT_UNIT },
          purchaseMinor = (purchase as? Num.Ok)?.value ?: 0L,
          saleMinor = (sale as? Num.Ok)?.value ?: 0L,
          minMilli = (min as? Num.Ok)?.value ?: 0L,
        )
        else -> existingRow(base, match, category, unit, purchase, sale, min, codes, update)
      }
    }
    return Plan(header, rows, ignored, similarOf(rows, existing), fp, update)
  }

  private fun existingRow(
    base: Row, e: Existing, category: String, unit: String, purchase: Num, sale: Num, min: Num,
    codes: List<String>, update: Boolean,
  ): Row {
    val changes = ArrayList<Change>()
    if (category.isNotEmpty() && category != e.category) changes += Change.Text(Col.CATEGORY, e.category, category)
    if (purchase is Num.Ok && !samePrice(purchase, e.purchaseMinor)) changes += Change.Price(Col.PURCHASE, e.purchaseMinor, purchase.value)
    if (sale is Num.Ok && !samePrice(sale, e.saleMinor)) changes += Change.Price(Col.SALE, e.saleMinor, sale.value)
    if (min is Num.Ok && min.value != e.minMilli) changes += Change.MinStock(e.minMilli, min.value)
    val added = codes.filter { it !in e.barcodes }
    if (added.isNotEmpty()) {
      if (e.barcodes.size + added.size > ProductBarcodes.MAX) {
        return base.copy(reason = "با بارکدهای تازه بیش از ${ProductBarcodes.MAX} بارکد می‌شود")
      }
      changes += Change.AddBarcodes(added)
    }
    val notes = if (unit.isNotEmpty() && unit != e.unit) {
      listOf("واحد در فایل «$unit» است و در دکان «${e.unit}» — واحد با ورود از فایل عوض نمی‌شود")
    } else emptyList()
    return base.copy(
      status = when {
        changes.isEmpty() -> Status.SAME
        update -> Status.UPDATE
        else -> Status.KEPT
      },
      category = category.ifEmpty { null },
      unit = unit.ifEmpty { null },
      purchaseMinor = (purchase as? Num.Ok)?.value,
      saleMinor = (sale as? Num.Ok)?.value,
      minMilli = (min as? Num.Ok)?.value,
      changes = changes,
      notes = notes,
    )
  }

  /** عددِ درستِ بی ممیز که همان قیمتِ دکان گردشده تا افغانی است = خروجیِ خودِ برنامه ⇒ تغییر نیست */
  private fun samePrice(file: Num.Ok, old: Long): Boolean =
    file.value == old || (file.whole && file.value % 100 == 0L && file.value / 100 == (old + 50).floorDiv(100L))

  private fun barcodeReason(codes: List<String>, repeated: Pair<String, Int>?, match: Existing?, owner: Map<String, Existing>): String? {
    codes.firstOrNull { SCIENTIFIC.matches(it) }?.let {
      return "بارکدِ «$it» در اکسل به نمادِ علمی رفته — ستونِ بارکد را «متن» کنید و بارکد را دوباره بنویسید"
    }
    codes.firstOrNull { it.length > MAX_BARCODE }?.let { return "بارکد بیش از $MAX_BARCODE نویسه است" }
    if (codes.size > ProductBarcodes.MAX) return "حداکثر ${ProductBarcodes.MAX} بارکد برای یک کالا"
    repeated?.let { (code, line) -> return "بارکدِ $code در ردیفِ $line هم آمده" }
    match?.let { m -> codes.firstOrNull { it in m.reserved } }?.let { return "بارکدِ $it بارکدِ بسته‌ای از همین کالاست، نه یک واحد" }
    codes.firstNotNullOfOrNull { c -> owner[c]?.takeIf { it.id != match?.id }?.let { c to it } }?.let { (code, other) ->
      return "بارکدِ $code مالِ کالای «${other.name}» است"
    }
    return null
  }

  private val SCIENTIFIC = Regex("^[0-9]+([.,][0-9]+)?[eE][+-]?[0-9]+$")

  private fun numberReason(label: String, n: Num.Bad): String = when (n.kind) {
    Num.Kind.NEGATIVE -> "$label منفی است: «${n.raw}»"
    Num.Kind.TOO_BIG -> "$label بیش از حد بزرگ است: «${n.raw}»"
    Num.Kind.INVALID -> "$label درست نیست: «${n.raw}»"
  }

  private fun similarOf(rows: List<Row>, existing: List<Existing>): List<Similar> {
    val fresh = rows.filter { it.status == Status.NEW }
    if (fresh.isEmpty()) return emptyList()
    val shop = SimilarNames.Index(existing.map { it.name })
    val file = SimilarNames.Index(fresh.map { it.name })
    return fresh.mapIndexedNotNull { i, r ->
      shop.find(r.name, limit = 1).firstOrNull()?.let { Similar(r.line, r.name, it.name, inFile = false) }
        ?: file.find(r.name, except = i, limit = 3).firstOrNull { it.index < i }?.let { Similar(r.line, r.name, it.name, inFile = true) }
    }
  }

  /* ------------------------------ فایلِ ردیف‌های رد ------------------------------ */

  /**
   *  «اصلاح و تلاشِ دوباره»: سرستونِ همان فایل + «ردیفِ فایلِ اصلی» + «دلیلِ رد»، و خانه‌های اصلیِ هر ردیفِ رد.
   *  کاربر همین را درست می‌کند و دوباره می‌دهد؛ این دو ستون در ورود بی‌صدا نادیده‌اند و ردیف‌های ثبت‌شده
   *  دیگر در فایل نیستند. ورودِ دوبارهٔ همین گزارش، شمارهٔ ردیفِ اصلی را نگه می‌دارد.
   */
  fun errorReport(plan: Plan): List<List<String>> {
    val drop = plan.header.indices.filter { key(plan.header[it]) in REPORT_HEADERS }.toSet()
    val lineCol = plan.header.indexOfFirst { key(it) == key(LINE_HEADER) }
    val keep = plan.header.indices.filter { it !in drop }
    val errors = plan.rows.filter { it.status == Status.ERROR }
    val width = maxOf(plan.header.size, errors.maxOfOrNull { it.cells.size } ?: 0)
    val cols = keep + (plan.header.size until width)
    val out = ArrayList<List<String>>()
    out += cols.map { plan.header.getOrNull(it).orEmpty() } + listOf(LINE_HEADER, REASON_HEADER)
    for (r in errors) {
      val original = r.cells.getOrNull(lineCol)?.trim()?.takeIf { lineCol >= 0 && it.isNotEmpty() } ?: r.line.toString()
      out += cols.map { r.cells.getOrNull(it).orEmpty() } + listOf(original, r.reason)
    }
    return out
  }

  /* ------------------------------ عدد از رشته (`DecimalText`) ------------------------------ */

  private sealed interface Num {
    enum class Kind { INVALID, NEGATIVE, TOO_BIG }
    data object Blank : Num
    data class Bad(val raw: String, val kind: Kind) : Num
    /** `whole`: در فایل بی ممیز و بی توان نوشته شده */
    data class Ok(val value: Long, val whole: Boolean) : Num
  }

  private fun number(raw: String?, scale: Int, max: Long): Num {
    val t = raw?.trim().orEmpty()
    if (t.isEmpty()) return Num.Blank
    val v = DecimalText.scaled(t, scale) ?: return Num.Bad(t, Num.Kind.INVALID)
    if (v < 0) return Num.Bad(t, Num.Kind.NEGATIVE)
    if (v > max) return Num.Bad(t, Num.Kind.TOO_BIG)
    return Num.Ok(v, whole = DecimalText.whole(t))
  }

  /** همان `DecimalText.scaled` (برای آزمون‌ها و صدازننده‌های پیشین) */
  fun scaled(text: String, scale: Int): Long? = DecimalText.scaled(text, scale)
}
