package ir.vil3ntec.tohid.data

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.OutputStream

/**
 *  خروجیِ کاملِ داده برای مالک — JSON و Excel (OPS-08).
 *
 *  ── چه چیزی را می‌بندد ──────────────────────────────────────────────
 *  دادهٔ دکان مالِ صاحبِ دکان است. تا امروز تنها راهِ بیرون بردنش
 *  «پشتیبان» بود: فایلی برای برگرداندن به همین برنامه، نه برای خواندن.
 *  گزارش‌های اکسل هم هر کدام فقط یک برش بودند (فروشِ این بازه، کالاهای
 *  کم…)، نه همهٔ داده.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── شکلِ JSON (قالبِ `tohid-owner-export`، نسخهٔ ۱) ─────────────────
 *      format, formatVersion, exportedAt, source{app, appVersion}, counts
 *      …همهٔ مجموعه‌های دفتر با همان کلیدهای `shop-data.json`…
 *      settings{storeName}
 *      archive{stockMovements, priceHistory, auditLog}   ← فقط اگر بایگانی دارد
 *
 *  سرآیند جلوی همه است تا هر خواننده‌ای پیش از داده، قالب و نسخه را
 *  ببیند. کلیدهای داده همان کلیدهای پشتیبان‌اند، پس همین فایل در «بازیابی
 *  از فایل» هم باز می‌شود (کلیدهای اضافه نادیده گرفته می‌شوند).
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ── شکلِ اکسل ──────────────────────────────────────────────────────
 *  برگهٔ اول «اطلاعات خروجی»، بعد یک برگه برای هر مجموعه با سرستونِ
 *  فارسی، بعد برگه‌های بایگانی. ستون‌ها از خودِ داده می‌آیند: فیلدی که
 *  فردا اضافه شود بی‌صدا نمی‌افتد، فقط تا وقتی نامِ فارسی بگیرد با نامِ
 *  خودش می‌آید. رشته رشته می‌ماند (تلفنِ «0700» صفرش را از دست نمی‌دهد).
 *
 *  نسخهٔ وب همین را با همین نام‌ها در `license/owner-export.js` دارد؛
 *  یکی بودنِ دو فهرستِ نام‌ها را `server/test/owner-export.test.js` می‌سنجد.
 *  ──────────────────────────────────────────────────────────────────
 *
 *  ⛔ **این‌جا فقط خوانده می‌شود.** مُهرِ پشتیبان نمی‌خورد — خروجی گرفتن
 *  پشتیبان گرفتن نیست. تنها نوشتن، کارِ صفحهٔ تنظیمات است: یک ردیفِ
 *  `data_export` در سابقهٔ عملیات (مشخصات ۲۱.۲: خروجی فقط برای مالک/نقشِ
 *  مجاز و با ثبتِ audit؛ شاگرد تنظیمات را اصلاً باز نمی‌کند).
 *
 *  قالبِ امن: هر رشته `inlineStr` است و هیچ خانه‌ای `<f>` ندارد، پس متنی
 *  مثلِ «=HYPERLINK(…)» در اکسل فرمول نمی‌شود.
 */
object OwnerExport {

  const val FORMAT = "tohid-owner-export"
  const val FORMAT_VERSION = 1

  /** سقفِ ردیف‌های یک برگهٔ اکسل (بی سرستون)؛ بیشتر ⇐ برگهٔ دنباله */
  const val MAX_ROWS = 1_048_575

  data class Meta(
    val exportedAt: Long,
    /** android | desktop | web */
    val app: String,
    val appVersion: String,
    val storeName: String,
  )

  data class Parsed(val formatVersion: Int, val data: ShopData, val archive: LedgerArchive.Archive)

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private val pretty = Json(json) { prettyPrint = true }

