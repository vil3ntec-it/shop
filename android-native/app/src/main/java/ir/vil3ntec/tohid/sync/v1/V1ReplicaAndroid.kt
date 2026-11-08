package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import ir.vil3ntec.tohid.core.sync.RestoredDevice
import ir.vil3ntec.tohid.core.sync.SyncReplica
import ir.vil3ntec.tohid.data.LedgerOwner
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.SyncStore
import kotlinx.serialization.json.JsonArray

/**
 *  `device_id` ِ همین حساب روی همین دستگاه برای سرور (push، pull، سریِ سند، سوکتِ زنده) — شرح سرِ
 *  `core/sync/SyncReplica.kt`. نخستین حساب (یا هر حسابی که پیش‌تر از این دستگاه push کرده) همان شناسهٔ خام؛ حسابِ
 *  دیگری که تازه روی همین گوشی شروع می‌کند (شاگرد با کدِ شاگرد پس از خروجِ صاحب) شناسهٔ خودش — یک بار انتخاب و در
 *  `SyncStateV1.replica` ِ همان حساب نگه داشته (`SyncV1Keys.move` با حالِ حساب جابه‌جایش می‌کند). جدا از
 *  `SyncV1Engine.kt` تا آن پرونده از سقفِ اندازه نگذرد.
 *
 *  DATA-06 (گوشیِ بازیابی‌شده از پشتیبانِ گوشیِ **دیگر**، شرح سرِ `core/sync/RestoredDevice.kt`): حالِ هر حساب به
 *  شناسهٔ همین دستگاه بسته است ([fence]). replica ِ نگه‌داشته‌ای که از `deviceUid` ِ امروز نیست هرگز برگردانده نمی‌شود.
 */
internal object V1Replica {

  /** همان `DocSeries.PREFS` (آن پرونده سقفِ وابستگیِ پلتفرمی دارد) — `DeviceOnlyPrefsTest` برابری را می‌سنجد */
  private const val DOC_SERIES = "tohid-doc-series"

  fun id(lock: Any, app: Context, store: SyncStore, state: SyncStateV1): String = synchronized(lock) {
    //  حالی که با رونوشتِ خامِ دستگاهِ دیگر آمد، پیش از نخستین درخواست (بی دفتر: فقط حصار، هرگز «بستن»)
    val verdict = fence(lock, app, store, state, ledger = null, queued = 0, baseline = null)
    val kept = state.replica
    //  ناخوانا ⇒ همان نگه‌داشته (هرگز چرخش بر شک)؛ وگرنه فقط replica ِ همین دستگاه
    if (kept.isNotBlank() && (verdict == RestoredDevice.Verdict.UNDECIDED || RestoredDevice.belongs(kept, store.deviceUid))) {
      return@synchronized kept
    }
    val device = store.deviceUid
    val account = SyncStateV1.currentAccount(app).trim()
    if (account.isEmpty()) return@synchronized device
    //  «تازه» = این حساب از این دستگاه هرگز push نکرده (Snapshot و صفِ نرفته بی‌خطرند: هیچ op ِ خودش با شناسهٔ
    //  خام پس از cursor روی سرور نیست که با شناسهٔ تازه «دیگری» دیده و دوباره نشانده شود)
    val id = SyncReplica.choose(device, account, store.replicaOwner, fresh = state.lastPushAt == 0L)
    if (id == device && store.replicaOwner.isBlank()) store.replicaOwner = account
    state.replica = id
    id
  }

