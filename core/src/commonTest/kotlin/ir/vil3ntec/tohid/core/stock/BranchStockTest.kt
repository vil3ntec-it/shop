package ir.vil3ntec.tohid.core.stock

import ir.vil3ntec.tohid.core.stock.BranchStock.Qty
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** POS-16/S17: فقط عدد از شعبه‌های دیگر؛ پرچمِ خاموش = درِ ناشناخته؛ مجوزِ صاحب. */
class BranchStockTest {

  private val b = "brn_" + "b".repeat(24)

  @Test fun parsesOnlyBranchIdNameAndQty() {
    val body = Json.parseToJsonElement(
      """{"branches":[{"branchId":"","branchName":"شعبهٔ اصلی","qty":4000},
        {"branchId":"$b","branchName":"ب","qty":-3000,"cost":99},{"branchId":"brn_bad","branchName":"x","qty":1},
        {"branchId":"$b","branchName":"ب"},{"branchName":"y","qty":2}]}""",
    ).jsonObject
    assertEquals(listOf(Qty("", "شعبهٔ اصلی", 4000), Qty(b, "ب", 0)), BranchStock.parse(body))
    assertEquals(emptyList(), BranchStock.parse(null))
    assertEquals(emptyList(), BranchStock.parse(Json.parseToJsonElement("""{"branches":"x"}""").jsonObject))
  }

  @Test fun callCarriesOnlyOwnBranchHeader() {
    val c = BranchStock.call(BranchTransfer.MAIN, "tea 1/2")
    assertEquals("GET", c.method)
    assertEquals("/shop/catalog/products/tea%201%2F2/other-branches", c.path)
    assertEquals(mapOf("X-Branch-Id" to "main"), c.headers)
    assertEquals(mapOf("X-Branch-Id" to b), BranchStock.call(b, "p1").headers)
  }

  @Test fun unknownDoorMeansOffAndWaitsBeforeAskingAgain() {
    assertTrue(BranchStock.off(404, "not_found"))
    assertFalse(BranchStock.off(404, "product_not_found"))
    assertFalse(BranchStock.off(403, "branch_forbidden"))
    assertEquals(0L, BranchStock.nextAsk(0L, 5_000L))
    assertEquals(BranchStock.OFF_RECHECK_MS - 1_000L, BranchStock.nextAsk(10_000L, 11_000L))
    assertEquals(0L, BranchStock.nextAsk(10_000L, 10_000L + BranchStock.OFF_RECHECK_MS + 1))
  }

  @Test fun ownerGrantBodyAndMemberFlag() {
    assertEquals("""{"perms":["stock.view_other_branches"]}""", BranchStock.grantBody(true).toString())
    assertEquals("""{"perms":[]}""", BranchStock.grantBody(false).toString())
    assertEquals("/shop/branches/members/mem_1/perms", BranchStock.grantPath("mem_1"))
    assertTrue(BranchStock.granted(Json.parseToJsonElement("""{"perms":["stock.view_other_branches"]}""").jsonObject))
    assertFalse(BranchStock.granted(Json.parseToJsonElement("""{"branches":[""]}""").jsonObject))
  }
}