  /** مجموعه ⇐ نامِ برگه؛ ترتیبِ برگه‌ها همین است */
  val SHEETS: Map<String, String> = linkedMapOf(
    "products" to "کالاها",
    "productCategories" to "دسته‌های کالا",
    "productUnits" to "واحدهای کالا",
    "warehouseEntries" to "ورودی‌های انبار",
    "sales" to "فاکتورهای فروش",
    "saleItems" to "اقلام فاکتورها",
    "returns" to "برگشتی‌های فروش",
    "debtors" to "قرض‌داران",
    "transactions" to "حساب قرض‌داران",
    "expenses" to "مصارف",
    "expenseCategories" to "دسته‌های مصارف",
    "suppliers" to "تأمین‌کنندگان",
    "purchases" to "خریدها",
    "supplierPayments" to "پرداخت به تأمین‌کنندگان",
    "stockMovements" to "حرکات انبار",
    "priceHistory" to "سابقهٔ قیمت‌ها",
    "auditLog" to "سابقهٔ عملیات",
  )

  /** فیلد ⇐ سرستونِ فارسی */
  val HEADERS: Map<String, String> = mapOf(
    "id" to "شناسه",
    "name" to "نام",
    "phone" to "تلفن",
    "dueDate" to "سررسید",
    "creditLimit" to "سقف اعتبار",
    "notes" to "یادداشت",
    "createdAt" to "زمان ثبت (UTC)",
    "date" to "تاریخ",
    "amount" to "مبلغ",
    "type" to "نوع",
    "title" to "عنوان",
    "category" to "دسته",
    "subcategory" to "زیردسته",
    "unit" to "واحد",
    "debtorId" to "شناسهٔ قرض‌دار",
    "productId" to "شناسهٔ کالا",
    "supplierId" to "شناسهٔ تأمین‌کننده",
    "saleId" to "شناسهٔ فاکتور",
    "saleItemId" to "شناسهٔ قلم فاکتور",
    "purchaseId" to "شناسهٔ خرید",
    "warehouseEntryId" to "شناسهٔ ورودی انبار",
    "refId" to "شناسهٔ مرجع",
    "purchasePrice" to "قیمت خرید",
    "salePrice" to "قیمت فروش",
    "wholesalePrice" to "قیمت عمده",
    "minStock" to "حداقل موجودی",
    "barcodes" to "بارکدها",
    "photo" to "عکس دارد",
    "active" to "فعال",
    "saleUnit" to "واحد فروش",
    "unitFactor" to "ضریب واحد",
    "cartons" to "تعداد کارتن",
    "perCarton" to "تعداد در هر کارتن",
    "units" to "تعداد واحد",
    "price" to "قیمت",
    "isAdjustment" to "اصلاح موجودی",
    "expiryDate" to "تاریخ انقضا",
    "batch" to "شمارهٔ بچ",
    "productionDate" to "تاریخ تولید",
    "invoiceNumber" to "شمارهٔ فاکتور",
    "series" to "سری سند",
    "channel" to "کانال فروش",
    "total" to "جمع",
    "discountType" to "نوع تخفیف",
    "discountValue" to "مقدار تخفیف",
    "discount" to "تخفیف",
    "finalTotal" to "مبلغ نهایی",
    "paymentMethod" to "روش پرداخت",
    "paidAmount" to "پرداخت‌شده",
    "remaining" to "باقی‌مانده",
    "status" to "وضعیت",
    "debtGiven" to "قرض داده‌شده",
    "debtSettled" to "قرض تسویه‌شده",
    "syncStatus" to "وضعیت همگام‌سازی",
    "cancelReason" to "دلیل لغو",
    "seller" to "فروشنده",
    "quantity" to "تعداد",
    "unitPrice" to "قیمت واحد",
    "totalPrice" to "جمع قلم",
    "returnedQty" to "تعداد برگشتی",
    "reason" to "دلیل",
    "address" to "نشانی",
    "totalAmount" to "مبلغ کل",
    "debt" to "بدهی",
    "invoiceNo" to "شمارهٔ فاکتور خرید",
    "qty" to "مقدار",
    "oldPrice" to "قیمت قبلی",
    "newPrice" to "قیمت تازه",
    "nextInvoiceNo" to "شمارهٔ فاکتور بعدی",
    //  موتورِ تازهٔ موجودی و بها (افزودنی، فقط وقتی تنظیمِ دکان روشن است)
    "landedCost" to "هزینهٔ جانبی",
    "locationId" to "محل",
    "toLocationId" to "محلِ مقصد",
    "userId" to "کاربر",
    "adjKind" to "نوعِ اصلاح",
    "lotId" to "سری",
  )

  /** کلیدهای سرآیند — داده نیستند و برگه نمی‌شوند */
  private val HEAD = setOf("format", "formatVersion", "exportedAt", "source", "counts", "settings", "archive")

