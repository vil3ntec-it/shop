package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.ImportTable
import ir.vil3ntec.tohid.core.product.ProductImportPlan
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

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

  /**
   *  PRD-19: ردیفی که ثبت می‌شود ولی نامش **مشابهِ** کالای دکان یا ردیفِ پیشینِ همین فایل است
   *  (فاصله/نیم‌فاصله، ترتیبِ کلمه، غلطِ تایپی؛ `SimilarNames` ِ core) — فقط هشدار در پیش‌نمایش.
   */
  data class Similar(val row: Int, val name: String, val other: String, val inFile: Boolean)

  data class Plan(
    val drafts: List<WarehouseEngine.ProductDraft>,
    val skipped: List<Skip>,
    val missingName: Boolean,
    val similar: List<Similar> = emptyList(),
  )

  private val ALIASES: Map<String, String> = mapOf(
    "نام محصول" to "name", "نام کالا" to "name", "نام" to "name", "کالا" to "name", "name" to "name", "product" to "name",
    "دسته" to "category", "دسته‌بندی" to "category", "دسته بندی" to "category", "category" to "category",
    "واحد" to "unit", "unit" to "unit",
    "قیمت خرید" to "purchase", "purchase price" to "purchase", "purchaseprice" to "purchase", "cost" to "purchase",
    "قیمت فروش" to "sale", "sale price" to "sale", "saleprice" to "sale", "price" to "sale",
    "حداقل موجودی" to "min", "min stock" to "min", "minstock" to "min",
    "بارکد" to "barcode", "barcode" to "barcode",
  )

  /**
   *  CSVِ استاندارد: گیومه، گیومهٔ دوتایی، خطِ تازه درونِ گیومه، BOM. همان خوانندهٔ همیشگی که به core
   *  رفت (`ImportTable.csv`، بایت‌به‌بایت) تا مسیرِ تازهٔ PRD-20 هم از همان بخواند.
   */
  fun parse(text: String): List<List<String>> = ImportTable.csv(text)

  /** «۱٬۲۵۰٫۵» ⇐ 1250.5؛ خالی ⇐ 0؛ نادرست ⇐ NaN */
  internal fun number(text: String): Double {
    //  ⚠️ «٬» (هزارگان) پیش از `latinDigits` برداشته شود — آن تابع «٬» را «.» می‌کند
    //  و «۱٬۲۰۰» می‌شد ۱٫۲
    val t = ir.vil3ntec.tohid.latinDigits(text.trim().replace("٬", "").replace(",", "").replace(" ", ""))
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
    val rowOf = ArrayList<Int>()   //  شمارهٔ ردیفِ اکسلِ هر draft
    rows.drop(1).forEachIndexed { i, r ->
      val n = i + 2   // شمارهٔ ردیف در اکسل (سرستون ردیفِ ۱ است)
      if (r.all { it.isBlank() }) return@forEachIndexed
      val name = at(r, "name")
      val key = ir.vil3ntec.tohid.core.product.SimilarNames.exactKey(name)
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
      if (reason != null) {
        skipped += Skip(n, name, reason)
      } else {
        rowOf += n
        drafts += WarehouseEngine.ProductDraft(
          name = name,
          category = at(r, "category"),
          unit = at(r, "unit").ifEmpty { "عدد" },
          purchasePrice = purchase,
          salePrice = sale,
          minStock = min,
          barcode = barcode,
        )
      }
    }
    return Plan(drafts, skipped, missingName = false, similar = similarOf(d, drafts, rowOf))
  }

  /** نامِ مشابه با کالاهای دکان، و با ردیف‌های پیشینِ همین فایل (هر ردیف حداکثر یک هشدار) */
  private fun similarOf(d: ShopData, drafts: List<WarehouseEngine.ProductDraft>, rowOf: List<Int>): List<Similar> {
    if (drafts.isEmpty()) return emptyList()
    val shop = ir.vil3ntec.tohid.core.product.SimilarNames.Index(d.products.map { it.name })
    val file = ir.vil3ntec.tohid.core.product.SimilarNames.Index(drafts.map { it.name })
    return drafts.mapIndexedNotNull { i, dr ->
      shop.find(dr.name, limit = 1).firstOrNull()?.let { Similar(rowOf[i], dr.name, it.name, inFile = false) }
        ?: file.find(dr.name, except = i, limit = 3).firstOrNull { it.index < i }
          ?.let { Similar(rowOf[i], dr.name, it.name, inFile = true) }
    }
  }

  /* =====================================================================
   *  PRD-20 — مسیرِ تازه (پشتِ `ShopPolicy.productTools`، پیش‌فرض خاموش)
   *
   *  xlsx یا CSV ⇐ `ImportTable` ⇐ پیش‌نمایشِ `ProductImportPlan` (همه در core) ⇐ `WarehouseEngine.importProducts`.
   *  این‌جا فقط کارِ سکو: بازکردنِ ZIP ِ xlsx با سقف و تبدیلِ کالای دکان به زبانِ core.
   * ===================================================================== */

  private val ZIP = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
  private val OLE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte())

  /** همهٔ بایت‌های بازشده در یک xlsx (بخش‌های نگه‌داشته و ردشده) — بمبِ فشرده بیش از این کار نمی‌کشد */
  private const val MAX_INFLATE_TOTAL: Long = 96L * 1024 * 1024
  private const val MAX_ENTRIES = 4_000

  private fun ByteArray.startsWith(head: ByteArray) = size >= head.size && head.indices.all { this[it] == head[it] }

  /**
   *  فایلِ برداشته ⇒ جدول. نوع از **محتوا** (نه پسوند): ZIP ⇒ xlsx، OLE ⇒ «xls ِ قدیمی، ذخیره به xlsx»،
   *  وگرنه CSV با UTF-8 (BOM یا بی) یا UTF-16 (BOM)؛ UTF-8 ِ نادرست رد می‌شود، نه با «�» خوانده.
   */
  fun read(bytes: ByteArray): ImportTable.Read {
    if (bytes.size > ImportTable.MAX_FILE_BYTES) {
      return ImportTable.Read.Failed("فایل بزرگ‌تر از ${ImportTable.MAX_FILE_BYTES / (1024 * 1024)} مگابایت است — آن را چند تکه کنید")
    }
    if (bytes.isEmpty()) return ImportTable.Read.Failed("فایل خالی است")
    if (bytes.startsWith(OLE)) {
      return ImportTable.Read.Failed("فایلِ xls ِ قدیمی (یا اکسلِ رمزدار) خوانده نمی‌شود — در اکسل «ذخیره به xlsx» کنید")
    }
    if (bytes.startsWith(ZIP)) return readXlsx(bytes)
    val text = when {
      bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
      bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() ->
        String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
      else -> runCatching {
        Charsets.UTF_8.newDecoder()
          .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
          .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
          .decode(java.nio.ByteBuffer.wrap(bytes)).toString()
      }.getOrNull() ?: return ImportTable.Read.Failed(
        "فایل UTF-8 نیست — در اکسل «CSV UTF-8» ذخیره کنید یا خودِ فایلِ xlsx را بدهید"
      )
    }
    if (text.contains('\u0000')) return ImportTable.Read.Failed("این فایل جدول نیست — xlsx یا CSV بدهید")
    return ImportTable.readCsv(text)
  }

  /**
   *  xlsx: یک گذر روی ZIP؛ هر بخش با شمارشِ خودمان تا ته خوانده می‌شود (نه `closeEntry` ِ بی‌حساب)، پس
   *  کارِ بازکردن به `MAX_INFLATE_TOTAL` و هر بخشِ نگه‌داشته به `MAX_PART_BYTES` بسته است. برگه‌ها تا
   *  روشن شدنِ «برگهٔ اول» (workbook + rels) نگه داشته می‌شوند.
   */
  private fun readXlsx(bytes: ByteArray): ImportTable.Read {
    val tooBig = ImportTable.Read.Failed("فایلِ اکسل پس از باز شدن بیش از حد بزرگ است — آن را چند تکه کنید")
    var workbook: String? = null
    var rels: String? = null
    var shared: String? = null
    val sheets = LinkedHashMap<String, String>()
    var total = 0L
    var entries = 0
    try {
      ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        val buf = ByteArray(16 * 1024)
        while (true) {
          val entry = zip.nextEntry ?: break
          if (++entries > MAX_ENTRIES) return tooBig
          val name = entry.name.removePrefix("/")
          val wanted = sheets.size < 64 && name.startsWith("xl/worksheets/") && name.endsWith(".xml") &&
            (workbook == null || rels == null || name == ImportTable.sheetPath(workbook, rels))
          val keep = name == "xl/workbook.xml" || name == "xl/_rels/workbook.xml.rels" ||
            name == "xl/sharedStrings.xml" || wanted
          val out = if (keep) java.io.ByteArrayOutputStream() else null
          var part = 0L
          while (true) {
            val n = zip.read(buf)
            if (n < 0) break
            part += n
            total += n
            if (total > MAX_INFLATE_TOTAL || (out != null && part > ImportTable.MAX_PART_BYTES)) return tooBig
            out?.write(buf, 0, n)
          }
          if (out != null) {
            val text = out.toString(Charsets.UTF_8.name())
            when (name) {
              "xl/workbook.xml" -> workbook = text
              "xl/_rels/workbook.xml.rels" -> rels = text
              "xl/sharedStrings.xml" -> shared = text
              else -> sheets[name] = text
            }
          }
        }
      }
    } catch (e: java.util.zip.ZipException) {
      return ImportTable.Read.Failed("فایلِ اکسل خراب است و باز نمی‌شود")
    } catch (e: java.io.IOException) {
      return ImportTable.Read.Failed("فایلِ اکسل خراب است و باز نمی‌شود")
    }
    if (workbook == null) {
      return ImportTable.Read.Failed("این ZIP اکسل (xlsx) نیست — فایلِ ods یا دیگر را «ذخیره به xlsx» کنید")
    }
    val sheet = sheets[ImportTable.sheetPath(workbook, rels)]
      ?: return ImportTable.Read.Failed("برگهٔ اولِ فایلِ اکسل پیدا نشد")
    return ImportTable.readXlsx(sheet, shared)
  }

  /** کالای دکان به زبانِ core (پول به یک‌صدم، حداقلِ موجودی به هزارم — همان `Math.round` ِ مرزِ امروز) */
  fun existingOf(d: ShopData): List<ProductImportPlan.Existing> = d.products.map { p ->
    ProductImportPlan.Existing(
      id = p.id, name = p.name, category = p.category, unit = p.unit,
      purchaseMinor = Math.round(p.purchasePrice * 100), saleMinor = Math.round(p.salePrice * 100),
      minMilli = Math.round(p.minStock * 1000), barcodes = p.barcodes,
    )
  }

  fun planOf(d: ShopData, rows: List<List<String>>, update: Boolean): ProductImportPlan.Plan =
    ProductImportPlan.plan(rows, existingOf(d), update)

  /** نوعِ ردیفِ سابقهٔ «ورود از فایل» در `auditLog`؛ `refId` = `import:<اثرِ انگشت>` */
  const val AUDIT_TYPE = "product_import"
  fun refOf(fingerprint: String) = "import:$fingerprint"

  /** سابقهٔ واردسازی — تازه‌ترین اول (همگام با دستگاه‌های دیگرِ دکان، چون در دفتر است) */
  fun history(d: ShopData): List<AuditEntry> =
    d.auditLog.filter { it.type == AUDIT_TYPE }.sortedByDescending { it.createdAt }

  /** همین داده پیش‌تر وارد شده؟ (آخرین بار) */
  fun previous(d: ShopData, fingerprint: String): AuditEntry? =
    history(d).firstOrNull { it.refId == refOf(fingerprint) }

  /** فایلِ خوانده‌شده: نام برای سابقه و جدولِ خام برای پیش‌نمایش و ثبت */
  data class Picked(val name: String, val rows: List<List<String>>)

  /** یک تغییرِ پیش‌نمایش به زبانِ صفحه — «قیمت خرید 100 ⇐ 110» */
  fun describe(c: ProductImportPlan.Change): String = when (c) {
    is ProductImportPlan.Change.Text -> "${c.col.label} «${c.old}» ⇐ «${c.new}»"
    is ProductImportPlan.Change.Price -> "${c.col.label} ${ir.vil3ntec.tohid.money(c.oldMinor / 100.0)} ⇐ ${ir.vil3ntec.tohid.money(c.newMinor / 100.0)}"
    is ProductImportPlan.Change.MinStock -> "حداقل موجودی ${ir.vil3ntec.tohid.qty(c.oldMilli / 1000.0)} ⇐ ${ir.vil3ntec.tohid.qty(c.newMilli / 1000.0)}"
    is ProductImportPlan.Change.AddBarcodes -> "بارکدِ تازه ${c.codes.joinToString("، ")}"
  }

  /** «فایلِ ردیف‌های رد» به xlsx: همه متن (نه عدد، نه فرمول) تا بارکد و صفرِ اول سالم بماند و فرمولی اجرا نشود */
  fun errorWorkbook(plan: ProductImportPlan.Plan): ByteArray =
    Xlsx.book(listOf(Xlsx.Sheet("ردیف‌های رد", ProductImportPlan.errorReport(plan).map { r -> r.map { Xlsx.Cell.Text(it) } })))
}
