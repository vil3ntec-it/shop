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
}
