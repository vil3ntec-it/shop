package ir.vil3ntec.tohid.migrate

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import ir.vil3ntec.tohid.db.AuditLog
import ir.vil3ntec.tohid.db.Barcode
import ir.vil3ntec.tohid.db.CashShift
import ir.vil3ntec.tohid.db.Currency
import ir.vil3ntec.tohid.db.Customer
import ir.vil3ntec.tohid.db.CustomerLedgerEntry
import ir.vil3ntec.tohid.db.Device
import ir.vil3ntec.tohid.db.Price
import ir.vil3ntec.tohid.db.PriceList
import ir.vil3ntec.tohid.db.Product
import ir.vil3ntec.tohid.db.Sale
import ir.vil3ntec.tohid.db.SaleLine
import ir.vil3ntec.tohid.db.SalePayment
import ir.vil3ntec.tohid.db.SaleReturn
import ir.vil3ntec.tohid.db.Shop
import ir.vil3ntec.tohid.db.ShopDb
import ir.vil3ntec.tohid.db.StockMove
import ir.vil3ntec.tohid.db.Warehouse
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 *  قاعده‌های ثابتِ بخشِ ۳.۴ — هرکدام باید در خودِ پایگاه بسته باشد، نه فقط در کد.
 */
class SchemaTest {

  private lateinit var driver: JdbcSqliteDriver
  private lateinit var db: ShopDb
  private val g = HashChain.GENESIS

  @BeforeTest fun open() {
    driver = DbSettings.openInMemory()
    ShopDb.Schema.create(driver)
    db = ShopDb(driver)
    db.shopQueries.insertCurrency(Currency("AFN", 2, 100))
    db.shopQueries.insertShop(Shop("S", "", "AFN", "Asia/Kabul", 0, 0))
    db.shopQueries.insertDevice(Device("D", "S", "K1", "", 1, 0))
    db.shopQueries.insertWarehouse(Warehouse("W", "S", "", 0))
    db.catalogQueries.insertProduct(Product("P", "S", "rice", "", "", null, 0, 0, null, "", 0, 1, 0))
    db.customersQueries.insertCustomer(Customer("C", "S", "x", "", "", 0))
  }

  @AfterTest fun close() = driver.close()

  private fun rejects(message: String, block: () -> Unit) {
    val e = assertFailsWith<Exception> { block() }
    assertTrue(generateSequence<Throwable>(e) { it.cause }.any { it.message.orEmpty().contains(message) }, "${e.message}")
  }

  private fun move(id: String, qty: Long, kind: String = "receipt", reason: String = "", device: String = "D") =
    StockMove(id, "S", device, "W", "P", kind, qty, null, null, null, reason, "2024-04-10", 0)

  private fun draft(id: String) =
    Sale(id, "S", "D", 1, "K1-000001", "draft", "AFN", 1000, 0, 1000, "C", "", null, "2024-04-10", 0, null, null, null, null, null)

  private fun line(id: String, sale: String) = SaleLine(id, sale, 1, "P", "", 2000, 500, 1000, 400)

  private fun finalSale(id: String = "A"): String {
    db.salesQueries.insertSale(draft(id))
    db.salesQueries.insertSaleLine(line("L$id", id))
    db.salesQueries.insertSalePayment(SalePayment("PAY$id", id, "cash", 1000, "AFN", 0))
    val s = db.salesQueries.saleChainOf("D").executeAsList()
    val prev = s.lastOrNull()?.hash ?: g
    val row = draft(id)
    val hash = HashChain.hash(prev, Docs.sale(row, listOf(line("L$id", id)), listOf(SalePayment("PAY$id", id, "cash", 1000, "AFN", 0))))
    db.salesQueries.finalizeSale(0, s.size + 1L, prev, hash, id)
    return hash
  }

