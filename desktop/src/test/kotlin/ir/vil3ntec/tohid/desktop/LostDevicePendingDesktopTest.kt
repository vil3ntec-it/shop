package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.LostDevicePending
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** S44 (د۱۸) — صفِ نرفتهٔ همگام‌سازی درونِ پشتیبان (`pending-ops.json`) و دسته‌های فرستادنش برای دستگاهِ گم‌شده. */
class LostDevicePendingDesktopTest {

  private fun op(i: Int): JsonObject = Json.parseToJsonElement(
    """{"op_id":"01JLOST${i.toString().padStart(19, '0')}","table":"sales","row_id":"s$i","type":"insert","ts":$i,"fields":{"total":$i}}""",
  ).jsonObject

  private fun zip(vararg entries: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z -> for ((n, t) in entries) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    return out.toByteArray()
  }

  @Test fun `صفِ پشتیبان همان opها با شناسهٔ دستگاه؛ خراب، خالی یا نسخهٔ ناشناخته هیچ`() {
    val ops = (1..3).map(::op)
    val text = LostDevicePending.encode("dev-raw-1", ops)!!
    val p = LostDevicePending.parse(text)!!
    assertEquals("dev-raw-1", p.deviceId)
    assertEquals(ops, p.ops)
    assertNull(LostDevicePending.encode("", ops))
    assertNull(LostDevicePending.encode("d", emptyList()))
    assertNull(LostDevicePending.parse("not json"))
    assertNull(LostDevicePending.parse("""{"v":2,"deviceId":"d","ops":[{}]}"""))
    assertNull(LostDevicePending.parse("""{"v":1,"deviceId":"d","ops":[]}"""))
  }

  @Test fun `دسته‌های ۵۰۰تایی، بدنه و شمارشِ پاسخ`() {
    val p = LostDevicePending.Pending("dev-1", (1..1201).map(::op))
    assertEquals(listOf(500, 500, 201), LostDevicePending.batches(p).map { it.size })
    val b = LostDevicePending.body("dev-1", p.ops.take(1))
    assertEquals("dev-1", b["deviceId"].toString().trim('"'))
    val res = Json.parseToJsonElement("""{"ok":true,"counts":{"applied":3,"duplicate":1,"rejected":0}}""").jsonObject
    assertEquals(LostDevicePending.Counts(3, 1, 0), LostDevicePending.counts(res, 4))
    assertEquals(LostDevicePending.Counts(0, 0, 4), LostDevicePending.counts(JsonObject(emptyMap()), 4))
  }

  @Test fun `BackupBundle قلمِ صف را می‌خواند و پشتیبانِ بی آن همان امروز است`() {
    val ctx = DesktopContext
    val withQueue = zip(
      BackupBundle.LEDGER_ENTRY to "{}",
      LostDevicePending.ENTRY to LostDevicePending.encode("dev-lost", listOf(op(7)))!!,
    )
    val a = BackupBundle.read(ctx, ByteArrayInputStream(withQueue)) { Result.success(ShopData()) }.getOrThrow()
    assertEquals("dev-lost", a.pending?.deviceId)
    assertEquals(listOf(op(7)), a.pending?.ops)
    val plain = BackupBundle.read(ctx, ByteArrayInputStream(zip(BackupBundle.LEDGER_ENTRY to "{}"))) { Result.success(ShopData()) }.getOrThrow()
    assertNull(plain.pending)
    BackupBundle.dropStaging(ctx)
  }
}
