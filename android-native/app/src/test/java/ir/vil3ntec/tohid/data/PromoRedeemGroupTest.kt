package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.data.repo.PromoRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRM-11 — گروهِ مشتری در `POST /api/shop/promos/redeem`: سرور کدِ قاعدهٔ گروه‌دار را فقط با گروهِ قرض‌دارِ دفترِ خودش
 *  می‌گیرد (`wrong_group`)، پس برنامه مشتریِ **همان** سبدی را که تخفیف با آن سنجیده شد می‌فرستد؛ بی مشتری ⇒ `null` ِ
 *  صریح (کلید همیشه هست؛ نبودنِ کلید یعنی برنامهٔ پیشین). سمتِ سرور: `promo-redemptions.test.js`.
 */
class PromoRedeemGroupTest {

  private val tea = Product(id = "tea", name = "چای", unit = "عدد", category = "نوشیدنی", salePrice = 300.0)
  private val d = ShopData(
    products = listOf(tea),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "tea", units = 50.0)),
    debtors = listOf(Debtor(id = "c1", name = "حمید", group = "عمده")),
  )
  private val cart = listOf(SalesEngine.CartLine("tea", 2.0))
  private val vip = PromoCore.Rule(id = "g1", name = "کدِ عمده", kind = "percent", bp = 1000, code = "BIG10", groups = listOf("عمده"), channels = listOf("pos"))
  private val snap = PromoEngine.Snapshot(enabled = true, rules = listOf(vip))

  @Test
  fun `نتیجهٔ تخفیف مشتریِ همان سبد را با خود دارد`() {
    val group = PromoEngine.groupOf(d, "c1", snap.rules)
    val withCustomer = PromoEngine.evaluate(d, cart, snap, listOf("BIG10"), "2026-11-03", 600, group, debtorId = "c1")
    assertEquals("c1", withCustomer?.debtorId)
    assertTrue("کدِ گروه برای مشتریِ همان گروه اعمال می‌شود", (withCustomer?.discount ?: 0.0) > 0.0)
    assertNull(PromoEngine.evaluate(d, cart, snap, listOf("BIG10"), "2026-11-03", 600)?.debtorId)
  }

  @Test
  fun `بدنهٔ redeem همیشه کلیدِ مشتری دارد — بی مشتری null ِ صریح`() {
    val body = PromoRepository.redeemBody("s1", listOf("BIG10"), "c1")
    assertEquals(JsonPrimitive("s1"), body["saleId"])
    assertEquals(JsonArray(listOf(JsonPrimitive("BIG10"))), body["codes"])
    assertEquals(JsonPrimitive("c1"), body["debtorId"])
    val none = PromoRepository.redeemBody("s2", listOf("BIG10"), null)
    assertTrue("بی مشتری هم کلید هست", none.containsKey("debtorId"))
    assertEquals(JsonNull, none["debtorId"])
  }

  @Test
  fun `دروازهٔ صندوق مشتریِ نتیجه را به سرور می‌دهد و نتیجه از همان مشتری ساخته می‌شود`() {
    val root = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }
    val live = File(root, "ui/PromoLive.kt").readText()
    assertTrue(live.contains("repository(context).redeem(saleKey, codes, outcome.debtorId)"))
    assertTrue(live.contains("PromoEngine.evaluate(d, cart, s, codes, today, minute, group, debtorId)"))
  }
}
