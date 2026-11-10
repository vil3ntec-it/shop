package ir.vil3ntec.tohid.core.storefront

import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery.LatLng
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery.ZoneDraft
import ir.vil3ntec.tohid.data.repo.StorefrontZoneDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  CHK-10 — لبه‌های به‌هم‌بُریدهٔ چندضلعیِ ناحیه (پاپیون) در برابرِ بردارِ مشترکِ `core/test-vectors/delivery-zone-v1.json`،
 *  همان که `server/test/delivery-zone-v1.test.js` با `crossing` ِ `lib/storefront-delivery.js` می‌خواند. سرور همان
 *  چندضلعیِ ذخیره‌شده را بی تغییر می‌پذیرد؛ برگه هم (`savedPolygon`).
 */
class StorefrontDeliveryVectorsTest {
  private val D = StorefrontDelivery

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/delivery-zone-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/delivery-zone-v1.json پیدا نشد")
  }

  @Test fun crossing_matches_the_shared_vector_of_the_server() {
    assertEquals(1, vec.getValue("version").jsonPrimitive.int)
    val cases = vec.getValue("crossing").jsonArray.map { it.jsonObject }
    assertTrue(cases.size >= 8)
    for (c in cases) {
      val pts = c.getValue("polygon").jsonArray.map { p -> p.jsonArray.let { LatLng(it[0].jsonPrimitive.double, it[1].jsonPrimitive.double) } }
      val want = c.getValue("want").let { w ->
        if (w is JsonNull) null else w.jsonArray.map { e -> e.jsonArray.map { it.jsonPrimitive.int } }
      }
      assertEquals(c.getValue("name").jsonPrimitive.content, want, D.crossing(pts))
    }
  }

  @Test fun bow_tie_polygon_is_refused_beside_its_field_unless_unchanged_since_loaded() {
    val bow = "34.50, 69.12\n34.56, 69.22\n34.50, 69.22\n34.57, 69.12"
    val square = "34.50, 69.12\n34.50, 69.22\n34.56, 69.22\n34.56, 69.12"
    fun zone(polygon: String, saved: String = "") = ZoneDraft(name = "کارته‌سه", kind = "polygon", fee = "30", min = "0", polygon = polygon, savedPolygon = saved)

    val bad = D.check(listOf(zone(square), zone(bow)), emptyList(), "3")
    assertEquals("لبهٔ نقطه‌های ۱ و ۲ لبهٔ نقطه‌های ۳ و ۴ را می‌بُرد؛ نقطه‌ها را به ترتیبِ دورِ ناحیه بنویسید", bad.errors["z1.polygon"])
    assertNull(bad.errors["z0.polygon"])
    //  نقطهٔ پایانیِ برابر با نخستین (رونوشت از ابزارِ نقشه) پاپیون نیست
    assertTrue(D.check(listOf(zone("$square\n34.50, 69.12")), emptyList(), "3").errors.isEmpty())
    //  همان پاپیونِ ذخیره‌شده (حتی با فاصله‌گذاریِ دیگر) می‌گذرد تا ذخیرهٔ هزینه نیفتد؛ تغییرش نه
    assertTrue(D.check(listOf(zone(bow.replace(", ", ","), saved = bow)), emptyList(), "3").errors.isEmpty())
    val moved = bow.replace("34.57, 69.12", "34.58, 69.12")
    assertTrue(D.check(listOf(zone(moved, saved = bow)), emptyList(), "3").errors.containsKey("z0.polygon"))
  }

  @Test fun zone_loaded_from_the_server_remembers_its_polygon() {
    val dto = StorefrontZoneDto(name = "کارته‌سه", polygon = listOf(listOf(34.5, 69.12), listOf(34.56, 69.22), listOf(34.5, 69.22), listOf(34.57, 69.12)))
    val d = dto.draft()
    assertEquals(d.polygon, d.savedPolygon)
    assertTrue(D.check(listOf(d), emptyList(), "3").errors.isEmpty())
  }
}
