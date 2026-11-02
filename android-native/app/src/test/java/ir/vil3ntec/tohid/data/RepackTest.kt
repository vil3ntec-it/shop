package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  INV-21 — بسته‌بندیِ دوباره (شکستنِ کارتن به دانه) و ساختِ بسته از اجزا (`Repack`): ریاضیِ مقدار به هزارم، بها به
 *  یک‌صدم (دانه = کارتن ÷ دانه‌ها، بسته = جمعِ اجزا)، یا-همه-یا-هیچ و یک‌بار برای هر کلید، کمبود رد، بیرون از ضایعات.
 */
class RepackTest {

  private var seq = 0
  private val newId: () -> String = { "n${(++seq).toString().padStart(3, '0')}" }
  private val today = "2026-11-02"

  private val base = ShopData(
    products = listOf(
      Product(id = "can", name = "قوطیِ نوشابه", unit = "عدد", purchasePrice = 50.0, packs = listOf(ProductPack("کارتن", 24_000))),
      Product(id = "ctn", name = "کارتنِ نوشابه", unit = "کارتن", purchasePrice = 1200.0),
      Product(id = "rib", name = "روبان", unit = "عدد", purchasePrice = 30.0),
      Product(id = "box", name = "جعبهٔ هدیه", unit = "عدد", purchasePrice = 0.0),
      Product(id = "kit", name = "کیتِ هدیه", unit = "عدد", kit = listOf(KitPart("can", 2.0))),
    ),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "ctn", units = 5.0, price = 1200.0),
      WarehouseEntry(id = "w2", productId = "can", units = 10.0, price = 50.0),
      WarehouseEntry(id = "w3", productId = "rib", units = 4.0, price = 30.0),
      WarehouseEntry(id = "w4", productId = "ctn", units = 3.0, price = 1200.0, locationId = "انبار"),
    ),
  )

  private fun ok(r: StockPlaces.Result): ShopData = (r as StockPlaces.Result.Ok).data
  private fun plan(p: Repack.Planned): Repack.Plan = (p as Repack.Planned.Ok).plan

  @Test fun `شکستنِ کارتن مقدار و بهای هر دانه`() {
    val p = plan(Repack.planBreak(base, "ctn", 2_000, "can", 24_000))
    assertEquals(Repack.Line("ctn", 2_000, 120_000, 240_000, 8_000), p.inputs.single())
    //  بهای هر دانه = ۱۲۰۰ ÷ ۲۴ = ۵۰ ؋
    assertEquals(Repack.Line("can", 48_000, 5_000, 240_000, 10_000), p.output)
    val valueBefore = WarehouseEngine.summary(base).value
    val d = ok(Repack.breakPack(base, "op1", "ctn", 2_000, "can", 24_000, null, "", "کریم", today, 500, newId))
    assertEquals(6.0, ShopStore.stock(d, "ctn"), 0.0)
    assertEquals(58.0, ShopStore.stock(d, "can"), 0.0)
    assertEquals(valueBefore, WarehouseEngine.summary(d).value, 1e-6)
    val rows = d.warehouseEntries.filter { it.refId == "op1" }
    assertEquals(listOf(-2.0 to 1200.0, 48.0 to 50.0), rows.map { it.units to it.price })
    assertTrue(rows.all { it.isAdjustment && it.adjKind == Repack.KIND && it.userId == "کریم" })
    val moves = d.stockMovements.filter { m -> rows.any { it.id == m.refId } }
    assertEquals(listOf(Repack.MOVE to -2.0, Repack.MOVE to 48.0), moves.map { it.type to it.qty })
    val doc = d.auditLog.single { it.id == "op1" }
    assertEquals(Repack.BREAK, doc.type)
    assertEquals(rows.map { "warehouseEntries/${it.id}" }, doc.refIds)
    //  بهای کسری: ۱۰۰۰ ÷ ۲۴ = ۴۱٫۶۶۷ ⇒ ۴۱٫۶۷ ؋ (mulDivRound)، کلِ بها همان ۱۰۰۰
    val odd = Repack.planBreak(base.copy(products = base.products.map { if (it.id == "ctn") it.copy(purchasePrice = 1000.0) else it }), "ctn", 1_000, "can", 24_000)
    assertEquals(4_167L, plan(odd).output.unitMinor)
    assertEquals(100_000L, plan(odd).output.totalMinor)
  }

  @Test fun `ساختِ بسته مقدار و بهای جمعِ اجزا`() {
    val parts = listOf(Repack.Part("can", 2_000), Repack.Part("rib", 500))
    val p = plan(Repack.planBuild(base, "box", 3_000, parts))
    assertEquals(listOf(Repack.Line("can", 6_000, 5_000, 30_000, 10_000), Repack.Line("rib", 1_500, 3_000, 4_500, 4_000)), p.inputs)
    //  بهای یک بسته = ۲ × ۵۰ + ۰٫۵ × ۳۰ = ۱۱۵ ؋
    assertEquals(Repack.Line("box", 3_000, 11_500, 34_500, 0), p.output)
    val d = ok(Repack.build(base, "op2", "box", 3_000, parts, null, "عید", "", today, 500, newId))
    assertEquals(4.0, ShopStore.stock(d, "can"), 0.0)
    assertEquals(2.5, ShopStore.stock(d, "rib"), 0.0)
    assertEquals(3.0, ShopStore.stock(d, "box"), 0.0)
    assertEquals(Repack.BUILD, d.auditLog.single { it.id == "op2" }.type)
    assertEquals(parts, Repack.lastRecipe(d, "box"))
    assertEquals(emptyList<Repack.Part>(), Repack.lastRecipe(base, "box"))
  }

  @Test fun `کمبود و کیت و ورودیِ نادرست رد می‌شوند بی هیچ ردیف`() {
    fun bad(r: StockPlaces.Result) = assertTrue(r.toString(), r is StockPlaces.Result.Failed)
    bad(Repack.breakPack(base, "x", "ctn", 9_000, "can", 24_000, null, "", "", today, 1, newId))
    bad(Repack.breakPack(base, "x", "ctn", 1_000, "ctn", 24_000, null, "", "", today, 1, newId))
    bad(Repack.breakPack(base, "x", "kit", 1_000, "can", 2_000, null, "", "", today, 1, newId))
    bad(Repack.breakPack(base, "x", "ctn", 1_000, "can", 0, null, "", "", today, 1, newId))
    bad(Repack.build(base, "x", "box", 3_000, listOf(Repack.Part("can", 4_000)), null, "", "", today, 1, newId))
    bad(Repack.build(base, "x", "box", 1_000, listOf(Repack.Part("kit", 1_000)), null, "", "", today, 1, newId))
    bad(Repack.build(base, "x", "box", 1_000, listOf(Repack.Part("box", 1_000)), null, "", "", today, 1, newId))
    bad(Repack.build(base, "x", "box", 1_000, listOf(Repack.Part("can", 1_000), Repack.Part("can", 1_000)), null, "", "", today, 1, newId))
    bad(Repack.build(base, "x", "box", 1_000, emptyList(), null, "", "", today, 1, newId))
    bad(Repack.build(base, "", "box", 1_000, listOf(Repack.Part("can", 1_000)), null, "", "", today, 1, newId))
    //  محلِ «انبار» فقط ۳ کارتن دارد (کل ۸)
    bad(Repack.breakPack(base, "x", "ctn", 4_000, "can", 24_000, "انبار", "", "", today, 1, newId))
    assertEquals(0, seq)
  }

  @Test fun `یک بار برای هر کلید و با محل و بیرون از ضایعات و سندِ تولیدِ دفترِ موجودی`() {
    val d = ok(Repack.breakPack(base, "op3", "ctn", 3_000, "can", 24_000, "انبار", "", "", today, 500, newId))
    assertSame(StockPlaces.Result.Already, Repack.breakPack(d, "op3", "ctn", 1_000, "can", 24_000, "انبار", "", "", today, 600, newId))
    assertEquals(mapOf("" to 10_000L, "انبار" to 72_000L), StockPlaces.stockAt(d)["can"])
    assertEquals(mapOf("" to 5_000L, "انبار" to 0L), StockPlaces.stockAt(d)["ctn"])
    assertEquals(emptyList<Any>(), ShrinkLoss.moves(d))
    val v = StockLedger.replay(d)
    val prod = v.rows.filter { it.type == Repack.MOVE }
    assertEquals(2, prod.size)
    val can = StockLedger.productView(v, "can")
    assertEquals(82_000L, can.qtyMilli)
    //  ۱۰ دانه × ۵۰ + ۳ کارتن × ۱۲۰۰ = ۴۱۰۰ ؋
    assertEquals(410_000L, can.valueMinor)
    assertEquals(600_000L, StockLedger.productView(v, "ctn").valueMinor)
  }
}
