package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  IMG-PRO-17 — صفحهٔ «تغییرِ دسته‌ایِ عکس‌ها» همان پاسخ‌های قفل‌شدهٔ `server/test/image-changes.test.js` را می‌خواند و
 *  فقط قلمِ معنادار می‌سازد؛ تطبیقِ نامطمئن فقط با تأییدِ همان کالا.
 */
class ImageChangesTest {

  private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  private val applied = json(
    """{"change":{"id":"ch1","key":"k","note":"عکس‌های تازه","status":"applied","item_count":2,"created_by":"u1","created_at":1000,
      "rolled_back_by":"","rolled_back_at":null,"rollback_reason":"",
      "items":[{"product_id":"p1","before":{"source":"shop","image_id":"y1","version":1,"match_status":"exact"},
                "after":{"source":"central","image_id":"c1","version":2,"match_status":"probable"}},
               {"product_id":"p2","before":null,"after":{"source":"shop","image_id":"y2","version":1,"match_status":"exact"}}]},
      "replayed":false}""",
  )

  @Test
  fun readsTheBatchWithBeforeAndAfterOfEachProduct() {
    val c = ImageChanges.of(applied)!!
    assertEquals("ch1", c.id)
    assertEquals(2, c.itemCount)
    assertFalse(c.rolledBack)
    assertEquals(listOf("p1", "p2"), c.items.map { it.productId })
    assertEquals(ImageChanges.Pick("shop", "y1", 1, "exact"), c.items[0].before)
    assertEquals(ImageChanges.Pick("central", "c1", 2, "probable"), c.items[0].after)
    assertNull(c.items[1].before, "بی ردیفِ پیشین ⇒ null (همان عکسِ همگام‌شده یا بی عکس)")
    assertNull(c.rolledBackAt)
  }

  @Test
  fun readsTheListAndTheRollbackAnswer() {
    val list = ImageChanges.changesOf(
      json("""{"changes":[{"id":"ch2","status":"rolled_back","item_count":1,"created_at":5,"rolled_back_at":9,"rollback_reason":"قدیمی"},{"status":"applied"},{"id":"ch1","status":"applied","item_count":3}]}"""),
    )
    assertEquals(listOf("ch2", "ch1"), list.map { it.id }, "ردیفِ بی شناسه کنار")
    assertTrue(list[0].rolledBack)
    assertEquals(9L, list[0].rolledBackAt)
    assertEquals("قدیمی", list[0].rollbackReason)
    assertTrue(ImageChanges.already(json("""{"change":{"id":"ch1"},"already":true}""")))
    assertFalse(ImageChanges.already(json("""{"change":{"id":"ch1"},"already":"true"}""")))
  }

  private fun current(s: String) = ImageChanges.currentOf(json(s))
  private val p = ImageChanges.Product("p1", "چای سبز", "6260000000017")

  @Test
  fun latestCentralOnlyWhereANewerApprovedVersionOrAPinnedCentralExists() {
    val onOld = current("""{"product_id":"p1","source":"central","image_id":"c1","version":1,"central":{"image_id":"c1","version":1},"update_available":3}""")
    assertEquals(
      ImageChanges.Planned.Send(ImageChanges.Line("p1", "central", "c1", 3, "چای سبز", "6260000000017")),
      ImageChanges.plan(ImageChanges.Action.LATEST_CENTRAL, p, onOld),
    )
    val latest = current("""{"product_id":"p1","source":"central","image_id":"c1","version":3,"central":{"image_id":"c1","version":3},"update_available":0}""")
    assertEquals(ImageChanges.Planned.Skipped("p1", ImageChanges.Skip.ALREADY), ImageChanges.plan(ImageChanges.Action.LATEST_CENTRAL, p, latest))
    val archived = current("""{"product_id":"p1","source":"central","image_id":"c1","version":1,"update_available":0,"archived":true}""")
    assertEquals(ImageChanges.Planned.Skipped("p1", ImageChanges.Skip.ARCHIVED), ImageChanges.plan(ImageChanges.Action.LATEST_CENTRAL, p, archived))
    //  حالا عکسِ خودِ دکان، ولی مرکزیِ سنجاق‌شده دارد ⇒ همان عکس، نسخهٔ جاریِ تأییدشدهٔ سرور
    val ownWithPin = current("""{"product_id":"p1","source":"shop","image_id":"y1","version":1,"central":{"image_id":"c1","version":1}}""")
    assertEquals(
      ImageChanges.Planned.Send(ImageChanges.Line("p1", "central", "c1", null, "چای سبز", "6260000000017")),
      ImageChanges.plan(ImageChanges.Action.LATEST_CENTRAL, p, ownWithPin),
    )
    val never = current("""{"product_id":"p1","source":"legacy_photo","placeholder":false}""")
    assertEquals(ImageChanges.Planned.Skipped("p1", ImageChanges.Skip.NO_CENTRAL), ImageChanges.plan(ImageChanges.Action.LATEST_CENTRAL, p, never))
    assertEquals(ImageChanges.Planned.Skipped("p1", ImageChanges.Skip.UNKNOWN), ImageChanges.plan(ImageChanges.Action.LATEST_CENTRAL, p, null))
  }

