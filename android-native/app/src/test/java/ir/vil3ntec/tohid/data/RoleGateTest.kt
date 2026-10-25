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
    stockMovements = listOf(StockMovement(id = "m1", productId = "p1")),
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
    val damagedReturn = base.copy(
      saleReturns = listOf(SaleReturn(id = "r1", docId = "doc-1")),
      warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = -1.0, isAdjustment = true, adjKind = "damage", refId = "doc-1")),
      stockMovements = listOf(StockMovement(id = "m3", productId = "p1")),
    )
    assertNull(RoleGate.blocked("cashier", base, damagedReturn))
    assertEquals("returns", RoleGate.blocked("storekeeper", base, damagedReturn))
    //  برگشتِ بی‌رسید با کالای سالم: همان، با نوعِ customer_return
    val restock = damagedReturn.copy(warehouseEntries = listOf(WarehouseEntry(id = "w2", productId = "p1", units = 1.0, adjKind = "customer_return", refId = "r1")))
    assertNull(RoleGate.blocked("cashier", base, restock))
    //  رسیدِ انبارِ دستی، نوعِ اصلاحِ دیگر، یا اشاره به برگشتی که نیست ⇒ بسته
    val manual = base.copy(warehouseEntries = listOf(WarehouseEntry(id = "w3", productId = "p1", units = 10.0)))
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", base, manual))
    val count = damagedReturn.copy(warehouseEntries = listOf(WarehouseEntry(id = "w4", productId = "p1", adjKind = "count", refId = "doc-1")))
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", base, count))
    val orphan = base.copy(warehouseEntries = listOf(WarehouseEntry(id = "w5", productId = "p1", adjKind = "damage", refId = "doc-404")))
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", base, orphan))
    //  ردیفِ موجود را صندوق‌دار نه ویرایش می‌کند نه حذف
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", damagedReturn, damagedReturn.copy(warehouseEntries = emptyList())))
    assertEquals("warehouseEntries", RoleGate.blocked("cashier", damagedReturn,
      damagedReturn.copy(warehouseEntries = damagedReturn.warehouseEntries.map { it.copy(units = -5.0) })))
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
