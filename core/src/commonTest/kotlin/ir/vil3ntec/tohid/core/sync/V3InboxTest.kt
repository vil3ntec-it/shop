package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  SYN-07 — خواندنِ صندوقِ تعارضِ v3، دو سو، کارها و تصمیم ([V3Inbox])؛ شکلِ ردیف همان `shapeConflict` ِ
 *  `server/src/lib/sync-v3.js` (همان که `server/test/sync-v3-inbox.test.js` می‌سنجد).
 */
class V3InboxTest {
  private fun obj(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val skew = """{"id":"cf_skew1","kind":"clock_skew","task":false,"revertible":true,"table":"products","row_id":"p1",
    "field":"*","winner_value":{"name":"چای","salePriceMinor":10000},"loser_value":{"name":"چای تازه","salePriceMinor":12500},
    "winner_hlc":"000001700000000000:000000:dev-a","loser_hlc":"000001700000600000:000000:dev-c","winner_op_id":"o1",
    "loser_op_id":"o2","winner_device":"dev-a","loser_device":"dev-c","detail":{},"status":"open","at":1700000000000,
    "resolved_at":null,"resolved_by":"","resolution_op_id":""}"""

  private val lww = """{"id":"cf_lww1","kind":"lww","task":false,"revertible":true,"table":"products","row_id":"p2",
    "field":"salePriceMinor","winner_value":2000,"loser_value":1500,"winner_hlc":"000001700000000020:000000:dev-a",
    "loser_hlc":"000001700000000010:000000:dev-b","winner_device":"dev-a","loser_device":"dev-b","status":"open","at":5}"""

  private fun page(vararg rows: String, inbox: String? = """{"v":1,"merge":true}""") =
    V3Inbox.parsePage(obj("""{"ok":true,"conflicts":[${rows.joinToString(",")}]${inbox?.let { ",\"inbox\":$it" } ?: ""}}"""))

  @Test fun pageIsOffWithoutInboxKeyAndHidesDocKinds() {
    val doc = """{"id":"cf_doc1","kind":"approval_required","task":true,"revertible":false,"status":"open","at":1}"""
    val off = page(skew, doc, inbox = null)
    assertFalse(off.on)
    assertFalse(off.merge)
    val on = page(skew, lww, doc)
    assertTrue(on.on && on.merge)
    assertEquals(listOf("cf_skew1", "cf_lww1"), on.items.map { it.id })
    assertFalse(page(skew, inbox = """{"v":1}""").merge, "بی merge ِ سرور ادغام نیست")
  }

  @Test fun badRowsAreDropped() {
    assertNull(V3Inbox.parse(obj("""{"id":"../x","kind":"lww"}""")))
    assertNull(V3Inbox.parse(obj("""{"id":"cf_1"}""")))
    assertEquals("cf_skew1", V3Inbox.parse(obj("""{"conflict":$skew}"""))?.id)
  }

  @Test fun sidesShowBothValuesAndWhoseTheyAre() {
    val c = V3Inbox.parse(obj(skew))!!
    val (cur, other) = V3Inbox.sides(c, me = "dev-c")
    assertTrue(cur.current)
    assertEquals(V3Inbox.Who.OTHER_DEVICE, cur.who)
    assertEquals(V3Inbox.Who.THIS_DEVICE, other.who)
    assertEquals(V3Inbox.Owner.THEIRS to V3Inbox.Owner.MINE, V3Inbox.owners(cur, other))
    assertEquals(setOf("name", "salePriceMinor"), cur.values.keys)
    assertEquals(JsonPrimitive(12500), other.values["salePriceMinor"])
    assertEquals(1_700_000_600_000L, other.wall)
    val third = V3Inbox.sides(c, me = "dev-z")
    assertEquals(V3Inbox.Who.OTHER_DEVICE, third.first.who)
    assertEquals(V3Inbox.Who.OTHER_DEVICE, third.second.who)
    assertEquals(V3Inbox.Owner.NEUTRAL to V3Inbox.Owner.NEUTRAL, V3Inbox.owners(third.first, third.second), "دستگاهِ سوم: «فعلی/دیگری»، نه دو «مالِ دیگری»")
    val single = V3Inbox.sides(V3Inbox.parse(obj(lww))!!, me = "dev-a")
    assertEquals(mapOf("salePriceMinor" to JsonPrimitive(2000)), single.first.values)
    assertEquals(V3Inbox.Who.THIS_DEVICE, single.first.who)
    assertEquals(V3Inbox.Owner.MINE to V3Inbox.Owner.THEIRS, V3Inbox.owners(single.first, single.second))
  }

  @Test fun deleteSidesAreMarkedDeleted() {
    val staleDel = V3Inbox.parse(obj("""{"id":"cf_d1","kind":"stale_delete","revertible":true,"field":"*delete*","status":"open","at":1,
      "winner_device":"","loser_device":"dev-b"}"""))!!
    val (keep, del) = V3Inbox.sides(staleDel, "dev-b")
    assertFalse(keep.deleted)
    assertTrue(del.deleted)
    assertEquals(V3Inbox.Who.UNKNOWN, keep.who)
    val afterDel = V3Inbox.parse(obj("""{"id":"cf_d2","kind":"stale_after_delete","revertible":true,"field":"*",
      "loser_value":{"name":"x","salePriceMinor":5},"status":"open","at":1}"""))!!
    val (gone, edit) = V3Inbox.sides(afterDel, "")
    assertTrue(gone.deleted)
    assertEquals(2, edit.values.size)
    assertFalse(V3Inbox.mergeable(afterDel, serverMerge = true), "ردیفِ حذف‌شده ادغام ندارد")
  }

  @Test fun picksFollowKindAndServerHint() {
    val c = V3Inbox.parse(obj(skew))!!
    assertEquals(listOf(V3Inbox.Pick.KEEP_CURRENT, V3Inbox.Pick.TAKE_OTHER, V3Inbox.Pick.MERGE), V3Inbox.picks(c, true))
    assertEquals(listOf(V3Inbox.Pick.KEEP_CURRENT, V3Inbox.Pick.TAKE_OTHER), V3Inbox.picks(c, false))
    assertEquals(listOf(V3Inbox.Pick.KEEP_CURRENT, V3Inbox.Pick.TAKE_OTHER), V3Inbox.picks(V3Inbox.parse(obj(lww))!!, true))
    val task = V3Inbox.parse(obj("""{"id":"cf_t1","kind":"negative_stock","task":true,"revertible":false,"status":"open","at":1}"""))!!
    assertEquals(listOf(V3Inbox.Pick.REVIEWED), V3Inbox.picks(task, true))
    val append = V3Inbox.parse(obj("""{"id":"cf_a1","kind":"append_only_edit","task":false,"revertible":false,"status":"open","at":1}"""))!!
    assertEquals(listOf(V3Inbox.Pick.KEEP_CURRENT), V3Inbox.picks(append, true))
    assertEquals(emptyList(), V3Inbox.picks(c.copy(status = V3Inbox.KEPT), true))
  }

  @Test fun decisionsMapToServerActions() {
    val c = V3Inbox.parse(obj(skew))!!
    assertEquals(V3Inbox.Decision(c.id, "keep"), V3Inbox.decide(c, V3Inbox.Pick.KEEP_CURRENT, true))
    assertEquals(V3Inbox.Decision(c.id, "revert"), V3Inbox.decide(c, V3Inbox.Pick.TAKE_OTHER, true))
    assertEquals(V3Inbox.Decision(c.id, "merge", listOf("salePriceMinor")), V3Inbox.decide(c, V3Inbox.Pick.MERGE, true, setOf("salePriceMinor")))
    assertEquals("revert", V3Inbox.decide(c, V3Inbox.Pick.MERGE, true, setOf("name", "salePriceMinor"))?.action, "همه از دیگری = برگرداندن")
    assertEquals("keep", V3Inbox.decide(c, V3Inbox.Pick.MERGE, true, emptySet())?.action, "هیچ از دیگری = نگه داشتن")
    assertNull(V3Inbox.decide(c, V3Inbox.Pick.MERGE, true, setOf("stock")), "فیلدِ ناشناخته")
    assertNull(V3Inbox.decide(c, V3Inbox.Pick.MERGE, false, setOf("name")), "بی پرچمِ سرور ادغام نه")
    assertNull(V3Inbox.decide(c, V3Inbox.Pick.REVIEWED, true))
    assertEquals(V3Inbox.REVERTED, V3Inbox.finalOf(V3Inbox.Decision(c.id, "merge", listOf("name")), false))
    assertEquals(V3Inbox.DISMISSED, V3Inbox.finalOf(V3Inbox.Decision(c.id, "keep"), task = true))
  }

  @Test fun valuesAreShownInTheirUnit() {
    assertEquals(V3Inbox.Shown(V3Inbox.Kind.MONEY, number = 12500), V3Inbox.shown("salePriceMinor", JsonPrimitive(12500)))
    assertEquals(V3Inbox.Shown(V3Inbox.Kind.QTY, number = 1500), V3Inbox.shown("minStockMilli", JsonPrimitive(1500)))
    assertEquals(V3Inbox.Kind.TEXT, V3Inbox.shown("name", JsonPrimitive("چای")).kind)
    assertEquals(V3Inbox.Kind.EMPTY, V3Inbox.shown("name", JsonPrimitive(" ")).kind)
    assertEquals(V3Inbox.Kind.EMPTY, V3Inbox.shown("name", null).kind)
    assertEquals(2, V3Inbox.shown("barcodes", Json.parseToJsonElement("""["1","2"]""")).size)
    assertEquals("salePrice", V3Inbox.base("salePriceMinor"))
    assertEquals("minStock", V3Inbox.base("minStockMilli"))
  }
}
