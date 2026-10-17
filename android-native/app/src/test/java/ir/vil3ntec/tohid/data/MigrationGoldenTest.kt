package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  نگهبانِ «محاسبهٔ قدیم»ِ ابزارِ مهاجرت (`tools/shop-migrate`).
 *
 *  آن ابزار برای گزارشِ تطبیق کپیِ خط‌به‌خطِ چند تابعِ همین برنامه را دارد
 *  (`LegacyFigures`)، چون موتورها به Context گره خورده‌اند. این آزمون همان
 *  عددها را با **موتورِ واقعی** — `ShopStore.stock/debt/supplierDebt` و
 *  `ReportEngine.sales` — روی همان فایلِ نمونه حساب می‌کند و با
 *  `legacy-golden.txt` می‌سنجد. پس اگر یکی از این موتورها عوض شود، این‌جا
 *  قرمز می‌شود و کپیِ ابزار هم باید همراهش عوض شود (و فایلِ مرجع از نو
 *  ساخته شود: `shop-migrate golden …`).
 *
 *  روی هر دو ساخت (اندروید و کامپیوتر) اجرا می‌شود؛ هیچ رفتاری از برنامه
 *  عوض نمی‌شود.
 */
class MigrationGoldenTest {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  private fun golden(name: String): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "tools/shop-migrate/src/test/resources/golden/$name")
      if (f.isFile) return f
      dir = dir.parentFile
    }
    throw AssertionError("tools/shop-migrate/src/test/resources/golden/$name پیدا نشد")
  }

  @Test fun realEnginesProduceTheMigrationToolReferenceNumbers() {
    val d = json.decodeFromString(ShopData.serializer(), golden("fixture-seed1.json").readText())
    assertTrue(d.sales.isNotEmpty() && d.saleReturns.isNotEmpty())

    val out = ArrayList<String>()
    d.products.map { it.id }.distinct().forEach { out += "stock/$it=${ShopStore.stock(d, it)}" }
    d.debtors.map { it.id }.distinct().forEach { out += "debt/$it=${ShopStore.debt(d, it)}" }
    d.suppliers.map { it.id }.distinct().forEach { out += "supplierDebt/$it=${ShopStore.supplierDebt(d, it)}" }
    fun report(label: String, from: String, to: String) {
      val r = ReportEngine.sales(d, from, to)
      listOf(
        "count" to r.count, "gross" to r.gross, "discount" to r.discount, "net" to r.net, "cogs" to r.cogs,
        "grossProfit" to r.grossProfit, "expenses" to r.expenses, "netProfit" to r.netProfit,
        "returnAmount" to r.returnAmount,
      ).forEach { (k, v) -> out += "report/$label/$k=$v" }
    }
    report("all", "0000-01-01", "9999-12-31")
    (d.sales.map { it.date } + d.saleReturns.map { it.date } + d.expenses.map { it.date })
      .mapNotNull { ir.vil3ntec.tohid.Jalali.ofIso(it) }
      .map { it.year to it.month }
      .distinct()
      .forEach { (jy, jm) ->
        val (from, to) = ReportEngine.jalaliMonth(jy, jm)
        report("%04d-%02d".format(jy, jm), from, to)
      }

    val expected = golden("legacy-golden.txt").readLines().filter { it.isNotBlank() }
    assertEquals(expected, out.sorted())
  }
}
