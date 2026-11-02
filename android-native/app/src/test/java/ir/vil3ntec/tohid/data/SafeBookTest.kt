package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.safe.SafeCore
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  گاوصندوق در دفترِ دکان (`SafeBook`) و روی خطِ Sync v1 (پلهٔ ۴، مجموعهٔ `safeEntries`).
 *  قاعده‌های خودِ هسته در `SafeCoreTest` با بردارهای مشترک سنجیده می‌شوند.
 */
class SafeBookTest {

  private var n = 0
  private val ids = { "01SAFE${(++n).toString().padStart(20, '0')}" }

  private fun ok(r: SafeBook.Result): SafeBook.Result.Ok {
    assertTrue("$r", r is SafeBook.Result.Ok)
    return r as SafeBook.Result.Ok
  }

  private fun failed(r: SafeBook.Result, code: String): SafeBook.Result.Failed {
    assertTrue("$r", r is SafeBook.Result.Failed)
    assertEquals(code, (r as SafeBook.Result.Failed).code)
    return r
  }

  @Test fun `شمارش، واریز، تحویل به بانک و برداشت؛ مانده و تاریخچه`() {
    var d = ShopData()
    d = ok(SafeBook.count(d, 100_000, null, " صاحب ", 1_000, ids)).data
    d = ok(SafeBook.deposit(d, 20_000, "deposit", null, "صاحب", 2_000, ids)).data
    d = ok(SafeBook.toBank(d, 50_000, "K-17", "بانکِ ملی", "صاحب", 3_000, ids)).data
    d = ok(SafeBook.withdraw(d, 5_000, "petty_expense", null, "صاحب", 4_000, ids)).data
    val v = SafeBook.view(d)
    assertEquals(65_000L, v.balanceMinor)
    assertEquals(SafeCore.COUNT, v.lastCount?.entry?.kind)
    assertEquals(listOf(SafeCore.OUT, SafeCore.TO_BANK, SafeCore.IN, SafeCore.COUNT), v.history.map { it.entry.kind })
    val first = d.safeEntries.first()
    assertEquals("صاحب", first.by)
    assertEquals(0L, first.expectedMinor)
    assertEquals(1_000L, first.createdAt)
  }

  @Test fun `اختلافِ شمارش بیش از آستانه دلیل می‌خواهد و ثبت می‌شود، نه یکی`() {
    val d = ok(SafeBook.count(ShopData(), 100_000, null, "", 1_000, ids)).data
    val f = failed(SafeBook.count(d, 90_000, null, "", 2_000, ids), SafeCore.Code.REASON_REQUIRED)
    assertEquals(-10_000L, f.varianceMinor)
    assertTrue(f.message, f.message.contains("کسری") && f.message.contains("100"))
    val c = ok(SafeBook.count(d, 90_000, "پول خرد جدا شد", "", 2_000, ids))
    assertEquals(100_000L, c.entry.expectedMinor)
    assertEquals(-10_000L, SafeBook.view(c.data).lastCount?.varianceMinor)
    assertEquals(90_000L, SafeBook.view(c.data).balanceMinor)
  }

  @Test fun `خطاهای ورودی پیامِ فارسی دارند و دفتر را عوض نمی‌کنند`() {
    val d = ok(SafeBook.count(ShopData(), 1_000, null, "", 1_000, ids)).data
    failed(SafeBook.withdraw(d, 1_001, "owner_withdrawal", null, "", 2_000, ids), SafeCore.Code.INSUFFICIENT_FUNDS)
    failed(SafeBook.toBank(d, 500, "  ", null, "", 2_000, ids), SafeCore.Code.BAG_REQUIRED)
    failed(SafeBook.deposit(d, 500, "other", " ", "", 2_000, ids), SafeCore.Code.NOTE_REQUIRED)
    val bad = failed(SafeBook.deposit(d, 0, "deposit", null, "", 2_000, ids), SafeCore.Code.INVALID_AMOUNT)
    assertFalse(bad.message.contains("INVALID"))
    assertEquals(1, d.safeEntries.size)
  }

