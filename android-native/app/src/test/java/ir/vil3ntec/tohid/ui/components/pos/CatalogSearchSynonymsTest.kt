package ir.vil3ntec.tohid.ui.components.pos

import ir.vil3ntec.tohid.core.text.TextNormalizer
import ir.vil3ntec.tohid.data.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-04 — مترادفِ فهرستِ مرکزی در جست‌وجوی صندوق (`CatalogSearch.search` با [CatalogSearch.Synonyms]). هم‌گروه‌ها با
 *  همان قاعدهٔ `TextNormalizer.matches` (و `text-core.matches` ِ ویترین) جورند؛ فهرستِ تهی دقیقاً همان جست‌وجوی پیشین است.
 */
class CatalogSearchSynonymsTest {

  private val potato = Product("potato", name = "سیب‌زمینی تازه", category = "سبزی")
  private val sugar = Product("sugar", name = "شکر سفید ۱ کیلو", category = "خوراکی", barcodes = listOf("626002000"))
  private val tomato = Product("tomato", name = "رومی سرخ", category = "سبزی")
  private val rice = Product("rice", name = "برنج هندی", category = "خوراکی")
  private val all = listOf(potato, sugar, tomato, rice)
  private val index = CatalogSearch.index(all, emptyMap())
  private val groups = listOf(listOf("کچالو", "سیب‌زمینی"), listOf("بوره", "شکر"), listOf("بادنجان رومی", "رومی"))
  private val syn = CatalogSearch.Synonyms.of(groups)

  @Test
  fun `هم‌گروه جور است؛ بی فهرست نه`() {
    assertEquals(listOf("potato"), CatalogSearch.search(index, "کچالو", null, syn))
    assertEquals(emptyList<String>(), CatalogSearch.search(index, "کچالو", null))
    assertEquals(listOf("sugar"), CatalogSearch.search(index, "بوره", null, syn))
    //  کلمه به کلمه: «بوره سفید» ⇐ «شکر سفید»
    assertEquals(listOf("sugar"), CatalogSearch.search(index, "بوره سفید", null, syn))
    //  کلِ پرس‌وجو خودش یک مترادفِ چندکلمه‌ای است
    assertEquals(listOf("tomato"), CatalogSearch.search(index, "بادنجان رومی", null, syn))
  }

  @Test
  fun `«ي» و «ی»، «ك» و «ک» در گروه و پرس‌وجو یکی‌اند`() {
    assertEquals(listOf("potato"), CatalogSearch.search(index, "كچالو", null, CatalogSearch.Synonyms.of(listOf(listOf("کچالو", "سيب‌زميني")))))
  }

  @Test
  fun `همان قاعدهٔ TextNormalizer_matches`() {
    for (q in listOf("کچالو", "بوره", "بوره سفید", "بادنجان رومی", "برنج", "شکر", "تازه کچالو", "زیره")) {
      val want = all.filter { TextNormalizer.matches(it.name, q, groups) }.map { it.id }.toSet()
      assertEquals(q, want, CatalogSearch.search(index, q, null, syn).toSet())
    }
  }

  @Test
  fun `فهرستِ تهی همان جست‌وجوی پیشین؛ بارکدِ کامل همچنان اول؛ دسته تنگ می‌کند`() {
    for (q in listOf("برنج", "شکر", "626002000", "رومی", "۶۲۶۰۰۲", "")) {
      assertEquals(q, CatalogSearch.search(index, q, null), CatalogSearch.search(index, q, null, CatalogSearch.Synonyms.of(emptyList())))
    }
    assertEquals(listOf("sugar"), CatalogSearch.search(index, "626002000", null, syn))
    assertEquals(emptyList<String>(), CatalogSearch.search(index, "کچالو", "خوراکی", syn))
    assertTrue(CatalogSearch.Synonyms.of(listOf(listOf("تنها"))).isEmpty)
  }
}