  const val INFO_SHEET = "اطلاعات خروجی"
  const val ARCHIVE_PREFIX = "بایگانی — "

  fun tree(d: ShopData, meta: Meta, archive: LedgerArchive.Archive = LedgerArchive.Archive()): JsonObject {
    val data = json.encodeToJsonElement(ShopData.serializer(), d).jsonObject
    val arch = json.encodeToJsonElement(LedgerArchive.Archive.serializer(), archive).jsonObject
    return buildJsonObject {
      put("format", FORMAT)
      put("formatVersion", FORMAT_VERSION)
      put("exportedAt", iso(meta.exportedAt))
      putJsonObject("source") {
        put("app", meta.app)
        put("appVersion", meta.appVersion)
      }
      putJsonObject("counts") { data.forEach { (k, v) -> if (v is JsonArray) put(k, v.size) } }
      data.forEach { (k, v) -> put(k, v) }
      putJsonObject("settings") { put("storeName", meta.storeName) }
      if (arch.values.any { it is JsonArray && it.isNotEmpty() }) put("archive", arch)
    }
  }

  fun json(d: ShopData, meta: Meta, archive: LedgerArchive.Archive = LedgerArchive.Archive()): String =
    pretty.encodeToString(JsonObject.serializer(), tree(d, meta, archive))

  /** همان JSON، مستقیم روی جریان — دفترِ بزرگ یک بارِ دیگر رشته نمی‌شود */
  @OptIn(ExperimentalSerializationApi::class)
  fun writeJson(d: ShopData, meta: Meta, archive: LedgerArchive.Archive, out: OutputStream) =
    pretty.encodeToStream(JsonObject.serializer(), tree(d, meta, archive), out)

