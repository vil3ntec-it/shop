package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.product.UnitSale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  PRD-07 — واحدِ فروشِ دوم (کیلو و گرم، کارتن و دانه) دقیق:
 *   • ضریب یک معنا دارد: هزارمِ گرد = برگهٔ ردیفِ صندوق (`UnitLine`) = سرور (`unit_factor_milli`)؛ `toStockQty` هم همان؛
 *   • ضریبِ **تازه**ای که به هزارم دقیق نیست (۱/۲۴) رد می‌شود، ولی ضریبِ کهنهٔ دست‌نخورده ویرایشِ دیگر را قفل نمی‌کند؛
 *   • واحدِ پایهٔ کالایی که گردش دارد بی‌صدا عوض نمی‌شود (۴۸ کیلوی دفتر «۴۸ گرم» خوانده می‌شد).
 */
class SaleUnitExactTest {

  private var seq = 0
  private fun newId(): String = "u${seq++}"
  private val today = "2026-11-09"

  private val kilo = Product(id = "rice", name = "برنج", unit = "کیلو", purchasePrice = 80.0, salePrice = 120.0, saleUnit = "گرم", unitFactor = 0.001)
  /** دادهٔ کهنه: «کارتن» پایه و «دانه» با ضریبِ ۱/۲۴ — پیش از این قاعده ساخته شده */
  private val legacy = Product(id = "oil", name = "روغن", unit = "کارتن", purchasePrice = 2000.0, salePrice = 2400.0, saleUnit = "دانه", unitFactor = 1.0 / 24)
  private val entry = WarehouseEntry(id = "w1", productId = "rice", units = 48.0, unit = "کیلو", price = 80.0, date = "2026-11-01", createdAt = 1)

  private fun failed(r: WarehouseEngine.Result): String = (r as? WarehouseEngine.Result.Failed)?.message ?: error("پذیرفته شد")
  private fun ok(r: WarehouseEngine.Result): ShopData = (r as? WarehouseEngine.Result.Ok)?.data ?: error("رد شد: $r")
  private fun draftOf(p: Product, unit: String = p.unit, factor: Double = p.unitFactor, name: String = p.name) = WarehouseEngine.ProductDraft(
    name = name, category = p.category, unit = unit, purchasePrice = p.purchasePrice, salePrice = p.salePrice,
    saleUnit = p.saleUnit, unitFactor = factor,
  )

  @Test
  fun gramsToKilo_exactToTheMilli() {
    assertEquals(0.25, SalesEngine.toStockQty(kilo, 250.0), 0.0)
    assertEquals(0.001, SalesEngine.toStockQty(kilo, 1.0), 0.0)
    assertEquals(0.333, SalesEngine.toStockQty(kilo, 333.0), 0.0)
    assertEquals(1.234, SalesEngine.toStockQty(kilo, 1234.0), 0.0)
    assertEquals(0.007, SalesEngine.toStockQty(kilo.copy(unitFactor = 0.007), 1.0), 0.0)
  }

  @Test
  fun exactFactor_sameAsTillUnitsAndServer_noFloatDrift() {
    //  «بستهٔ ۹ گرمی» از کالای کیلویی: ۱٫۵ بسته = ۱۳٫۵ گرم ⇒ برگهٔ ردیف (`UnitSale.toBase`) و سرور (`mulDivRound`) ۰٫۰۱۴؛
    //  `toStockQty` ِ پیشین با ضربِ ممیزِ شناور ۱۳٫۴۹۹… می‌دید و ۰٫۰۱۳ کم می‌کرد — یک فروش، دو موجودی بسته به راه
    val spice = kilo.copy(saleUnit = "بسته", unitFactor = 0.009)
    val pack = UnitLine.units(spice)[1]
    assertEquals(9L, pack.sizeMilli)
    for (q in listOf(0.5, 1.0, 1.5, 2.5, 7.5, 100.0)) {
      assertEquals("q=$q", UnitSale.toBase(q, pack, false)!!, SalesEngine.toStockQty(spice, q), 0.0)
    }
    assertEquals(0.014, SalesEngine.toStockQty(spice, 1.5), 0.0)
    //  ضریبِ کهنهٔ ناجور (۱/۲۴) همان ضربِ پیشین: ۲۴ دانه یک کارتنِ کامل
    assertEquals(1.0, SalesEngine.toStockQty(legacy, 24.0), 0.0)
    assertEquals(0.042, SalesEngine.toStockQty(legacy, 1.0), 0.0)
  }

