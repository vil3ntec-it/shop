package ir.vil3ntec.tohid.core.product

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  PRD-03 (کدِ کالا با تولیدِ خودکار) و PRD-09 (کالای خدماتی) در شکلِ تمیزِ فرم. پیش از این کالا
 *  کدِ دکان نداشت و هر کالا موجودی می‌خواست، حتی «دوختِ شلوار» یا «شارژِ کارت».
 */
class ProductSkuTest {

  @Test
  fun cleanUppercasesLatinizesDigitsAndDropsSpaces() {
    assertEquals("SKU-00042", ProductSku.clean(" sku-۰۰۰۴۲ "))
    assertEquals("CH500", ProductSku.clean("ch 500"))
    assertEquals("AB12", ProductSku.clean("ab١٢"))
    assertEquals("", ProductSku.clean("   "))
    assertEquals("A1", ProductSku.clean("A#1"))
    assertEquals(ProductSku.MAX, ProductSku.clean("x".repeat(80)).length)
  }

  @Test
  fun problemFlagsBadCharactersAndLength() {
    assertEquals(ProductSku.Problem.BAD_CHAR, ProductSku.problem("A#1"))
    assertEquals(ProductSku.Problem.TOO_LONG, ProductSku.problem("x".repeat(ProductSku.MAX + 1)))
    assertNull(ProductSku.problem("AB-1_2.3/4"))
    assertNull(ProductSku.problem(" sku ۱ "))
    assertNull(ProductSku.problem(""))
  }

  @Test
  fun nextIsOneAboveTheHighestNumberOfTheSamePrefix() {
    assertEquals("SKU-00001", ProductSku.next(emptyList()))
    assertEquals("SKU-00008", ProductSku.next(listOf("SKU-00007", "sku-00003", "CH-99", "SKU-12A", "SKU-")))
    assertEquals("SKU-100000", ProductSku.next(listOf("SKU-99999")))
    //  «۰۰۰۰۹» ِ فارسی همان ۹ است
    assertEquals("SKU-00010", ProductSku.next(listOf("sku-۰۰۰۰۹")))
  }

  @Test
  fun nextNeverReturnsACodeAlreadyInTheShop() {
    val taken = listOf("SKU-00001", "SKU-1", "SKU-000000000002")
    val got = ProductSku.next(taken)
    assertEquals("SKU-00003", got)
    assertTrue(taken.none { ProductSku.key(it) == got })
  }

  @Test
  fun ownerIsAnotherProductWithTheSameCode() {
    val codes = mapOf("a" to "SKU-00001", "b" to "CH-1")
    assertEquals("a", ProductSku.owner("sku-۰۰۰۰۱", null, codes))
    assertNull(ProductSku.owner("SKU-00001", "a", codes))
    assertEquals("b", ProductSku.owner("ch-1", "a", codes))
    assertNull(ProductSku.owner("", null, codes))
    assertNull(ProductSku.owner("SKU-00002", null, codes))
  }

  @Test
  fun startsWithIsACodePrefixNotAnyPart() {
    assertTrue(ProductSku.startsWith("SKU-00042", "sku-0004"))
    assertTrue(ProductSku.startsWith("SKU-00042", "SKU-00042"))
    assertFalse(ProductSku.startsWith("SKU-00042", "00042"))
    assertFalse(ProductSku.startsWith("SKU-00042", " "))
  }

  @Test
  fun infoKeepsCleanSkuAndStocklessAndSearchFindsTheCode() {
    val info = ProductInfo.of(null, null, null, null, null, null, null, sku = " ch-۵۰۰ ", stockless = true)
    assertEquals("CH-500", info.sku)
    assertTrue(info.stockless)
    assertFalse(info.isEmpty)
    assertTrue(info.matches("ch-5"))
    assertFalse(info.matches("500"))
    //  فقط «خدماتی» هم یعنی چیزی ثبت شده
    assertFalse(ProductInfo(stockless = true).isEmpty)
    assertTrue(ProductInfo.of(null, null, null, null, null, null, null).isEmpty)
  }
}
