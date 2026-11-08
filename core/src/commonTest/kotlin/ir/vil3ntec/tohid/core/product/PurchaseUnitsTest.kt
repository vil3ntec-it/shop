package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.product.PurchaseUnits.Checked
import ir.vil3ntec.tohid.core.product.PurchaseUnits.Code
import ir.vil3ntec.tohid.core.product.PurchaseUnits.Draft
import ir.vil3ntec.tohid.core.product.PurchaseUnits.Item
import ir.vil3ntec.tohid.core.purchase.PurchaseOrderCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** PRD-07: چای با واحدِ پایهٔ «دانه»، کارتنِ ۱۲ از الف و کارتنِ ۲۴ از ب — تبدیل، بهای واحد با جمعِ دقیق، سنجش و بارکد. */
class PurchaseUnitsTest {

  private val owners = mapOf("111" to "tea", "222" to "rice", "333" to "rice")
  private val suppliers = setOf("supA", "supB")

  private fun check(
    vararg d: Draft, unit: String = "دانه", fractional: Boolean = false,
    own: List<String> = listOf("111"), packs: Map<String, Long> = emptyMap(),
  ) = PurchaseUnits.check(d.toList(), unit, fractional, own, packs, "tea", { owners[it] }, { it in suppliers })

  private fun ok(c: Checked) = (c as? Checked.Ok)?.units ?: error("رد شد: $c")
  private fun code(c: Checked) = (c as? Checked.Failed)?.failure?.code ?: error("پذیرفته شد: $c")

  @Test fun teaBoughtInTwoCartonSizesFromTwoSuppliers() {
    val units = ok(check(Draft("کارتن ۱۲", "12", "supA", "۴۴۴"), Draft("کارتن ۲۴", "24", "supB", "555")))
    assertEquals(listOf(Item("کارتن ۱۲", 12_000, "supA", "444"), Item("کارتن ۲۴", 24_000, "supB", "555")), units)
    assertEquals(units[0], PurchaseUnits.defaultFor(units, "supA"))
    assertEquals(units[1], PurchaseUnits.defaultFor(units, "supB"))
    assertNull(PurchaseUnits.defaultFor(units, "supC"), "تأمین‌کنندهٔ دیگر ⇒ واحدِ پایه")
    assertEquals(units[1], PurchaseUnits.find(units, "  کارتن   ۲۴ "))
    assertEquals("کارتن ۲۴ (24 دانه)", PurchaseUnits.label(units[1], "دانه"))
    assertEquals(Draft("کارتن ۲۴", "24", "supB", "555"), PurchaseUnits.draftOf(units[1]))
  }

  @Test fun factorConversionToTheBaseUnit() {
    assertEquals(48_000, PurchaseUnits.baseQtyMilli(2_000, 24_000), "۲ کارتنِ ۲۴ = ۴۸ دانه")
    assertEquals(36_000, PurchaseUnits.baseQtyMilli(3_000, 12_000), "۳ کارتنِ ۱۲ = ۳۶ دانه")
    assertEquals(3_750, PurchaseUnits.baseQtyMilli(1_500, 2_500), "۱٫۵ کیسهٔ ۲٫۵ کیلو = ۳٫۷۵ کیلو")
    assertEquals(5_000_000, PurchaseUnits.baseQtyMilli(5_000, 1_000_000), "۵ کیلو با واحدِ پایهٔ گرام = ۵۰۰۰ گرام")
    assertEquals(8, PurchaseUnits.baseQtyMilli(3, 2_500), "۰٫۰۰۷۵ ⇐ نیمه به +∞ ⇒ ۰٫۰۰۸ (همان mulDivRound)")
  }

