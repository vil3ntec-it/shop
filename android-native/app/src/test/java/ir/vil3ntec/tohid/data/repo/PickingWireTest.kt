package ir.vil3ntec.tohid.data.repo

import ir.vil3ntec.tohid.core.model.PickEventsDto
import ir.vil3ntec.tohid.core.model.PickSheetDto
import ir.vil3ntec.tohid.core.net.ApiJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  شکلِ سیمِ جمع‌آوری همان است که `server/src/lib/order-picking.js` می‌دهد و می‌خواهد (همان کلیدهای
 *  آزمونِ `server/test/order-picking.test.js`).
 */
class PickingWireTest {

  private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject

  @Test fun `برگه — ترتیب، وزنی، پیشرفت، جمع و برچسب`() {
    val body = obj("""
      {"pick":{"orderId":"01JX0000000000000000000000","orderNo":12,"orderStatus":"packed","status":"packed","pickerId":"u1",
       "bags":2,"startedAt":1000,"packedAt":61000,"version":9,"toleranceBp":1000,
       "lines":[{"lineNo":2,"productId":"rice","name":"برنج","unit":"کیلو","status":"picked","orderedQtyMilli":1500,
                 "pickedQtyMilli":1600,"unitPriceMinor":12050,"originalTotalMinor":18100,"weighed":true,
                 "location":{"aisle":"1","shelf":"3","level":""},"scans":1,"wrongScans":0},
                {"lineNo":3,"productId":"salt","name":"نمک","unit":"بسته","status":"not_found","orderedQtyMilli":1000,
                 "pickedQtyMilli":0,"unitPriceMinor":5000,"originalTotalMinor":5000,"weighed":false,
                 "location":{"aisle":"","shelf":"","level":""},"scans":0,"wrongScans":2}],
       "progress":{"total":2,"pending":0,"done":2},
       "totals":{"originalTotalMinor":23100,"subtotalMinor":19300,"discountMinor":0,"deliveryFeeMinor":0,"totalMinor":19300,"deductedMinor":3800},
       "labels":[{"bag":1,"of":2,"qr":"VLNB-12-1-2"},{"bag":2,"of":2,"qr":"VLNB-12-2-2"}],
       "futureField":true}}
    """)
    val s = ApiJson.decodeAt<PickSheetDto>(body, "pick")!!
    assertEquals(listOf(2, 3), s.lines.map { it.lineNo })
    assertTrue(s.lines[0].weighed)
    assertEquals("1", s.lines[0].location.aisle)
    assertEquals(2, s.lines[1].wrongScans)
    assertEquals(0, s.progress.pending)
    assertEquals(3800, s.totals.deductedMinor)
    assertEquals(listOf("VLNB-12-1-2", "VLNB-12-2-2"), s.labels.map { it.qr })
    assertEquals(61000L, s.packedAt)
  }

  @Test fun `برگهٔ شروع‌نشده null است`() {
    assertNull(ApiJson.decodeAt<PickSheetDto>(obj("""{"pick":null}"""), "pick"))
  }

  @Test fun `نتیجهٔ رویداد — پذیرفته، رد با سقفِ وزن، تکرار`() {
    val out = ApiJson.decode<PickEventsDto>(obj("""
      {"results":[{"eventId":"pk-1","status":"accepted","line":{"lineNo":1,"status":"picked","pickedQtyMilli":2000}},
                  {"eventId":"pk-2","status":"rejected","code":"over_tolerance","message":"وزن از حدِ مجاز بیشتر است","maxMilli":1650},
                  {"eventId":"pk-1","status":"accepted","replayed":true}],
       "pick":{"orderNo":5,"status":"picking"}}
    """))
    assertEquals(listOf("accepted", "rejected", "accepted"), out.results.map { it.status })
    assertEquals(1650L, out.results[1].maxMilli)
    assertEquals("picked", out.results[0].line?.status)
    assertTrue(out.results[2].replayed)
    assertFalse(out.results[0].replayed)
    assertEquals("picking", out.pick?.status)
  }

  @Test fun `بدنهٔ رویداد — فقط کلیدهای همان نوع`() {
    val scan = PickingRepository.event("pk-abcdefgh", "scan", 2, barcode = "6260000000017")
    assertEquals(setOf("eventId", "type", "lineNo", "barcode"), scan.keys)
    assertEquals("scan", scan["type"]!!.jsonPrimitive.content)
    val nf = PickingRepository.event("pk-abcdefgh", "not_found", 3)
    assertEquals(setOf("eventId", "type", "lineNo"), nf.keys)
    val w = PickingRepository.event("pk-abcdefgh", "weigh", 2, qtyMilli = 1600)
    assertEquals("1600", w["qtyMilli"]!!.jsonPrimitive.content)
    assertEquals("2", PickingRepository.packJson(2)["bags"]!!.jsonPrimitive.content)
  }

  @Test fun `شناسهٔ رویداد با قاعدهٔ سرور می‌خواند و هر بار تازه است`() {
    val a = PickingRepository.newEventId()
    val b = PickingRepository.newEventId()
    assertTrue(Regex("^[A-Za-z0-9_.:-]{8,128}$").matches(a))
    assertTrue(a != b)
  }
}
