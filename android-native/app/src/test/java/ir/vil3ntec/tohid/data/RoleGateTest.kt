package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.perm.PermCore
import ir.vil3ntec.tohid.core.perm.RoleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  سدِ نقش پیش از نوشتنِ دفتر (A16) — همان `canWrite`ی که سرور در هر سه راهِ
 *  همگام‌سازی اجرا می‌کند. و آزمونِ مرجع: صاحب، مدیر و شاگرد هرگز بسته نمی‌شوند.
 */
class RoleGateTest {

  private val base = ShopData(
    products = listOf(Product(id = "p1", name = "برنج")),
    sales = listOf(Sale(id = "s0")),
  )
  private val sale = base.copy(
    sales = base.sales + Sale(id = "s1"),
    saleItems = listOf(SaleItem(id = "si1", saleId = "s1", productId = "p1")),
    stockMovements = listOf(StockMovement(id = "m1", productId = "p1", type = "sale", qty = -1.0, refId = "s1")),
    auditLog = listOf(AuditEntry(id = "a1")),
    nextInvoiceNo = 2,
  )
  private val purchase = base.copy(
    purchases = listOf(Purchase(id = "pu1", productId = "p1")),
    stockMovements = listOf(StockMovement(id = "m2", productId = "p1")),
  )
  private val expense = base.copy(expenses = listOf(Expense(id = "e1")))
  private val product = base.copy(products = base.products + Product(id = "p2", name = "روغن"))

  @Test fun changedTablesNamesSyncTables() {
    assertEquals(listOf("sales", "saleItems", "stockMovements", "auditLog"), RoleGate.changedTables(base, sale))
    assertEquals(listOf("purchases", "stockMovements"), RoleGate.changedTables(base, purchase))
    assertEquals(emptyList<String>(), RoleGate.changedTables(base, base.copy(nextInvoiceNo = 9)))
    //  هر نامی که برمی‌گردد یکی از جدول‌های همگام‌سازی است که هسته می‌شناسد
    val all = ShopData(
      debtors = listOf(Debtor(id = "d")), transactions = listOf(DebtTransaction(id = "t")),
      expenses = listOf(Expense(id = "e")), expenseCategories = listOf("x"), products = listOf(Product(id = "p")),
      productCategories = listOf("c"), productUnits = listOf("u"), warehouseEntries = listOf(WarehouseEntry(id = "w")),
      sales = listOf(Sale(id = "s")), saleItems = listOf(SaleItem(id = "i")), saleReturns = listOf(SaleReturn(id = "r")),
      suppliers = listOf(Supplier(id = "sp")), purchases = listOf(Purchase(id = "pu")),
      supplierPayments = listOf(SupplierPayment(id = "spp")), stockMovements = listOf(StockMovement(id = "m")),
      priceHistory = listOf(PriceChange(id = "ph")), auditLog = listOf(AuditEntry(id = "a")),
    )
    val names = RoleGate.changedTables(ShopData(expenseCategories = emptyList(), productUnits = emptyList()), all)
    assertEquals(17, names.size)
    for (n in names) assertTrue(n, PermCore.canWrite("owner", n) && RoleGate.tableTitle(n) != n)
  }

  @Test fun legacyRolesNeverBlocked() {
    for (role in listOf("owner", "manager", "staff")) {
      for (next in listOf(sale, purchase, expense, product)) assertNull(role, RoleGate.blocked(role, base, next))
    }
    //  نقشی که این نسخه نمی‌شناسد این‌جا بسته نمی‌شود؛ سرور می‌سنجد
    assertNull(RoleGate.blocked("future_role", base, purchase))
  }