  @Test fun costPerBaseUnitRoundsHalfUpAndTheExactTotalIsKept() {
    //  ۱ کارتنِ ۲۴ به ۱٬۰۰۰ افغانی: ۴۱٫۶۶۶… ⇒ ۴۱٫۶۷؛ ۲۴ × ۴۱٫۶۷ = ۱٬۰۰۰٫۰۸ ⇒ باقی −۰٫۰۸ و جمع همان ۱٬۰۰۰
    val afn = PurchaseUnits.baseCost(1_000, 24_000, 100_000)
    assertEquals(PurchaseUnits.BaseCost(24_000, 4_167, 100_000, -8), afn)
    //  همان مثال به پولِ خرد: ۱٬۰۰۰ صدم ÷ ۲۴ = ۴۱٫۶۶۶… ⇒ ۴۲؛ ۲۴ × ۴۲ = ۱٬۰۰۸ ⇒ باقی −۸
    assertEquals(PurchaseUnits.BaseCost(24_000, 42, 1_000, -8), PurchaseUnits.baseCost(1_000, 24_000, 1_000))
    //  گرد به پایین ⇒ باقیِ مثبت: ۱٬۰۰۰ ÷ ۱۲ = ۸۳٫۳۳ ⇒ ۸۳ و باقی +۴
    assertEquals(PurchaseUnits.BaseCost(12_000, 83, 1_000, 4), PurchaseUnits.baseCost(1_000, 12_000, 1_000))
    //  نیمهٔ درست به +∞: ۱۰۰ ÷ ۸ = ۱۲٫۵ ⇒ ۱۳
    assertEquals(13, PurchaseUnits.baseCost(1_000, 8_000, 100).unitCostMinor)
    //  ده کارتن: باقی بزرگ‌تر می‌شود ولی جمع هرگز (۱۰ × ۱۰۰٬۰۰۰ = ۱٬۰۰۰٬۰۰۰)
    val ten = PurchaseUnits.baseCost(10_000, 24_000, 1_000_000)
    assertEquals(240_000, ten.baseQtyMilli)
    assertEquals(1_000_000, ten.totalMinor)
    assertEquals(-80, ten.remainderMinor)
    for (c in listOf(afn, ten, PurchaseUnits.baseCost(7_000, 12_000, 123_457), PurchaseUnits.baseCost(1_250, 2_500, 99_999))) {
      assertEquals(c.totalMinor, PurchaseOrderCore.mulDivRound(c.baseQtyMilli, c.unitCostMinor, 1000) + c.remainderMinor, "جمع = مقدار × بها + باقی، دقیق")
    }
    //  مخرجِ بیش از 2^31 (۳ تن با واحدِ پایهٔ گرام) هم دقیق است
    assertEquals(PurchaseUnits.BaseCost(3_000_000_000, 100, 300_000_000, 0), PurchaseUnits.baseCost(3_000, 1_000_000_000, 300_000_000))
  }

  @Test fun roundDivIsTheSameRuleAsMulDivRound() {
    for (n in listOf(-7L, -5L, -1L, 0L, 1L, 5L, 7L, 100_000_000L)) for (d in listOf(1L, 2L, 3L, 8L, 24_000L)) {
      assertEquals(PurchaseOrderCore.mulDivRound(n, 1, d), PurchaseUnits.roundDiv(n, d), "$n/$d")
    }
    assertEquals(-2, PurchaseUnits.roundDiv(-5, 2), "−۲٫۵ ⇒ −۲ (نیمه به +∞)")
  }