  @Test fun stockMoveIsAppendOnlyAndStockIsTheSum() {
    db.stockQueries.insertStockMove(move("m1", 5_000))
    db.stockQueries.insertStockMove(move("m2", -1_250, "sale"))
    assertEquals(3_750L, db.stockQueries.stockOf("P").executeAsOne())
    rejects("append-only") { driver.execute(null, "UPDATE stockMove SET qtyMilli = 1 WHERE id = 'm1'", 0) }
    rejects("append-only") { driver.execute(null, "DELETE FROM stockMove WHERE id = 'm1'", 0) }
    rejects("CHECK") { db.stockQueries.insertStockMove(move("m3", -1, "adjustment", "  ")) }
    db.stockQueries.insertStockMove(move("m4", -1, "adjustment", "count"))
  }

  @Test fun finalSaleIsFrozenAndCancelNeedsReason() {
    finalSale("A")
    rejects("frozen") { db.salesQueries.insertSaleLine(line("L2", "A")) }
    rejects("frozen") { driver.execute(null, "UPDATE saleLine SET qtyMilli = 1 WHERE id = 'LA'", 0) }
    rejects("frozen") { driver.execute(null, "DELETE FROM salePayment WHERE id = 'PAYA'", 0) }
    rejects("only change allowed") { driver.execute(null, "UPDATE sale SET totalMinor = 1 WHERE id = 'A'", 0) }
    rejects("never deleted") { driver.execute(null, "DELETE FROM sale WHERE id = 'A'", 0) }
    rejects("CHECK") { db.salesQueries.cancelSale(" ", 5, "A") }
    db.salesQueries.cancelSale("wrong customer", 5, "A")
    rejects("only change allowed") { db.salesQueries.cancelSale("again", 6, "A") }
  }

  @Test fun saleIsBornDraftAndChainMustLink() {
    rejects("born as draft") { db.salesQueries.insertSale(draft("X").copy(status = "final")) }
    val first = finalSale("A")
    db.salesQueries.insertSale(draft("B").copy(number = 2))
    rejects("chain broken") { db.salesQueries.finalizeSale(0, 2, g, "f".repeat(64), "B") }
    rejects("chain broken") { db.salesQueries.finalizeSale(0, 3, first, "f".repeat(64), "B") }
    db.salesQueries.finalizeSale(0, 2, first, "f".repeat(64), "B")
    rejects("UNIQUE") { db.salesQueries.insertSale(draft("C")) }
  }

  @Test fun returnsOnlyOnFinalSaleAndOwnLine() {
    db.salesQueries.insertSale(draft("A"))
    db.salesQueries.insertSaleLine(line("LA", "A"))
    rejects("returns need") { db.salesQueries.insertSaleReturn(SaleReturn("R0", "A", "LA", 1000, 500, "", "2024-04-10", 0)) }
    db.salesQueries.finalizeSale(0, 1, g, HashChain.hash(g, mapOf("id" to "A")), "A")
    db.salesQueries.insertSaleReturn(SaleReturn("R1", "A", "LA", 1000, 500, "", "2024-04-10", 0))
    rejects("append-only") { driver.execute(null, "DELETE FROM saleReturn WHERE id = 'R1'", 0) }
    db.salesQueries.cancelSale("x", 1, "A")
    rejects("returns need") { db.salesQueries.insertSaleReturn(SaleReturn("R2", "A", "LA", 1000, 500, "", "2024-04-10", 0)) }
  }

  @Test fun ledgerIsAppendOnlyAndBalanceIsDerived() {
    fun e(id: String, kind: String, delta: Long) =
      CustomerLedgerEntry(id, "C", kind, delta, "AFN", null, "", "2024-04-10", 0)
    db.customersQueries.insertLedgerEntry(e("e1", "give", 10_000))
    db.customersQueries.insertLedgerEntry(e("e2", "receive", -2_500))
    rejects("CHECK") { db.customersQueries.insertLedgerEntry(e("e3", "give", -1)) }
    assertEquals(7_500L, db.customersQueries.balanceByCustomer().executeAsList().single().balanceMinor)
    rejects("append-only") { driver.execute(null, "UPDATE customerLedgerEntry SET deltaMinor = 0", 0) }
    rejects("append-only") { driver.execute(null, "DELETE FROM customerLedgerEntry", 0) }
  }

