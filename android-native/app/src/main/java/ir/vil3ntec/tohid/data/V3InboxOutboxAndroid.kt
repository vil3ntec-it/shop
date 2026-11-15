package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.perm.RemoteApproval
import ir.vil3ntec.tohid.core.sync.V3InboxClient
import ir.vil3ntec.tohid.core.sync.V3InboxQueueStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.V3InboxHttp
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 *  SYN-07 / S28 — مرزِ سکوی صندوقِ تعارضِ v3 (`core/sync/V3InboxClient`): صفِ تصمیم‌های هر حساب در SharedPreferences ِ
 *  `tohid-v3-inbox`، و فرستادنش پس از هر دورِ `SyncV1Worker` (همان درِ دورِ v3) و از صفحه/نوارِ پوسته.
 *
 *  ⛔ فقط وقتی همین دستگاه Sync v3 است (`SyncChoice`، پیش‌فرض v1) و نقش صاحب/مدیر است (`data.delete.any` ِ سرور) — وگرنه
 *     هیچ درخواستی، هیچ صفی. صف فقط وقتی پر می‌شود که سرور پرچمِ `shop.sync_v3_inbox` را روشن گفته باشد (کلیدِ `inbox`)،
 *     پس با پرچمِ خاموش این‌جا همیشه خالی است و [flush] هیچ نمی‌فرستد.
 *  ⛔ یک [lock] برای همهٔ فرستادن‌ها (صفحه و کارگر): یک تصمیم دو بار هم‌زمان نمی‌رود؛ تکرارش هم در سرور بی‌اثر است.
 */
object V3InboxOutboxAndroid {
  private const val PREFS = "tohid-v3-inbox"
  val lock = Mutex()

  /** کلیدِ صفِ همین حساب (همان کلیدِ صفِ همگام‌سازی) */
  fun key(context: Context): String =
    "q." + runCatching { ir.vil3ntec.tohid.sync.v1.SyncV1Engine.of(context).currentKey() }.getOrDefault("")

  fun store(context: Context, key: String = key(context)): V3InboxQueueStore {
    val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    return object : V3InboxQueueStore {
      override fun read(): String? = prefs.getString(key, null)
      //  `commit` نه `apply`: تصمیم پیش از فرستادن روی دیسک است (بسته شدنِ برنامه در میانه گمش نمی‌کند)
      override fun write(text: String) { prefs.edit().putString(key, text).commit() }
    }
  }

  /** همین دستگاه می‌تواند صندوق را ببیند؟ (Sync v3 ِ دستگاه، صاحب/مدیر، سرورِ تنظیم‌شده، بیرون از حالتِ آموزشی) */
  fun eligible(context: Context): Boolean = runCatching {
    ir.vil3ntec.tohid.sync.v3.SyncChoice.isV3(context) && !TrainingMode.on &&
      ShopRole.current(context) in RemoteApproval.APPROVER_ROLES && Backend.isReady(context)
  }.getOrDefault(false)

  /** شناسهٔ v3 ِ همین دستگاه — همان `device_id` ِ push ِ موتور ([ir.vil3ntec.tohid.sync.v3.SyncV3Engine.deviceId]) */
  fun me(context: Context): String = runCatching {
    ir.vil3ntec.tohid.sync.v3.v3Node(ir.vil3ntec.tohid.sync.SyncStore(context).deviceUid).ifEmpty { "device" }
  }.getOrDefault("")

  fun client(context: Context): V3InboxClient? =
    if (!eligible(context)) null
    else V3InboxClient(V3InboxHttp(Backend.api(context)), store(context)) { System.currentTimeMillis() }

  /** پس از دورِ همگام‌سازی: صف تا ته؛ صفِ خالی یا دستگاهِ نامجاز ⇒ هیچ درخواستی */
  suspend fun flush(context: Context): Int {
    if (!eligible(context)) return 0
    val c = client(context) ?: return 0
    if (c.queued().isEmpty()) return 0
    return lock.withLock { c.flush().size }
  }
}