  @Test fun `تکرارِ همان عملیات (همان شناسه) دفتر را عوض نمی‌کند`() {
    val d = ok(SafeBook.count(ShopData(), 1_000, null, "", 1_000) { "same-id" }).data
    val again = ok(SafeBook.count(d, 1_000, null, "", 5_000) { "same-id" })
    assertTrue(again.duplicate)
    assertSame(d, again.data)
    failed(SafeBook.count(d, 2_000, null, "", 5_000) { "same-id" }, SafeCore.Code.ID_CONFLICT)
  }

  @Test fun `روی خط — مجموعهٔ پلهٔ ۴، مبلغِ صحیح، بی کلیدِ null، رفت‌وبرگشتِ بی‌کم‌وکاست`() {
    assertEquals(4, LedgerDiff.SCHEMA_VERSION)
    assertTrue("safeEntries" in LedgerDiff.COLLECTIONS)
    val d = ok(SafeBook.toBank(ok(SafeBook.count(ShopData(), 100_000, null, "", 1_000, ids)).data, 2_500, "K-1", null, "", 2_000, ids)).data
    val tree = LedgerDiff.toJson(d)
    val rows = tree["safeEntries"] as JsonArray
    val bank = rows[1] as JsonObject
    assertEquals(JsonPrimitive(2_500L), bank["amountMinor"])
    assertEquals("K-1", bank["bagNo"]?.jsonPrimitive?.content)
    assertFalse(bank.containsKey("shiftId") || bank.containsKey("expectedMinor") || bank.containsKey("note"))
    assertEquals(d, LedgerDiff.fromJson(tree))
    assertTrue(LedgerDiff.isModelField("safeEntries", "bagNo"))
    assertFalse(LedgerDiff.isModelField("safeEntries", "nope"))

    val ops = LedgerDiff.diff(LedgerDiff.shadowOf(ShopData()), tree).filter { it.table == "safeEntries" }
    assertEquals(listOf(SyncOp.INSERT, SyncOp.INSERT), ops.map { it.type })
    assertEquals(d.safeEntries.map { it.id }, ops.map { it.rowId })
    //  دفترِ بی مجموعه (ساختِ پیش از پلهٔ ۴) خوانده می‌شود و گاوصندوقِ خالی دارد
    val old = JsonObject(tree.filterKeys { it != "safeEntries" })
    assertEquals(emptyList<SafeEntry>(), LedgerDiff.fromJson(old).safeEntries)
  }