  /**
   *  کادرِ ردیفِ صندوق (`CartLineDialog`، Compose ⇒ متن سنجیده می‌شود) خودش ضربِ ممیزِ شناور می‌کرد: «۱٫۵ بسته» ۰٫۰۱۳ کیلو
   *  ثبت می‌شد، در حالی که برگهٔ ردیف، `toStockQty` و سرور ۰٫۰۱۴. حالا همان `UnitSale.typedToBase` (= `toBaseQty`).
   */
  @Test
  fun tillLineDialog_sameMilliAsToStockQty() {
    val spice = kilo.copy(saleUnit = "بسته", unitFactor = 0.009)
    for (q in listOf(0.5, 1.0, 1.5, 2.5, 7.5, 100.0)) {
      assertEquals("q=$q", SalesEngine.toStockQty(spice, q), UnitSale.typedToBase(q, spice.unitFactor), 0.0)
    }
    assertEquals(0.014, UnitSale.typedToBase(1.5, 0.009), 0.0)
    assertEquals(1.0, UnitSale.typedToBase(24.0, legacy.unitFactor), 0.0)
    val rel = "ir/vil3ntec/tohid/ui/screens/SaleDialogs.kt"
    val src = listOf("src/main/java", "../android-native/app/src/main/java", "android-native/app/src/main/java")
      .map { java.io.File(it, rel) }.firstOrNull { it.isFile }?.readText() ?: error("$rel پیدا نشد")
    assertTrue(src.contains("if (inSaleUnit) ir.vil3ntec.tohid.core.product.UnitSale.typedToBase(it, unitFactor) else it"))
    assertTrue(src.contains("if (wantSale) v / unitFactor else ir.vil3ntec.tohid.core.product.UnitSale.typedToBase(v, unitFactor)"))
    assertTrue("ضربِ ممیزِ شناورِ کادر برگشت", !src.contains("* unitFactor * 1000.0") && !src.contains("v * unitFactor"))
  }

  @Test
  fun newFactorMustBeExactToTheMilli() {
    val d = ShopData()
    val draft = WarehouseEngine.ProductDraft(name = "روغن", unit = "کارتن", salePrice = 2400.0, saleUnit = "دانه", unitFactor = 1.0 / 24)
    assertTrue(failed(WarehouseEngine.addProduct(d, draft, 0, ::newId)).contains("ضریب"))
    assertTrue(failed(WarehouseEngine.addProduct(d, draft.copy(unitFactor = 0.0005), 0, ::newId)).contains("ضریب"))
    for (f in listOf(0.001, 0.5, 0.042, 24.0, 1000.0)) ok(WarehouseEngine.addProduct(d, draft.copy(unitFactor = f), 0, ::newId))
  }

  @Test
  fun legacyInexactFactor_untouched_doesNotBlockOtherEdits() {
    val d = ShopData(products = listOf(legacy))
    val renamed = ok(WarehouseEngine.editProduct(d, "oil", draftOf(legacy, name = "روغنِ آفتاب"), today, 0, ::newId))
    assertEquals(1.0 / 24, renamed.products.single().unitFactor, 0.0)
    assertTrue(failed(WarehouseEngine.editProduct(d, "oil", draftOf(legacy, factor = 0.0417), today, 0, ::newId)).contains("ضریب"))
    assertEquals(0.042, ok(WarehouseEngine.editProduct(d, "oil", draftOf(legacy, factor = 0.042), today, 0, ::newId)).products.single().unitFactor, 0.0)
  }

