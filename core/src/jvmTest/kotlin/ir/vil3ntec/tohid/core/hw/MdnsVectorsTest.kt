package ir.vil3ntec.tohid.core.hw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  HW-12 — `Mdns` روی بردارهای مشترکِ `core/test-vectors/mdns-v1.json`؛ بسته‌ها و `out` ِ parse ساختهٔ
 *  dnspython‌اند (نه کدِ این مخزن). همان پرونده را `server/test/hw-mdns.test.js` با پیاده‌سازیِ مرجعِ
 *  `mdns-ref.js` می‌دواند. چاپگرِ واقعی این‌جا نیست؛ این آزمون بایت را می‌پاید.
 */
class MdnsVectorsTest {

  private val v: JsonObject = run {
    val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
      .firstOrNull { File(it, "core/test-vectors/mdns-v1.json").isFile }
      ?: error("core/test-vectors/mdns-v1.json پیدا نشد")
    Json.parseToJsonElement(File(root, "core/test-vectors/mdns-v1.json").readText()).jsonObject
  }

  private val service: String get() = v.getValue("service").jsonPrimitive.content

  private fun list(k: String) = v.getValue(k).jsonArray.map { it.jsonObject }
  private fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
  private fun labels(e: JsonElement): List<String> = e.jsonArray.map { it.jsonPrimitive.content }
  private fun bytes(h: String) = ByteArray(h.length / 2) { h.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
  private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

  private fun record(o: JsonObject) = Mdns.Record(
    name = labels(o.getValue("name")),
    type = o.getValue("type").jsonPrimitive.int,
    ttl = o.getValue("ttl").jsonPrimitive.long,
    ptr = o["ptr"]?.let(::labels) ?: emptyList(),
    target = o["target"]?.let(::labels) ?: emptyList(),
    port = o["port"]?.jsonPrimitive?.int ?: 0,
    ipv4 = o["ipv4"]?.jsonPrimitive?.content ?: "",
  )

  @Test fun `پرسشِ PTR ِ یک‌باره همان بایت‌های dnspython است`() {
    assertEquals(Mdns.PDL, service)
    for (q in list("query")) assertEquals(q.s("hex"), hex(Mdns.query(service, q.getValue("id").jsonPrimitive.int)))
    val back = assertNotNull(Mdns.parse(Mdns.query(service, 0x1234)))
    assertEquals(Mdns.Message(0x1234, false, emptyList()), back)
  }

  @Test fun `خوانشِ بسته همان خوانشِ dnspython است و بیتِ cache-flush کنار می‌رود`() {
    assertTrue(list("parse").size >= 8)
    for (c in list("parse")) {
      val out = c.getValue("out").jsonObject
      val got = assertNotNull(Mdns.parse(bytes(c.s("hex"))), c.s("name"))
      assertEquals(out.getValue("id").jsonPrimitive.int, got.id, c.s("name"))
      assertEquals(out.getValue("response").jsonPrimitive.boolean, got.response, c.s("name"))
      assertEquals(out.getValue("records").jsonArray.map { record(it.jsonObject) }, got.records, c.s("name"))
    }
  }

  @Test fun `بستهٔ بدشکل یا پاسخِ خطا هیچ است و هیچ برشی استثنا نمی‌دهد`() {
    assertTrue(list("malformed").size >= 9)
    for (m in list("malformed")) assertNull(Mdns.parse(bytes(m.s("hex"))), m.s("name") + " — " + m.s("why"))
    val full = bytes(list("parse").first().s("hex"))
    for (n in 0 until full.size) Mdns.parse(full.copyOf(n))
  }

  @Test fun `از پاسخ‌ها فقط چاپگرِ خام با نشانیِ خصوصی می‌ماند، یکتا و به ترتیبِ نام`() {
    assertTrue(list("printers").size >= 12)
    for (c in list("printers")) {
      val messages = c.getValue("packets").jsonArray.mapNotNull { Mdns.parse(bytes(it.jsonPrimitive.content)) }
      val want = c.getValue("out").jsonArray.map { it.jsonObject.let { o -> o.s("link") to o.s("name") } }
      val got = Mdns.printers(messages, service)
      assertEquals(want, got.map { it.link to it.name }, c.s("name"))
      assertTrue(got.all { it.via == FoundPrinter.Via.NETWORK && it.printerLike }, c.s("name"))
    }
  }

  @Test fun `نامِ نمایشی بی نویسهٔ کنترلی و جهت‌دهندهٔ پنهان است`() {
    assertEquals("Front Desk", Mdns.clean("‮Front\u0007Desk‏"))
    assertEquals("چاپگر صندوق", Mdns.clean(" چاپگر صندوق⁦"))
  }
}