  @Test fun `opِ گاوصندوق از دستگاهِ دیگر در دفتر می‌نشیند`() {
    val other = ok(SafeBook.count(ShopData(), 7_000, null, "مدیر", 1_000, ids)).data
    val op = LedgerDiff.diff(LedgerDiff.shadowOf(ShopData()), LedgerDiff.toJson(other)).single { it.table == "safeEntries" }
    val mine = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(ShopData()), listOf(op)))
    assertEquals(other.safeEntries, mine.safeEntries)
    assertEquals(7_000L, SafeBook.view(mine).balanceMinor)
  }

  // ── انتقالِ دخل ⇄ گاوصندوق: دو ردیف با هم یا هیچ ─────────────────────

  /** صندوقِ باز با ۱٬۰۰۰ افغانی */
  private fun withShift(d: ShopData = ShopData()): ShopData =
    (CashShift.open(d, 1_000.0, "علی", "2026-11-01", 500) { "sh1" } as CashShift.Result.Ok).data

  @Test fun `از دخل به گاوصندوق — ردیفِ گاوصندوق با ردیفِ جفتِ خروجِ نقد`() {
    val d0 = withShift()
    val r = ok(SafeBook.transfer(d0, SafeCore.FROM_DRAWER, 40_000, " علی ", "2026-11-01", 1_000, "s-1", "a-1"))
    val safe = r.data.safeEntries.single()
    assertEquals(SafeCore.FROM_DRAWER, safe.kind)
    assertEquals(40_000L, safe.amountMinor)
    assertEquals("sh1", safe.shiftId)
    assertEquals("a-1", safe.auditId)
    assertEquals("علی", safe.by)
    val cash = r.data.auditLog.single { it.id == "a-1" }
    assertEquals("cash_out", cash.type)
    assertEquals("sh1", cash.refId)
    assertEquals(400.0, cash.amount, 0.0)
    assertEquals("other", cash.reason)
    assertEquals(SafeBook.DRAWER_NOTE, cash.note)
    assertEquals("2026-11-01", cash.date)
    assertTrue(cash.notes, cash.notes.contains("گاوصندوق") && cash.notes.contains("علی"))
    assertEquals(40_000L, SafeBook.view(r.data).balanceMinor)
    //  نقدِ موردِ انتظارِ دخل همان ۴۰۰ کم شد (هم صندوقِ ساده، هم pay-core)
    val shift = CashShift.current(r.data)!!
    assertEquals(600.0, CashShift.totalsOf(r.data, shift, 2_000).expected, 0.0)
    assertEquals(600.0, PayFlow.expectedCash(r.data, shift.open, 2_000), 0.0)
  }

  @Test fun `از گاوصندوق به دخل — ورودِ نقد، و بیش از مانده هیچ`() {
    var d = withShift(ok(SafeBook.count(ShopData(), 50_000, null, "", 100, ids)).data)
    failed(SafeBook.transfer(d, SafeCore.TO_DRAWER, 50_001, "", "2026-11-01", 1_000, "t-1", "a-1"), SafeCore.Code.INSUFFICIENT_FUNDS)
    d = ok(SafeBook.transfer(d, SafeCore.TO_DRAWER, 20_000, "", "2026-11-01", 1_000, "t-1", "a-1")).data
    assertEquals("cash_in", d.auditLog.single { it.id == "a-1" }.type)
    assertEquals(30_000L, SafeBook.view(d).balanceMinor)
    assertEquals(1_200.0, CashShift.totalsOf(d, CashShift.current(d)!!, 2_000).expected, 0.0)
  }

  @Test fun `خروج بیش از نقدِ دخل یا صندوقِ بسته هیچ ردیفی نمی‌نویسد`() {
    val d = withShift()
    val over = failed(SafeBook.transfer(d, SafeCore.FROM_DRAWER, 100_001, "", "2026-11-01", 1_000, "s-1", "a-1"), "INSUFFICIENT_CASH")
    assertTrue(over.message.isNotBlank())
    failed(SafeBook.transfer(ShopData(), SafeCore.FROM_DRAWER, 100, "", "2026-11-01", 1_000, "s-1", "a-1"), SafeBook.SHIFT_CLOSED)
    failed(SafeBook.transfer(d, SafeCore.TO_BANK, 100, "", "2026-11-01", 1_000, "s-1", "a-1"), SafeCore.Code.BAD_KIND)
    failed(SafeBook.transfer(d, SafeCore.FROM_DRAWER, 0, "", "2026-11-01", 1_000, "s-1", "a-1"), SafeCore.Code.INVALID_AMOUNT)
    assertTrue(d.safeEntries.isEmpty())
    assertEquals(1, d.auditLog.size)
  }

  @Test fun `تکرارِ همان انتقال (همان شناسه) ردیفِ دوم نمی‌سازد، حتی پس از بستنِ صندوق`() {
    val d = ok(SafeBook.transfer(withShift(), SafeCore.FROM_DRAWER, 10_000, "", "2026-11-01", 1_000, "s-1", "a-1")).data
    val again = ok(SafeBook.transfer(d, SafeCore.FROM_DRAWER, 10_000, "", "2026-11-01", 9_000, "s-1", "a-1"))
    assertTrue(again.duplicate)
    assertSame(d, again.data)
    failed(SafeBook.transfer(d, SafeCore.FROM_DRAWER, 20_000, "", "2026-11-01", 9_000, "s-1", "a-1"), SafeCore.Code.ID_CONFLICT)
    val closed = (CashShift.close(d, 900.0, "", "", "2026-11-01", 5_000) { "z1" } as CashShift.Result.Ok).data
    assertTrue(ok(SafeBook.transfer(closed, SafeCore.FROM_DRAWER, 10_000, "", "2026-11-01", 9_000, "s-1", "a-1")).duplicate)
  }
}
