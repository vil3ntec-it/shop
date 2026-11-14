package ir.vil3ntec.tohid.core.report

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ir.vil3ntec.tohid.core.calendar.BusinessDay
import ir.vil3ntec.tohid.core.calendar.CivilDays
import ir.vil3ntec.tohid.core.text.JsText
import kotlin.math.floor

/**
 *  دفتر برای گزارش — همان شکلِ دفترِ وب (`tohid-shop-data-v1`، یعنی همان
 *  `ShopData`)، فقط با عددِ صحیح: پول به واحدِ کوچک (۱ افغانی = ۱۰۰)،
 *  مقدار به هزارم. تبدیل یک بار و مثلِ `license/report-core.js`
 *  (`Math.round`) انجام می‌شود تا هر دو عیناً یک عدد بدهند.
 *
 *  ورودی `JsonObject` است، نه `ShopData`: هسته به لایهٔ `data` وابسته
 *  نیست و فیلدهای تازه‌ای را هم می‌خواند که هنوز در مدل نیستند
 *  (`returns[].by|user`، `auditLog[].by|user`، `sales[].cancelledBy`، `channel`).
 *  برنامه: `ReportLedger.parse(json.encodeToJsonElement(ShopData.serializer(), d).jsonObject)`.
 */
class ReportLedger private constructor() {
  class Product(val id: String, val name: String, val category: String)
  class Sale(
    val id: String, val total: Long, val discount: Long, val finalTotal: Long, val method: String,
    val cancelled: Boolean, val seller: String, val cancelledBy: String, val approvedBy: String, val channel: String,
    override val date: String, override val createdAt: Long,
  ) : Dated
  class Item(
    val id: String, val saleId: String, val productId: String, val name: String,
    val qty: Long, val unitCost: Long, val total: Long,
    /** POS-23 — فروشندهٔ ردیف (`saleItems[].seller`، پیراسته)؛ خالی ⇒ فروشندهٔ فاکتور. فقط با `lineSeller` ِ هسته دیده می‌شود */
    val seller: String = "",
  )
  class Return(
    val id: String, val saleId: String, val saleItemId: String, val productId: String, val qty: Long,
    val amount: Long, val user: String, val approvedBy: String, override val date: String, override val createdAt: Long,
  ) : Dated
  class Tx(val id: String, val debtorId: String, val give: Boolean, val amount: Long, override val date: String, override val createdAt: Long) : Dated
  class Purchase(val id: String, val supplierId: String, val total: Long, val paid: Long, override val date: String, override val createdAt: Long) : Dated
  class Payment(val id: String, val supplierId: String, val amount: Long, override val date: String, override val createdAt: Long) : Dated
  /** `value`: ارزشِ دقیقِ ورود (`costMinor` ِ ردیف) فقط برای خرید (PRD-07) و ردیفِ تولید (INV-21)؛ `null` = مقدار × `unitCost` ِ پیشین */
  class Entry(
    val id: String, val productId: String, val qty: Long, val unitCost: Long, val adjustment: Boolean,
    override val date: String, override val createdAt: Long,
    /** هزینهٔ جانبیِ کلِ ردیف (PUR-06)، واحدِ کوچک، ≥ ۰ — همان `landedOf` ِ JS */
    val landed: Long = 0L,
    /** ارزشِ دقیقِ ورود (`costMinor`) فقط جایی که `StockLedger` می‌خواند: خرید (PRD-07) و اصلاحِ `adjKind` = repack (INV-21، سندِ `production`)؛ نبود ⇒ null = مقدار × بها */
    val value: Long? = null,
  ) : Dated
  class Audit(
    val id: String, val type: String, val refId: String, val user: String, val approvedBy: String, val amount: Long,
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
      raw.list("products").forEach { p -> l.products[p.s("id")] = Product(p.s("id"), p.s("name"), JsText.trim(p.s("category"))) }
      raw.list("sales").forEach { s ->
        val x = Sale(
          id = s.s("id"), total = money(s, "total"), discount = money(s, "discount"), finalTotal = money(s, "finalTotal"),
          method = s.s("paymentMethod"), cancelled = s.s("status") == "cancelled", seller = JsText.trim(s.s("seller")),
          cancelledBy = JsText.trim(s.s("cancelledBy")), approvedBy = JsText.trim(s.s("approvedBy")).ifEmpty { slipBy(s["approval"]) }, channel = s.s("channel").ifEmpty { "pos" },
          date = s.s("date"), createdAt = ms(s, "createdAt"),
        )
        l.sales += x; l.saleById[x.id] = x
      }
      raw.list("saleItems").forEach { i ->
        val x = Item(
          id = i.s("id"), saleId = i.s("saleId"), productId = i.s("productId"), name = i.s("name"),
          qty = milli(i, "quantity"), unitCost = money(i, "purchasePrice"), total = money(i, "totalPrice"),
          seller = i.s("seller").trim(),
        )
        l.items += x; l.itemById[x.id] = x
        l.itemsBySale.getOrPut(x.saleId) { ArrayList() } += x
      }
      raw.list("returns").forEach { r ->
        l.returns += Return(
          id = r.s("id"), saleId = r.s("saleId"), saleItemId = r.s("saleItemId"), productId = r.s("productId"),
          //  `by` همان فیلدِ `SaleReturn` ِ برنامه (جریانِ تازهٔ برگشت و «سابقهٔ ضدتقلب»)؛ `user` شکلِ دفترِ وب
          qty = milli(r, "quantity"), amount = money(r, "amount"), user = JsText.trim(r.s("by").ifEmpty { r.s("user") }), approvedBy = JsText.trim(r.s("approvedBy")),
          date = r.s("date"), createdAt = ms(r, "createdAt"),
        )
      }
      raw.list("debtors").forEach { d -> l.debtors[d.s("id")] = d.s("name") }
      //  S89: جفتِ «اصل + سندِ معکوس» (`core/debt/DebtReversal`، هم‌زادِ `reversalNeutral` ِ JS) بیرون — سنِ بدهی همان حذفِ امروز
      val txs = raw.list("transactions")
      val neutral = ir.vil3ntec.tohid.core.debt.DebtReversal.neutral(
        txs.map { t -> ir.vil3ntec.tohid.core.debt.DebtReversal.Row(t.s("id"), t.s("type"), money(t, "amount"), t.s("kind").ifEmpty { null }, t.s("refId").ifEmpty { null }) },
      )
      txs.forEach { t ->
        if (t.s("id") in neutral) return@forEach
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
        val adjustment = (e["isAdjustment"] as? JsonPrimitive)?.let { !it.isString && it.content == "true" } == true
        l.entries += Entry(
          e.s("id"), e.s("productId"), milli(e, "units"), money(e, "price"),
          adjustment,
          e.s("date"), ms(e, "createdAt"), landedOf(e),
          exactValue((e["costMinor"] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull(), milli(e, "units"), money(e, "price"))
            ?.takeIf { !adjustment || e.s("adjKind") == "repack" },
        )
      }
      raw.list("auditLog").forEach { a ->
        //  `by`/`at`/`approvedBy` همان شکلِ رویدادِ صندوقِ pay-core (#237) است؛ `user`/`createdAt` شکلِ سابقهٔ امروز
        l.audit += Audit(
          a.s("id"), a.s("type"), a.s("refId"), JsText.trim(a.s("by").ifEmpty { a.s("user") }), JsText.trim(a.s("approvedBy")),
          money(a, "amount"), a.s("date"), ms(a, "createdAt").takeIf { it != 0L } ?: ms(a, "at"),
        )
      }
      return l
    }

    /**
     *  PRD-07 — `costMinor` ِ ردیفِ ورود فقط وقتی «ارزشِ دقیق» است که با مقدار × بهای **همان** ردیف جور باشد: بهای واحد
     *  گردِ همان جمع است، پس فرق حداکثر نیم‌صدم برای هر واحد (+۱ برای گردکردنِ عدد). نسخهٔ پیش از دستهٔ ۳۱ این فیلد را
     *  نمی‌شناسد: ویرایشِ بهای ورود روی آن فقط `price` را می‌فرستد و سرور `costMinor` ِ کهنه را نگه می‌دارد (A00) ⇒ ناجور
     *  ⇒ `null` = همان مقدار × بها ِ پیشین (DATA-SAFETY §۷). همتای `exactValue` ِ `license/report-core.js` و `StockLedger`.
     */
    fun exactValue(costMinor: Long?, qtyMilli: Long, unitCostMinor: Long): Long? {
      val c = costMinor ?: return null
      val plain = qtyMilli.toDouble() * unitCostMinor.toDouble() / 1000.0
      return c.takeIf { kotlin.math.abs(c - plain) <= kotlin.math.abs(qtyMilli.toDouble()) / 2000.0 + 1.0 }
    }

    private fun JsonObject.list(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    /** همان `str()`ِ JS: نبود یا null ⇒ "" */
    /**
     *  S08: تأییدکنندهٔ تخفیفِ بیش از سقف روی فاکتورِ برنامه فقط در برگهٔ امضاشده (`approval.by`) است، نه در `approvedBy`؛
     *  برگهٔ «درخواستِ از راهِ دور» (`{v, request}`) نامِ تأییدکننده ندارد ⇒ "" (همان `slipBy` ِ `license/report-core.js`)
     */
    private fun slipBy(a: kotlinx.serialization.json.JsonElement?): String = JsText.trim((a as? JsonObject)?.s("by").orEmpty())

    private fun JsonObject.s(k: String): String = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content ?: ""

    /** همان `num()`ِ JS: عدد، یا رشتهٔ عددی؛ هر چیزِ دیگر ۰ */
    private fun num(o: JsonObject, k: String): Double {
      val p = o[k] as? JsonPrimitive ?: return 0.0
      if (p is JsonNull) return 0.0
      //  رشته با همان `Number()` ِ JS (0x1F ⇒ ۳۱، 5d ⇒ NaN، فاصلهٔ نشکن بریده)، نه `toDoubleOrNull`
      val d = if (p.isString) JsText.toNumber(p.content) else p.content.toDoubleOrNull()
      return if (d == null || d.isNaN() || d.isInfinite()) 0.0 else d
    }

    /** `landedCostMinor` ِ صحیح و نامنفی (≤ ۲^۵۳−۱)، وگرنه `landedCost` ِ افغانی؛ منفی یا نادرست ⇒ ۰ */
    private fun landedOf(e: JsonObject): Long {
      val p = e["landedCostMinor"] as? JsonPrimitive
      val d = if (p != null && p !is JsonNull && !p.isString) p.content.toDoubleOrNull() else null
      if (d != null && d >= 0.0 && d <= 9_007_199_254_740_991.0 && floor(d) == d) return d.toLong()
      val x = money(e, "landedCost")
      return if (x > 0) x else 0L
    }

    private fun money(o: JsonObject, k: String): Long = Math.round(num(o, k) * 100)
    private fun milli(o: JsonObject, k: String): Long = Math.round(num(o, k) * 1000)
    private fun ms(o: JsonObject, k: String): Long = floor(num(o, k)).toLong()
  }
}

/**
 *  ساعتِ فروشگاه: روزِ کاری و ساعتِ دیواری از یک لحظه (UTC ms).
 *  روزِ کاری که از نیمه‌شب رد می‌شود مالِ روزِ شروعش است (بخشِ ۳.۲ ـ زمان) —
 *  خودِ حساب از `core/calendar/BusinessDay` است، نه نسخهٔ دوم.
 */
interface ReportClock {
  /** `yyyy-MM-dd`ِ روزِ کاری */
  fun date(ms: Long): String

  /** ساعتِ دیواری ۰ تا ۲۳ */
  fun hour(ms: Long): Int

  companion object {
    /** اختلافِ ثابت از UTC (کابل +۲۷۰، بی ساعتِ تابستانی) و شروعِ روزِ کاری (دقیقه از نیمه‌شب) */
    fun fixed(offsetMinutes: Int, dayStartMinute: Int): ReportClock = object : ReportClock {
      override fun date(ms: Long) = BusinessDay.businessDateAt(ms, offsetMinutes, dayStartMinute)
      override fun hour(ms: Long) = (Math.floorMod(ms + offsetMinutes * 60_000L, ReportDates.DAY_MS) / 3_600_000L).toInt()
    }
  }
}

/** شمارهٔ روز از ۱۹۷۰ ⇄ `yyyy-MM-dd` — روی `CivilDays`؛ تاریخِ نامعتبر (مثلاً ۰۲-۳۰) null */
object ReportDates {
  const val DAY_MS = 86_400_000L
  private const val JDN_1970 = 2440588

  fun epochDay(iso: String): Long? = CivilDays.jdnOfIso(iso)?.let { (it - JDN_1970).toLong() }

  fun addDays(iso: String, k: Int): String = CivilDays.addDays(iso, k) ?: ""
}
