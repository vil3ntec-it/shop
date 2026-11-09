package ir.vil3ntec.tohid.sync.platform

import android.content.Context
import ir.vil3ntec.tohid.core.audit.AuditAnchor
import ir.vil3ntec.tohid.data.AuditSealAndroid
import ir.vil3ntec.tohid.data.DiskWrite
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 *  STF-09 — مرزِ سکوی لنگرِ سرورِ زنجیرهٔ سابقهٔ عملیات: دیسک و شبکه برای `core/audit/AuditAnchor` (همهٔ تصمیم‌ها
 *  آن‌جاست؛ آزمون: `AuditAnchorTest`).
 *
 *  - [cycle] پس از هر دورِ `SyncV1Worker` (گوشی و کامپیوتر): سرِ هر زنجیرهٔ همین دستگاه از لنگرِ محلی
 *    (`AuditSealAndroid.anchors`) — فقط وقتی با «آخرین فرستاده» فرق دارد — به `POST /api/sync/v1/audit-anchor`.
 *    «آخرین فرستاده» کنارِ لنگر (`AuditSealAndroid.sentFile`، بیرون از پشتیبان، نوشتنِ اتمی با `DiskWrite`).
 *  - [verdict] حکمِ سرور برای صفحهٔ «سابقهٔ عملیات» (`GET`، فقط `audit.view`).
 *  - ⛔ پشتِ همان پرچمِ خاموشِ `shop.doc_chain` (`DocChainSync.enabled`، پاسخِ ذخیره‌شدهٔ سرور): خاموش ⇒ هیچ درخواستی.
 *  - ⛔ هیچ شکستی همگام‌سازی را نمی‌گیرد: هر خطا همین‌جا بلعیده می‌شود (جز لغوِ کوروتین) و دورِ بعد همان سر
 *    دوباره می‌رود؛ سرور تکرار را نمی‌نویسد. حالتِ آموزشی هیچ.
 */
object AuditAnchorSync {
  private val lock = Mutex()

  private suspend fun <T> quiet(block: suspend () -> T): T? = try {
    block()
  } catch (c: CancellationException) {
    throw c
  } catch (e: Exception) {
    null
  }

  /** یک دور؛ صدازننده (`SyncV1Worker`) شکست را نمی‌بیند */
  suspend fun cycle(context: Context) {
    if (ir.vil3ntec.tohid.data.TrainingMode.on) return
    val app = context.applicationContext ?: context
    if (!DocChainSync.enabled(app)) return
    val device = SyncStore(app).deviceUid
    if (device.isBlank()) return
    lock.withLock {
      withContext(Dispatchers.IO) {
        val anchors = AuditSealAndroid.anchors(app) ?: return@withContext
        val file = AuditSealAndroid.sentFile(app)
        val sent = AuditAnchor.Sent.decode(runCatching { if (file.isFile) file.readText(Charsets.UTF_8) else null }.getOrNull())
        val heads = AuditAnchor.due(device, anchors, sent)
        if (heads.isEmpty()) return@withContext
        val reply = quiet { AuditAnchor.reply(Backend.api(app).post(AuditAnchor.PATH, AuditAnchor.body(device, heads))) }
        val next = AuditAnchor.after(sent, heads, reply)
        if (next != sent) {
          runCatching {
            file.parentFile?.mkdirs()
            DiskWrite.text(file, next.encode())
          }
        }
      }
    }
  }

  /** حکمِ سرور روی رونوشت و لنگرهای همهٔ دستگاه‌های دکان؛ پرچمِ خاموش، بی‌اینترنت یا بی اجازه ⇐ null */
  suspend fun verdict(context: Context): AuditAnchor.Verdict? {
    val app = context.applicationContext ?: context
    if (!DocChainSync.enabled(app)) return null
    return withContext(Dispatchers.IO) { quiet { AuditAnchor.verdict(Backend.api(app).get(AuditAnchor.PATH)) } }
  }
}
