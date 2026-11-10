package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.ui.screens.PromoForm
import ir.vil3ntec.tohid.ui.screens.PromoText
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRM-11 — «هدف‌گیریِ کانال (حضوری یا اینترنتی)»: هسته `Rule.channels` را داشت و ویترین (MKT-01) فقط قاعدهٔ `online` (یا بی
 *  کانال) را می‌خواند، ولی ویرایشگر هر قاعدهٔ تازه را «فقط صندوق» می‌ساخت و کانال را هرگز عوض نمی‌کرد ⇒ صاحبِ دکان هیچ راهی برای
 *  تخفیفِ ویترین یا «فقط ویترین» نداشت. حالا با پرچمِ `online` ِ سرور (`shop.storefront.promos`) یا قاعدهٔ ویترین‌دار فرم «کجا اعمال
 *  شود» دارد؛ خاموش ⇒ همان فرم و همان کانال (N2).
 */
class PromoChannelTest {

  private val tea = Product(id = "tea", name = "چای", unit = "عدد", category = "نوشیدنی", salePrice = 300.0)
  private val d = ShopData(
    products = listOf(tea),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "tea", units = 50.0)),
  )
  private val cart = listOf(SalesEngine.CartLine("tea", 2.0))
  private val till = PromoCore.Rule(id = "c1", name = "ده درصد", kind = "percent", bp = 1000, channels = listOf("pos"))

  @Test
  fun `پرچمِ خاموش — فرم کانال ندارد و کانالِ قاعده دست نمی‌خورد`() {
    val f = PromoForm.of(till)
    assertNull(f.channel)
    assertEquals(listOf("pos"), f.copy(pct = "15").toRule(till).channels)
    val all = till.copy(channels = emptyList())
    assertEquals(emptyList<String>(), PromoForm.of(all).toRule(all).channels)
  }

  @Test
  fun `پرچمِ روشن — صندوق، ویترین یا هر دو؛ دست‌نخورده همان فهرست`() {
    val f = PromoForm.of(till, online = true)
    assertEquals(PromoText.POS, f.channel)
    assertEquals(listOf("pos"), f.toRule(till).channels)
    assertEquals(listOf("online"), f.copy(channel = PromoText.ONLINE).toRule(till).channels)
    assertEquals(listOf("pos", "online"), f.copy(channel = PromoText.BOTH).toRule(till).channels)
    //  کانالِ خالی (= همه) «هر دو» دیده می‌شود و دست‌نخورده خالی می‌ماند (N2)
    val all = till.copy(channels = emptyList())
    assertEquals(PromoText.BOTH, PromoForm.of(all, online = true).channel)
    assertEquals(emptyList<String>(), PromoForm.of(all, online = true).copy(pct = "20").toRule(all).channels)
    assertEquals(listOf("pos"), PromoForm.of(all, online = true).copy(channel = PromoText.POS).toRule(all).channels)
    //  هر گزینه از اعتبارسنجیِ هسته می‌گذرد
    for (ch in PromoText.CHANNELS.map { it.first }) {
      assertEquals(ch, emptyList<String>(), PromoCore.validate(f.copy(channel = ch).toRule(till)))
    }
    assertEquals(PromoText.ONLINE, PromoForm.of(till.copy(channels = listOf("online")), online = true).channel)
    assertEquals(PromoText.BOTH, PromoForm.of(till.copy(channels = listOf("online", "pos")), online = true).channel)
  }

  @Test
  fun `قاعدهٔ فقط‌ویترین در صندوق اعمال نمی‌شود و متنِ دامنه کانال را می‌گوید`() {
    fun off(r: PromoCore.Rule): Double =
      PromoEngine.evaluate(d, cart, PromoEngine.Snapshot(enabled = true, rules = listOf(r)), emptyList(), "2026-11-10", 600)?.discount ?: 0.0
    assertEquals(60.0, off(till), 0.001)
    assertEquals(0.0, off(till.copy(channels = listOf("online"))), 0.001)
    assertEquals(60.0, off(till.copy(channels = listOf("pos", "online"))), 0.001)
    assertFalse(PromoText.scopeText(till, d).contains("ویترین"))
    assertFalse(PromoText.scopeText(till.copy(channels = emptyList()), d).contains("ویترین"))
    val web = PromoText.scopeText(till.copy(channels = listOf("online")), d)
    assertTrue(web, web.endsWith(" · فقط ویترین"))
    val both = PromoText.scopeText(till.copy(channels = listOf("pos", "online")), d)
    assertTrue(both, both.endsWith(" · صندوق و ویترین"))
  }

  @Test
  fun `پرچمِ online از پاسخِ سرور می‌آید و نسخهٔ دستگاه فقط روشنش را می‌نویسد`() {
    val on = PromoJson.snapshotOf(buildJsonObject { put("enabled", true); put("online", true) }, 1)
    assertTrue(on.online)
    assertEquals(JsonPrimitive(true), PromoJson.toJson(on)["online"])
    assertTrue(PromoJson.snapshotOf(PromoJson.toJson(on), 1).online)
    val off = PromoJson.snapshotOf(buildJsonObject { put("enabled", true) }, 1)
    assertFalse(off.online)
    assertFalse(PromoJson.toJson(off).containsKey("online"))
  }

  @Test
  fun `ویرایشگر کانال را فقط با پرچم یا قاعدهٔ ویترین‌دار نشان می‌دهد`() {
    val root = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }
    val src = File(root, "ui/screens/Promos.kt").readText()
    for (needle in listOf(
      "val online = PromoLive.snap.value.online || PromoText.ONLINE in rule.channels",
      "PromoForm.of(rule, uses = counted, online = online)",
      "Choice(StrLine.promoWhereTitle, PromoText.CHANNELS, ch) { f = f.copy(channel = it) }",
    )) assertTrue(needle, src.contains(needle))
  }
}