  @Test fun validationNamesFactorsAndLimits() {
    assertEquals(Code.DUPLICATE_NAME, code(check(Draft("كارتن", "12"), Draft(" کارتن ", "24"))), "ي/ك و فاصله یکی‌اند")
    assertEquals(Code.NAME_IS_BASE, code(check(Draft("دانه", "1"))))
    assertEquals(Code.NAME_REQUIRED, code(check(Draft("", "12"))))
    assertEquals(Code.NAME_TOO_LONG, code(check(Draft("ک".repeat(31), "12"))))
    assertEquals(Code.BAD_FACTOR, code(check(Draft("کارتن", ""))))
    assertEquals(Code.BAD_FACTOR, code(check(Draft("کارتن", "0"))))
    assertEquals(Code.BAD_FACTOR, code(check(Draft("کارتن", "-3"))))
    assertEquals(Code.FACTOR_TOO_BIG, code(check(Draft("کارتن", "1000001"))))
    assertEquals(Code.WHOLE_ONLY, code(check(Draft("کارتن", "12.5"))), "دانه اعشار نمی‌گیرد")
    assertEquals(listOf(Item("کیسه", 2_500)), ok(check(Draft("کیسه", "۲٫۵"), unit = "کیلو", fractional = true)))
    assertEquals(Code.TOO_MANY, code(check(*Array(11) { Draft("واحد$it", "${it + 2}") })))
    assertEquals(10, ok(check(*Array(10) { Draft("واحد$it", "${it + 2}") })).size)
    assertEquals(Code.BAD_SUPPLIER, code(check(Draft("کارتن", "12", "supZ"))))
    assertTrue(ok(check(Draft(), Draft("  ", ""))).isEmpty(), "ردیفِ خالی نادیده")
  }

  @Test fun validationOfBarcodes() {
    assertEquals(Code.BARCODE_IS_PRODUCT, code(check(Draft("کارتن", "12", barcode = "111"))))
    assertEquals(Code.BARCODE_TAKEN, code(check(Draft("کارتن", "12", barcode = "222"))), "بارکدِ کالای دیگر")
    assertEquals(Code.DUPLICATE_BARCODE, code(check(Draft("کارتن", "12", barcode = "9"), Draft("بسته", "6", barcode = "۹"))))
    assertEquals(Code.BAD_BARCODE, code(check(Draft("کارتن", "12", barcode = "9، 8"))))
    //  همان کارتنِ فروش (بستهٔ ۲۴) همان بارکد را دارد؛ اندازهٔ دیگر نه
    assertEquals(24_000L, ok(check(Draft("کارتن", "24", barcode = "777"), packs = mapOf("777" to 24_000L)))[0].factorMilli)
    assertEquals(Code.BARCODE_IS_OTHER_PACK, code(check(Draft("کارتن", "12", barcode = "777"), packs = mapOf("777" to 24_000L))))
  }

  @Test fun storedShapeIsCheckedLikeTheServer() {
    assertNull(PurchaseUnits.validate(listOf(Item("کارتن", 24_000, "supB", "555"))))
    assertEquals(Code.BAD_FACTOR, PurchaseUnits.validate(listOf(Item("کارتن", 0)))?.code)
    assertEquals(Code.BAD_SUPPLIER, PurchaseUnits.validate(listOf(Item("کارتن", 1_000, " ")))?.code)
    assertEquals(Code.BAD_BARCODE, PurchaseUnits.validate(listOf(Item("کارتن", 1_000, barcode = "a b")))?.code)
    assertEquals(Code.DUPLICATE_NAME, PurchaseUnits.validate(listOf(Item("A", 1_000), Item("a", 2_000)))?.code)
    //  بی کالا (سرور): اعشار سنجیده نمی‌شود
    assertNull(PurchaseUnits.validate(listOf(Item("کیسه", 2_500))))
  }

  @Test fun scanningAPurchaseUnitBarcodePicksThatUnit() {
    val a = Item("کارتن ۱۲", 12_000, "supA", "444")
    val b = Item("کارتن ۲۴", 24_000, "supB", "555")
    val idx = PurchaseUnits.Index(listOf("tea" to listOf(a, b), "rice" to listOf(Item("بوری", 50_000, barcode = "666")), "oil" to listOf(Item("کارتن", 6_000, barcode = "666"))))
    assertEquals(PurchaseUnits.Hit("tea", b), idx.find("۵۵۵"))
    assertEquals(PurchaseUnits.Hit("tea", a), idx.find(" 444 "))
    assertNull(idx.find("666"), "کدِ دو کالا مبهم است ⇒ هیچ")
    assertNull(idx.find("44"), "نیمه‌بارکد نه")
    assertTrue(PurchaseUnits.Index(emptyList()).isEmpty)
  }
}