  @Test fun newRolesBlockedOutsideTheirTables() {
    assertNull(RoleGate.blocked("cashier", base, sale))
    assertEquals("purchases", RoleGate.blocked("cashier", base, purchase))
    assertEquals("expenses", RoleGate.blocked("cashier", base, expense))
    assertEquals("products", RoleGate.blocked("cashier", base, product))

    assertEquals("sales", RoleGate.blocked("storekeeper", base, sale))
    assertNull(RoleGate.blocked("storekeeper", base, purchase))
    assertNull(RoleGate.blocked("storekeeper", base, product))
    assertEquals("expenses", RoleGate.blocked("storekeeper", base, expense))

    //  برگشتِ ضایعات (#318): ردیفِ برگشت + اصلاحِ موجودی در رسیدِ انبار + حرکتِ انبار
    val ret = SaleReturn(id = "r1", saleId = "s0", productId = "p1", quantity = 2.0, docId = "doc-1")
    val dmg = WarehouseEntry(id = "w1", productId = "p1", units = -2.0, isAdjustment = true, adjKind = "damage", refId = "doc-1")
    val damagedReturn = base.copy(
      saleReturns = listOf(ret),
      warehouseEntries = listOf(dmg),
      stockMovements = listOf(
        StockMovement(id = "m3", productId = "p1", type = "customer_return", qty = 2.0, refId = "r1"),
        StockMovement(id = "m4", productId = "p1", type = "damage", qty = -2.0, refId = "r1"),
      ),
    )
    assertNull(RoleGate.blocked("cashier", base, damagedReturn))
    assertEquals("returns", RoleGate.blocked("storekeeper", base, damagedReturn))
    //  برگشتِ بی‌رسید با کالای سالم: همان، با نوعِ customer_return و مقدارِ مثبت
    val restock = damagedReturn.copy(warehouseEntries = listOf(dmg.copy(id = "w2", units = 2.0, adjKind = "customer_return", refId = "r1")))
    assertNull(RoleGate.blocked("cashier", base, restock))
    //  رسیدِ انبارِ دستی، نوعِ اصلاحِ دیگر، یا اشاره به برگشتی که نیست ⇒ بسته
    val manual = base.copy(warehouseEntries = listOf(WarehouseEntry(id = "w3", productId = "p1", units = 10.0)))
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", base, manual))
    fun withEntry(e: WarehouseEntry) = damagedReturn.copy(warehouseEntries = listOf(e))
    for (bad in listOf(
      dmg.copy(adjKind = "count"),                          //  نوعِ دیگر
      dmg.copy(refId = "doc-404"),                          //  برگشتی که نیست
      dmg.copy(isAdjustment = false),                       //  رسیدِ دستی با برچسبِ برگشت (بازبینیِ #336، یافتهٔ ۲)
      dmg.copy(price = 70.0),                               //  با قیمت
      dmg.copy(productId = "p2"),                           //  کالایی که در برگشت نیست
      dmg.copy(units = -500.0),                             //  بیش از قلم
      dmg.copy(units = 2.0),                                //  ضایعاتِ مثبت
    )) assertEquals(bad.toString(), "warehouseEntries", RoleGate.blocked("cashier", base, withEntry(bad)))
    //  هر قلم یک اصلاح
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", base,
      damagedReturn.copy(warehouseEntries = listOf(dmg, dmg.copy(id = "w1b")))))
    //  برگشتِ ثبت‌شده (نه همین ذخیره) پایهٔ اصلاحِ تازه نیست
    val old = base.copy(saleReturns = listOf(ret))
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", old, old.copy(warehouseEntries = listOf(dmg))))
    //  ردیفِ موجود را صندوق‌دار نه ویرایش می‌کند نه حذف
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", damagedReturn, damagedReturn.copy(warehouseEntries = emptyList())))
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", damagedReturn,
      damagedReturn.copy(warehouseEntries = damagedReturn.warehouseEntries.map { it.copy(units = -1.0) })))
    //  حرکتِ انبار فقط از فاکتور و برگشت (یافتهٔ ۴): اصلاح یا خریدِ آزاد، یا فاکتوری که نیست ⇒ بسته
    for (bad in listOf(
      StockMovement(id = "mx", productId = "p1", type = "adjustment", qty = 1000.0),
      StockMovement(id = "mx", productId = "p1", type = "purchase_in", qty = 5.0, refId = "s0"),
      StockMovement(id = "mx", productId = "p1", type = "sale", qty = -1.0, refId = "s404"),
      StockMovement(id = "mx", productId = "p1", type = "sale", qty = 1.0, refId = "s0"),
    )) assertEquals(bad.toString(), "stockMovements", RoleGate.blocked("cashier", base, base.copy(stockMovements = listOf(bad))))
    assertNull(RoleGate.blocked("cashier", base, base.copy(stockMovements = listOf(
      StockMovement(id = "mc", productId = "p1", type = "sale_cancel", qty = 1.0, refId = "s0")))))
    assertEquals("stockMovements", RoleGate.blocked("cashier", sale, sale.copy(stockMovements = emptyList())))
    //  انباردار رسیدِ دستی می‌نویسد؛ شاگرد و مدیر هم (بی قاعدهٔ ردیف)
    assertNull(RoleGate.blocked("storekeeper", base, manual))
    assertNull(RoleGate.blocked("staff", base, manual))

    assertEquals("sales", RoleGate.blocked("picker", base, sale))
    assertNull(RoleGate.blocked("picker", base, base.copy(auditLog = listOf(AuditEntry(id = "a")))))

    for (next in listOf(sale, purchase, expense, product)) assertTrue(RoleGate.blocked("accountant", base, next) != null)
    assertNull(RoleGate.blocked("accountant", base, base))
  }

  /** شرحِ فارسیِ نقش‌ها با هسته می‌خواند */
  @Test fun roleInfoMatchesCore() {
    for (role in PermCore.ROLES) assertTrue(role, RoleInfo.of(role) != null)
    assertTrue(PermCore.ROLES.containsAll(RoleInfo.PICKABLE))
    assertEquals(
      listOf("صندوق‌دار", "انباردار", "جمع‌آور", "حسابدار"),
      listOf("cashier", "storekeeper", "picker", "accountant").map(RoleInfo::title),
    )
    assertEquals("کارمند", RoleInfo.title("janitor"))
    //  «فقط‌خواندنی» راست است: حسابدار هیچ جدولی نمی‌نویسد ولی عدد و سابقه را می‌بیند
    assertEquals(emptyList<String>(), PermCore.writable("accountant"))
    assertTrue(PermCore.allows("accountant", null, "reports.money"))
    assertTrue(PermCore.allows("accountant", null, "audit.view"))
    //  راستیِ شرح: عددهای مالی فقط نمایشی است (`display`)، پس هیچ «نمی‌تواند»ی از آن نمی‌گوید
    assertTrue(PermCore.CATALOG.getValue("reports.money").display)
    for (role in listOf("staff", "cashier", "storekeeper", "picker")) {
      val info = RoleInfo.of(role)!!
      assertTrue(role, info.cannot.none { "مالی" in it })
      assertEquals(role, RoleInfo.MONEY_HIDDEN_NOTE, info.note)
      assertTrue(role, !PermCore.allows(role, null, "reports.money"))
    }
    //  صندوق‌دار برگشت می‌زند، انباردار و جمع‌آور نه
    assertTrue(PermCore.allows("cashier", null, "returns.create"))
    assertTrue(!PermCore.allows("storekeeper", null, "returns.create"))
  }
}