  @Test
  fun baseUnitOfProductWithHistory_isNotSilentlyChanged() {
    val rice = kilo.copy(saleUnit = "", unitFactor = 1.0)
    val sale = Sale(id = "s1", date = "2026-11-02", createdAt = 2)
    val histories = listOf(
      ShopData(products = listOf(rice), warehouseEntries = listOf(entry)),
      ShopData(products = listOf(rice), sales = listOf(sale), saleItems = listOf(SaleItem(id = "i1", saleId = "s1", productId = "rice", quantity = 2.0))),
      ShopData(products = listOf(rice), stockMovements = listOf(StockMovement(id = "m1", productId = "rice", type = "damage", qty = -1.0))),
      ShopData(products = listOf(rice), purchases = listOf(Purchase(id = "b1", productId = "rice", quantity = 5.0))),
    )
    for (d in histories) {
      val msg = failed(WarehouseEngine.editProduct(d, "rice", draftOf(rice, unit = "گرم"), today, 0, ::newId))
      assertTrue(msg, msg.contains("کیلو"))
      assertEquals("کیلو", d.products.single().unit)
    }
    //  بی گردش: واحد آزادانه عوض می‌شود؛ همان واحد با فاصله یا «ي/ك»، یا واحدِ خالیِ کهنه که تازه نوشته می‌شود، گردش را دوباره نمی‌خواند
    assertEquals("گرم", ok(WarehouseEngine.editProduct(ShopData(products = listOf(rice)), "rice", draftOf(rice, unit = "گرم"), today, 0, ::newId)).products.single().unit)
    ok(WarehouseEngine.editProduct(histories[0], "rice", draftOf(rice, unit = " کیلو "), today, 0, ::newId))
    ok(WarehouseEngine.editProduct(histories[0], "rice", draftOf(rice, unit = "كيلو"), today, 0, ::newId))
    val unnamed = histories[0].copy(products = listOf(rice.copy(unit = "")))
    assertEquals("کیلو", ok(WarehouseEngine.editProduct(unnamed, "rice", draftOf(rice, unit = "کیلو"), today, 0, ::newId)).products.single().unit)
  }

  @Test
  fun r2_blankingTheUnitOfProductWithHistory_isAlsoLocked() {
    //  ۲-۱۶ (R2 الف): «خالی، سپس واحدِ تازه» — گامِ اول دیگر آزاد نیست؛ بی گردش آزاد
    val rice = kilo.copy(saleUnit = "", unitFactor = 1.0)
    val moved = ShopData(products = listOf(rice), warehouseEntries = listOf(entry))
    for (blank in listOf("", "  ")) {
      val msg = failed(WarehouseEngine.editProduct(moved, "rice", draftOf(rice, unit = blank), today, 0, ::newId))
      assertTrue(msg, msg.contains("کیلو"))
    }
    assertEquals("", ok(WarehouseEngine.editProduct(ShopData(products = listOf(rice)), "rice", draftOf(rice, unit = ""), today, 0, ::newId)).products.single().unit)
  }

  @Test
  fun r2_dormantInexactFactor_thenSaleUnit_isRejected() {
    //  ۲-۱۶ (R2 الف): «ضریبِ ناجور بی واحدِ فروشِ دوم، سپس فقط واحدِ فروش» — ضریبِ خوابیده با روشن شدنِ واحدِ فروش تازه است
    //  فرمِ برنامه با واحدِ فروشِ خالی ضریب را ۱ می‌کند؛ ضریبِ خوابیده فقط از همگام‌سازی (دستگاه/برنامهٔ دیگر) می‌رسد
    val plain = legacy.copy(saleUnit = "", unitFactor = 1.0)
    assertEquals(1.0, ok(WarehouseEngine.editProduct(ShopData(products = listOf(plain)), "oil", draftOf(plain, factor = 1.0 / 24), today, 0, ::newId)).products.single().unitFactor, 0.0)
    val sleeping = legacy.copy(saleUnit = "", unitFactor = 1.0 / 24)
    val dormant = ShopData(products = listOf(sleeping))
    val step2 = WarehouseEngine.ProductDraft(
      name = sleeping.name, category = sleeping.category, unit = sleeping.unit, purchasePrice = sleeping.purchasePrice,
      salePrice = sleeping.salePrice, saleUnit = "دانه", unitFactor = sleeping.unitFactor,
    )
    assertTrue(failed(WarehouseEngine.editProduct(dormant, "oil", step2, today, 0, ::newId)).contains("ضریب"))
    //  ضریبِ دقیق ⇒ آزاد؛ کالایی که واحدِ فروش داشت با ضریبِ کهنهٔ دست‌نخورده همان آزاد (legacyInexactFactor…)
    ok(WarehouseEngine.editProduct(dormant, "oil", step2.copy(unitFactor = 0.042), today, 0, ::newId))
  }

  @Test
  fun saleIn250Grams_movesStockOnceAndExactly() {
    val d = ShopData(products = listOf(kilo), warehouseEntries = listOf(entry))
    val q = SalesEngine.toStockQty(kilo, 250.0)
    val r = SalesEngine.record(d, listOf(SalesEngine.CartLine("rice", q)), SalesEngine.Checkout(), today, 10, ::newId) as SalesEngine.Result.Ok
    assertEquals(47.75, ShopStore.stock(r.data, "rice"), 0.0)
    assertEquals(0.25, r.data.saleItems.single().quantity, 0.0)
  }
}