  /**
   *  خواندنِ همین قالب. نسخهٔ تازه‌تر رد می‌شود (نه نیمه‌خوانده)؛ فایلِ
   *  دیگری که سرآیندِ این قالب را ندارد هم.
   */
  fun parse(raw: String): Parsed {
    val tree = runCatching { json.parseToJsonElement(raw) }.getOrNull() as? JsonObject
      ?: error("فایلِ خروجی معتبر نیست")
    if ((tree["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) error("این فایل، خروجیِ کاملِ فروشگاه من نیست")
    val v = (tree["formatVersion"] as? JsonPrimitive)?.intOrNull ?: error("نسخهٔ قالب در فایل نیست")
    if (v < 1 || v > FORMAT_VERSION) error("این خروجی با قالبِ $v ساخته شده و این نسخهٔ برنامه آن را نمی‌شناسد")
    val data = json.decodeFromJsonElement(ShopData.serializer(), tree)
    val archive = (tree["archive"] as? JsonObject)
      ?.let { json.decodeFromJsonElement(LedgerArchive.Archive.serializer(), it) }
      ?: LedgerArchive.Archive()
    return Parsed(v, data, archive)
  }

  fun xlsx(d: ShopData, meta: Meta, archive: LedgerArchive.Archive = LedgerArchive.Archive()): ByteArray =
    Xlsx.book(sheets(tree(d, meta, archive)))

  fun writeXlsx(d: ShopData, meta: Meta, archive: LedgerArchive.Archive, out: OutputStream) =
    Xlsx.book(sheets(tree(d, meta, archive)), out)

  /** برگه‌ها از روی درختِ JSON — همان الگوریتمِ نسخهٔ وب */
  fun sheets(tree: JsonObject): List<Xlsx.Sheet> {
    val tables = ArrayList<Pair<String, List<List<Xlsx.Cell?>>>>()
    val keys = SHEETS.keys.filter { tree[it] is JsonArray } +
      tree.keys.filter { it !in SHEETS && it !in HEAD && tree[it] is JsonArray }
    for (k in keys) tables += split(SHEETS[k] ?: k, table(tree[k] as JsonArray))
    (tree["archive"] as? JsonObject)?.forEach { (k, v) ->
      if (v is JsonArray) tables += split(ARCHIVE_PREFIX + (SHEETS[k] ?: k), table(v))
    }
    return listOf(Xlsx.Sheet(INFO_SHEET, info(tree, tables))) + tables.map { Xlsx.Sheet(it.first, it.second) }
  }

  private fun info(tree: JsonObject, tables: List<Pair<String, List<List<Xlsx.Cell?>>>>): List<List<Xlsx.Cell?>> {
    fun t(s: String) = Xlsx.Cell.Text(s)
    fun str(e: JsonElement?) = (e as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""
    val source = tree["source"] as? JsonObject
    val settings = tree["settings"] as? JsonObject
    val rows = ArrayList<List<Xlsx.Cell?>>()
    rows += listOf(t("عنوان"), t("مقدار"))
    rows += listOf(t("قالب"), t(str(tree["format"])))
    rows += listOf(t("نسخهٔ قالب"), cell("formatVersion", tree["formatVersion"]))
    rows += listOf(t("زمان خروجی (UTC)"), t(str(tree["exportedAt"])))
    rows += listOf(t("برنامه"), t(str(source?.get("app"))))
    rows += listOf(t("نسخهٔ برنامه"), t(str(source?.get("appVersion"))))
    rows += listOf(t("نام فروشگاه"), t(str(settings?.get("storeName"))))
    tree.forEach { (k, v) -> if (k !in HEAD && v !is JsonArray) rows += listOf(t(HEADERS[k] ?: k), cell(k, v)) }
    settings?.forEach { (k, v) -> if (k != "storeName") rows += listOf(t("تنظیمات — $k"), cell(k, v)) }
    for ((name, table) in tables) rows += listOf(t("ردیف‌های «$name»"), Xlsx.Cell.Num((table.size - 1).toDouble()))
    rows += listOf(t("عکس‌ها"), t("در این خروجی نیستند؛ «پشتیبان کامل» آن‌ها را دارد"))
    return rows
  }

  private fun table(arr: JsonArray): List<List<Xlsx.Cell?>> {
    if (arr.isEmpty()) return listOf(listOf(Xlsx.Cell.Text("هیچ ردیفی ثبت نشده")))
    if (!arr.all { it is JsonObject }) {
      return listOf(listOf<Xlsx.Cell?>(Xlsx.Cell.Text("مقدار"))) + arr.map { listOf(cell("", it)) }
    }
    val cols = LinkedHashSet<String>()
    arr.forEach { cols.addAll((it as JsonObject).keys) }
    val head = cols.map<String, Xlsx.Cell?> { Xlsx.Cell.Text(HEADERS[it] ?: it) }
    return listOf(head) + arr.map { o -> cols.map { k -> cell(k, (o as JsonObject)[k]) } }
  }

  /** برگهٔ بیش از سقفِ اکسل ⇐ چند برگه، هر کدام با همان سرستون */
  private fun split(name: String, rows: List<List<Xlsx.Cell?>>): List<Pair<String, List<List<Xlsx.Cell?>>>> {
    if (rows.size - 1 <= MAX_ROWS) return listOf(name to rows)
    return rows.drop(1).chunked(MAX_ROWS).map { name to (listOf(rows[0]) + it) }
  }

  private fun cell(key: String, e: JsonElement?): Xlsx.Cell? = when (e) {
    null, is JsonNull -> null
    is JsonPrimitive -> when {
      e.isString -> Xlsx.Cell.Text(e.content)
      e.booleanOrNull != null -> Xlsx.Cell.Bool(e.booleanOrNull!!)
      else -> {
        val n = e.doubleOrNull
        val l = e.longOrNull
        when {
          n == null -> Xlsx.Cell.Text(e.content)
          //  زمانِ میلی‌ثانیه‌ای (`createdAt` و…) ⇐ تاریخ و ساعتِ خوانا
          key.endsWith("At") && l != null && l > 100_000_000_000L -> Xlsx.Cell.Time(l)
          else -> Xlsx.Cell.Num(n)
        }
      }
    }
    is JsonArray ->
      if (e.all { it is JsonPrimitive && it !is JsonNull }) Xlsx.Cell.Text(e.joinToString("، ") { (it as JsonPrimitive).content })
      else Xlsx.Cell.Text(e.toString())
    is JsonObject -> Xlsx.Cell.Text(e.toString())
  }

  private fun iso(ms: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
      timeZone = java.util.TimeZone.getTimeZone("UTC")
    }.format(java.util.Date(ms))
}
