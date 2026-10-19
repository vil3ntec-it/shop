package ir.vil3ntec.tohid.core.hw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 *  هستهٔ دستگاه‌ها (HW-01..04، HW-09، POS-29) روی بردارهای مشترکِ
 *  `core/test-vectors/hw-v1.json` — همان فایلی که `license/hw-core.js`
 *  (server/test/hw-core.test.js) می‌دواند. بایت‌ها باید یکی باشند.
 *
 *  `legacyRaster` از کدِ پیش از این تغییر گرفته شده (همان الگوریتمِ
 *  `ThermalPrinter.writeImage`): پیش‌فرضِ پروفایل باید عیناً همان باشد.
 *  چاپگرِ واقعی این‌جا نیست؛ این آزمون بایت را می‌پاید، نه کاغذ را.
 */
class HwCoreTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/hw-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/hw-v1.json پیدا نشد")
  }

  private fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
  private fun JsonObject.i(k: String) = getValue(k).jsonPrimitive.int
  private fun list(k: String) = v.getValue(k).jsonArray.map { it.jsonObject }

  private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
  private fun sha(b: ByteArray) = hex(MessageDigest.getInstance("SHA-256").digest(b))

  /** همان الگوی `pattern`ِ بردارها */
  private fun pattern(w: Int, h: Int): MonoImage {
    val argb = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      val a = if ((x + 2 * y) % 7 == 0) 100 else 255
      val r = (x * 37 + y * 11) and 255
      val g = (x * 13 + y * 29) and 255
      val b = (x * 7 + y * 3) and 255
      argb[y * w + x] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    return MonoImage.fromArgb(argb, w, h, w)
  }

  private fun same(bytes: ByteArray, want: JsonObject, label: String) {
    assertEquals("$label — طول", want.i("len"), bytes.size)
    assertEquals("$label — سر", want.s("head"), hex(bytes.copyOfRange(0, minOf(16, bytes.size))))
    assertEquals("$label — ته", want.s("tail"), hex(bytes.copyOfRange(maxOf(0, bytes.size - 8), bytes.size)))
    assertEquals("$label — sha256", want.s("sha256"), sha(bytes))
  }

  private fun profileOf(o: JsonObject?): PrinterProfile {
    if (o == null) return PrinterProfile()
    fun str(k: String) = o[k]?.jsonPrimitive?.content
    return PrinterProfile(
      paperDots = PrinterProfile.paper(o["paperDots"]?.jsonPrimitive?.int ?: PrinterProfile.WIDTH_58MM),
      mode = ImageMode.of(str("mode")),
      cut = CutMode.of(str("cut")),
      feedLines = o["feedLines"]?.jsonPrimitive?.int ?: 3,
      drawerPin = o["drawerPin"]?.jsonPrimitive?.int ?: 0,
    )
  }

  @Test fun `پیش‌فرضِ پروفایل همان بایت‌های پیشین است`() {
    for (l in list("legacyRaster")) {
      val w = l.i("w"); val h = l.i("h")
      same(EscPos.document(pattern(w, h), PrinterProfile(paperDots = w)), l, "${w}x$h")
    }
  }

  @Test fun `سندها — ستونی، برشِ کامل با کشو، بی‌برش`() {
    for (d in list("documents")) {
      val img = pattern(d.i("w"), d.i("h"))
      same(EscPos.document(img, profileOf(d.getValue("profile").jsonObject), d.getValue("openDrawer").jsonPrimitive.boolean), d, d.s("name"))
    }
  }

  @Test fun `کشو، برش و وضعیتِ کاغذ`() {
    for (d in list("drawer")) assertEquals(d.s("hex"), hex(EscPos.drawerKick(d.i("pin"), d.i("onMs"), d.i("offMs"))))
    for (d in list("drawerOnly")) assertEquals(d.s("hex"), hex(EscPos.drawerOnly(profileOf(d.getValue("profile").jsonObject))))
    for (c in list("cut")) assertEquals(c.s("mode"), c.s("hex"), hex(EscPos.cut(CutMode.of(c.s("mode")))))
    for (s in list("paperStatus")) {
      val got = EscPos.parsePaperStatus(s.i("byte"))
      val want = s.getValue("out")
      if (want is JsonNull) assertNull(got)
      else {
        val o = want.jsonObject
        assertEquals(PaperStatus(o.getValue("nearEnd").jsonPrimitive.boolean, o.getValue("out").jsonPrimitive.boolean), got)
      }
    }
  }

  @Test fun `EAN-13 و Code128 — ماژول به ماژول`() {
    for (e in list("ean13")) {
      val code = e.s("code")
      assertEquals(code, e.getValue("valid").jsonPrimitive.boolean, Ean13.isValid(code))
      if (Ean13.isValid(code)) assertEquals(code, e.s("modules"), Barcodes.modulesString(Ean13.modules(code)))
    }
    for (c in list("code128")) {
      val text = c.s("text")
      assertEquals(text, c.getValue("symbols").jsonArray.map { it.jsonPrimitive.int }, Code128.symbols(text).toList())
      assertEquals(text, c.s("modules"), Barcodes.modulesString(Code128.modules(text)))
    }
    for (bad in v.getValue("code128Errors").jsonArray.map { it.jsonPrimitive.content }) {
      try { Code128.symbols(bad); fail("باید رد می‌شد: $bad") } catch (e: IllegalArgumentException) { }
    }
    for (p in list("forProduct")) {
      val want = p.getValue("kind").let { if (it is JsonNull) null else it.jsonPrimitive.content }
      assertEquals(p.s("code"), want, Barcodes.forProduct(p.s("code"))?.kind?.name)
    }
  }

  @Test fun `هر نمادِ Code128 یازده ماژول است و پایان سیزده`() {
    for (sym in 0..105) assertEquals("نماد $sym", 11, Code128.widths(sym).sumOf { it - '0' })
    assertEquals(13, Code128.widths(106).sumOf { it - '0' })
  }

  @Test fun `برچسب — TSPL، ESC-POS، A4 و پهنای خط`() {
    val checker = MonoImage(20, 4)
    for (y in 0 until 4) for (x in 0 until 20) if ((x + y) % 3 == 0) checker[x, y] = true
    val t = v.getValue("tspl").jsonObject
    val size = t.getValue("size").jsonObject
    val bytes = Labels.tspl(checker, Labels.Size(size.i("widthMm"), size.i("heightMm"), size.i("gapMm")), t.i("copies"))
    same(bytes, t, "tspl")
    assertEquals(t.s("text"), String(bytes.copyOfRange(0, t.s("text").length), Charsets.ISO_8859_1))
    val le = v.getValue("labelEscPos").jsonObject
    same(Labels.escPos(checker, le.i("copies")), le, "label escpos")
    for (m in list("moduleDots")) assertEquals(m.toString(), m.i("out"), Labels.moduleDots(m.i("modules"), m.i("maxDots")))
    for (c in list("a4Cell")) {
      val want = c.getValue("out").jsonArray.map { it.jsonPrimitive.int }
      val got = Labels.A4.cell(c.i("index"))
      assertEquals(want, listOf(got.first, got.second))
    }
  }

  @Test fun `بارکدِ ترازو — S04`() {
    val w = v.getValue("weight").jsonObject
    val rules = w.getValue("rules").jsonArray.map { it.jsonObject }.map {
      WeightBarcode.Rule(it.s("prefix"), it.i("itemDigits"), it.i("valueDigits"), WeightBarcode.Kind.of(it.s("kind")), it.i("decimals"))
    }
    for (c in w.getValue("cases").jsonArray.map { it.jsonObject }) {
      val out = c.getValue("out").jsonObject
      when (val r = WeightBarcode.parse(c.s("code"), rules)) {
        is WeightBarcode.Result.Hit -> {
          assertEquals(c.s("code"), "hit", out.s("type"))
          assertEquals(out.s("prefix"), r.rule.prefix)
          assertEquals(out.s("kind"), r.rule.kind.key)
          assertEquals(out.s("itemCode"), r.itemCode)
          assertEquals(out.getValue("value").jsonPrimitive.double, r.value, 0.0)
          assertEquals(out.getValue("raw").jsonPrimitive.long, r.raw)
        }
        is WeightBarcode.Result.BadCheck -> assertEquals(c.s("code"), "badCheck", out.s("type"))
        WeightBarcode.Result.NotScale -> assertEquals(c.s("code"), "notScale", out.s("type"))
      }
    }
    assertEquals(WeightBarcode.Result.NotScale, WeightBarcode.parse("2100150012507", emptyList()))
    assertEquals("2100150012507", WeightBarcode.build(rules[0], "150", 1250))
  }

  @Test fun `ترازوی NCI — قابِ پاسخ`() {
    for (n in list("nci")) {
      val bytes = n.s("ascii").toByteArray(Charsets.ISO_8859_1)
      assertTrue(n.s("name"), NciScale.complete(bytes))
      val got = NciScale.parse(bytes)
      val want = n.getValue("out")
      if (want is JsonNull) { assertNull(n.s("name"), got); continue }
      val o = want.jsonObject
      requireNotNull(got)
      assertEquals(n.s("name"), o.getValue("kg").jsonPrimitive.double, got.kg!!, 1e-9)
      assertEquals(o.s("unit"), got.unit)
      assertEquals(o.getValue("motion").jsonPrimitive.boolean, got.motion)
      assertEquals(o.getValue("zero").jsonPrimitive.boolean, got.zero)
      assertEquals(o.getValue("over").jsonPrimitive.boolean, got.over)
      assertEquals(o.getValue("under").jsonPrimitive.boolean, got.under)
      assertEquals(o.getValue("error").jsonPrimitive.boolean, got.error)
      assertEquals(o.getValue("usable").jsonPrimitive.boolean, got.usable)
    }
    assertArrayEquals(byteArrayOf('W'.code.toByte(), 0x0D), NciScale.request())
  }

  @Test fun `پیش‌نمایش — decode(document(img)) همان تصویر است`() {
    for (mode in ImageMode.entries) for ((w, h) in listOf(384 to 40, 576 to 130)) {
      val img = pattern(w, h)
      val r = EscPosPreview.decode(EscPos.document(img, PrinterProfile(paperDots = w, mode = mode), openDrawer = true), w)
      assertEquals(emptyList<String>(), r.unknown)
      assertEquals(1, r.cuts)
      assertEquals(1, r.drawerKicks)
      val paper = requireNotNull(r.paper)
      assertArrayEquals("$mode ${w}x$h", img.bits, paper.bits.copyOfRange(0, img.bits.size))
    }
    assertEquals(listOf("0x99@2"), EscPosPreview.decode(byteArrayOf(0x1B, 0x40, 0x99.toByte()), 384).unknown)
  }
}
