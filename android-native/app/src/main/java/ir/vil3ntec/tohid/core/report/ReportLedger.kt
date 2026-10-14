package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.floor

/**
 *  دفتر برای گزارش — همان شکلِ دفترِ وب (`tohid-shop-data-v1`، یعنی همان
 *  `ShopData`)، فقط با عددِ صحیح: پول به واحدِ کوچک (۱ افغانی = ۱۰۰)،
 *  مقدار به هزارم. تبدیل یک بار و مثلِ `license/report-core.js`
 *  (`Math.round`) انجام می‌شود تا هر دو عیناً یک عدد بدهند.
 *
 *  ورودی `JsonObject` است، نه `ShopData`: هسته به لایهٔ `data` وابسته
 *  نیست و فیلدهای تازه‌ای را هم می‌خواند که هنوز در مدل نیستند
 *  (`returns[].user`، `auditLog[].user`، `sales[].cancelledBy`، `channel`).
 *  برنامه: `ReportLedger.parse(json.encodeToJsonElement(ShopData.serializer(), d).jsonObject)`.
 */
class ReportLedger private constructor() {
  class Product(val id: String, val name: String, val category: String)
  class Sale(
    val id: String, val total: Long, val discount: Long, val finalTotal: Long, val method: String,
    val cancelled: Boolean, val seller: String, val cancelledBy: String, val channel: String,
    override val date: String, override val createdAt: Long,
  ) : Dated
  class Item(
    val id: String, val saleId: String, val productId: String, val name: String,
    val qty: Long, val unitCost: Long, val total: Long,
  )
  class Return(
    val id: String, val saleId: String, val saleItemId: String, val productId: String, val qty: Long,
    val amount: Long, val user: String, override val date: String, override val createdAt: Long,
  ) : Dated
  class Tx(val id: String, val debtorId: String, val give: Boolean, val amount: Long, override val date: String, override val createdAt: Long) : Dated
  class Purchase(val id: String, val supplierId: String, val total: Long, val paid: Long, override val date: String, override val createdAt: Long) : Dated
  class Payment(val id: String, val supplierId: String, val amount: Long, override val date: String, override val createdAt: Long) : Dated
  class Entry(
    val id: String, val productId: String, val qty: Long, val unitCost: Long, val adjustment: Boolean,
    override val date: String, override val createdAt: Long,
  ) : Dated
  class Audit(
    val id: String, val type: String, val refId: String, val user: String, val amount: Long,
    override val date: String, override val createdAt: Long,
  ) : Dated

  interface Dated { val date: String; val createdAt: Long }

  val products = HashMap<String, Product>()
  val sales = ArrayList<Sale>()
  val saleById = HashMap<String, Sale>()
  val items = ArrayList<Item>()
  val itemById = HashMap<String, Item>()
  val itemsBySale = HashMap<String, MutableList<Item>>()
  val returns = ArrayList<Return>()
  val debtors = HashMap<String, String>()
  val transactions = ArrayList<Tx>()
  val suppliers = HashMap<String, String>()
  val purchases = ArrayList<Purchase>()
  val supplierPayments = ArrayList<Payment>()
  val entries = ArrayList<Entry>()
  val audit = ArrayList<Audit>()

