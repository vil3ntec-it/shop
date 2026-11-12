package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.promo.PromoCore
import ir.vil3ntec.tohid.ui.screens.PromoForm
import ir.vil3ntec.tohid.ui.screens.PromoText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  PRM-11 — «هدف‌گیریِ گروهِ مشتری»: هسته `Rule.groups` را از پیش داشت ولی صندوق همیشه `group = ""` می‌فرستاد و
 *  ویرایشگر گروه نمی‌گرفت. حالا گروهِ مشتریِ فاکتور (`Debtor.group`، CRM-08) از `PromoEngine.groupOf` به هسته می‌رود
 *  و ویرایشگرِ تخفیف «برای کدام مشتریان» دارد. قاعدهٔ بی گروه و فاکتورِ بی مشتری همان پیشین.
 */
class PromoGroupTest {

  private val tea = Product(id = "tea", name = "چای", unit = "عدد", category = "نوشیدنی", salePrice = 300.0)
  private val d = ShopData(
    products = listOf(tea),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "tea", units = 50.0)),
    debtors = listOf(
      Debtor(id = "c1", name = "حمید", group = "عمده"),
      //  همان گروه با «ي» ِ عربی و فاصلهٔ اضافه — کلیدِ `CustomerCrm.groups` یکی است
      Debtor(id = "c2", name = "کریم", group = " مشتري ويژه "),
      Debtor(id = "c3", name = "بی‌گروه"),
    ),
  )
  private val cart = listOf(SalesEngine.CartLine("tea", 2.0))
  private val onlyWholesale = PromoCore.Rule(id = "g1", name = "ده درصدِ عمده", kind = "percent", bp = 1000, groups = listOf("عمده"), channels = listOf("pos"))
  private val vip = PromoCore.Rule(id = "g2", name = "ویژه", kind = "percent", bp = 2000, groups = listOf("مشتری ویژه"), channels = listOf("pos"))
  private val snap = PromoEngine.Snapshot(enabled = true, rules = listOf(onlyWholesale, vip))

  private fun discount(debtorId: String?): Double {
    val group = PromoEngine.groupOf(d, debtorId, snap.rules)
    return PromoEngine.evaluate(d, cart, snap, emptyList(), "2026-11-03", 600, group)?.discount ?: 0.0
  }

  @Test
  fun `گروهِ مشتری به نامِ گروهِ قاعده می‌رسد؛ بی مشتری یا بی گروه خالی`() {
    assertEquals("عمده", PromoEngine.groupOf(d, "c1", snap.rules))
    assertEquals("مشتری ویژه", PromoEngine.groupOf(d, "c2", snap.rules))
    assertEquals("", PromoEngine.groupOf(d, "c3", snap.rules))
    assertEquals("", PromoEngine.groupOf(d, null, snap.rules))
    assertEquals("", PromoEngine.groupOf(d, "گم", snap.rules))
    //  گروهی که هیچ قاعده‌ای ندارد همان نامِ مشتری می‌ماند (هسته آن را به هیچ قاعدهٔ گروه‌دار نمی‌دهد)
    assertEquals("عمده", PromoEngine.groupOf(d, "c1", emptyList()))
  }

  @Test
  fun `قاعدهٔ گروه‌دار فقط برای مشتریِ همان گروه؛ بی مشتری هیچ`() {
    assertEquals(60.0, discount("c1"), 0.001)
    assertEquals(120.0, discount("c2"), 0.001)
    assertEquals(0.0, discount("c3"), 0.001)
    assertEquals(0.0, discount(null), 0.001)
    //  پیش‌فرضِ `evaluate` همان ورودیِ پیشین است: بی گروه ⇒ قاعدهٔ گروه‌دار نه
    assertNull(PromoEngine.evaluate(d, cart, snap, emptyList(), "2026-11-03", 600)?.takeIf { it.discount > 0 })
    //  قاعدهٔ بی گروه همه را می‌گیرد، با مشتری یا بی مشتری
    val all = PromoEngine.Snapshot(enabled = true, rules = listOf(onlyWholesale.copy(groups = emptyList())))
    assertEquals(60.0, PromoEngine.evaluate(d, cart, all, emptyList(), "2026-11-03", 600, "")!!.discount, 0.001)
    assertEquals(60.0, PromoEngine.evaluate(d, cart, all, emptyList(), "2026-11-03", 600, "عمده")!!.discount, 0.001)
  }

  @Test
  fun `ویرایشگر گروه‌ها را رفت‌وبرگشت نگه می‌دارد و متنِ دامنه آن را می‌گوید`() {
    val base = onlyWholesale.copy(groups = listOf("عمده", "الف"))
    val f = PromoForm.of(base)
    assertEquals(setOf("عمده", "الف"), f.groups)
    //  دست‌نخورده ⇒ همان ترتیب (هیچ تغییرِ بی‌صدا)
    assertEquals(listOf("عمده", "الف"), f.toRule(base).groups)
    assertEquals(listOf("الف", "عمده", "مشتری ویژه"), f.copy(groups = f.groups + "مشتری ویژه").toRule(base).groups)
    assertEquals(emptyList<String>(), f.copy(groups = emptySet()).toRule(base).groups)
    //  قاعدهٔ تازه بی گروه
    assertEquals(emptyList<String>(), PromoForm.of(PromoCore.Rule(id = "n", channels = listOf("pos"))).toRule(PromoCore.Rule(id = "n", channels = listOf("pos"))).groups)
    val text = PromoText.scopeText(base, d)
    assertTrue(text, text.contains("فقط گروهِ عمده، الف"))
    assertFalse(PromoText.scopeText(onlyWholesale.copy(groups = emptyList()), d).contains("فقط گروهِ"))
  }

  @Test
  fun `هر صندوق مشتریِ فاکتور را به تخفیف می‌دهد و ویرایشگر تراشهٔ گروه دارد`() {
    val root = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { File(it, "ir/vil3ntec/tohid") }.first { it.isDirectory }
    fun src(rel: String) = File(root, rel).readText()
    mapOf(
      "ui/v3/sales/cart/SaleRoute.kt" to "PromoLive.rememberOutcome(data, lines, debtorId)",
      "ui/v3/sales/checkout/CheckoutRoute.kt" to "PromoLive.rememberOutcome(data, lines, form.debtorId)",
      "ui/screens/DeskSale.kt" to "rememberOutcome(d, cart, cartDebtorId)",
      "ui/screens/Promos.kt" to "FilterChip(\n              selected = g in f.groups,",
    ).forEach { (file, needle) -> assertTrue("$file ⇐ $needle", src(file).contains(needle)) }
    assertTrue(src("ui/PromoLive.kt").contains("PromoEngine.evaluate(d, cart, s, codes, today, minute, group, debtorId)"))
  }
}
