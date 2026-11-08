package ir.vil3ntec.tohid.core.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  صندوقِ سندهای ردشده — قراردادِ کلاینتِ `ConflictInbox` در برابرِ پاسخِ `server/src/lib/conflict-inbox.js`: خواندنِ فهرست
 *  و فاکتورِ کامل، کارهای مجازِ هر ردیف، سبدِ «اصلاح و ثبت» و نشانِ «سرور نپذیرفت» روی فاکتورِ دستگاهِ سازنده.
 */
class ConflictInboxTest {

  private val id = "cf_0123456789abcdef01234567"

  /** همان شکلِ `GET /api/shop/conflict-inbox/:id` (درختِ ساده) */
  private fun row(status: String = "open", reason: String = "approval_required", resubmittable: Boolean = true, busy: Boolean = false) = mapOf(
    "id" to id, "kind" to "approval_required", "reason" to reason, "perm" to "sales.credit", "status" to status, "at" to 1_700_000_000_000L,
    "doc" to mapOf("table" to "sales", "rowId" to "s-1", "saleId" to "s-1"), "number" to 41L, "path" to "v1",
    "staff" to mapOf("id" to "u-ali", "name" to "علی", "role" to "staff"), "device" to mapOf("id" to "pos-1", "name" to "گوشیِ علی"),
    "amountMinor" to 1_000_000L, "itemCount" to 2L,
    "items" to listOf(mapOf("name" to "برنج", "qtyMilli" to 2000L), mapOf("name" to "روغن", "qtyMilli" to 1000L)),
    "resubmittable" to resubmittable, "fixable" to true, "busy" to busy, "lastError" to null,
    "resolvedAt" to null, "resolvedBy" to null, "note" to "", "fixedDoc" to "",
    "invoice" to mapOf(
      "number" to 41L, "createdAt" to 1_700_000_000_000L, "paymentMethod" to "credit", "seller" to "علی",
      "debtor" to mapOf("id" to "d-1", "name" to "احمد"),
      "totalMinor" to 1_000_000L, "discountMinor" to 0L, "finalMinor" to 1_000_000L, "paidMinor" to 400_000L,
      "remainingMinor" to 600_000L, "debtMinor" to 600_000L,
      "items" to listOf(
        mapOf("name" to "برنج", "productId" to "p-rice", "qtyMilli" to 2000L, "unitPriceMinor" to 300_000L, "totalMinor" to 600_000L),
        mapOf("name" to "روغن", "productId" to "p-oil", "qtyMilli" to 1500L, "unitPriceMinor" to null, "totalMinor" to 600_000L),
        mapOf("name" to "بی مقدار", "productId" to "p-x", "qtyMilli" to 0L, "unitPriceMinor" to 100L),
      ),
      "returns" to emptyList<Any>(), "truncated" to false,
    ),
  )

  @Test fun `فهرست و فاکتورِ کامل خوانده می‌شوند`() {
    val page = ConflictInbox.parsePage(mapOf("conflicts" to listOf(row(), mapOf("id" to "bad id!")), "open" to 3L))
    assertEquals(1, page.items.size, "ردیفِ بدشکل کنار می‌رود")
    assertEquals(3, page.open)
    val i = page.items.single()
    assertEquals("approval_required", i.reason)
    assertEquals("علی", i.staff.name)
    assertEquals("pos-1", i.deviceId)
    assertEquals(1_000_000L, i.amountMinor)
    assertEquals(listOf("برنج" to 2000L, "روغن" to 1000L), i.items.map { it.name to it.qtyMilli })
    val inv = assertNotNull(ConflictInbox.parseItem(mapOf("conflict" to row()))?.invoice)
    assertEquals("احمد", inv.debtorName)
    assertEquals(600_000L, inv.debtMinor)
    assertEquals(3, inv.items.size)
    assertNull(ConflictInbox.parseItem(mapOf("conflict" to mapOf("id" to ""))))
  }

