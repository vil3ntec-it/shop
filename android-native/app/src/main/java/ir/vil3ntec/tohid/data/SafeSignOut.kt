package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.sync.SignOutSafety
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.sync.AutoSync
import ir.vil3ntec.tohid.sync.SyncStore
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *  خروج از حساب **بی گم شدنِ هیچ داده‌ای** — یک راه برای گوشی، کامپیوتر و آیفون (برگهٔ v3 و صفحهٔ پیشین).
 *
 *   ۱) [check]: هر تغییرِ دفتر op می‌شود، دفترِ هرگز‌هم‌ترازنشده هم‌تراز، صف تا ته فرستاده و پاسخِ سرور سنجیده
 *      (`SyncV1Engine.flushForSignOut` ⇐ `SignOutSafety.decide`). تا سرور **همه** را نگرفته، هیچ چیزِ محلی پاک نمی‌شود.
 *   ۲) [force] (فقط پس از دو تأیید): پشتیبانِ دیدنی (`BackupMirror`) **و** گاوصندوقِ رمزدارِ همان حساب
 *      (`SignOutVault`)؛ هر کدام نشد ⇒ خروجی نیست. با ورودِ دوبارهٔ همان حساب برمی‌گردند و می‌روند (`AccountBind`).
 *   ۳) [finish]: باطل کردنِ نشست روی سرور (اگر شد) و پاک کردنِ توکن و نامِ حساب. **دفتر، صف، سایه و کلیدِ
 *      همگام‌سازیِ همین حساب دست نمی‌خورند**: دفترِ روی میز به نامِ همین حساب سند خورده (`LedgerOwner`) و حسابِ
 *      دیگری که وارد شود آن را زیرِ نامِ صاحبش بایگانی می‌کند؛ همان حساب که برگشت، همه سرِ جایش است.
 */
object SafeSignOut {

  suspend fun check(context: Context, store: ShopStore): Pair<SignOutSafety.Verdict, SyncV1Engine.SignOutFlush> {
    val app = context.applicationContext
    val engine = SyncV1Engine.of(app)
    engine.rebind()
    val readable = store.unreadable.value == null && !store.training
    val flush = withContext(Dispatchers.IO) {
      engine.flushForSignOut(
        read = if (readable) ({ store.ledgerNow() }) else null,
        write = { next -> store.applyFromServerNow(next) },
      )
    }
    val verdict = SignOutSafety.decide(
      SignOutSafety.Flush(
        queued = flush.queued,
        legacy = AutoSync.pendingCount,
        rejected = flush.rejected,
        online = flush.online,
        signedIn = flush.signedIn,
        failed = flush.error.isNotBlank(),
        reconciled = flush.reconciled,
      ),
    )
    return verdict to flush
  }

  /**
   *  خروجِ اجباری، پس از دو تأیید: پشتیبانِ دیدنی + گاوصندوقِ رمزدارِ همین حساب.
   *  @return نشانیِ پشتیبان؛ شکست ⇒ هیچ چیزی عوض نشده و خروجی هم نیست
   */
  suspend fun keepForAccount(context: Context): Result<String> = withContext(Dispatchers.IO) {
    val app = context.applicationContext
    runCatching {
      val backup = BackupMirror.write(app).getOrThrow()
      sealForAccount(app)
      backup.absolutePath
    }
  }

  /**
   *  صفِ نرفتهٔ همین حساب ⇒ گاوصندوقِ رمزدارِ همان حساب (`SignOutVault`).
   *  رمز در دسترس نبود (`-1`) ⇒ صفِ ساده زیرِ کلیدِ همین حساب سرِ جایش می‌ماند — هرگز پاک نمی‌شود — و همان
   *  حساب که برگشت فرستاده می‌شود؛ پنهان کردن هیچ‌گاه بر نگه داشتن مقدم نیست.
   */
  fun sealForAccount(context: Context): Int {
    val app = context.applicationContext
    val user = SyncStore(app).accountId.ifBlank { LedgerOwner.currentUser(app) }
    return runCatching { SyncV1Engine.of(app).sealQueueFor(user, LedgerOwner.currentShop(app)) }.getOrDefault(-1)
  }

  /** خروجِ واقعی — فقط پس از [check] ِ آزاد یا [keepForAccount] ِ موفق */
  suspend fun finish(context: Context) {
    val app = context.applicationContext
    runCatching { Backend.auth(app).signOut() }
    SyncStore(app).signOut()
    //  ⛔ `SyncStateV1.currentAccount` عمداً می‌ماند: صف و سایهٔ همین حساب تا ورودِ بعدی (هر حسابی) کلیدِ
    //  خودش را دارند؛ ورودِ حسابِ دیگر آن را با `AccountBind` به شناسهٔ خودش می‌برد، نه این‌جا با پاک کردن.
    runCatching { ir.vil3ntec.tohid.ui.PromoLive.reset(app) }
  }
}