  companion object {
    fun parse(raw: JsonObject): ReportLedger {
      val l = ReportLedger()
      raw.list("products").forEach { p -> l.products[p.s("id")] = Product(p.s("id"), p.s("name"), p.s("category").trim()) }
      raw.list("sales").forEach { s ->
        val x = Sale(
          id = s.s("id"), total = money(s, "total"), discount = money(s, "discount"), finalTotal = money(s, "finalTotal"),
          method = s.s("paymentMethod"), cancelled = s.s("status") == "cancelled", seller = s.s("seller").trim(),
          cancelledBy = s.s("cancelledBy").trim(), channel = s.s("channel").ifEmpty { "pos" },
          date = s.s("date"), createdAt = ms(s, "createdAt"),
        )
        l.sales += x; l.saleById[x.id] = x
      }
      raw.list("saleItems").forEach { i ->
        val x = Item(
          id = i.s("id"), saleId = i.s("saleId"), productId = i.s("productId"), name = i.s("name"),
          qty = milli(i, "quantity"), unitCost = money(i, "purchasePrice"), total = money(i, "totalPrice"),
        )
        l.items += x; l.itemById[x.id] = x
        l.itemsBySale.getOrPut(x.saleId) { ArrayList() } += x
      }
      raw.list("returns").forEach { r ->
        l.returns += Return(
          id = r.s("id"), saleId = r.s("saleId"), saleItemId = r.s("saleItemId"), productId = r.s("productId"),
          qty = milli(r, "quantity"), amount = money(r, "amount"), user = r.s("user").trim(),
          date = r.s("date"), createdAt = ms(r, "createdAt"),
        )
      }
      raw.list("debtors").forEach { d -> l.debtors[d.s("id")] = d.s("name") }
      raw.list("transactions").forEach { t ->
        l.transactions += Tx(t.s("id"), t.s("debtorId"), t.s("type") == "give", money(t, "amount"), t.s("date"), ms(t, "createdAt"))
      }
      raw.list("suppliers").forEach { s -> l.suppliers[s.s("id")] = s.s("name") }
      raw.list("purchases").forEach { p ->
        l.purchases += Purchase(p.s("id"), p.s("supplierId"), money(p, "totalAmount"), money(p, "paidAmount"), p.s("date"), ms(p, "createdAt"))
      }
      raw.list("supplierPayments").forEach { p ->
        l.supplierPayments += Payment(p.s("id"), p.s("supplierId"), money(p, "amount"), p.s("date"), ms(p, "createdAt"))
      }
      raw.list("warehouseEntries").forEach { e ->
        l.entries += Entry(
          e.s("id"), e.s("productId"), milli(e, "units"), money(e, "price"),
          (e["isAdjustment"] as? JsonPrimitive)?.let { !it.isString && it.content == "true" } == true,
          e.s("date"), ms(e, "createdAt"),
        )
      }
      raw.list("auditLog").forEach { a ->
        l.audit += Audit(a.s("id"), a.s("type"), a.s("refId"), a.s("user").trim(), money(a, "amount"), a.s("date"), ms(a, "createdAt"))
      }
      return l
    }

    private fun JsonObject.list(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    /** همان `str()`ِ JS: نبود یا null ⇒ "" */
    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""

    /** همان `num()`ِ JS: عدد، یا رشتهٔ عددی؛ هر چیزِ دیگر ۰ */
    private fun num(o: JsonObject, k: String): Double {
      val p = o[k] as? JsonPrimitive ?: return 0.0
      if (p is JsonNull) return 0.0
      val d = if (p.isString) (if (p.content.isEmpty()) 0.0 else p.content.toDoubleOrNull()) else p.content.toDoubleOrNull()
      return if (d == null || d.isNaN() || d.isInfinite()) 0.0 else d
    }

    private fun money(o: JsonObject, k: String): Long = Math.round(num(o, k) * 100)
    private fun milli(o: JsonObject, k: String): Long = Math.round(num(o, k) * 1000)
    private fun ms(o: JsonObject, k: String): Long = floor(num(o, k)).toLong()
  }
}

/**
 *  ساعتِ فروشگاه: روزِ کاری و ساعتِ دیواری از یک لحظه (UTC ms).
 *  روزِ کاری که از نیمه‌شب رد می‌شود مالِ روزِ شروعش است (بخشِ ۳.۲ ـ زمان).
 *  وقتی `core/calendar/BusinessDay` (PR #224) روی main بنشیند، همان پشتِ این رابط می‌نشیند.
 */
interface ReportClock {
  /** `yyyy-MM-dd`ِ روزِ کاری */
  fun date(ms: Long): String

  /** ساعتِ دیواری ۰ تا ۲۳ */
  fun hour(ms: Long): Int

  companion object {
    /** اختلافِ ثابت از UTC (افغانستان +۲۷۰، بی ساعتِ تابستانی) و شروعِ روزِ کاری (دقیقه از نیمه‌شب) */
    fun fixed(offsetMinutes: Int, dayStartMinute: Int): ReportClock = object : ReportClock {
      private val off = offsetMinutes * 60_000L
      private val start = dayStartMinute * 60_000L
      override fun date(ms: Long) = ReportDates.isoOf(Math.floorDiv(ms + off - start, ReportDates.DAY_MS))
      override fun hour(ms: Long) = (Math.floorMod(ms + off, ReportDates.DAY_MS) / 3_600_000L).toInt()
    }
  }
}

/** تاریخِ میلادیِ `yyyy-MM-dd` ⇄ شمارهٔ روز از ۱۹۷۰ (Hinnant) — همان `epochDay`/`isoOf`ِ JS */
object ReportDates {
  const val DAY_MS = 86_400_000L
  private val ISO = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

  fun epochDay(iso: String): Long? {
    val m = ISO.find(iso) ?: return null
    var y = m.groupValues[1].toLong()
    val mo = m.groupValues[2].toInt()
    val d = m.groupValues[3].toInt()
    if (mo < 1 || mo > 12 || d < 1 || d > 31) return null
    if (mo <= 2) y -= 1
    val era = Math.floorDiv(y, 400L)
    val yoe = y - era * 400
    val doy = (153 * (mo + if (mo > 2) -3 else 9) + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
  }

  fun isoOf(n: Long): String {
    val z = n + 719468
    val era = Math.floorDiv(z, 146097L)
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = if (mp < 10) mp + 3 else mp - 9
    val y = yoe + era * 400 + if (m <= 2) 1 else 0
    return y.toString().padStart(4, '0') + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')
  }

  fun addDays(iso: String, k: Int): String = epochDay(iso)?.let { isoOf(it + k) } ?: ""
}