  @Test fun `کارهای مجاز ـ باز و آزاد هر سه؛ «دوباره ثبت» فقط وقتی سرور بگوید؛ بسته یا در حالِ ثبت هیچ`() {
    val p = { m: Map<String, Any?> -> ConflictInbox.parseItem(m)!! }
    assertEquals(setOf("resubmit", "fix", "dismiss"), ConflictInbox.actions(p(row())))
    assertEquals(setOf("fix", "dismiss"), ConflictInbox.actions(p(row(resubmittable = false))))
    assertEquals(setOf("fix", "dismiss"), ConflictInbox.actions(p(row(reason = "return_exceeds_sold"))))
    assertTrue(ConflictInbox.actions(p(row(status = "resubmitted"))).isEmpty())
    assertTrue(ConflictInbox.actions(p(row(busy = true))).isEmpty())
  }

  @Test fun `سبدِ «اصلاح و ثبت» با همان قیمت؛ قیمتِ نبوده از جمعِ ردیف؛ بی مقدار کنار`() {
    val inv = ConflictInbox.parseItem(row())!!.invoice!!
    assertEquals(
      listOf(ConflictInbox.CartLine("p-rice", "برنج", 2.0, 3000.0), ConflictInbox.CartLine("p-oil", "روغن", 1.5, 4000.0)),
      ConflictInbox.cartLines(inv),
    )
  }

  @Test fun `بدنهٔ «کنار بگذار» ـ دلیلِ خالی نه؛ یک خط؛ شناسهٔ فاکتورِ تازه فقط درست`() {
    assertNull(ConflictInbox.dismissBody(" "))
    assertEquals(mapOf("reason" to "مشتری پس گرفت", "fixedDoc" to null), ConflictInbox.dismissBody(" مشتری\nپس   گرفت "))
    assertEquals("sales/s-9", ConflictInbox.dismissBody("اصلاح شد", "s-9")!!["fixedDoc"])
    assertNull(ConflictInbox.dismissBody("اصلاح شد", "bad id!")!!["fixedDoc"])
  }

  @Test fun `نشانِ فاکتور ـ دلیلِ خودِ سند، نه «همراه کنار رفت»؛ حالِ سرور با همان op`() {
    val rej = listOf(
      ConflictInbox.Rejected("op-i1", "saleItems", "s-1-i1", "s-1", ConflictInbox.TX_REJECTED, 10),
      ConflictInbox.Rejected("op-s1", "sales", "s-1", "s-1", "approval_required", 10),
      ConflictInbox.Rejected("op-t1", "transactions", "s-1-give", "s-1", ConflictInbox.TX_REJECTED, 11),
      ConflictInbox.Rejected("op-r2", "returns", "r-2", "s-2", "return_exceeds_sold", 12),
      ConflictInbox.Rejected("op-p", "products", "p-1", "", "table_not_allowed", 13),
    )
    assertTrue(ConflictInbox.needsMine(rej))
    assertFalse(ConflictInbox.needsMine(rej.takeLast(1)))
    val local = ConflictInbox.markers(rej, emptyList())
    assertEquals(setOf("s-1", "s-2"), local.keys)
    assertEquals(ConflictInbox.Marker("approval_required", "open", "", 11, false), local["s-1"])
    assertTrue(local.getValue("s-2").returnDoc)
    val mine = listOf(ConflictInbox.Mine("cf_1", "sales", "s-1", "op-s1", "pos-1", "approval_required", "resubmitted", 10, 20, "مشتریِ همیشگی", ""))
    val m = ConflictInbox.markers(rej, mine).getValue("s-1")
    assertEquals("resubmitted", m.status)
    assertEquals("مشتریِ همیشگی", m.note)
    assertEquals(ConflictInbox.OPEN, ConflictInbox.markers(rej, mine).getValue("s-2").status)
  }

  @Test fun `شناسهٔ فاکتورِ هر opِ ردشده`() {
    assertEquals("s-1", ConflictInbox.saleOf("sales", "s-1", null))
    assertEquals("s-1", ConflictInbox.saleOf("saleItems", "i-1", mapOf("saleId" to "s-1")))
    assertEquals("s-1", ConflictInbox.saleOf("transactions", "t-1", mapOf("refId" to "s-1")))
    assertEquals("", ConflictInbox.saleOf("products", "p-1", mapOf("saleId" to "s-1")))
  }
}
