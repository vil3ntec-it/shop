package ir.vil3ntec.tohid.core.image

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** ۴-۲۳ (۱) — خواندنِ پاسخ‌های `/api/shop/images/studio` و ساختنِ درخواست‌ها، همان شکلِ `server/src/routes/shop-image-studio.js` */
class ImageStudioTest {
  private fun j(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

  @Test fun paths() {
    assertEquals("/shop/images/studio/config", ImageStudio.configPath)
    assertEquals("/shop/images/studio/batches?limit=50", ImageStudio.batchesPath(500))
    assertEquals("/shop/images/studio/batches/b%201?after=j%2F2", ImageStudio.batchPath("b 1", "j/2"))
    assertEquals("/shop/images/studio/images/img1/versions/3/review", ImageStudio.reviewPath("img1", 3))
    assertEquals("/shop/images/mine?limit=60&cursor=c1", ImageStudio.minePath("c1"))
  }

  @Test fun configOnlyWhenEnabled() {
    val c = ImageStudio.configOf(j("""{"enabled":true,"presets":{"catalog":{},"enhance":{},"ad":{},"x":{}},"limits":{"batch_max":1000,"open_max":3}}"""))!!
    assertEquals(listOf("catalog", "enhance", "ad"), c.presets)
    assertEquals(1000, c.batchMax)
    assertNull(ImageStudio.configOf(j("""{"enabled":false}""")))
  }

  @Test fun batchAndItems() {
    val b = ImageStudio.batchOf(j("""{"id":"b1","state":"active","preset":"catalog","total":3,
      "progress":{"queued":0,"running":1,"done":1,"failed":1,"cancelled":0,"finished":2,"percent":66},
      "review":{"needs_review":1,"approved":0,"rejected":0},
      "items":[
        {"image_id":"i1","status":"done","source_version":1,"result_version":2,"review":"needs_review","risk":["crop_cut"],"before":"/a","after":"/b","error":null},
        {"image_id":"i2","status":"failed","source_version":1,"result_version":null,"review":null,"risk":[],"before":"/c","after":null,"error":"decode"},
        {"image_id":"","status":"done"}
      ],"next":null}"""))!!
    assertEquals(2, b.items.size, "قلمِ بی شناسه کنار")
    assertTrue(b.items[0].reviewable)
    assertEquals(listOf("crop_cut"), b.items[0].risk)
    assertFalse(b.items[1].reviewable)
    assertEquals("decode", b.items[1].error)
    assertEquals(66, b.progress.percent)
    assertEquals(1, b.needsReview)
    assertFalse(b.finished)
    assertEquals(listOf("pause", "cancel", "retry"), b.actions)
    assertNull(b.next)
  }

  @Test fun actionsFollowServerRule() {
    fun b(state: String, finished: Int, failed: Int) = ImageStudio.Batch("b", state, "catalog", 2,
      ImageStudio.Progress(0, 0, finished - failed, failed, 0, finished, 0), 0, emptyList(), null)
    assertEquals(listOf("resume", "cancel"), b("paused", 0, 0).actions)
    assertEquals(emptyList(), b("active", 2, 0).actions, "تمام‌شده بی خطا ⇒ هیچ")
    assertEquals(listOf("retry"), b("active", 2, 1).actions)
    assertEquals(emptyList(), b("cancelled", 1, 1).actions, "لغوشده دوباره اجرا نمی‌شود")
  }

  @Test fun checkAndBodies() {
    assertEquals(ImageStudio.Check.Empty, ImageStudio.check(emptyList(), "catalog", 10))
    assertEquals(ImageStudio.Check.TooMany(1), ImageStudio.check(listOf("a", "b"), "catalog", 1))
    assertEquals(ImageStudio.Check.Ok, ImageStudio.check(listOf("a", "a"), "catalog", 1), "تکرار یکی")
    assertEquals(ImageStudio.Check.BadPreset, ImageStudio.check(listOf("a"), "magic", 10))
    assertEquals("""{"key":"k1","image_ids":["a","b"],"preset":"ad"}""", ImageStudio.createBody("k1", listOf("a", "b", "a"), "ad").toString())
    assertEquals("""{"decision":"approve","confirm_risk":true}""", ImageStudio.reviewBody(true, true).toString())
    assertEquals("""{"decision":"reject"}""", ImageStudio.reviewBody(false, true).toString(), "رد هرگز confirm_risk ندارد")
    assertEquals("""{"action":"retry"}""", ImageStudio.actionBody("retry").toString())
    assertFailsWith<IllegalArgumentException> { ImageStudio.actionBody("delete") }
  }

  @Test fun mineAndList() {
    val (m, next) = ImageStudio.mineOf(j("""{"items":[{"id":"i1","product_id":"p1","urls":{"thumb":"/t"}},{"id":"i2","product_id":"p2","urls":{}}],"next_cursor":"i2"}"""))
    assertEquals(listOf(ImageStudio.Mine("i1", "p1", "/t"), ImageStudio.Mine("i2", "p2", null)), m)
    assertEquals("i2", next)
    val l = ImageStudio.batchesOf(j("""{"items":[{"id":"b1","state":"active","preset":"enhance","total":4,"finished":1,"created_at":5}]}"""))
    assertEquals(25, l.single().percent)
  }
}