  /**
   *  آیا حالِ این حساب مالِ دستگاهِ دیگری است — هنگامِ باز شدنِ دفتر (`SyncV1Engine.recover`)، پس از هر ورود
   *  (`AccountBind.signedIn`) و پیش از هر درخواست ([id]). تصمیم: `RestoredDevice.decide`.
   *
   *  حصار، به این ترتیب (هر گام دوباره‌پذیر؛ بسته شدن میانِ آن‌ها ⇒ دورِ بعد همان تصمیم و همان نتیجه):
   *   ۱) سایهٔ خالی (`tohid-sync-v1.xml` با پشتیبان نمی‌آید) ⇒ پایه = دفترِ همین حالا، **پیش از** نخستین ذخیره —
   *      همان کارِ نخستین `record`؛ بی این، نخستین فروشِ پس از بازیابی «پایه» می‌شد و هرگز به سرور نمی‌رفت.
   *   ۲) سریِ نگه‌داشتهٔ همین حساب کنار ⇒ `DocSeries.ensure` با replica ِ تازه از سرور.
   *   ۳) replica ِ تازه (`RestoredDevice.remap`)، بستن به همین دستگاه و «در انتظارِ بازسازی» — یک `commit()`.
   *  پس از آن تا «بازسازی از سرور» ([rebuildRestored]) نه Pull، نه هم‌ترازی (`prepare`): cursor ِ کهنه opهای خودِ گوشیِ
   *  پیشین را دوباره می‌آورد و `prepare` ردیف‌های کهنهٔ پشتیبان را روی سرور برنده می‌کرد.
   *
   *  @param ledger دفترِ روی میزِ همین حساب؛ `null` ⇒ نامعلوم (فقط حصار، هرگز «بستن» — تصمیمِ بی دفتر نصفه است)
   *  @param baseline `SyncCommit.record` ِ همین کلید
   *  @return تصمیم
   */
  fun fence(
    lock: Any,
    app: Context,
    store: SyncStore,
    state: SyncStateV1,
    ledger: ShopData?,
    queued: Int,
    baseline: ((ShopData) -> Unit)?,
  ): RestoredDevice.Verdict = synchronized(lock) {
    //  شناسه یا حالِ ناخوانا ⇒ هیچ تصمیمی، هیچ نوشتنی
    val device = runCatching { store.deviceUid }.getOrDefault("")
    val seen = runCatching {
      val rows = ledger?.let { rowsOf(it) } ?: 0
      val history = rows > 0 || queued > 0 || state.cursor > 0 || state.shadow.isNotBlank() ||
        state.reconciledAt > 0 || state.snapshotAt > 0 || state.lastPushAt > 0
      Triple(state.device, state.replica, history)
    }.getOrNull() ?: return@synchronized RestoredDevice.Verdict.UNDECIDED
    val (bound, kept, history) = seen
    val verdict = RestoredDevice.decide(device, bound, kept, store.restoredAt, history)
    when (verdict) {
      RestoredDevice.Verdict.UNDECIDED -> Unit
      //  حصارِ پیشینِ بی دفتر ([id]) ⇒ پایهٔ سایه همین حالا (گامِ ۱)
      RestoredDevice.Verdict.SAME ->
        if (ledger != null && baseline != null && state.restoredRebuild && state.shadow.isBlank()) baseline(ledger)
      RestoredDevice.Verdict.BIND -> if (ledger != null) state.commitBinding(device, fence = false, replica = kept)
      RestoredDevice.Verdict.FENCE -> {
        if (ledger != null && baseline != null && state.shadow.isBlank()) baseline(ledger)
        val account = SyncStateV1.currentAccount(app).trim()
        if (account.isNotEmpty()) app.getSharedPreferences(DOC_SERIES, Context.MODE_PRIVATE).edit().remove(account).commit()
        val next = RestoredDevice.remap(kept, device, account, store.replicaOwner)
        if (next == device && account.isNotEmpty() && store.replicaOwner.isBlank()) store.replicaOwner = account
        check(state.commitBinding(device, fence = true, replica = next)) { "حالِ همگام‌سازی روی دیسک ننشست" }  // داده: متنِ خامِ `lastError` — جزئیاتِ پشتیبانی
      }
    }
    verdict
  }

