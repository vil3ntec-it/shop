package ir.vil3ntec.tohid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  انبار — موجودی، ورودِ کالا و اصلاح.
 *
 *  موجودی عددی است که فروشنده بر اساسش سفارش می‌دهد و به مشتری «هست» یا
 *  «نیست» می‌گوید. اگر یک واحد اشتباه باشد، جای دیگری معلوم نمی‌شود.
 */
class WarehouseEngineTest {

  private var seq = 0
  private fun newId(): String = "w${seq++}"

  private val base = ShopData(
    products = listOf(
      Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0, minStock = 5.0,
        barcodes = listOf("111")),
    ),
  )

  private fun ok(r: WarehouseEngine.Result): WarehouseEngine.Result.Ok {
    assertTrue("انتظار می‌رفت ثبت شود، ولی: $r", r is WarehouseEngine.Result.Ok)
    return r as WarehouseEngine.Result.Ok
  }

  private fun failed(r: WarehouseEngine.Result): String {
    assertTrue("انتظار می‌رفت رد شود", r is WarehouseEngine.Result.Failed)
    return (r as WarehouseEngine.Result.Failed).message
  }

  /* ---------------------------- ثبتِ کالا ---------------------------- */

  @Test
  fun `کالای تازه با بارکدش ثبت می شود`() {
    val r = ok(
      WarehouseEngine.addProduct(
        base,
        WarehouseEngine.ProductDraft(
          name = "  روغن  ", category = "خوراکه", unit = "عدد",
          purchasePrice = 400.0, salePrice = 555.0, minStock = 2.0, barcode = "222",
        ),
        now = 1_700_000_000_000, newId = ::newId,
      )
    )
    val p = r.data.products.first { it.id == r.id }
    assertEquals("روغن", p.name)                       // فاصله‌های اضافی می‌روند
    assertEquals(listOf("222"), p.barcodes)
    assertEquals(555.0, p.salePrice, 0.0)
    assertEquals(2.0, p.minStock, 0.0)
    // دستهٔ تازه به فهرست اضافه می‌شود تا دفعهٔ بعد هم باشد
    assertTrue(r.data.productCategories.contains("خوراکه"))
    // موجودی صفر است تا ورودِ انبار ثبت نشود
    assertEquals(0.0, ShopStore.stock(r.data, r.id), 0.0)
  }

  @Test
  fun `بارکد تکراری کالای قبلی را خراب نمی کند`() {
    val message = failed(
      WarehouseEngine.addProduct(
        base,
        WarehouseEngine.ProductDraft(name = "شکر", salePrice = 100.0, barcode = "111"),
        now = 0, newId = ::newId,
      )
    )
    assertEquals("این بارکد قبلاً برای کالای دیگری ثبت شده", message)
  }

  @Test
  fun `کالای بی نام ثبت نمی شود`() {
    assertEquals(
      "نام کالا را بنویسید",
      failed(WarehouseEngine.addProduct(base, WarehouseEngine.ProductDraft(name = "   "), 0, ::newId)),
    )
  }

  @Test
  fun `همان بارکد روی همان کالا ایراد ندارد`() {
    // ویرایشِ کالا بدونِ عوض‌کردنِ بارکدش نباید رد شود
    val r = ok(
      WarehouseEngine.editProduct(
        base, "p1",
        WarehouseEngine.ProductDraft(name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 320.0, barcode = "111"),
        today = "2026-08-28", now = 0, newId = ::newId,
      )
    )
    assertEquals(320.0, r.data.products.single().salePrice, 0.0)
  }

  @Test
  fun `تغییر قیمت خرید در تاریخچه می ماند`() {
    val r = ok(
      WarehouseEngine.editProduct(
        base, "p1",
        WarehouseEngine.ProductDraft(name = "برنج", unit = "کیلو", purchasePrice = 250.0, salePrice = 300.0, barcode = "111"),
        today = "2026-08-28", now = 1_700_000_000_000, newId = ::newId,
      )
    )
    val change = r.data.priceHistory.single()
    assertEquals(200.0, change.oldPrice, 0.0)
    assertEquals(250.0, change.newPrice, 0.0)
    assertEquals("p1", change.productId)

    //  تغییرِ قیمتِ خرید ردیفِ خودش را دارد؛ بقیهٔ تغییرها (اینجا بارکد) در «ویرایش کالا»
    val entry = r.data.auditLog.single { it.type == "price_change" }
    assertEquals("تغییر قیمت خرید از 200 به 250 افغانی", entry.notes)
  }

  @Test
  fun `قیمت خرید که عوض نشود تاریخچه ای هم ساخته نمی شود`() {
    val r = ok(
      WarehouseEngine.editProduct(
        base, "p1",
        WarehouseEngine.ProductDraft(name = "برنج تازه", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0),
        today = "2026-08-28", now = 0, newId = ::newId,
      )
    )
    assertTrue(r.data.priceHistory.isEmpty())
    assertTrue(r.data.auditLog.none { it.type == "price_change" })
    //  نام عوض شد ⇒ ردیفِ «ویرایش کالا» با نامِ پیشین و تازه
    assertTrue(r.data.auditLog.single { it.type == "product_edit" }.notes.contains("«برنج تازه»"))
    assertEquals(emptyList<String>(), r.data.products.single().barcodes)  // بارکد برداشته شد
  }

  /* --------------------------- ورودِ کالا --------------------------- */

  @Test
  fun `ورود کالا موجودی را بالا می برد و حرکت انبار می سازد`() {
    val r = ok(
      WarehouseEngine.addEntry(
        base,
        WarehouseEngine.EntryDraft(productId = "p1", cartons = 4.0, perCarton = 25.0, units = 100.0,
          unit = "کیلو", price = 200.0, date = "2026-08-02", notes = "از شرکت نور"),
        today = "2026-08-28", now = 1_700_000_000_000, newId = ::newId,
      )
    )
    assertEquals(100.0, ShopStore.stock(r.data, "p1"), 0.0)

    val move = r.data.stockMovements.single()
    assertEquals("purchase_in", move.type)
    assertEquals(100.0, move.qty, 0.0)
    assertEquals("از شرکت نور", move.notes)
    assertEquals(r.id, move.refId)
    assertEquals("2026-08-02", move.date)
  }

  @Test
  fun `ورود بدون یادداشت هم متن روشنی در حرکت انبار می گذارد`() {
    val r = ok(
      WarehouseEngine.addEntry(
        base, WarehouseEngine.EntryDraft(productId = "p1", units = 10.0, price = 200.0),
        today = "2026-08-28", now = 0, newId = ::newId,
      )
    )
    assertEquals("ثبت ورود کالا", r.data.stockMovements.single().notes)
    assertEquals("2026-08-28", r.data.warehouseEntries.single().date)  // تاریخِ خالی یعنی امروز
  }

  @Test
  fun `ورود با تعداد صفر یا منفی رد می شود`() {
    val draft = WarehouseEngine.EntryDraft(productId = "p1", units = 0.0, price = 200.0)
    assertEquals(
      "تعداد واحد معتبر وارد کنید",
      failed(WarehouseEngine.addEntry(base, draft, "2026-08-28", 0, ::newId)),
    )
    assertEquals(
      "تعداد واحد معتبر وارد کنید",
      failed(WarehouseEngine.addEntry(base, draft.copy(units = -5.0), "2026-08-28", 0, ::newId)),
    )
  }

  @Test
  fun `ورود برای کالایی که نیست ثبت نمی شود`() {
    assertEquals(
      "ابتدا یک محصول انتخاب یا اضافه کنید",
      failed(
        WarehouseEngine.addEntry(
          base, WarehouseEngine.EntryDraft(productId = "نیست", units = 5.0, price = 10.0),
          "2026-08-28", 0, ::newId,
        )
      ),
    )
  }

  @Test
  fun `حذف ورودی موجودی و حرکتش را با هم می برد`() {
    val added = ok(
      WarehouseEngine.addEntry(
        base, WarehouseEngine.EntryDraft(productId = "p1", units = 40.0, price = 200.0),
        "2026-08-28", 0, ::newId,
      )
    )
    val removed = ok(WarehouseEngine.deleteEntry(added.data, added.id, "2026-01-01", 1L, ::newId))
    assertEquals(0.0, ShopStore.stock(removed.data, "p1"), 0.0)
    assertTrue(removed.data.stockMovements.isEmpty())
  }

  /* ------------------------- اصلاحِ موجودی ------------------------- */

  @Test
  fun `اصلاح موجودی کم می کند و دلیلش می ماند`() {
    val stocked = ok(
      WarehouseEngine.addEntry(
        base, WarehouseEngine.EntryDraft(productId = "p1", units = 50.0, price = 200.0),
        "2026-08-28", 0, ::newId,
      )
    ).data

    val r = ok(
      WarehouseEngine.adjustStock(
        stocked, "p1", quantity = 3.0, increase = false, reason = "خراب شد",
        kind = WarehouseEngine.AdjustKind.ADJUSTMENT,
        today = "2026-08-28", now = 0, newId = ::newId,
      )
    )
    assertEquals(47.0, ShopStore.stock(r.data, "p1"), 0.0)

    val entry = r.data.warehouseEntries.first { it.id == r.id }
    assertTrue("ردیفِ اصلاح باید نشانه‌دار باشد", entry.isAdjustment)
    assertEquals(-3.0, entry.units, 0.0)
    assertEquals(0.0, entry.price, 0.0)   // اصلاح ارزشِ انبار را بالا نمی‌برد

    val audit = r.data.auditLog.single()
    assertEquals("stock_adjustment", audit.type)
    assertEquals("اصلاح موجودی «برنج» به مقدار −3 — دلیل: خراب شد", audit.notes)
  }

  @Test
  fun `برگشت به تأمین کننده نوع خودش را می گیرد`() {
    val stocked = ok(
      WarehouseEngine.addEntry(
        base, WarehouseEngine.EntryDraft(productId = "p1", units = 5.0, price = 200.0),
        "2026-08-28", 0, ::newId,
      )
    ).data
    val r = ok(
      WarehouseEngine.adjustStock(
        stocked, "p1", 2.0, increase = false, reason = "معیوب بود",
        kind = WarehouseEngine.AdjustKind.SUPPLIER_RETURN,
        today = "2026-08-28", now = 0, newId = ::newId,
      )
    )
    assertEquals("supplier_return", r.data.stockMovements.last().type)
    assertEquals("supplier_return", r.data.auditLog.last().type)
    assertTrue(r.data.auditLog.last().notes.startsWith("برگشت به تأمین‌کننده «برنج»"))
  }

  @Test
  fun `کاهش بیش از موجودی پذیرفته نمی شود`() {
    val msg = failed(
      WarehouseEngine.adjustStock(
        base, "p1", 2.0, increase = false, reason = "شمارش",
        kind = WarehouseEngine.AdjustKind.ADJUSTMENT, today = "2026-08-28", now = 0, newId = ::newId,
      )
    )
    assertTrue(msg, msg.contains("موجود است"))
  }

  @Test
  fun `اصلاح بدون دلیل ثبت نمی شود`() {
    assertEquals(
      "دلیل اصلاح را بنویسید",
      failed(
        WarehouseEngine.adjustStock(
          base, "p1", 3.0, true, "   ", WarehouseEngine.AdjustKind.ADJUSTMENT, "2026-08-28", 0, ::newId,
        )
      ),
    )
  }

  /* ---------------------------- حذفِ کالا ---------------------------- */

  @Test
  fun `حذف کالا ورودی هایش را می برد ولی فاکتورها را نه`() {
    val stocked = ok(
      WarehouseEngine.addEntry(
        base, WarehouseEngine.EntryDraft(productId = "p1", units = 10.0, price = 200.0),
        "2026-08-28", 0, ::newId,
      )
    ).data
    val afterSale = (SalesEngine.record(
      stocked, listOf(SalesEngine.CartLine("p1", 2.0)), SalesEngine.Checkout(),
      "2026-08-28", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    val r = ok(WarehouseEngine.deleteProduct(afterSale, "p1", "2026-08-28", 0, ::newId))
    assertTrue(r.data.products.isEmpty())
    assertTrue(r.data.warehouseEntries.isEmpty())
    // فاکتور و اقلامش سرِ جایشان — سابقهٔ فروش حقیقتی است که افتاده
    assertEquals(1, r.data.sales.size)
    assertEquals(1, r.data.saleItems.size)
    assertEquals("حذف محصول «برنج»", r.data.auditLog.last().notes)
  }

  @Test
  fun `پیش از حذف می گوید چقدر از این کالا فروخته شده`() {
    val stocked = ok(
      WarehouseEngine.addEntry(base, WarehouseEngine.EntryDraft(productId = "p1", units = 10.0, price = 200.0),
        "2026-08-28", 0, ::newId)
    ).data
    val sold = (SalesEngine.record(
      stocked, listOf(SalesEngine.CartLine("p1", 2.0)), SalesEngine.Checkout(), "2026-08-28", 0, ::newId,
    ) as SalesEngine.Result.Ok).data

    assertTrue(WarehouseEngine.deleteWarning(sold, "p1").contains("2 واحد فروخته"))
    assertTrue(WarehouseEngine.deleteWarning(base, "p1").contains("سوابق ورود آن"))
  }

  /* ---------------------------- خلاصهٔ انبار ---------------------------- */

  @Test
  fun `خلاصهٔ انبار همان جمع های نسخهٔ وب را می دهد`() {
    var d = ok(
      WarehouseEngine.addEntry(
        base, WarehouseEngine.EntryDraft(productId = "p1", cartons = 4.0, perCarton = 25.0, units = 100.0, price = 200.0),
        "2026-08-28", 0, ::newId,
      )
    ).data
    d = ok(
      WarehouseEngine.addProduct(d, WarehouseEngine.ProductDraft(name = "روغن", salePrice = 500.0), 0, ::newId)
    ).data

    val s = WarehouseEngine.summary(d)
    assertEquals(2, s.products)
    assertEquals(4.0, s.cartons, 0.0)
    assertEquals(100.0, s.units, 0.0)
    assertEquals(20_000.0, s.value, 0.0)
    assertEquals(1, s.out)      // روغن هیچ ورودی ندارد
    assertEquals(0, s.low)
  }

  @Test
  fun `موجودی کم و تمام شده درست تشخیص داده می شود`() {
    val d = ok(
      WarehouseEngine.addEntry(base, WarehouseEngine.EntryDraft(productId = "p1", units = 4.0, price = 200.0),
        "2026-08-28", 0, ::newId)
    ).data
    // حدِ کم برای برنج ۵ است، موجودی ۴ → کم
    assertEquals("low", ShopStore.stockStatus(d, d.products.single()))
    assertEquals(1, WarehouseEngine.summary(d).low)

    val empty = ok(WarehouseEngine.deleteEntry(d, d.warehouseEntries.single().id, "2026-01-01", 1L, ::newId)).data
    assertEquals("out", ShopStore.stockStatus(empty, empty.products.single()))
  }

  @Test
  fun `کالای پیدا نشده پیام روشن می دهد`() {
    assertEquals("کالا پیدا نشد", failed(WarehouseEngine.deleteProduct(base, "نیست", "2026-08-28", 0, ::newId)))
    assertNull(base.products.find { it.id == "نیست" })
  }

  /* ------------- حدهای موجودی: نقطهٔ سفارش و بیشینه (INV-12) ------------- */

  private fun draftOf(p: Product, reorder: Double?, max: Double?, min: Double = p.minStock) = WarehouseEngine.ProductDraft(
    name = p.name, unit = p.unit, purchasePrice = p.purchasePrice, salePrice = p.salePrice, minStock = min,
    barcode = p.barcodes.firstOrNull().orEmpty(), editLevels = true, reorderPoint = reorder, maxStock = max,
  )

  @Test
  fun `نقطه سفارش و بیشینه با فرم ثبت و در سابقه دیده می شود`() {
    val p1 = base.products.single()
    val r = ok(WarehouseEngine.editProduct(base, "p1", draftOf(p1, 12.0, 40.0), "2026-08-28", 7, ::newId))
    val p = r.data.products.single()
    assertEquals(12.0, p.reorderPoint!!, 0.0)
    assertEquals(40.0, p.maxStock!!, 0.0)
    val note = r.data.auditLog.single { it.type == "product_edit" }.notes
    assertTrue(note, note.contains("نقطهٔ سفارش — ⇐ 12") && note.contains("حداکثر موجودی — ⇐ 40"))
  }

  @Test
  fun `ترتیب حدها سنجیده می شود و فرم بی حدها آن ها را دست نمی زند`() {
    val p1 = base.products.single()
    //  کمینه ۵ و نقطهٔ سفارش ۳ ⇒ رد
    assertEquals("نقطهٔ سفارش کمتر از حدِ کم بودن است", failed(WarehouseEngine.editProduct(base, "p1", draftOf(p1, 3.0, null), "2026-08-28", 7, ::newId)))
    assertEquals("بیشینه باید بیشتر از نقطهٔ سفارش باشد", failed(WarehouseEngine.editProduct(base, "p1", draftOf(p1, 10.0, 10.0), "2026-08-28", 7, ::newId)))
    assertEquals("عددِ حدِ موجودی درست نیست", failed(WarehouseEngine.editProduct(base, "p1", draftOf(p1, Double.NaN, null), "2026-08-28", 7, ::newId)))
    val withLevels = ok(WarehouseEngine.editProduct(base, "p1", draftOf(p1, 12.0, 40.0), "2026-08-28", 7, ::newId)).data
    //  فرمِ امروز (تنظیم خاموش) فقط نام را عوض می‌کند ⇒ نقطهٔ سفارش و بیشینه می‌مانند
    val plain = WarehouseEngine.ProductDraft(name = "برنج اعلا", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0, minStock = 5.0, barcode = "111")
    val after = ok(WarehouseEngine.editProduct(withLevels, "p1", plain, "2026-08-29", 8, ::newId)).data.products.single()
    assertEquals(12.0, after.reorderPoint!!, 0.0)
    assertEquals(40.0, after.maxStock!!, 0.0)
    //  کالای بی حدِ تازه: همان رفتارِ امروز، فیلدها null و در JSON نوشته نمی‌شوند
    val json = kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(Product.serializer(), p1)
    assertTrue(json, !json.contains("reorderPoint") && !json.contains("maxStock"))
  }

  @Test
  fun `ویرایش گروهی حدها ردیف ناجور را عوض نمی کند و بقیه را می کند`() {
    val d = base.copy(
      products = base.products + Product(id = "p2", name = "روغن", unit = "عدد", minStock = 30.0) +
        Product(id = "p3", name = "قند", unit = "کیلو"),
    )
    val patch = ir.vil3ntec.tohid.core.stock.StockLevelRules.Patch(
      reorder = ir.vil3ntec.tohid.core.stock.StockLevelRules.Edit.To(10_000),
      max = ir.vil3ntec.tohid.core.stock.StockLevelRules.Edit.To(50_000),
    )
    val plan = WarehouseEngine.previewLevels(d, setOf("p1", "p2", "p3"), patch)
    assertEquals(listOf("p2", "p1", "p3"), plan.map { it.id })   // روغن (کمینه ۳۰ > ۱۰) ناموفق و اول
    val r = ok(WarehouseEngine.bulkLevels(d, setOf("p1", "p2", "p3"), patch, "2026-08-28", 9, ::newId)).data
    val by = r.products.associateBy { it.id }
    assertEquals(10.0, by.getValue("p1").reorderPoint!!, 0.0)
    assertEquals(5.0, by.getValue("p1").minStock, 0.0)            // کمینه دست نخورد
    assertNull(by.getValue("p2").reorderPoint)
    assertEquals(50.0, by.getValue("p3").maxStock!!, 0.0)
    assertEquals(2, r.auditLog.count { it.type == "product_edit" })
  }

  @Test
  fun `فهرست نیاز به سفارش با پیشنهاد تا بیشینه`() {
    val d = ShopData(
      products = listOf(
        Product(id = "a", name = "الف", unit = "عدد", minStock = 2.0, reorderPoint = 10.0, maxStock = 40.0),  // موجودی ۸ ⇒ وقتِ سفارش
        Product(id = "b", name = "ب", unit = "عدد", minStock = 5.0),                                       // موجودی ۳ ⇒ زیرِ کمینه، بی بیشینه
        Product(id = "c", name = "پ", unit = "عدد", reorderPoint = 5.0, maxStock = 20.0),                   // موجودی ۱۵ ⇒ کافی
        Product(id = "x", name = "ت", unit = "عدد"),                                                       // بی حد ⇒ نمی‌آید
        Product(id = "z", name = "ث", unit = "عدد", reorderPoint = 5.0, active = false),                    // غیرفعال ⇒ نمی‌آید
      ),
      warehouseEntries = listOf(
        WarehouseEntry(id = "e1", productId = "a", units = 8.0),
        WarehouseEntry(id = "e2", productId = "b", units = 3.0),
        WarehouseEntry(id = "e3", productId = "c", units = 15.0),
      ),
    )
    val due = WarehouseEngine.reorderList(d)
    assertEquals(listOf("b", "a"), due.map { it.product.id })     // زیرِ کمینه پیش از وقتِ سفارش
    assertNull(due[0].suggest)
    assertEquals(32.0, due[1].suggest!!, 0.0)
    assertEquals(ir.vil3ntec.tohid.core.stock.StockLevelRules.State.REORDER, due[1].state)
    //  همان فهرست در برگهٔ اکسلِ کامپیوتر
    val sheet = ReportCsv.of("reorder", d, "", "", "2026-08-28")
    assertEquals(listOf("ب", "بیشینه تعریف نشده"), sheet.rows[1].let { listOf(it[0], it[8]) })
    assertEquals(listOf("الف", "وقتِ سفارش", "32"), sheet.rows[2].let { listOf(it[0], it[7], it[8]) })
  }
}
