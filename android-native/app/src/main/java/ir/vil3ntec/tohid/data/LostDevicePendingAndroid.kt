package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.data.LostDevicePending.plus
import ir.vil3ntec.tohid.data.repo.Backend
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 *  S44 (د۱۸) — مرزِ سکوی [LostDevicePending]: صفِ همین دستگاه برای پشتیبان (`BackupBundle.write`) و فرستادنِ صفِ پشتیبانِ
 *  دستگاهِ گم‌شده به `POST /shop/devices/:id/replay`. ⛔ حالتِ آموزشی ⇒ پشتیبانِ بی صف. خطای سرور ⇒ همان خطا به صفحه.
 */
object LostDevicePendingAndroid {
  /** متنِ `pending-ops.json` یا null (صفِ خالی، حالتِ آموزشی، یا خواندنِ صف شکست خورد) */
  fun of(context: Context): String? = runCatching {
    if (TrainingMode.on) return@runCatching null
    val engine = ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context)
    LostDevicePending.encode(engine.deviceId(), engine.queuedOps().map { it.toJson() })
  }.getOrNull()

  /**
   *  صفِ نرفتهٔ یک فایلِ پشتیبان (ZIP ِ کامل، ساده یا رمزدار — `BackupSeal.unwrap`) — **فقط** همان قلمِ `pending-ops.json`:
   *  نه دفتر، نه عکس، نه تنظیم خوانده یا جایی نوشته می‌شود (پوشهٔ موقتِ بازیابی دست نمی‌خورد). پشتیبانِ بی صف (کهنه یا
   *  «فقط دفتر») ⇐ null. رمزدار با عبارت و کلید این‌جا نیست ⇐ همان `AtRestException` (`BackupSeal.needsPhrase`).
   */
  fun read(context: Context, input: InputStream, typed: String? = null): LostDevicePending.Pending? {
    val plain = BackupSeal.unwrap(context, input, typed)
    ZipInputStream(plain.buffered()).use { zip ->
      while (true) {
        val entry = zip.nextEntry ?: return null
        if (!entry.isDirectory && entry.name == LostDevicePending.ENTRY) {
          return LostDevicePending.parse(zip.readBytes().toString(Charsets.UTF_8))
        }
      }
    }
  }

  /** صفِ پشتیبان ⇒ سرور، دسته‌دسته؛ [deviceRowId] شناسهٔ ردیفِ دستگاهِ غیرفعال در فهرستِ دستگاه‌ها */
  suspend fun replay(context: Context, deviceRowId: String, pending: LostDevicePending.Pending): Result<LostDevicePending.Counts> = runCatching {
    var total = LostDevicePending.Counts(0, 0, 0)
    for (batch in LostDevicePending.batches(pending)) {
      val res = Backend.api(context).post("/shop/devices/$deviceRowId/replay", LostDevicePending.body(pending.deviceId, batch))
      total += LostDevicePending.counts(res, batch.size)
    }
    total
  }
}
