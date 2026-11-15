package ir.vil3ntec.tohid.ui.v3.money

import ir.vil3ntec.tohid.Jalali
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.ShopData

/**
 *  «رسیدها» (`receipts?tab=debtors|expenses&y=&m=`) — منطقِ خالص، زیرِ آزمون: پولی که از قرض‌داران
 *  گرفته شده و خرجی که شده، هر دو به تفکیکِ ماه و سالِ خورشیدی. سال‌ها از خودِ داده ساخته می‌شوند
 *  (سالی که هیچ ردیفی ندارد نمی‌آید).
 */
internal object ReceiptsModel {

  enum class Tab(val key: String) {
    DEBTORS("debtors"),
    EXPENSES("expenses");

    companion object {
      /** پیوندِ قدیمِ «receipts» هم همان تبِ قرض‌داران است */
      fun parse(key: String?): Tab = entries.firstOrNull { it.key == key } ?: DEBTORS
    }
  }

  data class Row(
    val id: String,
    val title: String,
    val note: String,
    val amount: Double,
    val date: String,
    /** قرض‌دارِ رسید (تبِ قرض‌داران)؛ مصرف ⇒ null */
    val debtorId: String?,
    /** قرض‌دار هنوز هست؟ (ردیفِ «قرض‌دار حذف‌شده» پیوند ندارد) */
    val linked: Boolean,
    val year: Int,
    val month: Int,
  )

  /** همهٔ ردیف‌های یک تب، تازه‌ترین اول; تاریخِ ناخوانا نمی‌آید (مثلِ امروز) */
  fun rows(d: ShopData, tab: Tab): List<Row> = when (tab) {
    //  S89: رسیدِ معکوس‌شده و خودِ سندِ معکوس در فهرستِ رسیدها نیستند (سابقه در حسابِ قرض‌دار)
    //  PERF-SCREEN: مشتری از یک نقشه (نخستینِ هر شناسه = همان `debtors.find`)، نه گشتنِ همهٔ مشتری‌ها برای هر رسید
    Tab.DEBTORS -> firstById(d).let { byId ->
      ir.vil3ntec.tohid.data.DebtReverse.effective(d.transactions).filter { it.type == "receive" && !ir.vil3ntec.tohid.data.DebtReverse.isReversal(it) }.mapNotNull { t ->
        val j = Jalali.ofIso(t.date) ?: return@mapNotNull null
        //  CRM-10: رسیدِ مشتریِ ادغام‌شده زیرِ ماندگار (نام و فیلترِ «قرض‌دار»)
        val who = ir.vil3ntec.tohid.data.CustomerAlias.keeperOf(d, t.debtorId)
        val debtor = byId[who]
        Row(t.id, debtor?.name ?: StrMoney.deletedDebtor, t.notes, t.amount, t.date, who, debtor != null, j.year, j.month)
      }
    }
    Tab.EXPENSES -> d.expenses.mapNotNull { x ->
      val j = Jalali.ofIso(x.date) ?: return@mapNotNull null
      Row(x.id, x.title.ifBlank { StrMoney.expenseFallback }, x.category, x.amount, x.date, null, true, j.year, j.month)
    }
  }.sortedByDescending { it.date }

  private fun firstById(d: ShopData): Map<String, ir.vil3ntec.tohid.data.Debtor> =
    HashMap<String, ir.vil3ntec.tohid.data.Debtor>(d.debtors.size * 2).also { m -> d.debtors.forEach { m.putIfAbsent(it.id, it) } }

  /** سال‌هایی که ردیف دارند، تازه‌ترین اول */
  fun years(rows: List<Row>): List<Int> = rows.map { it.year }.distinct().sortedDescending()

  /** فیلترِ سال، ماه (۱ تا ۱۲) و قرض‌دار؛ null یعنی «همه» */
  fun filter(rows: List<Row>, year: Int?, month: Int?, debtorId: String?): List<Row> = rows.filter {
    (year == null || it.year == year) && (month == null || it.month == month) && (debtorId == null || it.debtorId == debtorId)
  }

  fun total(rows: List<Row>): Double = rows.sumOf { it.amount }
}