  @Test fun auditLogChainIsEnforced() {
    fun row(seq: Long, prev: String): AuditLog {
      val draft = AuditLog("a$seq", "S", "D", seq, "sale", null, 0, "", "2024-04-10", seq, prev, "")
      return draft.copy(hash = HashChain.hash(prev, Docs.audit(draft)))
    }
    val a1 = row(1, g)
    rejects("chain broken") { db.auditQueries.insertAudit(row(1, "1".repeat(64))) }
    db.auditQueries.insertAudit(a1)
    rejects("chain broken") { db.auditQueries.insertAudit(row(3, a1.hash)) }
    rejects("chain broken") { db.auditQueries.insertAudit(row(2, g)) }
    db.auditQueries.insertAudit(row(2, a1.hash))
    rejects("append-only") { driver.execute(null, "UPDATE auditLog SET notes = 'x'", 0) }
    rejects("append-only") { driver.execute(null, "DELETE FROM auditLog", 0) }
    val chain = db.auditQueries.auditChainOf("D").executeAsList().map { HashChain.Link(it.id, it.seq, it.prevHash, it.hash, Docs.audit(it)) }
    assertEquals(null, HashChain.firstBroken(chain))
  }

  @Test fun deactivatedDeviceAcceptsNothing() {
    db.shopQueries.deactivateDevice("D")
    rejects("deactivated") { db.stockQueries.insertStockMove(move("m1", 1)) }
    rejects("deactivated") { db.salesQueries.insertSale(draft("A")) }
    rejects("UNIQUE") { db.shopQueries.insertDevice(Device("D2", "S", "K1", "", 1, 0)) }
  }

  @Test fun barcodeUniqueInShopUnlessFlaggedDuplicate() {
    db.catalogQueries.insertProduct(Product("P2", "S", "tea", "", "", null, 0, 0, null, "", 0, 1, 0))
    db.catalogQueries.insertBarcode(Barcode("b1", "S", "123", "P", 0))
    rejects("UNIQUE") { db.catalogQueries.insertBarcode(Barcode("b2", "S", "123", "P2", 0)) }
    db.catalogQueries.insertBarcode(Barcode("b3", "S", "456", "P", 1))
    db.catalogQueries.insertBarcode(Barcode("b4", "S", "456", "P2", 1))
    assertEquals(2, db.catalogQueries.productByBarcode("S", "456").executeAsList().size)
  }

  @Test fun priceHistoryNeverOverlapsAndOnlyClosesOpenRange() {
    db.catalogQueries.insertPriceList(PriceList("R", "S", "retail", "", "AFN"))
    db.catalogQueries.insertPrice(Price("p1", "R", "P", 1000, 0, 100))
    db.catalogQueries.insertPrice(Price("p2", "R", "P", 1200, 100, null))
    rejects("overlap") { db.catalogQueries.insertPrice(Price("p3", "R", "P", 1300, 150, null)) }
    rejects("history") { driver.execute(null, "UPDATE price SET amountMinor = 1 WHERE id = 'p2'", 0) }
    rejects("history") { db.catalogQueries.closePrice(120, "p1") }
    db.catalogQueries.closePrice(200, "p2")
    db.catalogQueries.insertPrice(Price("p4", "R", "P", 1300, 200, null))
    rejects("history") { driver.execute(null, "DELETE FROM price WHERE id = 'p1'", 0) }
  }

  @Test fun closedShiftIsFrozen() {
    db.cashQueries.insertCashShift(CashShift("sh", "S", "D", 0, 50_000, "", "2024-04-10", null, null, null, ""))
    rejects("CHECK") { db.cashQueries.closeShift(10, null, null, "", "sh") }
    db.cashQueries.closeShift(10, 49_000, 50_000, "short 10", "sh")
    rejects("frozen") { db.cashQueries.closeShift(11, 50_000, 50_000, "fixed", "sh") }
    rejects("never deleted") { driver.execute(null, "DELETE FROM cashShift", 0) }
  }

  @Test fun foreignKeysAreEnforced() {
    rejects("FOREIGN KEY") { db.stockQueries.insertStockMove(move("m1", 1).copy(productId = "nope")) }
  }
}