  @Test
  fun revertCentralOnlyForProductsThatLeftTheirCentralPhoto() {
    val own = current("""{"product_id":"p1","source":"shop","image_id":"y1","version":1,"central":{"image_id":"c1","version":2}}""")
    assertEquals(
      ImageChanges.Planned.Send(ImageChanges.Line("p1", "central", null, null, "چای سبز", "6260000000017")),
      ImageChanges.plan(ImageChanges.Action.REVERT_CENTRAL, p, own),
    )
    val none = current("""{"product_id":"p1","source":"none","placeholder":true,"central":{"image_id":"c1","version":2}}""")
    assertTrue(ImageChanges.plan(ImageChanges.Action.REVERT_CENTRAL, p, none) is ImageChanges.Planned.Send)
    val onCentral = current("""{"product_id":"p1","source":"central","image_id":"c1","version":2,"central":{"image_id":"c1","version":2}}""")
    assertEquals(ImageChanges.Planned.Skipped("p1", ImageChanges.Skip.ALREADY), ImageChanges.plan(ImageChanges.Action.REVERT_CENTRAL, p, onCentral))
    val noPin = current("""{"product_id":"p1","source":"shop","image_id":"y1","version":1}""")
    assertEquals(ImageChanges.Planned.Skipped("p1", ImageChanges.Skip.NO_CENTRAL), ImageChanges.plan(ImageChanges.Action.REVERT_CENTRAL, p, noPin))
  }

  @Test
  fun bodyConfirmsOnlyTheProductsTheUserConfirmedOneByOne() {
    val lines = listOf(
      ImageChanges.Line("p1", "central", "c1", 3, "چای", "111"),
      ImageChanges.Line("p2", "central", null, null, "قند", "222"),
    )
    val b = ImageChanges.body("batch-1", "  عکس‌های تازه  ", lines, confirmed = setOf("p1"))
    assertEquals("batch-1", (b["key"] as JsonPrimitive).content)
    assertEquals("عکس‌های تازه", (b["note"] as JsonPrimitive).content)
    val items = b["items"]!!.jsonArray.map { it.jsonObject }
    assertEquals(listOf("p1", "p2"), items.map { (it["product_id"] as JsonPrimitive).content })
    assertEquals("true", items[0]["confirm"].toString())
    assertNull(items[1]["confirm"], "بی تأییدِ همان کالا ⇒ بی confirm")
    assertEquals("3", items[0]["version"].toString())
    assertEquals("111", (items[0]["expect"]!!.jsonObject["barcode"] as JsonPrimitive).content)
    assertNull(items[1]["image_id"], "برگشت به مرکزی بی شناسه")
    assertNull(items[1]["expect"])
    assertNull(ImageChanges.body("k", "   ", lines.take(1), emptySet())["note"], "یادداشتِ خالی فرستاده نمی‌شود")
    assertFailsWith<IllegalArgumentException> { ImageChanges.body("k", "", emptyList(), emptySet()) }
    val many = List(ImageChanges.MAX_ITEMS + 1) { ImageChanges.Line("q$it", "central", null, null, "", "") }
    assertFailsWith<IllegalArgumentException> { ImageChanges.body("k", "", many, emptySet()) }
  }

  @Test
  fun serverProblemsNameTheProduct() {
    val lines = listOf(ImageChanges.Line("p1", "central", "c1", null, "", ""), ImageChanges.Line("p4", "central", "c9", null, "", ""))
    val one = ImageChanges.problem("needs_confirmation", json("""{"code":"needs_confirmation","index":1}"""), lines)
    assertEquals("p4", one.productId, "از شمارهٔ قلم")
    assertTrue(one.confirmable)
    val named = ImageChanges.problem("barcode_mismatch", json("""{"index":0,"product_id":"p1"}"""), lines)
    assertEquals("p1", named.productId)
    assertFalse(named.confirmable, "بارکدِ متفاوت تأیید ندارد")
    val since = ImageChanges.problem("changed_since", json("""{"code":"changed_since","products":["p1","p2"]}"""))
    assertEquals(listOf("p1", "p2"), since.products)
    assertNull(ImageChanges.problem("key_reused", null).productId)
  }

  @Test
  fun pathsAreEncoded() {
    assertEquals("/shop/images/products/%DA%86%D8%A7%DB%8C%201", ImageChanges.productPath("چای 1"))
    assertEquals("/shop/images/changes/ch1/rollback", ImageChanges.rollbackPath("ch1"))
    assertEquals("/shop/images/changes?limit=50", ImageChanges.listPath(500))
  }
}