  /**
   *  حسابِ کلیدِ همگام‌سازی (`SyncV1Engine.keyOf`). گوشیِ بازیابی‌شده: `account_id` با `tohid-sync-v1.xml` نیامد ⇒ یک بار صاحبِ
   *  دفترِ روی میز (`LedgerOwner`، با پشتیبان می‌آید) — همان کلیدی که صف و ژورنالِ پشتیبان دارند و پیش از این اصلاح هم
   *  همان بود. بی این، ذخیره‌های پیش از ورود در کلیدِ دستگاه می‌ماندند و هیچ‌وقت نمی‌رفتند (`SyncAccountKey.carryFrom`).
   */
  fun account(app: Context, store: SyncStore): String {
    val kept = SyncStateV1.currentAccount(app)
    if (kept.isNotBlank()) return kept
    val restored = runCatching { store.deviceUid.isNotEmpty() && store.restoredAt > 0 }.getOrDefault(false)
    val owner = if (restored) LedgerOwner.currentUser(app).trim() else ""
    if (owner.isNotEmpty()) SyncStateV1.setCurrentAccount(app, owner)
    return owner
  }

  /** `SyncStore.deviceUid` همین حالا ساخته می‌شود: ردِ کارِ پیشین هست؟ ⇒ زمانِ همین حالا، وگرنه ۰ (`RestoredDevice.mintedOverHistory`) */
  fun mintedOver(app: Context): Long {
    val owner = runCatching { LedgerOwner.currentUser(app) }.getOrDefault("")
    val files = runCatching { app.filesDir.list()?.toList() }.getOrNull().orEmpty()
    return if (RestoredDevice.mintedOverHistory(owner, files)) System.currentTimeMillis() else 0L
  }

  /** شمارِ ردیف‌های دفتر (همهٔ مجموعه‌های همگام) — حصارِ v3 هم (`sync/v3/V3State.kt`) */
  fun rowsOf(d: ShopData): Int {
    val tree = LedgerDiff.toJson(d)
    return LedgerDiff.COLLECTIONS.sumOf { (tree[it] as? JsonArray)?.size ?: 0 }
  }

  /** این حساب هنوز «بازسازی از سرور» ِ پس از بازیابی را می‌خواهد */
  fun restoredPending(app: Context): Boolean =
    runCatching { SyncStateV1(app, SyncV1Engine.of(app).currentKey()).restoredRebuild }.getOrDefault(false)

  /**
   *  «بازسازی از سرور» ِ گوشیِ بازیابی‌شده — همان راهِ «بازیابیِ پشتیبان» ِ دستگاهِ همگام (S143، `ShopStore.restoreBackup` با
   *  دفترِ همین حالا): صف **اول** تا ته (opهای پشتیبان با همان op_id ⇒ سرور «تکراری» می‌شمارد)، بعد ردیف‌ها فقط از Snapshot،
   *  تنظیم‌ها از دفترِ همین حالا، کفِ فاکتور، سایه و cursor ِ همان Snapshot؛ دفترِ پیشین کنار می‌رود (`.unreadable-<زمان>`)،
   *  پاک نمی‌شود. حسابِ هرگز همگام‌نشده با سرورِ خالی ⇒ همان دفتر خودش سایه (`resetTo`) و `prepare` ِ همیشه.
   *  نشد (بی ورود، بی شبکه، صفِ نرفته، دیسک) ⇒ هیچ چیزی عوض نشده، «در انتظار» می‌ماند و دورِ بعد دوباره.
   *  @return انجام شد (یا لازم نبود)
   */
  suspend fun rebuildRestored(app: Context, store: ShopStore): Boolean {
    val key = SyncV1Engine.of(app).currentKey()
    if (!SyncStateV1(app, key).restoredRebuild) return true
    //  بی ورود «بازیابی» راهِ دفترِ محلی را می‌رفت (`RestoreRoute.LOCAL`) — این‌جا فقط با سرور
    if (!Backend.isReady(app)) return false
    val done = store.restoreBackup(store.ledgerNow()).ok
    //  همان کلید (ورودِ دیگری میانِ کار نیامد) ⇒ «در انتظار» برداشته
    if (done && SyncV1Engine.of(app).currentKey() == key) SyncStateV1(app, key).restoredRebuild = false
    return done
  }
}
