package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.sync.PushSince
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.platform.HubExchangeV1
import ir.vil3ntec.tohid.platform.HubLinkV1Android
import ir.vil3ntec.tohid.platform.HubPeerLink

/**
 *  S29/SYN-09: دورِ همگام‌سازی به سرور **نرسید** ⇒ همان دسته‌ها به هابِ جفت‌شدهٔ شبکهٔ دکان و opهای دستگاه‌های دیگر از
 *  آن (`HubLinkV1Android`؛ پرچمِ `app.multi_server` یا تنظیمِ `hub.peer` خاموش ⇒ هیچ). صدازننده `SyncV1Worker` پس از
 *  `runOnce` ِ شکست‌خورده است؛ موتور یک خط هم بزرگ‌تر نشد.
 *
 *  - صف، شکست، `lastOkAt` و cursor همان «به سرور نرسیدیم» می‌مانند؛ op فقط با پاسخِ خودِ سرور از صف بیرون می‌رود.
 *  - op ِ دستگاهِ دیگر با همان نشاندنِ pull (`absorbRemote`، cursor ِ `null`، `keepPending`) «موقت» می‌نشیند؛ pull ِ بعدیِ
 *    سرور از همان cursor ِ پیشین همان opها را به ترتیبِ سرور دوباره می‌آورد و مرجع است.
 *  - بازیابی/بازسازیِ در راه (`restorePending`، `restoredRebuild`، `recoverArchive`) ⇒ هیچ (همان جاهایی که pull هم نمی‌رود).
 *  - `caps` همان قاعدهٔ `pushQueue` (A00 و opِ دوباره‌فرستاده بی نشان).
 */
internal fun SyncV1Engine.hubExchange(context: Context, error: Throwable?, read: () -> ShopData, write: (ShopData) -> Unit): HubExchangeV1.Outcome? {
  if (error == null || !HubPeerLink.cloudDown(error)) return null
  val st = state
  if (st.restorePending || st.restoredRebuild || st.recoverArchive) return null
  val device = deviceId()
  val link = HubLinkV1Android.of(context, device) ?: return null
  return link.exchange(
    device, queuedOps(), { b -> LedgerDiff.capsFor(b.map { it.opId }, st.legacyOpsUpTo) && b.none { restore.isReplay(it.opId) } },
    LedgerDiff.wireSchema(st.serverSchema), PushSince.since(st.pushSince, st.cursor), st.cursor,
  ) { ops -> absorbRemote(ops, read, write, cursor = null, keepPending = true) }
}
