package ir.vil3ntec.tohid.core.sync.v3

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 *  HLCِ برنامه = HLCِ سرور (SYN-05): همان `core/test-vectors/hlc-v1.json` که
 *  `server/scripts/sync-v3-vectors.js` از `server/src/lib/hlc.js` می‌سازد و
 *  `server/test/sync-v3-client.test.js` روی وب می‌دواند.
 */
class HlcVectorsTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/hlc-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/hlc-v1.json پیدا نشد")
  }

  private fun rows(key: String): List<JsonArray> = v.getValue(key).jsonObject.getValue("rows").jsonArray.map { it.jsonArray }
  private fun str(e: kotlinx.serialization.json.JsonElement): String? = if (e is JsonNull) null else e.jsonPrimitive.content

  @Test fun parse() {
    for (r in rows("parse")) {
      val got = Hlc.parse(str(r[0]))
      val want = r[1]
      if (want is JsonNull) {
        assertEquals("parse(${r[0]})", null, got)
      } else {
        val o = want.jsonObject
        assertEquals("parse(${r[0]})", HlcTime(o.getValue("wall").jsonPrimitive.long, o.getValue("counter").jsonPrimitive.long, o.getValue("node").jsonPrimitive.content), got)
      }
    }
  }

  @Test fun formatCanonicalCompare() {
    for (r in rows("format")) {
      assertEquals(r[3].jsonPrimitive.content, Hlc.format(r[0].jsonPrimitive.long, r[1].jsonPrimitive.long, r[2].jsonPrimitive.content))
    }
    for (r in rows("canonical")) assertEquals("canonical(${r[0]})", r[1].jsonPrimitive.content, Hlc.canonical(str(r[0])))
    for (r in rows("compare")) {
      assertEquals("${r[0]} ⟷ ${r[1]}", r[2].jsonPrimitive.int, Hlc.compare(r[0].jsonPrimitive.content, r[1].jsonPrimitive.content))
    }
    assertEquals(v.getValue("skewMs").jsonPrimitive.long, Hlc.SKEW_MS)
  }

  @Test fun fromTs() {
    for (r in rows("fromTs")) {
      //  ts ممکن است 1e16 باشد — در JSON عددِ اعشاری
      val ts = r[0].jsonPrimitive.content.toDouble().toLong()
      assertEquals(r[3].jsonPrimitive.content, Hlc.fromTs(ts, r[1].jsonPrimitive.int, r[2].jsonPrimitive.content))
    }
  }

  @Test fun clockScripts() {
    for (sc in v.getValue("clock").jsonObject.getValue("scripts").jsonArray.map { it.jsonObject }) {
      var t = 0L
      val name = sc.getValue("name").jsonPrimitive.content
      val clock = HlcClock(sc.getValue("node").jsonPrimitive.content, { t }, sc.getValue("skewMs").jsonPrimitive.long)
      for (step in sc.getValue("steps").jsonArray.map { it.jsonArray }) {
        t = step[0].jsonPrimitive.long
        val op = step[1].jsonPrimitive.content
        val arg = str(step[2])
        val out = step[3]
        when (op) {
          "tick" -> assertEquals("$name tick", out.jsonPrimitive.content, clock.tick())
          "peek" -> assertEquals("$name peek", out.jsonPrimitive.content, clock.peek())
          "observe" -> clock.observe(arg)
          "isSkewed" -> assertEquals("$name isSkewed($arg)", out.jsonPrimitive.content.toBoolean(), clock.isSkewed(arg))
          "recv" -> {
            val o = out.jsonObject
            val r = clock.recv(arg)
            assertEquals("$name recv($arg)", o.getValue("skewed").jsonPrimitive.content.toBoolean(), r.skewed)
            assertEquals("$name recv($arg)", o.getValue("hlc").jsonPrimitive.content, r.hlc)
          }
          else -> error(op)
        }
      }
    }
  }
}
