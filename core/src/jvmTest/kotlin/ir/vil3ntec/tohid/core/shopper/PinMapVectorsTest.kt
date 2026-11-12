package ir.vil3ntec.tohid.core.shopper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 *  CHK-08 — «انتخاب روی نقشه» ِ برنامهٔ خرید ([PinMap]) در برابرِ `core/test-vectors/pin-map-v1.json`، همان که
 *  `server/test/store-pin-map.test.js` با `server/public/store/pin-map.js` ِ ویترین می‌خواند (بردار از خودِ همان پرونده ساخته
 *  شده): همان نشانهٔ وسط ⇒ همان پین در وب و برنامه. سپس تکه‌های قابِ نما، کشیدن، بزرگنمایی و الگوی تکه‌ها.
 */
class PinMapVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/pin-map-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/pin-map-v1.json پیدا نشد")
  }

  private fun cases(key: String) = v.getValue(key).jsonArray.map { it.jsonObject }
  private fun JsonObject.d(k: String) = getValue(k).jsonPrimitive.double
  private fun JsonObject.want() = getValue("want").jsonArray.map { it.jsonPrimitive.double }

  @Test
  fun projectionMatchesTheWebStorefrontVector() {
    assertEquals(1, v.getValue("version").jsonPrimitive.int)
    assertTrue(cases("toPx").size >= 8)
    for (c in cases("toPx")) {
      val (x, y) = PinMap.toPx(c.d("lat"), c.d("lng"), c.getValue("z").jsonPrimitive.int)
      val w = c.want()
      assertTrue(abs(x - w[0]) <= 1e-6 && abs(y - w[1]) <= 1e-6, "$c ⇒ $x,$y")
    }
    for (c in cases("toLL")) {
      val p = PinMap.toLL(c.d("x"), c.d("y"), c.getValue("z").jsonPrimitive.int)
      assertEquals(c.want(), listOf(p.lat, p.lng), c.toString())
    }
    for (c in cases("roundTrip")) {
      val z = c.getValue("z").jsonPrimitive.int
      val (x, y) = PinMap.toPx(c.d("lat"), c.d("lng"), z)
      val p = PinMap.toLL(x, y, z)
      assertEquals(c.want(), listOf(p.lat, p.lng), c.toString())
      assertEquals(listOf(c.d("lat"), c.d("lng")), listOf(p.lat, p.lng), "نشانه روی همان پین می‌ماند")
    }
  }

  @Test
  fun viewTilesDragAndZoomFollowTheWebMap() {
    val tpl = "/tiles/{z}/{x}/{y}.png"
    val start = PinMap.open(GeoPin(34.531234, 69.172345))
    assertEquals(PinMap.START_Z, start.z)
    assertEquals(GeoPin(34.531234, 69.172345), PinMap.here(start))
    assertEquals(PinMap.DEFAULT, PinMap.here(PinMap.open(null)), "بی پین: کابل")
    //  قابِ ۳۲۰×۲۴۰: تکه‌ها قاب را می‌پوشانند و هر کدام همان الگو با ستونِ دورزده
    val tiles = PinMap.tiles(start, 320, 240, tpl)
    assertTrue(tiles.size in 4..6, tiles.toString())
    assertTrue(tiles.all { it.left <= 320 && it.left + PinMap.SIZE >= 0 && it.top <= 240 && it.top + PinMap.SIZE >= 0 })
    assertTrue(tiles.all { it.url.matches(Regex("/tiles/16/\\d+/\\d+\\.png")) })
    val (cx, cy) = PinMap.toPx(34.531234, 69.172345, 16)
    val under = tiles.single { t -> t.left <= 160 && t.left + 256 > 160 && t.top <= 120 && t.top + 256 > 120 }
    assertEquals("/tiles/16/${(cx / 256).toInt()}/${(cy / 256).toInt()}.png", under.url, "تکهٔ زیرِ نشانه همان تکهٔ پین است")
    assertTrue(PinMap.tiles(start, 0, 240, tpl).isEmpty())
    //  کشیدن: طول دور می‌زند، عرض در لبه می‌ایستد
    val dragged = PinMap.move(start, PinMap.STEP.toDouble(), 0.0)
    assertTrue(PinMap.here(dragged).lng > 69.172345)
    assertEquals(0.0, PinMap.move(start, 0.0, -1e12).y)
    var edge = PinMap.open(GeoPin(10.0, 179.9999))
    repeat(20) { edge = PinMap.zoom(edge, -1) }
    assertTrue(PinMap.tiles(edge, 512, 256, tpl).any { it.url.startsWith("/tiles/3/0/") }, "پس از ۱۸۰ درجه، ستونِ صفر")
    //  بزرگنمایی: همان نقطه زیرِ نشانه، در بازهٔ ۳ تا ۱۹
    val inn = PinMap.zoom(start, 1)
    assertEquals(17, inn.z)
    assertEquals(PinMap.here(start), PinMap.here(inn))
    var far = start
    repeat(30) { far = PinMap.zoom(far, -1) }
    assertEquals(PinMap.MIN_Z, far.z)
    assertSame(far, PinMap.zoom(far, -1))
    var near = start
    repeat(10) { near = PinMap.zoom(near, 1) }
    assertEquals(PinMap.MAX_Z, near.z)
  }

  @Test
  fun tileTemplateMustBeTheSameSafeShapeTheServerAccepts() {
    assertTrue(PinMap.valid("/tiles/{z}/{x}/{y}.png"))
    assertTrue(PinMap.valid("https://tile.example.org/{z}/{x}/{y}.png"))
    assertTrue(PinMap.valid("https://tiles.example.org:8443/osm/{z}/{x}/{y}.png"))
    assertFalse(PinMap.valid("http://tile.example.org/{z}/{x}/{y}.png"), "فقط https")
    assertFalse(PinMap.valid("//evil.example/{z}/{x}/{y}.png"))
    assertFalse(PinMap.valid("https://tile.example.org/{z}/{x}.png"), "سه جای‌نگهدار")
    assertFalse(PinMap.valid("https://tile.example.org/{z}/{x}/{y}.png\"; script-src *"))
    assertFalse(PinMap.valid("javascript:{z}{x}{y}"))
    assertFalse(PinMap.valid("/" + "a".repeat(300) + "{z}{x}{y}"))
    assertNull(PinMap.usable(MapPinFeature("http://x/{z}/{x}/{y}")))
    assertNull(PinMap.usable(null))
    assertEquals("/t/{z}/{x}/{y}", PinMap.usable(MapPinFeature("/t/{z}/{x}/{y}", "OpenStreetMap"))?.tiles)
    assertEquals("https://t.example/5/9/12.png", PinMap.url("https://t.example/{z}/{x}/{y}.png", 5, 9, 12))
  }
}
