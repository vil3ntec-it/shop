package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.audit.AuditChain
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.platform.DocChainSync
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import java.io.File

/**
 *  STF-09 — مرزِ سکوی `AuditSeal`: نشستِ همان لحظه، پرچم و پروندهٔ لنگر (گوشی و کامپیوتر).
 *
 *  - کاربر = کاربرِ صندوق اگر هست، وگرنه حسابِ واردشده (`TillUser.meId`)؛ نقش = همان `ShopRole.current`؛ دستگاه =
 *    `SyncStore.deviceUid` (همان شناسه‌ای که همگام‌سازی با آن می‌فرستد).
 *  - زنجیره فقط با پرچمِ `shop.doc_chain` (همان پاسخِ آخرِ `DocChainSync`، پیش‌فرض خاموش)؛ مُهر همیشه.
 *  - لنگر در `noBackupFilesDir/audit-chain/<حساب>/<دکان>.anchor` (بیرون از پشتیبانِ اندروید، همان روحِ DATA-06 و
 *    زنجیرهٔ اسناد): گوشیِ بازیابی‌شده زنجیرهٔ کهنه را ادامه نمی‌دهد و دورهٔ تازه می‌گیرد. نوشتن اتمی (`DiskWrite`).
 */
object AuditSealAndroid {

  fun plan(context: Context, prior: ShopData, base: ShopData, next: ShopData): AuditSeal.Plan =
    AuditSeal.plan(prior, base, next, session = { session(context) }, anchors = anchorStore(context))

  fun session(context: Context): AuditSeal.Session? = runCatching {
    val app = context.applicationContext ?: context
    AuditSeal.Session(
      AuditChain.Stamp(
        userId = TillUser.meId(app).ifBlank { null },
        userRole = ShopRole.current(app).ifBlank { null },
        deviceId = SyncStore(app).deviceUid,
      ),
      chainOn = DocChainSync.enabled(app),
    )
  }.getOrNull()

  /** شناسهٔ همین دستگاه برای «این دستگاه» ِ صفحهٔ سابقه */
  fun deviceId(context: Context): String = runCatching { SyncStore(context.applicationContext ?: context).deviceUid }.getOrDefault("")

  /** نامِ کاربرانی که همین دستگاه می‌شناسد (حسابِ واردشده و کاربرانِ صندوق) — شناسهٔ دیگر همان شناسه می‌ماند */
  fun names(context: Context): Map<String, String> = runCatching {
    val app = context.applicationContext ?: context
    val sync = SyncStore(app)
    buildMap {
      StaffPins(app).all().forEach { if (it.name.isNotBlank()) put(it.userId, it.name) }
      if (sync.accountId.isNotBlank() && sync.accountName.isNotBlank()) put(sync.accountId, sync.accountName)
    }
  }.getOrDefault(emptyMap())

  /** لنگرِ همین حساب و دکان برای سنجشِ صفحه؛ نبود ⇐ null (بی سنجشِ دم) */
  fun anchors(context: Context): AuditChain.Anchors? = runCatching { anchorStore(context).read() }.getOrNull()

  fun anchorStore(context: Context): AuditSeal.AnchorStore = FileAnchors(file(context, "anchor"))

  /** «تا کجا به سرور گفته شد» (`core/audit/AuditAnchor.Sent`، `AuditAnchorSync`) — کنارِ همان لنگر، بیرون از پشتیبان */
  fun sentFile(context: Context): File = file(context, "sent")

  private fun file(context: Context, ext: String): File {
    val app = context.applicationContext ?: context
    val account = runCatching { SyncStateV1.currentAccount(app) }.getOrDefault("").ifBlank { "local" }
    val shop = LedgerOwner.currentShop(app).ifBlank { "local" }
    return File(app.noBackupFilesDir, "audit-chain/${safe(account)}/${safe(shop)}.$ext")
  }

  private fun safe(s: String): String = s.replace(Regex("[^A-Za-z0-9_.-]"), "_").take(80)

  private class FileAnchors(private val file: File) : AuditSeal.AnchorStore {
    override fun read(): AuditChain.Anchors? =
      if (file.isFile) AuditChain.Anchors.decode(file.readText(Charsets.UTF_8)) else null

    override fun write(anchors: AuditChain.Anchors) {
      file.parentFile?.mkdirs()
      DiskWrite.text(file, anchors.encode())
    }
  }
}
