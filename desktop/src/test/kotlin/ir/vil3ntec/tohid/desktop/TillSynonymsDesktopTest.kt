package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.TillSynonyms
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.v3.sales.cart.CatalogModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 *  POS-04 — فهرستِ مرکزیِ مترادف تا کاشی‌های صندوق، روی پیکربندیِ واقعیِ کامپیوتر: `TillSynonyms.refresh` همان درِ همگانیِ
 *  سرور (`/api/v1/search/synonyms`، سرورِ ساختگیِ `TestBackend`) را می‌خواند، در `tohid-synonyms` می‌گذارد و `CatalogModel.items`
 *  ِ صندوق بی هیچ پارامترِ تازه با همان می‌گردد. پیش از گذشتنِ یک روز هیچ درخواستی نمی‌رود؛ پاسخِ بدشکل یا بی‌شبکه نسخهٔ
 *  ذخیره‌شده را دست نمی‌زند؛ نسخهٔ ذخیره‌شده پس از بارِ دوباره (بی‌شبکه) همان جست‌وجو را می‌دهد.
 */
class TillSynonymsDesktopTest {

  private val prefs get() = DesktopContext.getSharedPreferences("tohid-synonyms", 0)
  private val keys = listOf("table", "fetched_at")
  private var saved: Map<String, Any?> = emptyMap()
  private val path = "/api/v1/search/synonyms"
  private val doc = """{"version":3,"updatedAt":1,"groups":[["کچالو","سیب‌زمینی"],["بوره","شکر"]]}"""

  private val d = ShopData(products = listOf(
    Product("potato", name = "سیب‌زمینی تازه", category = "سبزی"),
    Product("sugar", name = "شکر سفید", category = "خوراکی"),
  ))
  private fun shown(q: String) = CatalogModel.items(d, CatalogModel.index(d), q, null, emptyMap()).map { it.id }

  @Before fun before() {
    saved = prefs.all.filterKeys { it in keys }
    val e = prefs.edit()
    keys.forEach { e.remove(it) }
    e.commit()
    TillSynonyms.load(DesktopContext)
  }

  @After fun after() {
    val e = prefs.edit()
    keys.forEach { e.remove(it) }
    for ((k, v) in saved) when (v) {
      is Long -> e.putLong(k, v)
      is String -> e.putString(k, v)
    }
    e.commit()
    TillSynonyms.load(DesktopContext)
  }

  @Test fun `بی فهرست همان جست‌وجوی پیشین`() {
    assertEquals(0L, TillSynonyms.table.version)
    assertEquals(emptyList<String>(), shown("کچالو"))
    assertEquals(listOf("potato"), shown("سیب"))
  }

  @Test fun `گرفتن از سرور ⇐ ذخیره ⇐ کاشی‌های صندوق؛ بارِ دوباره بی‌شبکه همان`() {
    TestBackend().use { b ->
      b.server.on(path) { 200 to doc }
      assertTrue(runBlocking { TillSynonyms.refresh(DesktopContext) })
      assertEquals(1, b.server.hits(path))
      assertEquals(3L, TillSynonyms.table.version)
      assertEquals(listOf("potato"), shown("کچالو"))
      assertEquals(listOf("sugar"), shown("بوره"))
      //  یک روز نگذشته ⇒ هیچ درخواستی
      assertFalse(runBlocking { TillSynonyms.refresh(DesktopContext) })
      assertEquals(1, b.server.hits(path))
    }
    TillSynonyms.load(DesktopContext)
    assertEquals(listOf("potato"), shown("کچالو"))
  }

  @Test fun `پاسخِ بدشکل یا بی‌شبکه نسخهٔ ذخیره‌شده را دست نمی‌زند`() {
    prefs.edit().putString("table", doc).putLong("fetched_at", 1L).commit()
    TillSynonyms.load(DesktopContext)
    TestBackend().use { b ->
      b.server.on(path) { 200 to """{"version":"x","groups":[]}""" }
      assertFalse(runBlocking { TillSynonyms.refresh(DesktopContext) })
      b.online = false
      assertFalse(runBlocking { TillSynonyms.refresh(DesktopContext) })
    }
    assertTrue(prefs.getString("table", null)!!.contains("کچالو"))
    assertEquals(1L, prefs.getLong("fetched_at", 0L))
    assertEquals(listOf("sugar"), shown("بوره"))
  }
}
