package ir.vil3ntec.tohid.core.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** PRD-06: برند و برچسبِ کالا. */
class ProductLabelsTest {

  @Test fun brandIsSqueezedAndBoundedLikeTheServerCatalog() {
    assertEquals("کابل فود", ProductLabels.brand("  کابل   فود "))
    assertEquals(80, ProductLabels.brand("ب".repeat(200)).length)
    assertEquals("", ProductLabels.brand("   "))
  }

  @Test fun tagsSplitDedupeByCompareKeyAndKeepFirstSpelling() {
    assertEquals(listOf("تخفیفی", "ارگانیک", "بسته‌ای"),
      ProductLabels.tags(" تخفیفی، ارگانیک ,تخفيفي؛ بسته‌ای\nبسته ای ; "))
    assertEquals(emptyList(), ProductLabels.tags(" ، , ;"))
  }

  @Test fun tagsAreBounded() {
    val many = (1..25).joinToString("،") { "برچسب$it" }
    assertEquals(10, ProductLabels.tags(many).size)
    assertEquals(30, ProductLabels.tags("x".repeat(50)).single().length)
  }

  @Test fun hasTagUsesTheSameKey() {
    assertTrue(ProductLabels.hasTag(listOf("بسته‌ای"), "بسته ای"))
    assertFalse(ProductLabels.hasTag(null, "بسته"))
    assertFalse(ProductLabels.hasTag(listOf("بسته"), " "))
  }
}
