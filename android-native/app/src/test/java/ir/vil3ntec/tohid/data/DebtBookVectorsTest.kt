package ir.vil3ntec.tohid.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 *  دفترِ قرض‌داران — همان بردارهای مرجعی که `server/test/debt-book.test.js` برای
 *  `license/debt-book.js` می‌خواند (`license/test-vectors/debt-book-v1.json`).
 *  یک فرق میانِ دو سکو ⇒ این آزمون یا آن یکی می‌شکند.
 *
 *  همین پوشهٔ آزمون در CIِ اندروید (`app/`) و کامپیوتر (`desktop/`) اجرا می‌شود.
 */
class DebtBookVectorsTest {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private val v: JsonObject = run {
    val f = listOf(
      "../../license/test-vectors/debt-book-v1.json",   // android-native/app
      "../license/test-vectors/debt-book-v1.json",      // desktop
      "license/test-vectors/debt-book-v1.json",         // ریشهٔ مخزن
    ).map { File(it) }.firstOrNull { it.isFile }
      ?: error("بردارهای دفترِ قرض پیدا نشد (cwd=${File(".").absoluteFile})")
    Json.parseToJsonElement(f.readText()).jsonObject
  }

  private fun str(e: JsonElement?): String? = if (e == null || e is JsonNull) null else e.jsonPrimitive.content

  @Test fun `همهٔ بردارها`() {
    val cases = v["cases"]!!.jsonArray
    check(cases.size >= 4)
    for (c in cases.map { it.jsonObject }) {
      val name = c["name"]!!.jsonPrimitive.content
      val d = json.decodeFromJsonElement(ShopData.serializer(), c["ledger"]!!)
      val id = c["debtorId"]!!.jsonPrimitive.content
      val today = c["today"]!!.jsonPrimitive.content
      val x = c["expect"]!!.jsonObject
      val book = DebtBook.book(d, id, today)!!

      assertEquals(name, x["balanceMinor"]!!.jsonPrimitive.long, book.balance.minor)
      assertEquals(name, x["overdueMinor"]!!.jsonPrimitive.long, book.overdueAmount.minor)
      assertEquals(name, x["overdue"]!!.jsonArray.map { it.jsonPrimitive.content }, book.overdue.map { it.tx.id })
      assertEquals(name, str(x["nextDue"]), book.nextDue?.tx?.id)
      //  ماندهٔ آخر همان عددِ امروز است
      assertEquals(name, DebtBook.minor(ShopStore.debt(d, id)), book.balance)

      val credit = x["credit"]
      if (credit == null || credit is JsonNull) assertNull(name, book.credit) else {
        val o = credit.jsonObject
        val b = book.credit!!
        assertEquals(name, o["limitMinor"]!!.jsonPrimitive.long, b.limit.minor)
        assertEquals(name, o["balanceMinor"]!!.jsonPrimitive.long, b.balance.minor)
        assertEquals(name, o["headroomMinor"]!!.jsonPrimitive.long, b.headroom.minor)
        assertEquals(name, o["over"]!!.jsonPrimitive.boolean, b.over)
      }
      x["creditCheck"]?.jsonObject?.let { cc ->
        val r = DebtBook.creditCheck(d, id, ir.vil3ntec.tohid.core.money.Money(cc["extraMinor"]!!.jsonPrimitive.long, DebtBook.CURRENCY))
        val want = cc["result"]
        if (want == null || want is JsonNull) assertNull(name, r) else {
          val o = want.jsonObject
          assertEquals(name, o["limitMinor"]!!.jsonPrimitive.long, r!!.limit.minor)
          assertEquals(name, o["balanceMinor"]!!.jsonPrimitive.long, r.balance.minor)
          assertEquals(name, o["afterMinor"]!!.jsonPrimitive.long, r.after.minor)
          assertEquals(name, o["over"]!!.jsonPrimitive.boolean, r.over)
          assertEquals(name, o["excessMinor"]!!.jsonPrimitive.long, r.excess.minor)
        }
      }

      val want = x["entries"]!!.jsonArray.map { it.jsonObject }
      assertEquals(name, want.map { it["id"]!!.jsonPrimitive.content }, book.entries.map { it.tx.id })
      want.zip(book.entries).forEach { (w, e) ->
        val at = "$name / ${e.tx.id}"
        assertEquals(at, w["row"]!!.jsonPrimitive.int, e.row)
        assertEquals(at, w["give"]!!.jsonPrimitive.boolean, e.give)
        assertEquals(at, w["balanceAfterMinor"]!!.jsonPrimitive.long, e.balanceAfter.minor)
        assertEquals(at, w["openMinor"]!!.jsonPrimitive.long, e.open.minor)
        assertEquals(at, w["overdue"]!!.jsonPrimitive.boolean, e.overdue)
        assertEquals(at, str(w["due"]), e.dueDate)
        assertEquals(at, str(w["sale"]), e.sale?.id)
        assertEquals(
          at,
          (w["allocations"] as JsonArray).map { p -> p.jsonArray.let { it[0].jsonPrimitive.content to it[1].jsonPrimitive.long } },
          e.allocations.map { it.txId to it.amount.minor },
        )
        val lines = w["lines"]!!.jsonArray.map { it.jsonObject }
        assertEquals(at, lines.size, e.lines.size)
        lines.zip(e.lines).forEach { (wl, l) ->
          assertEquals(at, wl["name"]!!.jsonPrimitive.content, l.name)
          assertEquals(at, wl["quantity"]!!.jsonPrimitive.double, l.quantity, 0.0)
          assertEquals(at, wl["unit"]!!.jsonPrimitive.content, l.unit)
          assertEquals(at, wl["unitPriceMinor"]!!.jsonPrimitive.long, l.unitPrice.minor)
          assertEquals(at, wl["totalMinor"]!!.jsonPrimitive.long, l.total.minor)
          assertEquals(at, wl["returned"]!!.jsonPrimitive.double, l.returned, 0.0)
        }
      }
    }
  }
}
