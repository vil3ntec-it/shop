package ir.vil3ntec.tohid.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  INV-20/21 — تنظیمِ خاموش (پیش‌فرض) ⇒ دفتر، عددِ موجودی، دفترِ موجودی و بها، گزارشِ ضایعات و همگام‌سازی بایت‌به‌بایت همان امروز.
 *  [scenario] فقط راه‌های امروزِ انبار را می‌رود (ورود با محل، اصلاح، انبارگردانی، انتقالِ دفترِ موجودی، فروشِ کالا و کیت)؛
 *  اثرِ انگشتِ SHA-256 ِ خروجی‌اش روی همان main ِ پیش از این کار گرفته شد. دفتر به JSON ِ مدل (همان که دیسک و Sync
 *  می‌نویسند) — فیلد یا ردیفِ تازه، یا عددِ دیگری از `StockLedger`/`ShopStore`، اثر را عوض می‌کرد.
 */
class StockPlacesOffTest {

  private var seq = 0
  private val newId: () -> String = { "id${(++seq).toString().padStart(3, '0')}" }
  private val json = Json { encodeDefaults = true }
  private val today = "2026-11-02"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "قوطیِ نوشابه", unit = "عدد", purchasePrice = 50.0, salePrice = 70.0),
      Product(id = "p2", name = "کارتنِ نوشابه", unit = "کارتن", purchasePrice = 1200.0, salePrice = 1600.0),
      Product(id = "p3", name = "روبان", unit = "عدد", purchasePrice = 30.0, salePrice = 40.0),
      Product(id = "k1", name = "بستهٔ هدیه", unit = "عدد", salePrice = 200.0, kit = listOf(KitPart("p1", 2.0), KitPart("p3", 1.0))),
    ),
  )

  private fun ok(r: WarehouseEngine.Result): ShopData = (r as WarehouseEngine.Result.Ok).data

  private fun scenario(): String {
    var d = base
    d = ok(WarehouseEngine.addEntry(d, WarehouseEngine.EntryDraft(productId = "p1", units = 20.0, unit = "عدد", price = 50.0), today, 100, newId))
    d = ok(WarehouseEngine.addEntry(d, WarehouseEngine.EntryDraft(productId = "p2", cartons = 5.0, perCarton = 1.0, units = 5.0, unit = "کارتن", price = 1200.0, locationId = "انبار"), today, 110, newId))
    d = ok(WarehouseEngine.addEntry(d, WarehouseEngine.EntryDraft(productId = "p3", units = 10.0, unit = "عدد", price = 30.0), today, 120, newId))
    d = ok(WarehouseEngine.adjustStock(d, "p1", 2.0, false, "شکسته", WarehouseEngine.AdjustKind.ADJUSTMENT, today, 130, newId, adjKind = "damage", userId = "کریم"))
    d = ok(WarehouseEngine.stockCount(d, mapOf("p3" to 9.0), "قفسه", today, 140, newId))
    d = (StockActions.transfer(d, StockLedger.replay(d), "p1", "", "انبار", 4.0, null, "پشتِ دکان", "کریم", today, 150, newId) as StockActions.Result.Ok).data
    d = (SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 3.0), SalesEngine.CartLine("k1", 1.0)), SalesEngine.Checkout(), today, 160, newId, saleId = "s1") as SalesEngine.Result.Ok).data

    val out = StringBuilder()
    for (p in d.products) out.append(p.id).append('=').append(ShopStore.stock(d, p.id)).append(' ').append(ShopStore.stockStatus(d, p)).append('\n')
    out.append(WarehouseEngine.summary(d)).append('\n')
    val v = StockLedger.replay(d)
    v.rows.forEach { out.append(it).append('\n') }
    d.products.forEach { out.append(StockLedger.productView(v, it.id)).append('\n') }
    out.append(v.tasks).append('\n').append(v.errors).append('\n')
    out.append(StockLedger.locations(d)).append('\n')
    out.append(ShrinkLoss.moves(d)).append('\n')
    out.append(json.encodeToString(d))
    return out.toString()
  }

  private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

  @Test fun `تنظیمِ خاموش ⇒ دفتر و موجودی و بها و ضایعات و همگام‌سازی همان امروز`() {
    val s = scenario()
    System.getProperty("tohid.golden")?.let { File(it).writeText(s) }
    assertEquals(GOLDEN, sha(s))
  }

  /** متنِ خامِ `android-native` (کامپیوتر پیش از کامپایل چند عبارت را عوض می‌کند، این دو پرونده را نه) */
  private fun src(rel: String): String =
    listOf("src/main/java/ir/vil3ntec/tohid", "../android-native/app/src/main/java/ir/vil3ntec/tohid", "android-native/app/src/main/java/ir/vil3ntec/tohid")
      .map { File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد (cwd=${File(".").absoluteFile})")

  @Test fun `پیش‌فرض خاموش و دکمه و نوشتن فقط پشتِ همان تنظیم`() {
    val pref = src("data/StockPlacesAndroid.kt")
    assertTrue(pref.contains("getBoolean(PLACES, false)") && pref.contains("getBoolean(REPACK, false)"))
    val entry = src("ui/screens/StockPlacesUiAndroid.kt")
    assertTrue(entry.contains("if (!placesOn && !repackOn) return"))
    //  هر نوشتن فقط از `StockPlacesPref.record` (دفترِ همین لحظهٔ دیسک، یک بار برای هر کلید)
    assertEquals(1, Regex(Regex.escape("StockPlacesPref.record(")).findAll(entry).count())
    assertEquals(5, Regex("""(?<!fun )\bwrite\(""").findAll(entry).count())
    val wh = src("ui/screens/Warehouse.kt")
    assertEquals(1, Regex(Regex.escape("StockPlacesEntry(")).findAll(wh).count())
    assertTrue("انبار خودش هیچ نوشتنِ تازه‌ای ندارد", !wh.contains("StockPlaces.") && !wh.contains("Repack."))
  }

  private companion object {
    const val GOLDEN = "2287befc01e734f6425e255d33755f045988782097b833475ca52cda17a08192"
  }
}
