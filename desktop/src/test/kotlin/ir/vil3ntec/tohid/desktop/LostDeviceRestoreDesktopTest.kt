package ir.vil3ntec.tohid.desktop

import ir.vil3ntec.tohid.core.i18n.StrLostDevice
import ir.vil3ntec.tohid.core.model.ShopDeviceDto
import ir.vil3ntec.tohid.core.model.ShopDevicesDto
import ir.vil3ntec.tohid.core.net.ApiJson
import ir.vil3ntec.tohid.data.BackupBundle
import ir.vil3ntec.tohid.data.LostDevicePending
import ir.vil3ntec.tohid.data.LostDevicePendingAndroid
import ir.vil3ntec.tohid.data.LostDeviceRestore
import ir.vil3ntec.tohid.ui.v3.account.team.MemberModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 *  S44 — دکمهٔ «بازیابی از پشتیبان» ِ دستگاهِ غیرفعال (گوشیِ نسخهٔ ۳ و کامپیوتر، `LostDeviceReplaySheet`): کی دیده می‌شود
 *  (فقط غیرفعال، فقط صاحب، فقط با `replay` ِ سرور)، خواندنِ **فقط** صفِ نرفتهٔ فایلِ پشتیبان (`LostDevicePendingAndroid.read`؛
 *  هیچ پوشهٔ موقتی ساخته نمی‌شود)، و پیامِ هر پاسخِ سرور.
 */
class LostDeviceRestoreDesktopTest {

  private fun op(i: Int): JsonObject = Json.parseToJsonElement(
    """{"op_id":"01JREST${i.toString().padStart(19, '0')}","table":"sales","row_id":"s$i","type":"insert","ts":$i,"fields":{"total":$i}}""",
  ).jsonObject

  private fun zip(vararg entries: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { z -> for ((n, t) in entries) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() } }
    return out.toByteArray()
  }

  private val blocked = ShopDeviceDto(id = "d1", status = "revoked", disabledAt = 1)

  @Test fun `دکمه فقط برای دستگاهِ غیرفعال، صاحب و پرچمِ روشن`() {
    assertTrue(MemberModel.canReplay(blocked, canEdit = true, replay = true))
    assertFalse("پرچمِ خاموش", MemberModel.canReplay(blocked, canEdit = true, replay = false))
    assertFalse("مدیر/شاگرد", MemberModel.canReplay(blocked, canEdit = false, replay = true))
    assertFalse("دستگاهِ زنده", MemberModel.canReplay(blocked.copy(disabledAt = null, status = "active"), canEdit = true, replay = true))
    assertFalse("همین دستگاه", MemberModel.canReplay(blocked.copy(current = true), canEdit = true, replay = true))
  }

  @Test fun `پاسخِ سرور — بی replay همان پیشین، با آن روشن`() {
    assertFalse(ApiJson.decode<ShopDevicesDto>(Json.parseToJsonElement("""{"devices":[{"id":"d1"}]}""")).replay)
    val on = ApiJson.decode<ShopDevicesDto>(Json.parseToJsonElement("""{"devices":[{"id":"d1","disabledAt":5}],"replay":true}"""))
    assertTrue(on.replay)
    assertEquals(5L, on.devices.single().disabledAt)
  }

  @Test fun `از فایلِ پشتیبان فقط صفِ نرفته خوانده می‌شود؛ پشتیبانِ بی صف یا فقط‌دفتر هیچ`() {
    val ctx = DesktopContext
    BackupBundle.dropStaging(ctx)
    val full = zip(
      BackupBundle.LEDGER_ENTRY to """{"sales":[{"id":"x"}]}""",
      "photos/p1.webp" to "img",
      LostDevicePending.ENTRY to LostDevicePending.encode("dev-lost", (1..3).map(::op))!!,
    )
    val p = LostDevicePendingAndroid.read(ctx, ByteArrayInputStream(full))!!
    assertEquals("dev-lost", p.deviceId)
    assertEquals((1..3).map(::op), p.ops)
    assertTrue(LostDeviceRestore.afterRead(p) is LostDeviceRestore.Step.Ready)
    assertNull(LostDevicePendingAndroid.read(ctx, ByteArrayInputStream(zip(BackupBundle.LEDGER_ENTRY to "{}"))))
    assertNull("پشتیبانِ «فقط دفتر» (JSON)", LostDevicePendingAndroid.read(ctx, ByteArrayInputStream("""{"sales":[]}""".toByteArray())))
    assertEquals(LostDeviceRestore.Step.Failed(StrLostDevice.none), LostDeviceRestore.afterRead(null))
  }

  @Test fun `پیامِ هر ردِ سرور و شمارِ پایان`() {
    assertEquals(StrLostDevice.errMismatch, LostDeviceRestore.failText("device_mismatch", "x"))
    assertEquals(StrLostDevice.errNotDisabled, LostDeviceRestore.failText("device_not_disabled", "x"))
    assertEquals(StrLostDevice.errOff, LostDeviceRestore.failText("lost_device_replay_off", "x"))
    assertEquals("پیامِ سرور", LostDeviceRestore.failText("rate_limited", "پیامِ سرور"))
    assertEquals(StrLostDevice.errRead, LostDeviceRestore.failText(null, null))
    assertEquals(StrLostDevice.done(3, 1, 0), LostDeviceRestore.doneText(LostDevicePending.Counts(3, 1, 0)))
  }
}
